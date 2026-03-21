# 회원 도메인 유지보수 가이드

작성일: 2026-03-16

## 1. 이 문서의 목적

이 문서는 `member` 도메인을 나중에 다시 볼 때 빠르게 판단하고 수정할 수 있도록 현재 구조와 정책을 정리한 유지보수용 문서다.

이 문서가 답하려는 질문은 아래와 같다.

- 회원 도메인이 어디까지 책임지는가
- 인증(`auth`)과 어디서 연결되는가
- 회원 상태는 무엇으로 표현되는가
- 가입, 인증, 비밀번호 변경, 탈퇴, OAuth 가입이 실제로 어떻게 동작하는가
- 어떤 변경은 안전하고 어떤 변경은 위험한가

포트폴리오 관점에서는 `README`보다 이 문서가 더 상세한 내부 설계 기록 역할을 한다.

## 2. 범위

이 문서의 범위는 아래 두 가지다.

- `member` 모듈 내부 구조
- 회원 상태를 변경하거나 참조하는 `auth` 연동 지점

즉, 문서 중심은 `member`이지만 아래 항목은 함께 본다.

- 로그인 시 회원 조회
- 이메일 인증
- 비밀번호 재설정
- OAuth 회원 생성
- 비밀번호 변경/탈퇴 후 세션 무효화

## 3. 모듈 경계

### 3.1 member 모듈의 역할

`member` 모듈은 아래 책임을 가진다.

- 회원 엔티티와 회원 상태 관리
- 일반 회원 가입
- 내 정보 조회/수정
- 비밀번호 변경
- 회원 탈퇴
- 이메일 인증 발송/재발송/검증
- OAuth 회원 생성 정책
- 오래된 미인증 로컬 계정 정리

### 3.2 외부에 공개하는 API

`member` 모듈이 다른 모듈에 공개하는 진입점은 [MemberApi](C:/Users/freetime/Desktop/projects/reservation-platform/backend/src/main/kotlin/io/github/chan808/reservation/member/api/MemberApi.kt) 이다.

다른 모듈은 직접 `MemberRepository`를 쓰지 않고 이 API를 통해서만 회원 인증용 정보를 조회하거나 회원 상태를 바꾼다.

현재 공개 메서드는 아래 용도다.

- `findAuthMemberByEmail`: 로그인/비밀번호 재설정 요청용 회원 조회
- `findAuthMemberById`: JWT 검증, OAuth 토큰 발급, 주문 처리용 회원 조회
- `verifyEmail`: 이메일 인증 완료
- `resendVerification`: 인증 메일 재발송
- `resetPassword`: 비밀번호 재설정 확정
- `findOrCreateOAuthMember`: OAuth 최초 로그인 시 회원 생성 또는 기존 회원 조회

### 3.3 modulith 경계

[MemberModule](C:/Users/freetime/Desktop/projects/reservation-platform/backend/src/main/kotlin/io/github/chan808/reservation/member/MemberModule.kt)는 아래 의존성만 허용한다.

- `common`
- `common :: metrics`
- `common :: ratelimit`

즉, `member`는 `auth`를 직접 의존하지 않는다. 반대로 `auth`가 `MemberApi`를 사용한다.

이 방향은 유지하는 것이 좋다.

## 4. 핵심 도메인 모델

### 4.1 Member 엔티티

핵심 엔티티는 [Member](C:/Users/freetime/Desktop/projects/reservation-platform/backend/src/main/kotlin/io/github/chan808/reservation/member/domain/Member.kt) 하나다.

주요 필드는 아래와 같다.

- `email`: 회원 식별 이메일. 탈퇴 전까지 유니크
- `password`: 로컬 계정 비밀번호 해시. OAuth 계정은 `null`
- `emailVerified`: 이메일 인증 완료 여부
- `provider`: OAuth 제공자. 로컬 계정은 `null`
- `providerId`: OAuth 제공자 내 사용자 식별값
- `nickname`: 프로필용 선택 필드
- `role`: `USER`, `ADMIN`
- `tokenVersion`: access token 즉시 무효화용 버전 값
- `withdrawnAt`: soft delete 여부

