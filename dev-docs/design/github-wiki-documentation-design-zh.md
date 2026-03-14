# PigeonPod GitHub Wiki 文档体系设计

## 1. 背景

当前 PigeonPod 面向用户的文档主要放在仓库 `documents/` 目录中，并通过 `README.md` 中的链接暴露入口。

这种方式在文档数量较少时足够简单，但随着项目功能和用户场景增长，已经出现几个问题：

- 用户文档入口分散，阅读路径依赖 `README.md` 中的零散链接。
- `documents/` 同时承载多语言 README、副文档与素材，信息边界不清晰。
- 后续如果继续扩充“安装、配置、排错、FAQ、进阶自定义”等内容，缺少稳定导航结构。
- 当前目标用户主要来自 GitHub，天然会寻找仓库内更直接的文档入口。

基于当前项目的人力约束和用户画像，本方案不追求建设复杂的产品型 Docs 平台，而是采用 **GitHub Wiki 作为面向技术型自托管用户的轻量文档入口**，并逐步取消 `documents/` 目录中的对外用户文档。

## 2. 目标与非目标

## 2.1 目标

本方案的目标是：

1. 为 GitHub 上发现 PigeonPod 的技术用户提供清晰、低成本、可持续维护的使用文档入口。
2. 让用户能够在 Wiki 中完成：
   - 安装与升级
   - 核心配置
   - 日常使用
   - 常见排错
   - 少量进阶自定义
3. 让 `README.md` 回归项目介绍与导航入口角色，不再承担长文档职责。
4. 将内部设计、架构、实现细节继续保留在仓库 `dev-docs/`，与用户文档明确分层。

## 2.2 非目标

本方案明确不做以下事情：

- 不建设类似 Cloudflare Docs 的产品级文档站。
- 不引入复杂静态站点框架、版本化文档体系、多层级导航系统。
- 不在 Wiki 中维护完整的多语言文档集合。
- 不把内部设计文档、开发实现方案整体搬进 Wiki。
- 不要求所有功能都必须有独立 Wiki 页面。

## 3. 用户画像与设计判断

本方案服务的核心用户不是泛大众用户，而是这类技术型自托管用户：

- 在 GitHub 上发现项目；
- 能自己使用 Docker、JAR、环境变量、反向代理等基础技术；
- 愿意根据文档完成部署、排错和轻量定制；
- 主要需要“能跑起来、知道怎么配、出问题怎么查”。

基于这个画像，GitHub Wiki 的能力已经足够满足主要诉求：

- 维护成本低，直接使用 Markdown；
- 与 GitHub 仓库天然邻接，用户容易发现；
- 适合承载安装、配置、排错、FAQ、进阶玩法这类操作型内容；
- 后续如果要迁移到仓库 `docs/` 或 Pages，Markdown 资产也可以复用。

因此本方案的核心判断是：

**PigeonPod 的 GitHub Wiki 应定位为“轻量用户操作手册”，而不是复杂的产品文档平台。**

## 4. 文档分层策略

未来文档体系按以下边界分层：

### 4.1 `README.md`

职责：

- 项目简介
- 核心特性
- 最短部署入口
- 指向 Wiki、Releases、Issues 的导航

约束：

- 不承载长篇操作手册
- 不承载大量 FAQ
- 不承载多篇独立指南正文

### 4.2 GitHub Wiki

职责：

- 面向用户的安装、配置、使用、排错、FAQ、进阶自定义文档

约束：

- 仅维护英文版
- 优先解决“如何操作”和“如何排错”
- 不复制内部设计细节

### 4.3 `dev-docs/`

职责：

- 架构设计
- 功能方案
- 实现评审稿
- 发布说明
- 维护流程文档

约束：

- 面向维护者和贡献者
- 不作为普通用户主入口

### 4.4 `documents/`

处理策略：

- 作为历史目录逐步迁移内容后删除
- 迁移完成后不再新增对外用户文档
- 静态素材可按实际需要迁移到仓库其他稳定位置，避免继续依赖该目录承载文档体系

## 5. 语言策略

本方案采用：

- **Wiki 仅维护英文**

理由：

1. GitHub Wiki 不提供适合当前目标的原生多语言文档体系。
2. 当前项目人力不支持持续维护多语言 Wiki。
3. PigeonPod Wiki 的目标用户主要是能够完成自托管部署的技术用户，英文文档可接受。
4. 如果后续确实需要补充其他语言，应基于实际使用数据单独评估，而不是在初期预设多语言负担。

## 6. Wiki 信息架构

Wiki 页面控制在少量核心主题，不追求一次性铺满。

## 6.1 顶层页面结构

建议的首批 Wiki 页面如下：

1. `Home`
2. `Quick Start`
3. `Installation`
4. `Upgrade`
5. `Configuration Overview`
6. `YouTube API Key Setup`
7. `YouTube Cookies Setup`
8. `Find a YouTube Channel ID`
9. `Daily Usage`
10. `Feed Settings Explained`
11. `Media Formats and Quality`
12. `Storage and Backup`
13. `Troubleshooting`
14. `FAQ`
15. `Advanced Customization`

## 6.2 页面职责定义

