# Auth Template

회원가입, 로그인, OAuth, 이메일 인증, 비밀번호 재설정, 세션 관리, 관측성까지 포함한 실무형 인증 템플릿입니다.

단순히 로그인 API만 붙인 예제가 아니라, 새 프로젝트를 시작할 때 반복되는 인증 기반을 빠르게 가져가면서도 운영과 확장을 고려할 수 있게 만드는 것을 목표로 했습니다.

## Why This Template

- JWT Access Token + Redis Refresh Token 세션 관리
- 이메일 인증 재전송, 비밀번호 재설정, 미인증 계정 정리 포함
- Google, Naver, Kakao OAuth 로그인 지원
- IP/이메일 기준 rate limiting 적용
- Flyway 기반 스키마 관리
- unit test / integration test 분리
- Actuator, Prometheus, Grafana 기반 관측성 구성
- Spring Modulith 경계 검증 테스트 포함

## Stack

### Backend

- Kotlin
- JDK 21
- Spring Boot 4
- Spring Security
- Spring Data JPA
- MySQL
- Redis
- Flyway
- OAuth2 Client
- Spring Modulith
- Micrometer / Prometheus
- Testcontainers / MockK

### Frontend

- Next.js 16
- React 19
- TypeScript
- next-intl
- TanStack Query
- Zustand
- Axios
- Tailwind CSS 4
- shadcn/ui

### Infra

- Docker Compose
- Nginx
- Prometheus
- Grafana

## What Matters

- Refresh Token을 JWT로 두지 않고 Redis 세션으로 관리합니다.
- 비밀번호 변경, 로그아웃, 회원 탈퇴 시 세션 무효화가 가능합니다.
- 인증 관련 API에 rate limiting을 적용해 기본적인 방어선을 갖춥니다.
- 운영 환경을 고려해 observability 구성을 템플릿 단계에서 포함합니다.
- 구조를 나누는 데서 끝나지 않고 Modulith 검증 테스트로 경계를 확인합니다.

## Project Structure

```text
auth-template/
|-- backend/
|   |-- src/main/kotlin
|   |-- src/main/resources
|   |-- src/test/kotlin
|   |-- docker-compose.yml
|   |-- docker-compose.storage.yml
|   |-- .env.example
|   `-- OBSERVABILITY.md
|-- frontend/
|   |-- src
|   |-- messages
|   |-- public
|   `-- .env.example
|-- infra/
|   `-- nginx/
`-- README.md
```

## Quick Start

### 1. Config

- `backend/.env.example` -> `backend/.env`
- `frontend/.env.example` -> `frontend/.env.local`

### 2. Backend Infra

기본:

```bash
cd backend
docker compose up -d mysql redis
```

선택형 스토리지(MinIO):

```bash
cd backend
docker compose -f docker-compose.yml -f docker-compose.storage.yml up -d minio
```

관측성:

```bash
cd backend
docker compose --profile observability up -d prometheus grafana
```

### 3. Backend Run

기본 실행:

```bash
cd backend
./gradlew bootRun
```

관측성 프로필로 실행:

```powershell
cd backend
$env:SPRING_PROFILES_ACTIVE='observability'
.\gradlew bootRun
```

### 4. Frontend Run

```bash
cd frontend
pnpm install
pnpm dev
```

## Test

```bash
cd backend
./gradlew test
./gradlew integrationTest
```

```bash
cd frontend
pnpm lint
pnpm build
```

## Notes

- Local Prometheus scraping requires the `observability` profile on the backend.
- MinIO is optional and separated from the default compose file.
- The current OAuth login flow still assumes a single-server deployment because the authorization request and locale handoff use the server session. For multi-instance deployment, move to Redis-backed session storage or a stateless handoff design.

## Documents

- Backend observability guide: [backend/OBSERVABILITY.md](backend/OBSERVABILITY.md)
- Frontend notes: [frontend/README.md](frontend/README.md)