### 4.2 파생 상태

엔티티에 직접 메서드로 드러난 파생 상태는 아래 두 가지다.

- `isOAuthAccount`: `provider != null`
- `isWithdrawn`: `withdrawnAt != null`

### 4.3 현재 상태 모델의 의미

회원 상태는 별도 status enum이 아니라 아래 조합으로 표현된다.

- 정상 로컬 회원: `provider = null`, `withdrawnAt = null`
- 정상 OAuth 회원: `provider != null`, `withdrawnAt = null`
- 미인증 로컬 회원: `emailVerified = false`, `provider = null`, `withdrawnAt = null`
- 탈퇴 회원: `withdrawnAt != null`

현재는 별도 정지/잠금 상태는 없다.

## 5. DB 스키마 기준

회원 테이블의 기초는 [V1__create_members.sql](C:/Users/freetime/Desktop/projects/reservation-platform/backend/src/main/resources/db/migration/V1__create_members.sql)에 있다.

이후 회원 관련 변경은 아래 두 개가 중요하다.

- [V3__add_member_withdrawal_soft_delete.sql](C:/Users/freetime/Desktop/projects/reservation-platform/backend/src/main/resources/db/migration/V3__add_member_withdrawal_soft_delete.sql): `withdrawn_at` 추가
- [V6__add_member_token_version.sql](C:/Users/freetime/Desktop/projects/reservation-platform/backend/src/main/resources/db/migration/V6__add_member_token_version.sql): `token_version` 추가

현재 테이블 관점에서 중요한 제약은 아래와 같다.

- `email` 유니크
- `provider_key` 유니크
- 탈퇴는 row 삭제가 아니라 `withdrawn_at` 기반 soft delete

## 6. 상태 불변식

유지보수할 때 아래 규칙은 깨지면 안 된다.

### 6.1 OAuth 계정은 비밀번호가 없다

- `provider != null` 이면 `password`는 의미가 없다
- OAuth 계정은 비밀번호 로그인 불가
- OAuth 계정은 비밀번호 재설정 불가

### 6.2 탈퇴 회원은 조회 대상이 아니다

`MemberRepository`는 대부분 `...AndWithdrawnAtIsNull` 조건으로 조회한다.

즉, 탈퇴한 회원은 실질적으로 활성 회원 조회에서 빠진다.

### 6.3 탈퇴는 익명화까지 포함한다

탈퇴는 단순 soft delete가 아니다. 아래 값도 함께 바뀐다.

- `email` 익명화
- `providerId` 익명화
- `password = null`
- `nickname = null`
- `emailVerified = false`

이유는 아래 두 가지다.

- 기존 주문의 FK는 유지해야 함
- 원래 이메일로 재가입 가능해야 함

### 6.4 민감한 상태 변경은 tokenVersion을 올린다

현재 `tokenVersion`을 올리는 경우는 아래다.

- 비밀번호 변경
- 비밀번호 재설정
- 회원 탈퇴

이 값은 JWT claim에도 포함되고, 현재 회원의 `tokenVersion`과 다르면 기존 access token은 거절된다.

## 7. 회원 생명주기

### 7.1 로컬 회원 가입

실제 진입점은 [MemberController](C:/Users/freetime/Desktop/projects/reservation-platform/backend/src/main/kotlin/io/github/chan808/reservation/member/presentation/MemberController.kt)의 `POST /api/members` 이고, 핵심 로직은 [MemberCommandService](C:/Users/freetime/Desktop/projects/reservation-platform/backend/src/main/kotlin/io/github/chan808/reservation/member/application/MemberCommandService.kt)의 `signup()` 에 있다.

흐름은 아래와 같다.

1. IP 기준 회원가입 rate limit 검사
2. 이메일 소문자/trim 정규화
3. 같은 이메일의 활성 회원 조회
4. 기존 활성 회원이 있으면 분기
5. 신규 회원이면 비밀번호 검증 후 저장
6. 인증 메일 발송

### 7.2 특이 정책: 미인증 로컬 계정 재가입

현재 정책상 같은 이메일의 미인증 로컬 계정이 있으면 새 회원을 만들지 않는다.