### `Home`

职责：

- 说明 PigeonPod 是什么
- 说明这份 Wiki 适合谁
- 提供最关键的入口链接

必须包含：

- Quick Start 入口
- Troubleshooting 入口
- Advanced Customization 入口
- 仓库、Release、Issue 入口

### `Quick Start`

职责：

- 让用户在最短路径内完成首次部署与首次使用验证

建议覆盖：

- Docker Compose 启动
- 默认登录
- 配置 YouTube API Key
- 添加第一个 feed
- 验证 RSS 或下载功能是否正常

### `Installation`

职责：

- 说明支持的安装方式与环境要求

建议覆盖：

- Docker
- JAR
- Java / yt-dlp / 数据目录要求

### `Upgrade`

职责：

- 说明版本升级路径和升级后检查项

建议覆盖：

- 容器升级
- JAR 升级
- 升级前备份建议
- 升级后核验点
- 常见升级失败回滚建议

### `Configuration Overview`

职责：

- 给出配置项的整体地图，而不是堆所有参数细节

建议覆盖：

- auth
- storage
- yt-dlp
- proxy
- cookies
- API key

### `YouTube API Key Setup`

职责：

- 说明如何申请和配置 YouTube API Key

### `YouTube Cookies Setup`

职责：

- 说明 cookies 在什么场景下需要，以及如何配置

### `Find a YouTube Channel ID`

职责：

- 提供一个短小、直接、纯操作型的说明页

### `Daily Usage`

职责：

- 说明用户在日常使用中的关键操作路径

建议覆盖：

- 添加订阅
- 刷新
- 获取历史节目
- 下载 / 重试 / 取消 / 删除
- 将 RSS 用于播客客户端

### `Feed Settings Explained`

职责：

- 解释 feed 相关配置项的实际含义和效果

建议覆盖：

- auto download
- delay minutes
- maximum episodes
- keyword filters
- subtitle
- chapters
- audio / video 相关设置入口

### `Media Formats and Quality`

职责：

- 合并当前音频质量和视频编码相关说明

约束：

- 只解释用户需要做出的选择
- 不展开成媒体技术百科

### `Storage and Backup`

职责：

- 解释数据目录、媒体文件、数据库和备份要点

### `Troubleshooting`

职责：

- 按“症状”组织常见故障与排查步骤

建议覆盖：

- 无法登录
- API quota 不足
- 下载失败
- cookies 失效
- yt-dlp 版本问题
- RSS 无内容

### `FAQ`

职责：

- 收敛高频重复问题

约束：

- 仅收录重复出现的问题
- 控制在少量高价值条目内

### `Advanced Customization`

职责：

- 为技术用户提供高阶部署和轻量定制入口

建议覆盖：

- 反向代理
- 关闭内置认证的前提与风险
- 本地开发与重新打包入口
- 指向 `dev-docs/` 的深度链接

## 7. 导航规范

Wiki 采用简单导航，不设计复杂层级。

## 7.1 `_Sidebar.md` 结构

`_Sidebar.md` 建议固定为 4 组：

- Getting Started
  - Home
  - Quick Start
  - Installation
  - Upgrade
- Setup
  - Configuration Overview
  - YouTube API Key Setup
  - YouTube Cookies Setup
  - Find a YouTube Channel ID
- Usage
  - Daily Usage
  - Feed Settings Explained
  - Media Formats and Quality
  - Storage and Backup
- Help
  - Troubleshooting
  - FAQ
  - Advanced Customization

## 7.2 `_Footer.md` 结构

`_Footer.md` 只保留全局链接：

- Repository
- Releases
- Issues
- Discussions
- 安全提示：不要在公开互联网中暴露关闭内置认证的实例

## 7.3 导航控制原则

- 不新增超过两层的导航结构。
- 新页面默认先挂到既有分组中，不增加新的顶层分组。
- 若某个主题需要更多页面，优先合并内容而不是继续细分。

## 8. 页面写作规范

Wiki 面向技术用户，但仍需保持清晰、直接、可操作。

## 8.1 页面模板

除 `Home` 和 `FAQ` 外，普通页面建议使用统一结构：

1. What this page is for
2. Prerequisites
3. Steps
4. Verify
5. Common failures
6. Related pages

## 8.2 内容风格

- 只写英文。
- 直接说明如何做，不写长背景故事。
- 优先写用户行动步骤、验证方法、失败信号和修复方式。
- 默认读者具备基本技术背景，不需要从零解释 Docker、日志、代理等通用概念。
- 单页只解决一个主题，不做大杂烩页面。

## 8.3 篇幅控制

- 单页尽量保持精简。
- 当某页超过约 `1000-1500` 词时，应优先评估是否拆分。
- 拆分后必须同步更新 `Home` 和 `_Sidebar.md` 中的导航。

## 8.4 代码与命令规范

- 代码块优先使用可直接执行的最小示例。
- 避免提供与当前主线版本不一致的过期参数。
- 所有命令示例默认面向 Linux / Docker 常见环境。

## 8.5 链接规范

