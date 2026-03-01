# Release Notes / 发布说明

- Release date / 发布日期: 2026-03-01
- Version / 版本: v1.23.0

## Features / 功能
- Add default `minimumDuration` in feed configuration and change its unit from minutes to seconds. / 在订阅源默认配置中新增 `minimumDuration`，并将其单位从分钟改为秒。（849811b）
- Add source video link to episode titles in the web app and unify cover-click behavior by removing redirect logic for undownloaded episodes. / 在 Web 端剧集标题中新增源视频链接，并移除未下载剧集封面跳转逻辑以统一封面点击行为。（9557686）
- Add `<itunes:block>` to RSS feeds to reduce unintended indexing of private feeds by podcast platforms. / 在 RSS 中新增 `<itunes:block>` 标签，降低私有订阅被播客平台意外索引的风险。（a3066a9）
- Add source video link and source channel link (playlist subscriptions only) in RSS content. / 在 RSS 内容中新增源视频链接与源频道链接（仅播放列表订阅类型）。（16b9a70）
- Wrap RSS descriptions in CDATA, and improve playlist source channel rendering using a standard `<a>` tag plus line breaks. / 将 RSS 描述内容包裹为 CDATA，并通过标准 `<a>` 标签与换行优化播放列表来源频道显示。（8416cd4）

## Fixes / 修复
- Split audio download and chapter-embedding into separate procedures so chapter embedding errors do not fail downloads. / 将音频下载与章节嵌入拆分为独立流程，避免章节嵌入失败导致下载失败。（d194d29）
- Fix the issue where default `minimumDuration` did not take effect for new subscriptions. / 修复新建订阅时默认 `minimumDuration` 不生效的问题。（d2ac138）
- Fix custom artwork not being displayed. / 修复自定义封面不显示的问题。（a37ffb9）

## Breaking Changes / 破坏性变更
- None / 无
