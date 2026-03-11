# REQUIREMENTS.md
> 26년 3월 기준 작성 | 한정 상품 판매 플랫폼

---

## 프로젝트 목적

특정 시간에 오픈되는 한정 상품에 대규모 트래픽이 몰리는 시나리오를 상정한 고성능 커머스 플랫폼.  
동시성 제어, 성능 튜닝, 트래픽 제어의 단계적 개선을 통해 기술적 깊이를 증명한다.

---

## 핵심 시나리오

```
오픈 시간 도달
→ 다수 유저 동시 접근
→ 상품 선택 및 수량 지정
→ 재고 차감 (원자적)
→ 결제 요청 (PG 연동)
→ 결제 성공 → 주문 확정
→ 결제 실패 → 재고 복구
→ 재고 소진 → 이후 요청 즉시 거절
```

---

## 상정 부하 규모

| 단계 | 시나리오 | 목표 |
|------|----------|------|
| 1차 | 1,000 VU 동시 요청 | 병목 지점 파악 |
| 2차 | 5,000 VU 동시 요청 | TPS 개선, 에러율 감소 |
| 3차 | 10,000 VU 동시 요청 | 안정적 트래픽 제어 |

---

## 기능 범위

### 필수 구현 (전 단계 공통)

**상품**
- 상품 등록 (이름, 설명, 가격, 재고 수량, 오픈 시간)
- 상품 목록 조회 / 상세 조회
- 오픈 전 / 판매 중 / 매진 상태 관리

**주문**
- 주문 생성 (재고 차감 → 결제 요청)
- 주문 조회 / 주문 취소
- 결제 실패 시 재고 자동 복구
- 결제 타임아웃 시 자동 취소 스케줄러

**결제**
- 토스페이먼츠 테스트 모드 연동 (실 구현)
- 카카오페이, 페이팔 인터페이스 정의 (확장 구조만)
- Strategy 패턴으로 결제 수단 추상화
- Webhook 수신 후 주문 상태 업데이트

**인증**
- 회원 템플릿 그대로 사용 (JWT, OAuth)

### 2차 추가

- Redis 기반 재고 관리 및 원자적 차감 (Lua script)
- Redisson 분산 락
- Kafka 기반 결제 이벤트 비동기 처리
- 주문 이벤트 발행 (재고 복구, 알림 등)

### 3차 추가

- Redis Sorted Set 기반 대기열
- SSE로 대기 순번 실시간 전달
- 다중 서버 구성 (수평 확장)
- 세션리스 완전 stateless 확인

---

## 결제 확장 설계

```kotlin
interface PaymentGateway {
    val type: PaymentType
    fun pay(request: PaymentRequest): PaymentResult
    fun cancel(paymentId: String): CancelResult
    fun webhook(payload: String): WebhookEvent
}

enum class PaymentType {
    TOSS, KAKAO, PAYPAL
}

// 실 구현
class TossPaymentGateway : PaymentGateway

// 인터페이스만 정의 (추후 구현)
class KakaoPaymentGateway : PaymentGateway
class PaypalGateway : PaymentGateway
```

---

## 단계별 로드맵

### 1차 - 단순 구현 + 병목 확인

**목표:** 어디서 터지는지 확인  
**기술 스택:** Spring Boot, JPA, MySQL, Redis(X), Kafka(X)  
**동시성 제어:** DB 비관적 락 또는 낙관적 락  
**k6 측정 지표:**
- TPS (초당 처리량)
- p95 / p99 응답시간
- 에러율
- DB 커넥션 고갈 지점
- 재고 race condition 발생 여부

**완료 기준:** k6 리포트 + 병목 지점 문서화

---

### 2차 - Redis + Kafka 도입

**목표:** 동시성 해결, TPS 개선  
**추가 기술:** Redis, Redisson, Kafka  
**핵심 변경:**
- 재고를 Redis로 이관, Lua script 원자적 차감
- 분산 락으로 중복 요청 제어
- 결제 이벤트 Kafka로 비동기 처리
- 재고 복구를 Kafka consumer에서 처리

**k6 측정:** 1차 대비 TPS, 에러율 개선 수치 비교  
**완료 기준:** 1차 대비 정량적 개선 수치 확인

---

### 3차 - 대기열 + 다중 서버

**목표:** 피크 트래픽 제어, 수평 확장  
**추가 기술:** Redis Sorted Set, SSE, Docker Compose scale  
**핵심 변경:**
- 오픈 시점 요청을 대기열로 흡수
- SSE로 유저에게 대기 순번 실시간 전달
- 다중 서버 환경에서 세션/상태 정합성 확인
- Nginx 로드밸런서 구성

**k6 측정:** 10,000 VU 시나리오에서 안정적 처리 확인  
**완료 기준:** 대기열 도입 전후 수치 비교 + 다중 서버 정상 동작

---

## 기술 스택 (최종)

| 영역 | 기술 |
|------|------|
| Language | Kotlin |
| Framework | Spring Boot 3.x |
| ORM | Spring Data JPA + QueryDSL |
| DB | MySQL 8.x |
| Cache / 분산 락 | Redis 7.x + Redisson |
| Message Queue | Kafka |
| 결제 | 토스페이먼츠 (테스트 모드) |
| 인증 | JWT (회원 템플릿 기반) |
| 모니터링 | Prometheus + Grafana |
| 부하 테스트 | k6 |
| 인프라 | Docker Compose (단일 → 다중 서버) |
| 문서 | Swagger (springdoc-openapi) |

---

## 비기능 요구사항

- 재고는 절대 초과 차감되지 않는다
- 결제 실패 시 반드시 재고가 복구된다
- 동일 유저의 중복 주문을 방지한다
- 모든 주문 상태 변경은 추적 가능해야 한다
- 각 단계별 k6 리포트를 README에 수치로 공개한다

---

## 명시적으로 제외하는 기능

- 배송 추적
- 리뷰 / 평점
- 포인트 / 쿠폰
- 어드민 페이지 (기본 API만)
- 실제 결제 (테스트 모드만)

> 위 기능은 도메인 복잡도만 높이고 기술력 어필과 무관하므로 제외