대신 아래처럼 처리한다.

- 기존 비밀번호는 유지
- 새 비밀번호로 덮어쓰지 않음
- 인증 메일만 다시 보냄
- 응답은 기존 회원 기준으로 반환

이 정책은 “미인증 계정 재시도”를 허용하되 계정 상태를 복잡하게 바꾸지 않기 위한 선택이다.

주의할 점은 아래다.

- 미인증 계정 재가입 시 비밀번호를 바꾸는 UX는 현재 지원하지 않는다
- 이 정책을 바꾸면 가입 로직과 테스트를 같이 수정해야 한다

### 7.3 이메일 인증

이메일 인증 흐름은 [EmailVerificationService](C:/Users/freetime/Desktop/projects/reservation-platform/backend/src/main/kotlin/io/github/chan808/reservation/member/application/EmailVerificationService.kt)를 중심으로 동작한다.

핵심 저장소는 [EmailVerificationStore](C:/Users/freetime/Desktop/projects/reservation-platform/backend/src/main/kotlin/io/github/chan808/reservation/member/infrastructure/redis/EmailVerificationStore.kt) 이다.

구조는 아래와 같다.

- 토큰 -> memberId
- memberId -> 현재 토큰

즉, 회원당 최신 토큰 1개만 유지하는 방식이다.

정책은 아래와 같다.

- TTL은 24시간
- 새 토큰 발급 시 이전 토큰은 삭제
- 이미 인증된 회원은 재인증 불가
- OAuth 계정은 이메일 인증 재발송 대상이 아님

메일 자체는 [MemberEventListener](C:/Users/freetime/Desktop/projects/reservation-platform/backend/src/main/kotlin/io/github/chan808/reservation/member/application/MemberEventListener.kt)가 `MemberRegisteredEvent`를 `AFTER_COMMIT`으로 받아서 보낸다.

이 구조의 장점은 아래다.

- 트랜잭션 롤백된 가입에 대해 살아 있는 인증 링크가 나가지 않음
- 서비스 로직과 메일 전송을 느슨하게 분리

### 7.4 로그인 가능 조건

로그인 로직은 `auth` 모듈의 [AuthCommandService](C:/Users/freetime/Desktop/projects/reservation-platform/backend/src/main/kotlin/io/github/chan808/reservation/auth/application/AuthCommandService.kt)에 있지만, 회원 관점에서 실제 조건은 아래와 같다.

- 회원이 존재해야 함
- 탈퇴 회원이 아니어야 함
- OAuth 계정이 아니어야 함
- 비밀번호가 일치해야 함
- `emailVerified = true` 여야 함

현재 외부 응답은 계정 존재 여부, OAuth 계정 여부, 미인증 여부를 모두 감추고 `INVALID_CREDENTIALS`로 통일되어 있다.

운영자는 로그/metrics reason으로 상세 원인을 볼 수 있다.

### 7.5 프로필 수정

프로필 수정은 현재 `nickname` 하나만 다룬다.

정책은 아래와 같다.

- 최대 길이 50
- 공백 문자열은 서비스 내부에서 `null` 처리

즉, `nickname = null`은 “설정 없음” 상태다.

### 7.6 비밀번호 변경

[MemberCommandService](C:/Users/freetime/Desktop/projects/reservation-platform/backend/src/main/kotlin/io/github/chan808/reservation/member/application/MemberCommandService.kt)의 `changePassword()` 에서 처리한다.

순서는 아래와 같다.

1. 현재 비밀번호 일치 확인
2. 취약 비밀번호 검사
3. 새 비밀번호 해시 저장
4. `tokenVersion` 증가
5. `PasswordChangedEvent` 발행

이 이벤트를 [AuthEventListener](C:/Users/freetime/Desktop/projects/reservation-platform/backend/src/main/kotlin/io/github/chan808/reservation/auth/application/AuthEventListener.kt)가 받아 모든 refresh session과 access token version 캐시를 지운다.

즉, 비밀번호를 바꾸면 기존 로그인 세션이 모두 무효화된다.

