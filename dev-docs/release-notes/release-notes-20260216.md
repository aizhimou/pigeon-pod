# Release Notes / 发布说明

Release date / 发布日期: 2026-02-16
Version / 版本: 1.21.0

## Features / 功能
- Add supported URL ID format hints and optimize FeedHeader UI / 新增受支持 URL ID 格式提示并优化 FeedHeader UI (421f979)
- Optimize FeedHeader UI / 优化 FeedHeader UI (a6fa95a)
- Add bilibili subscription support / 新增 Bilibili 订阅支持 (3159d06)
- Add configuration hints for local audio/video/cover patch / 新增本地音频/视频/封面补丁配置提示 (296c202)
- Move legacy fields and logic from user to system config / 将 legacy 字段及逻辑从 user 迁移到 system config (7b50bd5)
- Simplify deployment configuration by moving storage policy and base URL config to web page and database storage / 将存储策略与 base url 配置迁移到网页和数据库存储，简化部署配置 (1ae5753)
- Add batch download functionality for historical episodes / 新增历史节目批量下载能力 (7f366de)
- Separate channel and playlist views to avoid source interference for same-channel episodes / 分离频道与播放列表视图，避免同频道节目时订阅源互相干扰 (e38737a)
- Add database lock error handler and i18n messages / 新增数据库锁错误处理和 i18n 消息 (81938ad)
- Upgrade keyword filtering from simple OR to OR + AND patterns / 关键字过滤从仅“或”升级为支持“或 + 与” (873176c)
- Add YouTube Data API usage limits and statistics / 新增 YouTube Data API 使用限额与统计功能 (1f159d3)
- Use yt-dlp --flat-playlist with YouTube Data API to reduce calls and improve sync performance / 结合 `yt-dlp --flat-playlist` 与 YouTube Data API 提升同步性能并减少 API 调用 (da1ba25)
- Add `itunes:category` and `itunes:explicit` tags in RSS and adjust chapter type / RSS 新增 `itunes:category` 与 `itunes:explicit`，并调整 chapter type (107d99b)

## Fixes / 修复
- Remove redundant fields in user class / 移除 user 类中的冗余字段 (1fedf27)

## Breaking Changes / 破坏性变更
- None / 无
