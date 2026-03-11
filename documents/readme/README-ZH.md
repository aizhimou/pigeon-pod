<div align="center">
  <img src="../../documents/assets/logo-with-brand.png" alt="pigeonpod" width="260" />
  <h2>随时随地收听 YouTube 与 Bilibili。</h2>
  <h3>如果自托管不是你的菜，欢迎看看我们即将上线的在线服务：
    <a target="_blank" href="https://pigeonpod.cloud/?utm_source=github&utm_medium=repo&utm_campaign=readme&utm_content=cta">PigeonPod</a>
  </h3>
</div>

<div align="center">
  
[English](../../README.md) | [Español](README-ES.md) | [Português](README-PT.md) | [日本語](README-JA.md) | [Deutsch](README-DE.md) | [Français](README-FR.md) | [한국어](README-KO.md)
</div>

## Screenshots

![index-dark&light](documents/assets/screenshots/home-27-11-2025.png)
<div align="center">
  <p style="color: gray">订阅列表</p>
</div>

![detail-dark&light](documents/assets/screenshots/feed-27-11-2025.png)
<div align="center">
  <p style="color: gray">节目详情</p>
</div>

## 核心功能

- **🎯 智能订阅与预览**：几秒内订阅 YouTube 或 Bilibili 的频道与播放列表。
- **📻 安全的 RSS 播客订阅**：为任何播客客户端生成受保护的标准 RSS。
- **🎦 灵活的音视频输出**：按需下载音频或视频，并控制质量与格式。
- **🤖 自动同步与历史补齐**：持续更新订阅内容，并按需补齐历史节目。
- **🍪 自定义 Cookie 支持**：使用 YouTube 和 Bilibili Cookies，更稳定访问受限内容。
- **🌍 代理支持的网络访问**：让 YouTube API 与 yt-dlp 支持自定义代理。
- **🔗 单集节目一键分享**：通过公开页面分享单集，无需登录即可直接播放。
- **📦 快速批量下载**：高效搜索、勾选并排队历史节目。
- **📊 下载面板与批量操作**：跟踪任务状态，并批量重试、取消或删除。
- **🔍 按订阅过滤与保留策略**：用关键词、时长和集数限制控制同步范围。
- **⏱ 更智能的新节目下载**：延迟自动下载，提升新视频处理效果。
- **🎛 可定制订阅与内置播放器**：自定义标题和封面，并在网页中直接播放节目。
- **🧩 节目管理与控制**：下载、重试、取消和删除节目时同步清理文件。
- **🔓 受信环境自动登录**：在受信访问控制后方部署时可跳过手动登录。
- **📈 YouTube API 用量洞察**：在同步触及限额前监控配额使用情况。
- **🔄 OPML 订阅导出**：轻松导出订阅，便于迁移到其他播客客户端。
- **⬆️ 应用内 yt-dlp 更新**：无需离开应用即可更新 yt-dlp。
- **🛠 高级 yt-dlp 参数**：通过自定义 yt-dlp 参数精细调整下载行为。
- **📚 Podcasting 2.0 章节支持**：生成章节文件，带来更丰富的播放导航。
- **🌐 多语言自适应界面**：支持八种界面语言，桌面和移动端均可流畅使用。

## 部署方式

### 使用 Docker Compose（推荐）

**确保你的机器上已安装 Docker 和 Docker Compose。**

1. 使用 docker-compose 配置文件，注意根据自己的需求修改环境变量
```yml
version: '3.9'
services:
  pigeon-pod:
    image: 'ghcr.io/aizhimou/pigeon-pod:latest'
    restart: unless-stopped
    container_name: pigeon-pod
    ports:
      - '8834:8080'
    environment:
      - SPRING_DATASOURCE_URL=jdbc:sqlite:/data/pigeon-pod.db # 替换为你的数据库路径
      # 可选：当你已经使用其他认证层保护 Web UI 时，可关闭 PigeonPod 内置认证
      # - PIGEON_AUTH_ENABLED=false
    volumes:
      - data:/data

volumes:
  data:
```