### 7.7 비밀번호 재설정

비밀번호 재설정은 `auth` 모듈의 [PasswordResetService](C:/Users/freetime/Desktop/projects/reservation-platform/backend/src/main/kotlin/io/github/chan808/reservation/auth/application/PasswordResetService.kt)가 요청/확정을 나누어 처리한다.

회원 도메인 관점의 규칙은 아래와 같다.

- 요청 단계에서 모르는 이메일은 조용히 무시
- OAuth 계정은 조용히 무시
- 확정 단계에서만 `memberApi.resetPassword()` 호출
- 실제 비밀번호 변경은 `member` 모듈에서 수행
- 완료 시 `tokenVersion` 증가 + 세션 무효화

즉, 비밀번호 재설정도 비밀번호 변경과 동일한 보안 강도를 갖도록 맞춰져 있다.

### 7.8 회원 탈퇴

[MemberCommandService](C:/Users/freetime/Desktop/projects/reservation-platform/backend/src/main/kotlin/io/github/chan808/reservation/member/application/MemberCommandService.kt)의 `withdraw()` 가 핵심이다.

탈퇴 시 처리하는 항목은 아래와 같다.

- `withdrawnAt` 기록
- 이메일 익명화
- OAuth `providerId` 익명화
- 비밀번호 제거
- 닉네임 제거
- 이메일 인증 상태 초기화
- `tokenVersion` 증가
- `MemberWithdrawnEvent` 발행

이벤트 이후 `auth` 쪽에서 모든 세션을 무효화한다.

현재 컨트롤러는 탈퇴 응답 시 refresh token cookie도 즉시 만료 처리한다.

### 7.9 OAuth 가입/로그인

OAuth 쪽 실제 회원 생성은 `auth` 모듈의 [MemberOAuthService](C:/Users/freetime/Desktop/projects/reservation-platform/backend/src/main/kotlin/io/github/chan808/reservation/auth/infrastructure/oauth2/MemberOAuthService.kt)가 `MemberApi.findOrCreateOAuthMember()`를 호출하면서 시작된다.

[MemberQueryService](C:/Users/freetime/Desktop/projects/reservation-platform/backend/src/main/kotlin/io/github/chan808/reservation/member/application/MemberQueryService.kt)의 `findOrCreateOAuthMember()` 정책은 아래와 같다.

- `(provider, providerId)`가 이미 있으면 기존 회원 반환
- 같은 이메일의 활성 회원이 이미 있으면 생성 거부
- 새 OAuth 회원은 `emailVerified = true`로 생성
- 기본 role은 `USER`

즉, 현재는 “같은 이메일로 로컬 계정과 OAuth 계정 중복 공존”을 허용하지 않는다.

## 8. 취약 비밀번호 정책

[BreachedPasswordChecker](C:/Users/freetime/Desktop/projects/reservation-platform/backend/src/main/kotlin/io/github/chan808/reservation/member/infrastructure/security/BreachedPasswordChecker.kt)가 아래 두 수준을 검사한다.

### 8.1 문맥 기반 검사

- 비밀번호에 서비스명 포함 금지
- 이메일 local-part 일부 포함 금지

### 8.2 HIBP 검사

- SHA-1 k-anonymity 방식
- `api.pwnedpasswords.com/range/{prefix}` 사용
- 이미 유출된 비밀번호면 거부

현재 정책은 fail-open 이다.

즉, 외부 API 장애가 있어도 회원가입/비밀번호 변경 전체를 막지는 않는다. 대신 경고 로그만 남긴다.

이 선택은 가용성을 우선한 것이다.

## 9. rate limit 정책

회원 도메인과 직접 연결된 rate limit은 아래 세 가지다.

### 9.1 회원가입

[SignupRateLimitService](C:/Users/freetime/Desktop/projects/reservation-platform/backend/src/main/kotlin/io/github/chan808/reservation/member/application/SignupRateLimitService.kt)

- 기준: IP
- TTL: 1시간
- 제한: 5회

### 9.2 이메일 인증 재발송

