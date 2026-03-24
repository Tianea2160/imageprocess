# Image Process Service

외부 Mock Worker와 연동하여 비동기 이미지 처리 작업을 관리하는 서비스.

## 빌드 & 실행

```bash
./gradlew build                # 빌드
./gradlew test                 # 테스트
./gradlew ktlintCheck          # 린트 검사
./gradlew ktlintFormat         # 린트 자동 수정
docker compose up -d           # DB + Redis 실행
```

## 아키텍처: 헥사고날 (Ports & Adapters)

```
adapter/inbound  →  application  →  domain
adapter/outbound ←  application  ←  domain
```

### 의존성 방향 규칙

- **domain**: 외부 의존성 없음. 순수 Kotlin만 사용. Spring/JPA 어노테이션 금지.
- **application**: domain에만 의존. adapter를 직접 참조하지 않고 port 인터페이스를 통해 접근.
- **adapter**: domain, application에 의존 가능. Spring, JPA, Redis 등 기술 의존성 허용.

### 패키지 구조

- `domain/model/` — 엔티티, VO, 상태 머신 (순수 Kotlin)
- `domain/port/inbound/` — 유스케이스 인터페이스 (Controller → Application)
- `domain/port/outbound/` — 인프라 인터페이스 (Application → Adapter)
- `application/` — 유스케이스 구현 (포트 조합)
- `adapter/inbound/web/` — REST Controller
- `adapter/outbound/persistence/` — JPA Entity, Repository 구현체
- `adapter/outbound/mockworker/` — Mock Worker HTTP 클라이언트
- `adapter/outbound/redis/` — Redis 기반 RateLimiter, CircuitBreaker

### 도메인 모델 ↔ JPA Entity 완전 분리

- `domain/model/Task`는 순수 도메인 객체 (어노테이션 없음)
- `adapter/outbound/persistence/`에 JPA Entity + 매퍼 별도 구현
- 도메인 로직이 인프라 기술에 의존하지 않도록 보장

## 컨벤션

- Kotlin 코드 스타일: ktlint_official (.editorconfig 참조)
- max line length: 140
- 테스트: JUnit 5 + Testcontainers
