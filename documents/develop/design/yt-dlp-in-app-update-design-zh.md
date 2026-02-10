# PigeonPod 应用内更新 yt-dlp 方案（Docker 持久化版）

## 1. 背景与目标

当前系统在 Docker 运行时通过 `pip3 install "yt-dlp[default,curl-cffi]"` 预装 yt-dlp，并由后端 `DownloadHandler` 直接调用 `yt-dlp` 命令执行下载。

本方案目标：

- 在前端 `UserSetting` 页面提供“应用内更新 yt-dlp”能力。
- 支持 `stable` / `nightly` 两个更新渠道。
- 更新结果必须在 Docker 容器重建后仍然生效（依赖 `/data` volume 持久化）。
- 使用异步任务 + 轮询状态，避免阻塞请求。
- 更新失败自动回滚到旧版本，保证下载能力可用。

## 2. 设计决策

- 更新渠道：`stable` / `nightly`。
- 持久化目录：`/data/tools/yt-dlp`（可配置）。
- 更新方式：后端创建异步更新任务，前端轮询任务状态。
- 失败策略：仅在新版本验证成功后切换；若切换后检查失败，立即回滚到旧版本。
- 安全边界：不允许用户传递任意 shell 参数，仅允许固定渠道值。

## 3. 架构改造点

### 3.1 新增后端能力

1. `YtDlpRuntimeService`（新）
   - 管理受控目录结构。
   - 读取当前生效版本。
   - 执行更新任务（下载安装、验证、原子切换、回滚）。
   - 提供任务状态查询。

2. `AccountController`（扩展）
   - `GET /api/account/yt-dlp/runtime`
   - `POST /api/account/yt-dlp/update`
   - `GET /api/account/yt-dlp/update-status`

3. `DownloadHandler`（改造）
   - 从 `YtDlpRuntimeService` 获取 yt-dlp 可执行命令（优先受控版本，回退系统 `yt-dlp`）。

### 3.2 前端改造点

在 `frontend/src/pages/UserSetting/index.jsx` 增加“yt-dlp 版本管理”区块：

- 显示当前版本、当前渠道、最近更新状态。
- 支持选择渠道并触发更新。
- 更新中展示 loading，并轮询状态。
- 成功/失败通过通知提示，并刷新运行时信息。

## 4. 目录与状态持久化

默认根目录（可配置）：

```text
/data/tools/yt-dlp/
  current -> versions/2026.02.07-170501/
  versions/
    2026.01.30-120000/
    2026.02.07-170501/
  state.json
```

说明：

- `versions/`：每次更新生成一个新目录。
- `current`：符号链接，指向当前生效版本目录。
- `state.json`：保存最近一次异步任务状态。

## 5. 更新流程（异步任务）

1. 前端提交更新请求（channel=`stable|nightly`）。
2. 后端校验并尝试获取全局更新锁；若已有任务运行，直接返回业务错误。
3. 后端在独立线程执行：
   - 创建 staging 目录（`versions/<timestamp>`）。
   - 执行 pip 安装：
     - `stable`: `python3 -m pip install --no-cache-dir -U "yt-dlp[default,curl-cffi]" --target <dir>`
     - `nightly`: `python3 -m pip install --no-cache-dir -U --pre "yt-dlp[default,curl-cffi]" --target <dir>`
   - 使用 `PYTHONPATH=<dir>` 执行 `python3 -m yt_dlp --version` 验证。
   - 验证成功后原子切换 `current`。
   - 可选保留最近 N 个版本目录，其余清理。
4. 任一阶段失败：
   - 不切换或切换失败时回滚 `current` 到旧版本。
   - 记录失败原因写入 `state.json`。
5. 前端轮询 `update-status`，直到 `SUCCESS` 或 `FAILED`。

## 6. API 契约（草案）

### 6.1 `GET /api/account/yt-dlp/runtime`

返回示例：

```json
{
  "code": 200,
  "msg": "ok",
  "data": {
    "managedRoot": "/data/tools/yt-dlp",
    "managedReady": true,
    "version": "2026.02.01",
    "channel": "stable",
    "updating": false,
    "status": {
      "state": "SUCCESS",
      "startedAt": "2026-02-07T17:05:01",
      "finishedAt": "2026-02-07T17:05:26",
      "beforeVersion": "2026.01.30",
      "afterVersion": "2026.02.01",
      "error": null
    }
  }
}
```

### 6.2 `POST /api/account/yt-dlp/update`

请求：

```json
{
  "channel": "stable"
}
```

响应：

```json
{
  "code": 200,
  "msg": "ok",
  "data": {
    "accepted": true,
    "state": "RUNNING"
  }
}
```

### 6.3 `GET /api/account/yt-dlp/update-status`

返回最近任务状态，前端每 2~3 秒轮询。

## 7. 配置项

在 `application.yml` 中新增：

- `pigeon.yt-dlp.managed-root`：默认 `/data/tools/yt-dlp`
- `pigeon.yt-dlp.keep-versions`：默认 `3`
- `pigeon.yt-dlp.update-timeout-seconds`：默认 `300`

可通过 Docker 环境变量覆盖（Spring relaxed binding）。

## 8. 安全与鲁棒性

- 所有接口保留 `@SaCheckLogin`。
- 渠道参数严格白名单校验。
- 子进程命令使用参数数组，不拼接 shell 字符串。
- 设置执行超时，超时即失败并记录。
- 更新失败不影响当前稳定版本下载。
- 应用重启后若发现状态仍是 `RUNNING`，应标记为 `FAILED`（stale 任务恢复）。

## 9. 前端交互说明

在 `UserSetting` 中新增区块：

- 字段：当前版本、渠道、状态。
- 操作：渠道选择、立即更新。
- 状态：
  - `RUNNING`：按钮禁用，展示 `Loader`。
  - `SUCCESS`：toast 成功并刷新版本。
  - `FAILED`：toast 失败并显示错误摘要。

## 10. 验收标准

1. 用户可在 `UserSetting` 发起 yt-dlp 更新。
2. 更新过程异步，不阻塞页面。
3. 更新失败后仍能继续下载（自动回滚）。
4. Docker 容器重建后，更新后的 yt-dlp 版本仍生效。
5. 下载链路对旧环境保持兼容（无受控版本时回退系统 `yt-dlp`）。
