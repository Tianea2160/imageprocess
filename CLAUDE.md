# Image Process Service

외부 Mock Worker와 연동하여 비동기 이미지 처리 작업을 관리하는 서비스.

## 빌드 & 실행

```bash
./gradlew build                              # 전체 빌드
./gradlew test                               # 전체 테스트
./gradlew :core:domain:test                  # 모듈별 테스트
./gradlew ktlintCheck                        # 린트 검사
./gradlew ktlintFormat                       # 린트 자동 수정
./gradlew stress-test:gatlingRun             # 부하 테스트 (Gatling)
docker compose up -d                         # DB + Redis + Kafka 실행
```

## 모듈 구조

```
core/domain/         # 순수 도메인 모델, 포트 인터페이스 (Spring 금지)
core/application/    # 유스케이스 구현 (@Service, @Transactional)
infra/persistence/   # JPA + Flyway (PostgreSQL)
infra/redis/         # Redis 기반 RateLimiter, CircuitBreaker
infra/mockworker/    # Mock Worker HTTP 클라이언트
infra/kafka/         # Kafka producer
app-api/             # REST API 서버 (port 8082)
app-consumer/        # Kafka consumer + 폴링 워커 (port 8081)
convention-test/     # Konsist 아키텍처 규칙 검증
stress-test/         # Gatling 부하 테스트
```

## 아키텍처: 헥사고날 (Ports & Adapters)

의존성 방향 규칙은 `.claude/rules/`에 레이어별로 정의. 핵심 원칙:
- domain → 순수 Kotlin만. Spring/JPA/외부 라이브러리 금지.
- application → domain만 참조. adapter 직접 참조 금지.
- adapter 간 직접 참조 금지 (persistence ↛ redis, inbound ↛ outbound).

## 환경

| 서비스 | 로컬 포트 |
|--------|----------|
| PostgreSQL | 25432 |
| Redis | 36379 |
| Kafka | 29092 |

- `MOCK_WORKER_API_KEY`: Mock Worker API 인증 키 (기본값 있음, 프로덕션에서는 환경변수 설정 필요)
- Spring 프로필: `default` (로컬), `docker` (컨테이너)

## 컨벤션

- Kotlin 코드 스타일: ktlint_official (.editorconfig 참조)
- max line length: 140
- 테스트: JUnit 5 + Testcontainers (`@ServiceConnection` 자동 설정)
- 도메인 모델은 불변 객체 — `withXxx()` 메서드로 새 인스턴스 생성
- JPA Entity와 도메인 모델 완전 분리 — 매퍼로 변환

## 주의사항

- 두 개의 Spring Boot 앱이 존재: app-api (REST), app-consumer (워커). 각각 독립 실행.
- fingerprint(SHA-256)로 중복 요청 방지 — 동일 imageUrl은 기존 Task 반환.
- 폴링에 가상 스레드 사용 (`spring.threads.virtual.enabled: true`).
- TaskJpaEntity에 `@Version` 낙관적 잠금 — 동시 업데이트 충돌 방지.