- 用户操作页之间使用相互链接，避免孤立页面。
- 需要深入设计细节时，直接链接仓库中的 `dev-docs/` 文件。
- 不把内部设计文档内容大段复制到 Wiki。

## 9. 内容边界规范

为了避免 Wiki 失控膨胀，需要明确哪些内容应该写，哪些不应该写。

## 9.1 应该进入 Wiki 的内容

- 安装与升级说明
- 核心配置说明
- 高价值的使用指南
- 高频排错
- 高频 FAQ
- 少量进阶自定义入口

## 9.2 不应该进入 Wiki 的内容

- 后端架构设计
- 数据库设计方案
- 功能评审稿
- 大量历史背景
- 很少有人会使用的实现细节
- 尚未发布功能的设计草案

## 9.3 需要链接到仓库文档而不是复制的内容

- 架构设计
- 复杂功能实现方案
- 发布说明
- 维护者专用流程

## 10. 从 `documents/` 的迁移策略

Wiki 建设采用“按价值分批迁移”，而不是一次性大搬家。

## 10.1 直接迁移

以下内容可以直接改写并迁移到 Wiki：

- `How to get YouTube API Key`
- `How to setup YouTube Cookies`
- `How to get YouTube channel ID`

## 10.2 合并后迁移

以下内容不应继续拆成多个零散用户文档，建议合并后进入单页：

- `audio-quality-guide`
- `video-encoding-guide`

合并目标页：

- `Media Formats and Quality`

## 10.3 不迁移

以下内容不再作为 Wiki 维护对象：

- 多语言 README 副本
- 仅为 README 服务的重复内容
- 单纯素材目录结构本身

## 10.4 保留在仓库

以下内容继续保留在仓库中：

- `dev-docs/architecture/**`
- `dev-docs/design/**`
- `dev-docs/release-notes/**`

## 10.5 删除时机

当以下条件同时满足时，可删除 `documents/`：

1. 所有仍对外提供价值的用户文档都已迁移到 Wiki；
2. `README.md` 已切换到新的 Wiki 入口；
3. 不再有页面依赖 `documents/` 中的相对链接；
4. 截图与素材已迁移到新的稳定位置或不再需要。

## 11. Wiki 建设节奏

## 11.1 第一阶段：建立骨架

目标：

- 创建 Wiki 基本页面
- 建立 `Home`、`_Sidebar.md`、`_Footer.md`
- 明确首批核心入口

首批建议优先完成：

1. `Home`
2. `Quick Start`
3. `Troubleshooting`
4. `Feed Settings Explained`
5. `YouTube API Key Setup`

## 11.2 第二阶段：迁移高价值内容

目标：

- 将当前最有使用价值的 `documents/` 内容迁移到 Wiki
- 合并重复和碎片化说明

## 11.3 第三阶段：收缩旧目录

目标：

- 更新 `README.md`
- 移除对 `documents/` 的公开入口依赖
- 删除历史目录

## 12. 长期维护规则

Wiki 长期可维护的关键不在于“写得多”，而在于“边界稳定、更新集中”。

## 12.1 版本发布后的检查页

每次发布新版本后，只强制检查以下页面是否需要更新：

1. `Home`
2. `Quick Start`
3. `Upgrade`
4. `Feed Settings Explained`
5. `Troubleshooting`

这样可以显著降低文档维护成本。

## 12.2 新功能文档准入规则

只有在满足以下任一条件时，才为新功能新增 Wiki 内容：

- 用户需要明确操作步骤；
- 用户容易踩坑；
- 用户经常提问；
- 没有文档会显著增加支持成本。

否则：

- 优先补充到已有页面，而不是新建页面。

## 12.3 FAQ 准入规则

FAQ 仅收录已经重复出现的问题，不提前预测问题。

## 12.4 排错页维护规则

`Troubleshooting` 必须按“用户观察到的症状”组织，而不是按代码模块组织。

推荐格式：

- Symptom
- Possible causes
- How to verify
- How to fix

## 12.5 过期内容处理规则

- 若某页长期过时，优先删除或合并。
- 不保留“半正确、半失效”的旧文档。
- 若无法立即更新，至少在页面开头明确标记状态。

## 13. 维护者执行原则

后续建设 GitHub Wiki 时，维护者需要遵守以下执行原则：

1. 优先保持入口页准确，而不是追求覆盖一切。
2. 优先维护高频页面，而不是平均维护所有页面。
3. 优先合并相近主题，避免文档碎片化。
4. 需要深度技术细节时，链接到仓库文档，不在 Wiki 复写。
5. 若新增页面会让导航复杂化，应先质疑其必要性。

## 14. 结论

PigeonPod 的 GitHub Wiki 不应被设计为复杂的产品文档平台，而应被建设为：

- 面向技术型自托管用户；
- 聚焦安装、配置、使用、排错；
- 成本可控、结构稳定、长期可维护的轻量用户手册。

在这个定位下：

- `README.md` 负责入口；
- Wiki 负责用户操作文档；
- `dev-docs/` 负责内部设计与维护文档；
- `documents/` 最终被淘汰。

本文件作为 PigeonPod Wiki 长期建设的统一规范，后续如需新增页面、迁移旧文档或调整导航，应以本方案为准。
