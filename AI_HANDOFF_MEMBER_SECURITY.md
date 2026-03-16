# AI Handoff: Member and Security Review

Last reviewed: 2026-03-13

## Purpose

This note is for the next AI/code reviewer who will continue work on member/auth/security improvements in this repository.

Focus area:

- backend member/auth structure
- authentication and authorization safety
- production-readiness for burst traffic shopping scenarios

## High-level project layout

- `backend`: Kotlin, Spring Boot 4, Spring Security, JPA, Redis, Flyway
- `frontend`: Next.js app using in-memory access token + HttpOnly refresh token cookie
- architectural intent: feature modules with exposed APIs/events across modules

Relevant backend modules:

- `member`: signup, profile, password change, withdrawal, email verification
- `auth`: login, refresh, logout, password reset, OAuth2/OIDC, JWT
- `order`: uses authenticated member id for order operations
- `common`: exception handling, rate limit, client IP resolution, config

## Current auth/member flow

### Local login

1. `POST /api/auth/login`
2. `AuthCommandService.login()` validates email/password
3. access token is returned in response body
4. refresh token is stored in Redis as hashed token session
5. refresh token is sent as HttpOnly cookie with path `/api/auth`

Main files:

- `backend/src/main/kotlin/io/github/chan808/reservation/auth/presentation/AuthController.kt`
- `backend/src/main/kotlin/io/github/chan808/reservation/auth/application/AuthCommandService.kt`
- `backend/src/main/kotlin/io/github/chan808/reservation/auth/infrastructure/redis/RefreshTokenStore.kt`

### Access token usage

- client sends `Authorization: Bearer <AT>`
- `JwtAuthenticationFilter` extracts subject and role from JWT
- `AuthenticationPrincipal` is used as `Long memberId` in controllers

Main file:

- `backend/src/main/kotlin/io/github/chan808/reservation/auth/infrastructure/security/JwtAuthenticationFilter.kt`

### Refresh flow

1. frontend sends `POST /api/auth/reissue`
2. cookie carries refresh token
3. frontend adds `X-CSRF-GUARD: 1`
4. backend rotates refresh token and issues new access token

### OAuth flow

1. Spring Security OAuth login succeeds
2. backend issues refresh token cookie
3. backend stores access token temporarily in Redis as one-time code
4. frontend callback page exchanges code for access token
5. frontend stores access token in Zustand in memory only

Main files:

- `backend/src/main/kotlin/io/github/chan808/reservation/auth/infrastructure/oauth2/OAuth2SuccessHandler.kt`
- `backend/src/main/kotlin/io/github/chan808/reservation/auth/infrastructure/redis/OAuthCodeStore.kt`
- `frontend/src/features/auth/stores/authStore.ts`
- `frontend/src/shared/api/axios.ts`
- `frontend/src/features/auth/components/OAuthCallbackClient.tsx`

## What looks good already

- module split between `member` and `auth` is mostly clean
- controllers are thin; business logic is in application services
- refresh token is not stored raw in Redis; SHA-256 hash is stored
- refresh token reissue has per-session lock to reduce race conditions
- password hashing uses BCrypt
- login, signup, password reset, email resend all have Redis-backed rate limiting
- access token is not stored in `localStorage`; frontend keeps it in memory
- member withdrawal anonymizes email/provider id and clears password
- password change and withdrawal publish events that invalidate refresh sessions
- email verification and password reset tokens are one-time or effectively single-current-token flows

## Security findings from review

### 1. High: old access tokens stay valid after password change or withdrawal

Current behavior:

- password change and withdrawal delete refresh sessions only
- already-issued access tokens keep working until expiry
- `JwtAuthenticationFilter` trusts JWT claims directly and does not check member state

Impact:

- a stolen access token can still call protected APIs after password change
- a withdrawn member may still use an existing access token until it expires

Important code:

- `backend/src/main/kotlin/io/github/chan808/reservation/auth/application/AuthEventListener.kt`
- `backend/src/main/kotlin/io/github/chan808/reservation/auth/infrastructure/security/JwtAuthenticationFilter.kt`
- `backend/src/main/kotlin/io/github/chan808/reservation/order/application/OrderService.kt`

Why this matters more here:

- burst-sale systems tend to keep access token TTL practical, not ultra-short
- even a short residual window can be meaningful during purchase spikes

Recommended directions:

