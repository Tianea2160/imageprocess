---
globs:
  - infra/**/*.kt
  - app-api/**/*.kt
  - app-consumer/**/*.kt
---

# Adapter Layer Rules

이 디렉토리는 외부 기술과의 연결을 담당하는 레이어입니다.

## 의존성 규칙

- domain, application 패키지 참조 가능
- Spring, JPA, Redis 등 기술 의존성 허용
- adapter 간 직접 참조 금지 (inbound ↛ outbound, persistence ↛ redis 등)

## 서브 패키지별 역할

### inbound/web/
- REST Controller (`@RestController`)
- 요청/응답 DTO 정의
- inbound port(유스케이스)를 호출

### outbound/persistence/
- JPA Entity (`@Entity`) 정의
- JPA Repository 구현
- 도메인 모델 ↔ JPA Entity 매핑 (Mapper)
- outbound port 구현체 (`@Repository`, `@Component`)

### outbound/mockworker/
- Mock Worker HTTP 클라이언트 (RestClient)
- 요청/응답 DTO 정의
- outbound port 구현체

### outbound/redis/
- Redis 기반 RateLimiter, CircuitBreaker 구현
- outbound port 구현체
