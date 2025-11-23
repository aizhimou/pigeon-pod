<div align="center">
  <img src="../../frontend/src/assets/pigeonpod.svg" alt="pigeonpod" width="120" />
  <h1>PigeonPod</h1>
  <h2>좋아하는 YouTube 채널을 가장 간단하고 우아한 방법으로 팟캐스트 채널로 변환하세요.</h2>
  <h3>셀프 호스팅이 부담스럽다면, 곧 출시될 온라인 서비스를 확인해보세요:
    <a target="_blank" href="https://pigeonpod.cloud/">PigeonPod</a>
  </h3>
</div>

<div align="center">
  
[![English README](https://img.shields.io/badge/README-English-blue)](../../README.md) [![中文 README](https://img.shields.io/badge/README-%E4%B8%AD%E6%96%87-red)](README-ZH.md) [![Español README](https://img.shields.io/badge/README-Español-orange)](README-ES.md) [![Português README](https://img.shields.io/badge/README-Português-green)](README-PT.md) [![日本語 README](https://img.shields.io/badge/README-日本語-blue)](README-JA.md) [![Deutsch README](https://img.shields.io/badge/README-Deutsch-yellow)](README-DE.md) [![Français README](https://img.shields.io/badge/README-Français-purple)](README-FR.md)
</div>

## 스크린샷

![index-dark&light](../assets/screenshots/Snipaste_2025-11-23_23-17-24.png)
<div align="center">
  <p style="color: gray">채널 목록</p>
</div>

![detail-dark&light](../assets/screenshots/Snipaste_2025-11-23_23-16-12.png)
<div align="center">
  <p style="color: gray">채널 상세</p>
</div>

## 핵심 기능

- **🎯 스마트 구독 및 미리보기**: YouTube 채널·재생목록 URL을 붙여넣기만 하면 유형을 자동으로 판별하고, 구독 전에 피드 정보와 에피소드 목록을 미리 확인할 수 있습니다.
- **🤖 자동 동기화와 히스토리 가져오기**: 백그라운드에서 새 업로드를 주기적으로 증분 동기화하고, 피드별 초기 가져올 에피소드 수를 설정하며, 버튼 한 번으로 과거 에피소드도 추가할 수 있습니다.
- **📻 모든 클라이언트에서 사용할 수 있는 안전한 RSS**: 채널·재생목록용 표준 RSS 링크를 생성하고 API 키로 보호하여, 어떤 팟캐스트 앱에서도 안전하게 구독할 수 있습니다.
- **🔍 피드별 필터링 및 보존 정책**: 제목/설명 키워드(포함/제외)와 최소 재생 길이로 에피소드를 필터링하고, 피드마다 동기화 여부와 최대 보관 에피소드 수를 설정할 수 있습니다.
- **📊 다운로드 대시보드와 일괄 작업**: 대기/다운로드 중/완료/실패 상태별 작업을 실시간으로 확인하고, 오류 로그를 확인하며, 한 번의 클릭으로 전체 취소·삭제·재시도를 수행할 수 있습니다.
- **🧩 에피소드 관리 및 세밀한 제어**: 무한 스크롤 에피소드 목록에서 개별 에피소드의 수동 다운로드, 재시도, 취소, 삭제를 지원하고, 이에 맞춰 로컬 파일도 함께 관리합니다.
- **🎦 유연한 오디오/비디오 출력**: 피드별로 오디오 전용(AAC) 또는 비디오 다운로드를 선택할 수 있고, 음질 단계나 해상도/인코딩을 조절하며 메타데이터·챕터·커버를 자동으로 삽입합니다.
- **🍪 제한 콘텐츠 지원**: YouTube Data API 키와 업로드한 쿠키를 함께 사용하여 연령 제한·멤버십 전용 콘텐츠에 보다 안정적으로 접근할 수 있습니다.
- **🎛 커스터마이징 가능한 피드와 내장 플레이어**: 피드마다 사용자 정의 제목과 커버를 설정하고, 내장 웹 플레이어로 오디오·비디오를 빠르게 재생할 수 있습니다.
- **🌐 다국어·반응형 UI**: 영어, 중국어, 스페인어, 포르투갈어, 일본어, 프랑스어, 독일어, 한국어 UI를 완전 지원하며, 데스크톱과 모바일에서 모두 쾌적한 경험을 제공합니다.

## 배포

### Docker Compose 사용 (권장)

**Docker와 Docker Compose가 시스템에 설치되어 있는지 확인하세요.**

1. docker-compose 설정 파일을 사용하고, 필요에 따라 환경 변수를 수정하세요:
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
      - 'PIGEON_BASE_URL=https://pigeonpod.cloud' # 도메인으로 설정
      - 'PIGEON_AUDIO_FILE_PATH=/data/audio/' # 오디오 파일 경로 설정
      - 'PIGEON_COVER_FILE_PATH=/data/cover/' # 커버 파일 경로로 설정
      - 'SPRING_DATASOURCE_URL=jdbc:sqlite:/data/pigeon-pod.db' # 데이터베이스 경로 설정
    volumes:
      - data:/data

volumes:
  data:
```

2. 서비스 시작:
```bash
docker-compose up -d
```

3. 애플리케이션 접속:
브라우저를 열고 `http://localhost:8834`에 접속하여 **기본 사용자명: `root`, 기본 비밀번호: `Root@123`**로 로그인

### JAR로 실행

**Java 17+와 yt-dlp가 시스템에 설치되어 있는지 확인하세요.**

1. [Releases](https://github.com/aizhimou/pigeon-pod/releases)에서 최신 릴리스 JAR 다운로드

2. JAR 파일과 같은 디렉토리에 data 디렉토리 생성:
```bash
mkdir -p data
```

3. 애플리케이션 실행:
```bash
java -jar -DPIGEON_BASE_URL=http://localhost:8080 \  # 도메인으로 설정
           -DPIGEON_AUDIO_FILE_PATH=/path/to/your/audio/ \  # 오디오 파일 경로 설정
           -Dspring.datasource.url=jdbc:sqlite:/path/to/your/pigeon-pod.db \  # 데이터베이스 경로 설정
           pigeon-pod-x.x.x.jar
```

4. 애플리케이션 접속:
브라우저를 열고 `http://localhost:8080`에 접속하여 **기본 사용자명: `root`, 기본 비밀번호: `Root@123`**로 로그인

## 문서

- [YouTube API 키 얻는 방법](../how-to-get-youtube-api-key/how-to-get-youtube-api-key-en.md)
- [YouTube 쿠키 설정 방법](../youtube-cookie-setup/youtube-cookie-setup-en.md)
- [YouTube 채널 ID 얻는 방법](../how-to-get-youtube-channel-id/how-to-get-youtube-channel-id-en.md)

## 기술 스택

### 백엔드
- **Java 17** - 핵심 언어
- **Spring Boot 3.5** - 애플리케이션 프레임워크
- **MyBatis-Plus 3.5** - ORM 프레임워크
- **Sa-Token** - 인증 프레임워크
- **SQLite** - 경량 데이터베이스
- **Flyway** - 데이터베이스 마이그레이션 도구
- **YouTube Data API v3** - YouTube 데이터 검색
- **yt-dlp** - 비디오 다운로드 도구
- **Rome** - RSS 생성 라이브러리

### 프론트엔드
- **Javascript (ES2024)** - 핵심 언어
- **React 19** - 애플리케이션 프레임워크
- **Vite 7** - 빌드 도구
- **Mantine 8** - UI 컴포넌트 라이브러리
- **i18next** - 국제화 지원
- **Axios** - HTTP 클라이언트

## 개발 가이드

### 환경 요구사항
- Java 17+
- Node.js 22+
- Maven 3.9+
- SQLite
- yt-dlp

### 로컬 개발

1. 프로젝트 클론:
```bash
git clone https://github.com/aizhimou/PigeonPod.git
cd PigeonPod
```

2. 데이터베이스 설정:
```bash
# 데이터 디렉토리 생성
mkdir -p data/audio

# 데이터베이스 파일은 첫 시작 시 자동으로 생성됩니다
```

3. YouTube API 설정:
   - [Google Cloud Console](https://console.cloud.google.com/)에서 프로젝트 생성
   - YouTube Data API v3 활성화
   - API 키 생성
   - 사용자 설정에서 API 키 구성

4. 백엔드 시작:
```bash
cd backend
mvn spring-boot:run
```

5. 프론트엔드 시작 (새 터미널):
```bash
cd frontend
npm install
npm run dev
```

6. 애플리케이션 접속:
- 프론트엔드 개발 서버: `http://localhost:5173`
- 백엔드 API: `http://localhost:8080`

### 개발 참고사항
1. yt-dlp가 설치되어 있고 명령줄에서 사용 가능한지 확인
2. 올바른 YouTube API 키 설정
3. 오디오 저장 디렉토리에 충분한 디스크 공간이 있는지 확인
4. 공간 절약을 위해 정기적으로 오래된 오디오 파일 정리

---

<div align="center">
  <p>팟캐스트 애호가를 위해 ❤️로 제작했습니다!</p>
  <p>⭐ PigeonPod가 마음에 드신다면, GitHub에서 별점을 남겨주세요!</p>
</div>