- add `tokenVersion` or similar version field to member/session state and embed/check it in JWT
- or add JWT `jti` + server-side denylist for critical events
- or at minimum re-check active member state in critical write paths, though this is weaker and more expensive

### 2. High: production safety depends too much on environment discipline

Current behavior:

- `cookie.secure` defaults to `false`
- `app.base-url` defaults to `http://localhost:3000`
- HSTS is enabled only when `cookie.secure=true`
- `application-prod.yml` does not force secure defaults

Impact:

- if ops misses env configuration, refresh token cookies may not require HTTPS
- verification/reset links may use plain HTTP

Important code:

- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/application-prod.yml`
- `backend/.env.example`
- `backend/src/main/kotlin/io/github/chan808/reservation/auth/infrastructure/security/SecurityConfig.kt`

Recommended directions:

- force secure cookie and HTTPS base URL in `prod`
- fail startup in production if unsafe values are detected
- consider separate config validation for auth-critical properties

### 3. Medium: OAuth login issues tokens with default USER role

Current behavior:

- `OAuth2SuccessHandler` calls `issueTokensForOAuth(memberId)`
- `issueTokensForOAuth()` defaults role to `"USER"`
- stored member role is not re-read before issuing token

Impact:

- any OAuth account with elevated role in DB still gets USER authority in JWT
- admin OAuth flows will be broken or inconsistent

Important code:

- `backend/src/main/kotlin/io/github/chan808/reservation/auth/infrastructure/oauth2/OAuth2SuccessHandler.kt`
- `backend/src/main/kotlin/io/github/chan808/reservation/auth/application/AuthCommandService.kt`
- `backend/src/main/kotlin/io/github/chan808/reservation/member/application/MemberQueryService.kt`

Recommended directions:

- resolve member role from `MemberApi` before issuing OAuth tokens
- add test for non-USER OAuth member role propagation

### 4. Medium: login errors reveal account state

Current behavior:

- unknown email / wrong password => `INVALID_CREDENTIALS`
- unverified account => `EMAIL_NOT_VERIFIED`
- OAuth account on password login => `OAUTH_ACCOUNT_NO_PASSWORD`

Impact:

- allows partial account enumeration and account-type discovery

Important code:

- `backend/src/main/kotlin/io/github/chan808/reservation/auth/application/AuthCommandService.kt`
- `backend/src/main/kotlin/io/github/chan808/reservation/common/ErrorCode.kt`

Recommended directions:

- unify external response for login failure
- keep detailed reasons in logs/metrics only
- if UX needs guidance, expose it only after verified ownership steps

### 5. Low: CSRF guard header is presence-only, not a real secret

Current behavior:

- `/reissue` and `/logout` require `X-CSRF-GUARD`
- backend does not validate its content, only presence
- frontend always sends `X-CSRF-GUARD: 1`

Impact:

- this is not a true CSRF token mechanism
- current safety mostly comes from `SameSite=Strict` and cookie scoping

Important code:

- `backend/src/main/kotlin/io/github/chan808/reservation/auth/presentation/AuthController.kt`
- `backend/src/main/kotlin/io/github/chan808/reservation/auth/infrastructure/security/SecurityConfig.kt`
- `frontend/src/shared/api/axios.ts`

Recommended directions:

- either document this as a lightweight cross-site guard and rely on cookie policy
- or implement real double-submit / synchronizer token strategy

## Areas that deserve deeper structural review next

### Authorization model

Check whether all write/admin endpoints are consistently protected with:

- request matcher security
- method security like `@PreAuthorize`
- correct role propagation for both local and OAuth accounts

Known current example:

- product creation already requires `ADMIN`

File:

- `backend/src/main/kotlin/io/github/chan808/reservation/product/presentation/ProductController.kt`

### Member state model

Questions worth examining:

- should JWT remain valid when `withdrawnAt != null`?
- should email-unverified members be able to hold any access token at all?
- should there be account status fields beyond `emailVerified` and `withdrawnAt`?
- should there be explicit lock/suspension support for fraud or abuse response?

### Token/session model

Questions worth examining:

- should access token contain `jti`, `sid`, or `tokenVersion`?
- should refresh token absolute expiry and Redis TTL stay aligned?
- should logout-all-devices be exposed as explicit user action?
- should privileged actions require step-up auth or recent-auth check?

### Frontend auth UX and resilience

Questions worth examining:

- how is app bootstrap supposed to restore auth after page reload?
- should there be a silent reissue attempt on app startup?
- how are locale-aware redirects and auth errors handled across routes?

## Suggested next implementation order

1. Fix post-password-change / post-withdrawal access token invalidation.
2. Lock down production config defaults and add startup validation.
3. Fix OAuth role propagation and add tests.
4. Normalize login failure responses to reduce account discovery.
5. Decide whether to replace the current CSRF header with a real pattern.

## Suggested tests to add

- password change invalidates both refresh and previously issued access token behavior
- withdrawal invalidates active access token behavior
- OAuth admin account receives `ADMIN` authority in JWT
- production config validation fails on insecure auth settings
- login response does not reveal whether account is unverified or OAuth-based

## Relevant files map

### Backend auth/security

- `backend/src/main/kotlin/io/github/chan808/reservation/auth/infrastructure/security/SecurityConfig.kt`
- `backend/src/main/kotlin/io/github/chan808/reservation/auth/infrastructure/security/JwtProvider.kt`
- `backend/src/main/kotlin/io/github/chan808/reservation/auth/infrastructure/security/JwtAuthenticationFilter.kt`
- `backend/src/main/kotlin/io/github/chan808/reservation/auth/infrastructure/security/SecurityExceptionHandler.kt`
- `backend/src/main/kotlin/io/github/chan808/reservation/auth/application/AuthCommandService.kt`
- `backend/src/main/kotlin/io/github/chan808/reservation/auth/application/AuthEventListener.kt`
- `backend/src/main/kotlin/io/github/chan808/reservation/auth/infrastructure/redis/RefreshTokenStore.kt`
- `backend/src/main/kotlin/io/github/chan808/reservation/auth/presentation/AuthController.kt`

### Backend member

- `backend/src/main/kotlin/io/github/chan808/reservation/member/application/MemberCommandService.kt`
- `backend/src/main/kotlin/io/github/chan808/reservation/member/application/MemberQueryService.kt`
- `backend/src/main/kotlin/io/github/chan808/reservation/member/application/EmailVerificationService.kt`
- `backend/src/main/kotlin/io/github/chan808/reservation/member/domain/Member.kt`
- `backend/src/main/kotlin/io/github/chan808/reservation/member/infrastructure/persistence/MemberRepository.kt`
- `backend/src/main/kotlin/io/github/chan808/reservation/member/presentation/MemberController.kt`

### Backend shared utilities

- `backend/src/main/kotlin/io/github/chan808/reservation/common/ErrorCode.kt`
- `backend/src/main/kotlin/io/github/chan808/reservation/common/config/GlobalExceptionHandler.kt`
- `backend/src/main/kotlin/io/github/chan808/reservation/common/ClientIpResolver.kt`
- `backend/src/main/kotlin/io/github/chan808/reservation/common/ratelimit/RateLimiter.kt`

### Frontend auth

- `frontend/src/features/auth/stores/authStore.ts`
- `frontend/src/shared/api/axios.ts`
- `frontend/src/features/auth/api/authApi.ts`
- `frontend/src/features/auth/components/OAuthCallbackClient.tsx`

## Test status from this review

I attempted to run selected backend auth/member tests, but the current environment did not have Java 21 available.

Gradle/toolchain note:

- `backend/build.gradle.kts` requires Java 21 toolchain

Useful commands once Java 21 is available:

```powershell
cd backend
$env:GRADLE_USER_HOME='C:\Users\freetime\AppData\Local\Temp\.gradle'
.\gradlew test --tests "io.github.chan808.reservation.auth.presentation.AuthControllerTest"
.\gradlew test --tests "io.github.chan808.reservation.member.presentation.MemberControllerTest"
.\gradlew test --tests "io.github.chan808.reservation.common.security.JwtProviderTest"
.\gradlew test --tests "io.github.chan808.reservation.auth.infrastructure.oauth2.OAuth2HandlersTest"
```

## Good prompt for the next AI

You can paste something close to this:

```text
Read `AI_HANDOFF_MEMBER_SECURITY.md` first, then inspect the related backend/frontend auth files it references.
I want you to:
1. validate whether the findings are correct in code,
2. propose the best fix for access token invalidation after password change/withdrawal,
3. implement the fix with tests,
4. harden production auth config defaults,
5. review whether OAuth role propagation is correct and fix it if needed.
Keep module boundaries intact and follow existing ApiResponse/exception/logging patterns.
```