[EmailVerificationResendRateLimitService](C:/Users/freetime/Desktop/projects/reservation-platform/backend/src/main/kotlin/io/github/chan808/reservation/member/application/EmailVerificationResendRateLimitService.kt)

- IP 기준: 1시간 10회
- 이메일 기준: 15분 3회

### 9.3 비밀번호 재설정 요청

구현은 `auth` 쪽에 있지만 회원 생명주기와 직접 연결된다.

- 요청 남용 방지
- 모르는 이메일에도 같은 외부 응답 유지

## 10. 미인증 계정 정리 정책

[UnverifiedMemberCleanupService](C:/Users/freetime/Desktop/projects/reservation-platform/backend/src/main/kotlin/io/github/chan808/reservation/member/application/UnverifiedMemberCleanupService.kt)와 [UnverifiedMemberCleanupScheduler](C:/Users/freetime/Desktop/projects/reservation-platform/backend/src/main/kotlin/io/github/chan808/reservation/member/application/UnverifiedMemberCleanupScheduler.kt)가 오래된 미인증 로컬 계정을 정리한다.

현재 정책은 아래와 같다.

- 대상: 로컬 계정
- 조건: `emailVerified = false`
- 조건: `withdrawnAt is null`
- 조건: 생성 시점이 cutoff 이전
- 처리: 인증 토큰 삭제 후 회원 row 일괄 삭제

기본 설정은 아래다.

- 활성화: `true`
- 보관 기간: 7일
- 실행 시간: 매일 새벽 3시

중요한 점은 아래다.

- 이 정리는 soft delete가 아니라 실제 삭제다
- 대상이 “아직 인증도 안 된 로컬 계정”으로 매우 제한되어 있다

## 11. auth 연동 포인트

유지보수 시 꼭 같이 봐야 하는 연동 지점은 아래다.

### 11.1 로그인

[AuthCommandService](C:/Users/freetime/Desktop/projects/reservation-platform/backend/src/main/kotlin/io/github/chan808/reservation/auth/application/AuthCommandService.kt)가 `findAuthMemberByEmail()`을 사용한다.

회원 쪽에서 바뀌면 로그인 영향이 큰 항목은 아래다.

- `emailVerified`
- `provider`
- `role`
- `tokenVersion`

### 11.2 JWT 즉시 무효화

비밀번호 변경/재설정/탈퇴 시 `tokenVersion`이 바뀌고, JWT에는 이 값이 들어간다.

JWT 검증 시 현재 회원의 `tokenVersion`과 다르면 access token을 거절한다.

즉, 회원 상태 변경이 인증 계층에 즉시 반영된다.

### 11.3 OAuth 로그인 성공 처리

[OAuth2SuccessHandler](C:/Users/freetime/Desktop/projects/reservation-platform/backend/src/main/kotlin/io/github/chan808/reservation/auth/infrastructure/oauth2/OAuth2SuccessHandler.kt)는 `issueTokensForOAuth(memberId)`를 호출한다.

현재는 내부에서 회원을 다시 조회해 role과 tokenVersion을 읽어 토큰을 만든다.

즉, OAuth 계정도 로컬 계정과 동일한 권한/토큰 버전 정책을 따른다.

## 12. 유지보수할 때 자주 보는 파일

### 12.1 도메인/정책

- [Member.kt](C:/Users/freetime/Desktop/projects/reservation-platform/backend/src/main/kotlin/io/github/chan808/reservation/member/domain/Member.kt)
- [MemberRole.kt](C:/Users/freetime/Desktop/projects/reservation-platform/backend/src/main/kotlin/io/github/chan808/reservation/member/domain/MemberRole.kt)

### 12.2 유스케이스

- [MemberCommandService.kt](C:/Users/freetime/Desktop/projects/reservation-platform/backend/src/main/kotlin/io/github/chan808/reservation/member/application/MemberCommandService.kt)
- [MemberQueryService.kt](C:/Users/freetime/Desktop/projects/reservation-platform/backend/src/main/kotlin/io/github/chan808/reservation/member/application/MemberQueryService.kt)
- [EmailVerificationService.kt](C:/Users/freetime/Desktop/projects/reservation-platform/backend/src/main/kotlin/io/github/chan808/reservation/member/application/EmailVerificationService.kt)
- [UnverifiedMemberCleanupService.kt](C:/Users/freetime/Desktop/projects/reservation-platform/backend/src/main/kotlin/io/github/chan808/reservation/member/application/UnverifiedMemberCleanupService.kt)

