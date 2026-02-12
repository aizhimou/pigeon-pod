# PigeonPod 关键词组合过滤（`+` / `,`）设计方案

## 1. 背景与目标

当前 `title_contain_keywords` / `description_contain_keywords` / `title_exclude_keywords` / `description_exclude_keywords` 仅支持“逗号分隔，任一命中”。

这会导致复杂筛选表达能力不足，例如：

- 需要 `(raw AND full highlights) OR (smackdown AND full highlights)` 时，现有逻辑无法准确表达。
- 仅有 `raw` 或仅有 `full highlights` 也会被误匹配。

本方案目标：

1. 在不引入复杂表达式引擎的前提下，提供轻量组合能力。
2. 统一四个关键词字段的语法与心智模型。
3. 保持向后兼容现有配置。

---

## 2. 语法定义

统一语法：

- `,` 表示 OR（组间“任一组命中”）
- `+` 表示 AND（组内“全部词命中”）

字段示例：

```text
raw+full highlights,smackdown+full highlights
```

语义：

```text
(raw AND full highlights) OR (smackdown AND full highlights)
```

---

## 3. 四个字段的统一语义

## 3.1 contain 字段

适用字段：

- `title_contain_keywords`
- `description_contain_keywords`

判定规则：

- 若配置为空：视为“不过滤”，直接通过。
- 若配置非空：任一 OR 组命中（组内 AND 全命中）即通过。
- 全部组不命中则不通过。

## 3.2 exclude 字段

适用字段：

- `title_exclude_keywords`
- `description_exclude_keywords`

判定规则：

- 若配置为空：视为“不过滤”，不排除。
- 若配置非空：任一 OR 组命中（组内 AND 全命中）即排除。
- 全部组不命中则不排除。

说明：  
`contain` 与 `exclude` 语法相同，仅“命中后的动作”不同（通过 vs 排除）。

---

## 4. 匹配规则细节

1. 大小写不敏感（沿用当前实现）。
2. 仍为“子串匹配”（`contains`），不引入正则与词边界。
3. 解析时去除首尾空白，忽略空 token。
4. 连续分隔符产生的空 token 忽略（例如 `a++b` 解析为 `a+b`）。

---

## 5. 兼容性策略

保持完全向后兼容：

- 旧配置如 `raw,smackdown` 仍按 OR 逻辑生效。
- 旧配置中无 `+` 时，行为与现在一致。
- 数据库无需迁移；`V26` 的“逗号分隔”历史规则继续可用。

---

## 6. 代码落点设计

核心改造点：

- `backend/src/main/java/top/asimov/pigeon/helper/YoutubeVideoHelper.java`
  - 当前方法：`notMatchesKeywordFilter(String text, String containKeywords, String excludeKeywords)`

建议重构：

1. 新增表达式解析方法（私有）：
   - 输入：`raw+full highlights,smackdown+full highlights`
   - 输出：`List<List<String>>`（外层 OR 组，内层 AND token）

2. 新增统一匹配方法（私有）：
   - `matchesExpression(normalizedText, groups)`：
   - 任一组内 token 全命中则返回 `true`

3. 原有 `notMatchesKeywordFilter` 改为：
   - contain 非空且不匹配 => `true`
   - exclude 非空且匹配 => `true`
   - 否则 `false`

前端改造：

- `frontend/src/components/EditFeedModal.jsx`
  - 可保持现有 `TagsInput`，只补充 placeholder / help 文案说明：
  - `+` 表示并且，`,` 表示或者。
  - 示例：`raw+full highlights,smackdown+full highlights`

---

## 7. 示例与预期

配置：

```text
title_contain_keywords = raw+full highlights,smackdown+full highlights
```

标题样例：

1. `xxxx raw xxx xxx xx full highlights` -> 命中（通过）
2. `xx xxx smackdown xxxx xx full highlights` -> 命中（通过）
3. `xxx xx raw xxxx` -> 不命中（不通过）
4. `xxxx xx xxxx smackdown xxxx` -> 不命中（不通过）
5. `xxxx full highlights xxxx xx` -> 不命中（不通过）

exclude 示例：

```text
title_exclude_keywords = reaction+full highlights,rumor
```

含义：

- 命中 `reaction AND full highlights` 的标题排除；
- 命中 `rumor` 的标题也排除。

---

## 8. 测试方案

## 8.1 单元测试（后端必须）

覆盖：

1. 纯 OR 旧语法回归：`a,b`
2. 纯 AND：`a+b`
3. OR+AND 组合：`a+b,c+d`
4. 空格与空 token 容错：` a + b , , c `
5. contain/exclude 四字段语义差异验证
6. 大小写不敏感验证
7. 中文与多语言字符（非 ASCII）匹配验证

## 8.2 集成验证

在频道与播放列表：

- 预览
- 初始化
- 定时刷新
- 历史抓取

确保过滤结果一致且无回归。

---

## 9. 范围与限制

本方案明确不支持：

1. 顺序匹配（例如 `raw` 必须在 `full highlights` 前）
2. 词边界匹配（完整单词）
3. 正则表达式
4. 括号优先级

如未来需要上述能力，再升级为规则树/查询 DSL。

---

## 10. 交付建议

MVP（本次）：

1. 后端解析与匹配逻辑改造（统一四字段）。
2. 单元测试补齐。
3. 前端文案提示升级（不改交互组件）。

后续：

1. 可视化规则构建器（降低手写语法门槛）。
2. 可选“词边界模式”与“顺序模式”。
