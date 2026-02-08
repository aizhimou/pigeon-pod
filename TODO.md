# PigeonPod TODO List

This document tracks all open issues and feature requests for the PigeonPod project.

**Last Updated:** 2026-02-08  
**Total Open Issues:** 11

---

## 🐛 Bugs & Issues

### 🔴 High Priority

- [ ] **#87 - Title Contains Keywords Broken**
  - **Status:** Open
  - **Reported by:** @Rumik
  - **Issue:** Keyword filtering for channel feeds not working correctly. When one feed has keywords configured, refreshing other feeds causes the keyword to be ignored, resulting in all videos showing up in the keyword feed and none in other feeds.
  - **Comments:** 2
  - **Link:** https://github.com/aizhimou/pigeon-pod/issues/87

- [ ] **#90 - Download Error**
  - **Status:** Open (Resolved by user)
  - **Reported by:** @dadm0de
  - **Issue:** Downloads failing due to outdated yt-dlp version. User solved by updating yt-dlp via pip inside docker exec.
  - **Solution:** Update yt-dlp in the Docker image to latest version.
  - **Comments:** 1
  - **Link:** https://github.com/aizhimou/pigeon-pod/issues/90

- [ ] **#86 - VPS freezes due to OOM when adding playlist (Ubuntu 24.04, 1GB RAM)**
  - **Status:** Open
  - **Reported by:** @birdset
  - **Issue:** Adding YouTube playlists causes uncontrolled memory usage growth, leading to OOM and VPS becoming unresponsive on low-resource servers (1GB RAM).
  - **Comments:** 1
  - **Link:** https://github.com/aizhimou/pigeon-pod/issues/86

---

## 🚀 Feature Requests

### ⭐ Storage & Media Management

- [ ] **#89 - Automatic cleanup / retention policy for downloaded media**
  - **Status:** Open
  - **Requested by:** @brtsos
  - **Description:** Add automatic media cleanup with time-based retention (TTL) and/or playback-based cleanup to prevent disk usage issues.
  - **Proposed Solutions:**
    - Time-based retention (e.g., delete media files older than X days)
    - Configurable via env variable (e.g., `MEDIA_RETENTION_DAYS=7`)
    - Playback-based cleanup (delete after fully played/watched)
  - **Comments:** 3
  - **Link:** https://github.com/aizhimou/pigeon-pod/issues/89

- [ ] **#38 - S3 support for audio hosting**
  - **Status:** Open
  - **Requested by:** @LucaTNT
  - **Description:** Add S3 support for storing audio files to enable running the app on servers without much local storage.
  - **Comments:** 3
  - **Link:** https://github.com/aizhimou/pigeon-pod/issues/38

### 🎵 Audio/Video Features

- [ ] **#88 - Support for audio track/language selection for multi-track YouTube videos**
  - **Status:** Open
  - **Requested by:** @frontsmith
  - **Description:** Add ability to choose preferred audio language for YouTube videos with multiple audio tracks (e.g., original vs auto-translated/dubbed).
  - **Proposed Solutions:**
    - Global configuration via env variable (e.g., `PREFERRED_AUDIO_LANG=zh,en`)
    - Per-channel/feed setting in Web UI
    - Pass specific yt-dlp flags like `--audio-langs "zh-Hans,en.*"`
  - **Comments:** 1
  - **Link:** https://github.com/aizhimou/pigeon-pod/issues/88

- [ ] **#84 - Add chapters to videos without them**
  - **Status:** Open
  - **Requested by:** @eddively
  - **Description:** Auto-generate chapters for YouTube videos that don't have them automatically. Could be time-based (every X minutes) or percentage-based (every X%).
  - **Comments:** 7
  - **Link:** https://github.com/aizhimou/pigeon-pod/issues/84

- [ ] **#64 - Add Ads detection and automatic cutting off**
  - **Status:** Open
  - **Requested by:** @StevenRudenko
  - **Description:** Detect and remove ad segments from downloaded content using AI (like Claude) to analyze subtitles for ad detection.
  - **Reference:** Similar functionality in https://github.com/hemant6488/podcast-server
  - **Comments:** 8
  - **Link:** https://github.com/aizhimou/pigeon-pod/issues/64

### 📤 Import/Export Features

- [ ] **#91 - OPML Export for Podcast Subscriptions**
  - **Status:** Open
  - **Requested by:** @nicolasjust
  - **Description:** Add option to export user's subscribed podcast list as an OPML file (standard format for moving subscriptions between podcast players).
  - **Benefits:**
    - Portability: Users can own and back up their subscription data
    - Standardization: OPML is universal format for podcasting
  - **Comments:** 1
  - **Link:** https://github.com/aizhimou/pigeon-pod/issues/91

### 🌏 Platform Support

- [ ] **#78 - 增加订阅B站的功能 (Add Bilibili subscription support)**
  - **Status:** Open
  - **Requested by:** @frontsmith
  - **Description:** Add support for subscribing to Bilibili (B站) content.
  - **Reference:** Existing implementation in https://github.com/yangtfu/podsync
  - **Comments:** 2
  - **Link:** https://github.com/aizhimou/pigeon-pod/issues/78

---

## 📚 Documentation & Community

- [ ] **#70 - Can you do a few videos of setting up the application?**
  - **Status:** Open
  - **Requested by:** @CookieyedCodes
  - **Description:** Create video tutorials for setting up the application.
  - **Comments:** 5
  - **Link:** https://github.com/aizhimou/pigeon-pod/issues/70

---

## 📊 Issue Statistics

| Category | Count |
|----------|-------|
| 🐛 Bugs | 3 |
| 🚀 Features | 7 |
| 📚 Documentation | 1 |
| **Total** | **11** |

---

## 🏷️ Issue Tags Summary

- **Storage/Infrastructure:** #89, #38, #86
- **Audio/Video Processing:** #88, #84, #64
- **Import/Export:** #91
- **Platform Support:** #78
- **Bug Fixes:** #87, #90
- **Documentation:** #70

---

## 💡 Development Priorities

### Immediate Action Required
1. **#90** - Update yt-dlp in Docker image (quick fix)
2. **#87** - Fix keyword filtering bug (affects core functionality)
3. **#86** - Address OOM issues (critical for low-resource deployments)

### High Impact Features
1. **#89** - Automatic cleanup/retention (addresses storage concerns)
2. **#91** - OPML export (industry standard, high user value)
3. **#88** - Multi-track audio selection (user experience improvement)

### Nice to Have
1. **#84** - Auto-generated chapters
2. **#64** - Ad detection/removal
3. **#38** - S3 storage support
4. **#78** - Bilibili support
5. **#70** - Setup video tutorials

---

*This TODO list is automatically generated from GitHub issues. For the most up-to-date information, please check the [GitHub Issues page](https://github.com/aizhimou/pigeon-pod/issues).*