### 12.3 API/컨트롤러

- [MemberController.kt](C:/Users/freetime/Desktop/projects/reservation-platform/backend/src/main/kotlin/io/github/chan808/reservation/member/presentation/MemberController.kt)
- [AuthController.kt](C:/Users/freetime/Desktop/projects/reservation-platform/backend/src/main/kotlin/io/github/chan808/reservation/auth/presentation/AuthController.kt)

### 12.4 연동/이벤트

- [MemberApi.kt](C:/Users/freetime/Desktop/projects/reservation-platform/backend/src/main/kotlin/io/github/chan808/reservation/member/api/MemberApi.kt)
- [MemberEventListener.kt](C:/Users/freetime/Desktop/projects/reservation-platform/backend/src/main/kotlin/io/github/chan808/reservation/member/application/MemberEventListener.kt)
- [AuthEventListener.kt](C:/Users/freetime/Desktop/projects/reservation-platform/backend/src/main/kotlin/io/github/chan808/reservation/auth/application/AuthEventListener.kt)

### 12.5 저장소/보조 인프라

- [MemberRepository.kt](C:/Users/freetime/Desktop/projects/reservation-platform/backend/src/main/kotlin/io/github/chan808/reservation/member/infrastructure/persistence/MemberRepository.kt)
- [EmailVerificationStore.kt](C:/Users/freetime/Desktop/projects/reservation-platform/backend/src/main/kotlin/io/github/chan808/reservation/member/infrastructure/redis/EmailVerificationStore.kt)
- [BreachedPasswordChecker.kt](C:/Users/freetime/Desktop/projects/reservation-platform/backend/src/main/kotlin/io/github/chan808/reservation/member/infrastructure/security/BreachedPasswordChecker.kt)

## 13. 변경할 때 체크리스트

회원 도메인을 수정할 때는 아래를 같이 확인하는 것이 좋다.

### 13.1 상태 필드 변경 시

- `Member` 필드 변경이 `AuthMemberView`에도 반영되어야 하는가
- `MemberResponse`에 노출해야 하는가
- migration이 필요한가
- soft delete 조회 조건이 깨지지 않는가

### 13.2 로그인/세션 관련 변경 시

- `tokenVersion` 증가 시점이 맞는가
- `AuthEventListener` 세션 무효화가 같이 필요한가
- OAuth 토큰 발급에도 같은 정책이 반영되는가

### 13.3 이메일 인증 변경 시

- member당 최신 토큰 1개 정책을 유지할 것인가
- 메일 발송은 여전히 AFTER_COMMIT인가
- 재발송 rate limit이 충분한가

### 13.4 탈퇴 정책 변경 시

- 주문 FK 보존 요구를 깨지 않는가
- 이메일 재사용 정책과 충돌하지 않는가
- 익명화 범위가 충분한가

### 13.5 OAuth 정책 변경 시

- 로컬 계정과 OAuth 계정의 이메일 충돌 정책을 유지할 것인가
- `providerId` 익명화 규칙과 충돌하지 않는가

## 14. 현재 의도적으로 남겨둔 비범위

현재 회원 도메인에는 아래 기능이 없다.

- 계정 잠금/정지 상태
- 최근 인증 시점 기반 step-up auth
- 여러 프로필 테이블 분리
- 마케팅 수신 동의 같은 부가 속성

즉, 현재 설계는 “쇼핑몰/예약 서비스의 기본 회원 관리 + 인증 연동”에 집중한 상태다.

## 15. 한 줄 요약

현재 회원 도메인은 `members` 단일 엔티티를 중심으로 로컬/OAuth 회원을 함께 관리하고, 이메일 인증·비밀번호 변경·탈퇴 같은 상태 변화를 `auth` 세션 무효화와 연결해 운영 위험을 줄이는 구조다.
