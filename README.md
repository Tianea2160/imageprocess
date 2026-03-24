# Image Process Service

외부 이미지 처리 서비스(Mock Worker)와 연동하여 비동기 이미지 처리 작업을 관리하는 백엔드 서비스입니다.

---

## 실행 방법

### 사전 요구사항

- Docker, Docker Compose

### 실행

```bash
docker compose up -d
```

- 애플리케이션: `http://localhost:8080`

---

## API 명세

### 1. 이미지 처리 요청

```
POST /api/tasks
Content-Type: application/json

{
  "imageUrl": "https://example.com/image.png"
}
```

**응답 (202 Accepted)**

```json
{
  "taskId": "uuid",
  "status": "PENDING",
  "createdAt": "2026-03-24T12:00:00"
}
```

### 2. 작업 상태 조회

```
GET /api/tasks/{taskId}
```

**응답 (200 OK)**

```json
{
  "taskId": "uuid",
  "imageUrl": "https://example.com/image.png",
  "status": "COMPLETED",
  "result": "처리 결과",
  "failReason": null,
  "retryCount": 0,
  "createdAt": "2026-03-24T12:00:00",
  "updatedAt": "2026-03-24T12:00:30"
}
```

### 3. 작업 목록 조회

```
GET /api/tasks?page=0&size=20&status=COMPLETED
```

---

## 설계 문서

### 상태 모델

```
PENDING ──→ SUBMITTED ──→ PROCESSING ──→ COMPLETED
                │                │
                ↓                ↓
          RETRY_WAITING ──→ SUBMITTED (재시도)
                │
                ↓ (최대 재시도 초과)
              FAILED
```

| 상태 | 설명 |
|------|------|
| `PENDING` | 클라이언트 요청 접수, DB 저장 완료. Mock Worker에 아직 미전송 |
| `SUBMITTED` | Mock Worker에 요청 전송 완료 (jobId 수신) |
| `PROCESSING` | Mock Worker가 작업 진행 중 |
| `COMPLETED` | 작업 완료, 결과 저장됨 |
| `FAILED` | 최종 실패 (재시도 횟수 초과 또는 복구 불가능한 오류) |
| `RETRY_WAITING` | 일시적 실패 후 재시도 대기 중 |

**허용되는 상태 전이 (경량 상태 머신으로 직접 구현):**

```kotlin
enum class TaskStatus {
    PENDING, SUBMITTED, PROCESSING, COMPLETED, FAILED, RETRY_WAITING;

    companion object {
        private val allowedTransitions = mapOf(
            PENDING to setOf(SUBMITTED, FAILED),
            SUBMITTED to setOf(PROCESSING, FAILED, RETRY_WAITING),
            PROCESSING to setOf(COMPLETED, FAILED, RETRY_WAITING),
            RETRY_WAITING to setOf(SUBMITTED, FAILED),
            // COMPLETED, FAILED → 전이 불가 (terminal states)
        )

        fun canTransition(from: TaskStatus, to: TaskStatus): Boolean =
            allowedTransitions[from]?.contains(to) ?: false
    }
}
```

- Entity 내부에서 상태 변경 시 `canTransition`으로 검증, 위반 시 예외 발생
- Spring Statemachine 등 외부 라이브러리 대신 직접 구현 — 과제 규모에 비해 프레임워크 도입은 과도
- 전이 규칙이 enum 한 곳에 집중되어 테스트 및 유지보수 용이

**허용되지 않는 상태 전이:**

- `COMPLETED → *` : 완료된 작업은 변경 불가
- `FAILED → *` : 최종 실패 상태는 변경 불가 (별도 재요청 필요)
- `PROCESSING → PENDING` : 역방향 전이 불가
- `SUBMITTED → PENDING` : 역방향 전이 불가

### 설계 의도

**왜 서버가 자체 상태를 관리하는가?**

Mock Worker가 상태를 관리하지만, 다음 이유로 자체 상태가 필요합니다:

1. Mock Worker 장애 시에도 클라이언트에 일관된 상태 제공
2. 중복 요청 판별을 위한 기록 보관
3. 서버 재시작 후 미완료 작업 복구 (jobId 매핑 유지)
4. Mock Worker에 작업 목록 API가 없으므로 자체 관리 필요
5. 상태 전이 규칙을 서버 측에서 강제

### 중복 요청 처리

- `imageUrl`의 SHA-256 해시를 fingerprint로 사용하여 중복 판별
- 동일 imageUrl 요청 시 Mock Worker에 재전송하지 않고 기존 task를 반환
- fingerprint 컬럼에 unique constraint 적용 → 동시 중복 요청도 DB 레벨에서 방지
- 클라이언트에 별도 헤더(Idempotency-Key 등)를 강제하지 않음 — 요청 데이터 자체로 멱등성 보장

