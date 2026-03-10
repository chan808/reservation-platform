# Frontend

인증/회원 기능을 실제 사용자 흐름으로 검증하기 위한 Next.js 애플리케이션이다.  
단순 API 호출 화면이 아니라 로그인 상태 관리, Access Token 메모리 저장, Refresh Token 재발급, OAuth 콜백, i18n 라우팅까지 포함한다.

## Stack

- Next.js 16
- React 19
- TypeScript
- next-intl
- TanStack Query
- Zustand
- Axios
- Tailwind CSS 4
- shadcn/ui

## Responsibilities

- 로그인, 회원가입, 이메일 인증, 비밀번호 재설정 화면 제공
- OAuth 로그인 버튼과 콜백 처리
- 로그인 후 대시보드에서 프로필 수정, 비밀번호 변경, 탈퇴 처리
- Access Token 메모리 저장
- 401 응답 시 Refresh Token 기반 자동 재발급 시도
- ko/en 국제화 라우팅 지원

## Run

`.env.example`를 `.env.local`로 복사해서 사용한다.

```env
NEXT_PUBLIC_API_URL=http://localhost:8080
NEXT_PUBLIC_BACKEND_URL=http://localhost:8080
NEXT_PUBLIC_OAUTH_PROVIDERS=google
```

```bash
pnpm install
pnpm dev
```

## Verify

```bash
pnpm lint
pnpm build
```

## Structure

```text
src/
├─ app/
│  ├─ [locale]/
│  │  ├─ (auth)/
│  │  ├─ (main)/
│  │  └─ auth/callback/
├─ features/
│  ├─ auth/
│  └─ member/
├─ shared/
│  ├─ api/
│  └─ components/
└─ i18n/
```

루트 프로젝트 설명은 [../README.md](../README.md)에서 확인할 수 있다.
