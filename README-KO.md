<div align="center">
  <img src=".github/docs-assets/logo-with-brand.png" alt="pigeonpod" width="260" />
  <h2>YouTube와 Bilibili를 어디서나 들으세요.</h2>
  <h3>셀프 호스팅이 부담스럽다면, 곧 출시될 온라인 서비스를 확인해보세요:
    <a target="_blank" href="https://pigeonpod.cloud/?utm_source=github&utm_medium=repo&utm_campaign=readme&utm_content=cta">PigeonPod</a>
  </h3>
</div>

<div align="center">

[English](README.md) | [简体中文](README-ZH.md) | [Español](README-ES.md) | [Português](README-PT.md) | [日本語](README-JA.md) | [Deutsch](README-DE.md) | [Français](README-FR.md)
</div>

> [!NOTE]
> 자세한 사용 문서는 현재 영어 GitHub Wiki에서 유지됩니다.
> 이 한국어 README는 가벼운 프로젝트 입구 역할만 하며, 최신 영어 문서보다 업데이트가 늦을 수 있습니다.

## PigeonPod란?

PigeonPod는 기술 사용자용 셀프 호스팅 프로젝트로, YouTube 채널, YouTube 재생목록, Bilibili 콘텐츠를 팟캐스트용 RSS로 변환하고 동기화, 다운로드, 관리 규칙을 직접 제어할 수 있게 해줍니다.

특히 다음과 같은 사용자에게 적합합니다:

- 직접 서비스를 배포하고 운영하고 싶은 사람
- YouTube나 Bilibili 콘텐츠를 팟캐스트 앱으로 가져오고 싶은 사람
- 필터, 자동 다운로드, 보존 정책, 저장 방식을 세밀하게 제어하고 싶은 사람

## 핵심 기능

- **🎯 스마트 구독 및 미리보기**: YouTube와 Bilibili 채널·재생목록을 몇 초 만에 구독할 수 있습니다.
- **📻 모든 클라이언트에서 사용할 수 있는 안전한 RSS**: 어떤 팟캐스트 앱에도 쓸 수 있는 보호된 표준 RSS를 생성합니다.
- **🎦 유연한 오디오/비디오 출력**: 오디오나 비디오로 다운로드하고 품질과 형식을 조절할 수 있습니다.
- **🤖 자동 동기화와 히스토리 가져오기**: 구독을 계속 최신 상태로 유지하고 필요할 때 과거 영상을 가져옵니다.
- **🍪 확장된 쿠키 지원**: YouTube와 Bilibili 쿠키로 제한 콘텐츠에 더 안정적으로 접근합니다.
- **🌍 프록시 지원 네트워크 액세스**: YouTube API와 yt-dlp 트래픽을 사용자 지정 프록시로 라우팅합니다.
- **🔗 원클릭 에피소드 공유**: 로그인 없이 바로 재생할 수 있는 공개 페이지로 에피소드를 공유합니다.
- **📦 빠른 일괄 다운로드**: 대량의 과거 카탈로그도 효율적으로 검색, 선택, 대기열 추가가 가능합니다.
- **📊 다운로드 대시보드와 일괄 작업**: 작업 상태를 추적하고 재시도, 취소, 삭제를 한 번에 처리합니다.
- **🔍 피드별 필터링 및 보존 정책**: 키워드, 길이, 에피소드 제한으로 동기화 범위를 제어합니다.
- **⏱ 더 똑똑한 신규 에피소드 다운로드**: 자동 다운로드를 지연해 새 영상 처리 품질을 높입니다.
- **🎛 커스터마이징 가능한 피드와 내장 플레이어**: 제목과 커버를 바꾸고 웹에서 바로 에피소드를 재생합니다.
- **🧩 에피소드 관리 및 세밀한 제어**: 다운로드, 재시도, 취소, 삭제와 함께 파일 정리도 처리합니다.
- **🔓 신뢰 환경 자동 로그인**: 신뢰할 수 있는 접근 제어 뒤에서는 수동 로그인을 건너뜁니다.
- **📈 YouTube API 사용량 인사이트**: 동기화가 한도에 닿기 전에 할당량 사용량을 확인합니다.
- **🔄 OPML 구독 내보내기**: 구독을 쉽게 내보내 다른 팟캐스트 클라이언트로 옮길 수 있습니다.
- **⬆️ 앱 내 yt-dlp 업데이트**: 앱을 벗어나지 않고 yt-dlp를 업데이트합니다.
- **🛠 고급 yt-dlp 인자 설정**: 사용자 정의 yt-dlp 인자로 다운로드 동작을 세밀하게 조정합니다.
- **📚 Podcasting 2.0 챕터 지원**: 챕터 파일을 생성해 더 풍부한 플레이어 탐색을 제공합니다.
- **🌐 다국어·반응형 UI**: 8개 UI 언어와 데스크톱·모바일 환경을 모두 지원합니다.

## 빠른 시작

권장 배포 방법은 Docker Compose입니다:

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
      - SPRING_DATASOURCE_URL=jdbc:sqlite:/data/pigeon-pod.db
      # 선택 사항: 다른 인증 계층이 웹 UI를 보호할 때만 내장 인증을 비활성화하세요
      # - PIGEON_AUTH_ENABLED=false
    volumes:
      - data:/data

volumes:
  data:
```

서비스 시작:

```bash
docker compose up -d
```

접속 주소:

```text
http://localhost:8834
```

기본 계정:

- 사용자명: `root`
- 비밀번호: `Root@123`

> [!WARNING]
> `PIGEON_AUTH_ENABLED`의 기본값은 `true`입니다. auth proxy, 리버스 프록시 접근 제어, VPN 또는 사설 네트워크처럼 다른 신뢰할 수 있는 보호 계층이 이미 웹 UI를 보호하고 있을 때에만 `false`로 설정하세요.
>
> 인증이 꺼진 인스턴스를 인터넷에 직접 노출하지 마세요.

## 문서

공식 사용자 문서는 영어 GitHub Wiki에 있습니다:

- [Wiki Home](https://github.com/aizhimou/PigeonPod/wiki)
- [Quick Start](https://github.com/aizhimou/PigeonPod/wiki/Quick-Start)
- [Installation](https://github.com/aizhimou/PigeonPod/wiki/Installation)
- [Configuration Overview](https://github.com/aizhimou/PigeonPod/wiki/Configuration-Overview)
- [Troubleshooting](https://github.com/aizhimou/PigeonPod/wiki/Troubleshooting)
- [Advanced Customization](https://github.com/aizhimou/PigeonPod/wiki/Advanced-Customization)

## 자주 쓰는 링크

- [영문 메인 README](README.md)
- [GitHub Wiki](https://github.com/aizhimou/PigeonPod/wiki)
- [Releases](https://github.com/aizhimou/PigeonPod/releases)
- [Issues](https://github.com/aizhimou/PigeonPod/issues)

## 참고

- 현재 권장 배포 방식은 Docker이며, JAR 직접 실행은 더 이상 권장되지 않습니다.
- 프로젝트가 나에게 맞는지 빠르게 판단하려면 이 README와 영어 Wiki만 봐도 충분합니다.
- 더 깊은 커스터마이징, 개발, 아키텍처 이해가 필요하면 저장소의 `dev-docs/`를 참고하세요.

---

<div align="center">
  <p>팟캐스트 애호가를 위해 ❤️로 제작했습니다!</p>
  <p>⭐ PigeonPod가 마음에 드신다면 GitHub에서 Star를 남겨주세요!</p>
</div>