> [!WARNING]
> `PIGEON_AUTH_ENABLED` 默认值为 `true`。只有在已有其他可信保护层守护 Web UI 时，例如 auth proxy、反向代理访问控制、VPN 或私有网络，才应将其设置为 `false`。
>
> 如果你关闭了内置认证，必须通过其他方式保证 PigeonPod 的安全。不要将关闭认证的实例直接暴露在公网。

2. 启动服务
```bash
docker-compose up -d
```

3. 访问应用
打开浏览器访问 `http://{localhost}:8834`，**默认用户名: `root`，密码：`Root@123`**

### 使用 JAR 包运行
**确保你的机器上已安装 Java 17+ 和 yt-dlp。**
1. 从 [Releases](https://github.com/aizhimou/pigeon-pod/releases) 下载最新的发布 JAR 包

2. 在与 JAR 包相同的目录下创建数据目录
```bash
mkdir -p data
```

3. 运行应用
```bash
java -jar -Dspring.datasource.url=jdbc:sqlite:/path/to/your/pigeon-pod.db \  # 替换为你的数据库路径
           pigeon-pod-x.x.x.jar
```

4. 访问应用
打开浏览器访问 `http://localhost:8080`，**默认用户名: `root`，密码：`Root@123`**

## 存储配置说明

- PigeonPod 支持 `LOCAL` 与 `S3` 两种存储模式。
- 两种模式只能二选一，不能融合启用。
- `S3` 模式支持 MinIO、Cloudflare R2、AWS S3 及其他兼容 S3 协议的存储服务。
- 切换存储模式时，历史媒体文件不会自动迁移，必须手动迁移。

### 模式优劣对比

| 模式 | 优点 | 缺点 |
| --- | --- | --- |
| `LOCAL` | 配置简单，无外部依赖 | 占用本地磁盘，扩容不便 |
| `S3` | 存储扩展性好，适合云部署 | 需要对象存储账号与凭证，存在 API/网络成本 |


## 文档

- [如何获取 YouTube Data API 密钥](../how-to-get-youtube-api-key/how-to-get-youtube-api-key-zh.md)
- [如何配置 YouTube Cookies](../youtube-cookie-setup/youtube-cookie-setup-zh.md)
- [如何获取 YouTube 频道 ID](../how-to-get-youtube-channel-id/how-to-get-youtube-channel-id-en.md)


## 技术栈

### 后端
- **Java 17** - 核心语言
- **Spring Boot 3.5** - 应用框架
- **MyBatis-Plus 3.5** - ORM 框架
- **SQLite** - 轻量级数据库
- **Flyway** - 数据库版本管理工具
- **Sa-Token** - 权限认证框架
- **YouTube Data API v3** - YouTube 数据获取
- **yt-dlp** - 视频下载工具
- **Rome** - RSS 生成库

### 前端
- **Javascript (ES2024)** - 核心语言
- **React 19** - 应用框架
- **Vite 7** - 构建工具
- **Mantine 8** - UI 组件库
- **i18next** - 国际化支持
- **Axios** - HTTP 客户端

## 开发指南

### 环境要求
- Java 17+
- Node.js 22+
- Maven 3.9+
- SQLite
- yt-dlp

### 本地开发

1. 克隆项目
```bash
git clone https://github.com/aizhimou/PigeonPod.git
cd PigeonPod
```

2. 配置数据库
```bash
# 创建数据目录
mkdir -p data/audio

# 数据库文件会在首次启动时自动创建
```

3. 配置 YouTube API
   - 在 [Google Cloud Console](https://console.cloud.google.com/) 创建项目
   - 启用 YouTube Data API v3
   - 创建 API 密钥
   - 在用户设置中配置 API 密钥

4. 启动后端
```bash
cd backend
mvn spring-boot:run
```

5. 启动前端（新终端）
```bash
cd frontend
npm install
npm run dev
```

6. 访问应用
- 前端开发服务器: `http://localhost:5173`
- 后端 API: `http://localhost:8080`

### 开发注意事项
1. 确保 yt-dlp 已安装并可在命令行中使用
2. 配置正确的 YouTube API 密钥
3. 确保音频存储目录有足够的磁盘空间
4. 定期清理旧的音频文件以节省空间

---

<div align="center">
  <p>为播客爱好者用 ❤️ 制作！</p>
  <p>⭐ 如果你喜欢 PigeonPod，请在 GitHub 上给我们一个星！</p>
</div>