### 처리 보장 모델

**At-least-once delivery**

- Mock Worker 호출 실패 시 재시도하므로 중복 처리될 수 있음
- DB에 상태를 먼저 기록한 후 외부 호출 → 서버 장애 시에도 재시도 보장
- Mock Worker 측 중복 호출은 jobId 기반으로 판별 가능

**근거:**
- 이미지 처리는 누락보다 중복이 낫다 (결과가 동일하므로)
- exactly-once는 분산 환경에서 달성이 어렵고 복잡성 대비 이점이 적음
- at-most-once는 작업 유실 가능성이 있어 부적합

### 실패 처리 전략

1. **Mock Worker 요청 실패 (네트워크/5xx):** RETRY_WAITING 전이 → exponential backoff 재시도 (최대 3회)
2. **429 Too Many Requests:** RETRY_WAITING 전이 → 지연 후 재시도
3. **4xx (잘못된 요청):** 즉시 FAILED 전이 (재시도 불필요)
4. **Mock Worker 작업 자체 FAILED:** FAILED 전이, failReason 기록

### 폴링 부하 분산 전략

동시에 다수의 작업이 접수되면 폴링 타이머가 동시에 실행되는 thundering herd 문제가 발생합니다.
이를 계층적으로 방어합니다.

**1) 작업별 `nextPollAt` 관리**

고정 주기(`@Scheduled(fixedDelay)`)로 전체 작업을 순회하는 대신,
작업마다 다음 폴링 시각(`nextPollAt`)을 DB에 저장하고 `WHERE next_poll_at <= now()` 조건으로 조회합니다.

```kotlin
// 작업 생성 시 — 초기 폴링 시점 분산 (staggered scheduling)
job.nextPollAt = Instant.now() + estimatedProcessingTime + random(0..spreadWindow)

// 폴링 후 — exponential backoff + full jitter
val backoff = min(baseDelay * 2.0.pow(pollCount), maxDelay)
job.nextPollAt = Instant.now() + Duration.ofMillis(random.nextLong(0, backoff.toLong()))
```

**2) Full Jitter (AWS 권장)**

전체 딜레이를 `random(0, base * 2^attempt)`로 랜덤화합니다.
AWS Architecture Blog 시뮬레이션에서 가장 적은 총 호출 수와 가장 빠른 완료 시간을 달성한 전략입니다.

> 참고: Marc Brooker, "Exponential Backoff and Jitter," AWS Architecture Blog, 2015

**3) Rate Limiter (Token Bucket) — Redis 기반**

Redis에 Token Bucket을 구현하여 Mock Worker에 대한 초당 요청 수를 하드캡합니다.
다중 인스턴스 환경에서도 전역적으로 요청률을 제어할 수 있습니다.

**4) Circuit Breaker — Redis 기반**

Mock Worker가 연속적으로 429/5xx를 반환하면 Circuit Breaker가 열려 폴링을 일시 중단합니다.
cooldown 후 half-open 상태에서 점진적으로 재개합니다.
Redis에 상태를 저장하여 다중 인스턴스 간 Circuit Breaker 상태를 공유합니다.

**5) 코루틴 기반 비동기 I/O**

스케줄러 내부에서 코루틴 스코프를 사용하여 Mock Worker 폴링의 I/O 블로킹을 최소화합니다.
WebMVC 기반은 유지하되, 스케줄러의 I/O 작업만 코루틴으로 병렬 처리합니다.

```kotlin
@Scheduled(fixedDelay = 2000)
fun pollTasks() = runBlocking {
    val tasks = taskRepository.findPollableTasks(Instant.now(), budgetPerTick)
    coroutineScope {
        tasks.map { task ->
            async(Dispatchers.IO) {
                pollAndUpdateTask(task)
            }
        }.awaitAll()
    }
}
```

- 스레드 몇 개로 수십~수백 건의 폴링을 동시에 처리
- Controller(WebMVC)는 변경 없이 유지

**폴링 스케줄러 실행 흐름:**

```
@Scheduled (매 1~2초)
  1. Circuit Breaker 상태 확인 — open이면 skip
  2. DB 조회: SELECT * FROM tasks
       WHERE status IN ('SUBMITTED', 'PROCESSING')
       AND next_poll_at <= now()
       ORDER BY next_poll_at ASC
       LIMIT :budgetPerTick
       FOR UPDATE SKIP LOCKED
  3. coroutineScope 내에서 각 작업을 async(Dispatchers.IO)로 병렬 폴링
  4. 각 폴링은 RateLimiter를 통해 Mock Worker 호출
  5. 결과에 따라 상태 전이 + nextPollAt 갱신
  6. 429 응답 시 → RateLimiter 조정, Circuit Breaker 카운트 증가
```

### 동시 요청 시 고려 사항

- fingerprint(imageUrl 해시)에 unique constraint를 걸어 동시 중복 요청 방지
- 상태 전이 시 optimistic locking (JPA `@Version`)으로 동시 수정 충돌 방지
- 폴링 스케줄러가 동일 작업을 중복 처리하지 않도록 `SELECT FOR UPDATE SKIP LOCKED` 활용

### 트래픽 증가 시 병목 가능 지점

| 병목 지점 | 증상 | 대응 |
|-----------|------|------|
| Mock Worker 호출부 | 429 응답 증가 | RateLimiter + Circuit Breaker로 요청 속도 제어 |
| 폴링 스케줄러 | 미완료 작업 폭증 시 폴링 지연 | `LIMIT` 기반 배치 크기 제한, nextPollAt 기반 우선순위 |
| DB | 작업 수 증가 시 조회 성능 저하 | 상태+nextPollAt 복합 인덱스, 페이징 처리 |
| 작업 제출 | Mock Worker 응답 지연으로 제출 적체 | @Async로 비동기 제출, 제출 큐 depth 모니터링 |

### 외부 시스템 연동 방식

- **RestClient** (Spring 4.0 기본 HTTP 클라이언트) 사용
- **Resilience4j** — RateLimiter, CircuitBreaker, Retry 통합
- 타임아웃 설정: connection 5s, read 30s
- Mock Worker API Key는 환경변수로 관리

### 서버 재시작 시 동작

**복구 프로세스:**
1. 서버 시작 시 `PENDING`, `SUBMITTED`, `PROCESSING`, `RETRY_WAITING` 상태 작업 스캔
2. `SUBMITTED`/`PROCESSING` 작업: jobId로 Mock Worker에 상태 확인 → DB 동기화
3. `PENDING` 작업: Mock Worker에 재전송
4. `RETRY_WAITING` 작업: 재시도 스케줄에 재등록

**데이터 정합성이 깨질 수 있는 지점:**
- Mock Worker에 요청을 보낸 직후, jobId를 DB에 저장하기 전에 서버가 종료되면 → PENDING 상태로 남아 중복 요청 발생 가능 (at-least-once 허용 범위)
- Mock Worker에서 COMPLETED로 전이되었으나 폴링 전에 서버가 종료되면 → 재시작 후 폴링으로 복구

---

## 기술 스택

| 구성요소 | 선택 |
|---------|------|
| Language | Kotlin |
| Framework | Spring Boot 4.0 |
| DB | PostgreSQL 17 |
| Cache/Resilience | Redis 7 (RateLimiter, CircuitBreaker 상태 공유) |
| Migration | Flyway |
| HTTP Client | RestClient |
| Async I/O | Kotlin Coroutines (스케줄러 내 I/O 병렬 처리) |
| Test | JUnit 5, Testcontainers |
| Container | Docker Compose |
| Lint | ktlint |

---

## 프로젝트 구조 (헥사고날 아키텍처)

```
src/main/kotlin/com/example/imageprocess/
├── ImageprocessApplication.kt
│
├── domain/                              # 핵심 도메인 (순수 Kotlin, 외부 의존성 없음)
│   ├── model/
│   │   ├── Task.kt                      # 도메인 엔티티
│   │   └── TaskStatus.kt               # 상태 머신 enum
│   └── port/
│       ├── inbound/
│       │   └── TaskUseCase.kt           # 유스케이스 인터페이스
│       └── outbound/
│           ├── TaskRepository.kt        # DB 저장 포트
│           ├── ImageProcessor.kt        # Mock Worker 호출 포트
│           ├── RateLimiter.kt           # Rate Limiter 포트
│           └── CircuitBreaker.kt        # Circuit Breaker 포트
│
├── application/                         # 유스케이스 구현 (도메인 포트 조합)
│   ├── TaskService.kt
│   └── TaskPollingService.kt
│
└── adapter/                             # 외부 기술 구현체
    ├── inbound/
    │   └── web/
    │       ├── TaskController.kt        # REST API
    │       ├── TaskRequest.kt           # 요청 DTO
    │       └── TaskResponse.kt          # 응답 DTO
    └── outbound/
        ├── persistence/
        │   ├── TaskJpaEntity.kt         # JPA Entity
        │   ├── TaskJpaRepository.kt     # Spring Data JPA
        │   └── TaskPersistenceAdapter.kt # outbound port 구현체 + 매퍼
        ├── mockworker/
        │   └── MockWorkerAdapter.kt     # Mock Worker HTTP 클라이언트
        └── redis/
            ├── RedisRateLimiterAdapter.kt
            └── RedisCircuitBreakerAdapter.kt
```

**의존성 방향:** `adapter → application → domain` (domain은 외부를 모름)
