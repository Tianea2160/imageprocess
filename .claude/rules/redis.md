---
globs: infra/redis/**/*.kt
---

# Redis Module Rules

## 공식 문서

- Spring Data Redis: https://docs.spring.io/spring-data-redis/reference/
- Redis Commands (HMGET, HMSET, PEXPIRE 등): https://redis.io/docs/latest/commands/
- Lua Scripting in Redis: https://redis.io/docs/latest/develop/interact/programmability/eval-intro/
- Lettuce Client: https://redis.github.io/lettuce/

## 현재 구현 패턴

### RateLimiter (Token Bucket)
- **Lua 스크립트**로 원자적 토큰 버킷 구현 — `HMGET`/`HMSET` + `PEXPIRE`
- Redis Hash 키: `rate_limiter:mock_worker` (필드: `tokens`, `last_refill`)
- 설정: `max-tokens`, `refill-rate` (토큰/인터벌), `refill-interval-ms`
- 키 자동 만료로 idle 상태 시 메모리 정리

### CircuitBreaker (3-State)
- Redis 키 3개: `failure_count`, `open_until`, `half_open_calls` (prefix: `circuit_breaker:mock_worker`)
- `failure_count`에 TTL(`failure-window-seconds`) 적용 — 오래된 실패 자동 제거
- `isOpen()` 호출 시 half-open 상태면 `half_open_calls` increment로 호출 횟수 추적
- `recordSuccess()` 시 3개 키 모두 삭제 (완전 리셋)

## 주의사항

- Lua 스크립트 수정 시 원자성 보장 확인 필요 — 스크립트 내에서 다른 키 접근 금지 (클러스터 호환)
- `StringRedisTemplate` 사용 — 모든 값이 문자열로 저장됨
- 테스트에 Testcontainers Redis 7 Alpine 사용 (`RedisTestConfig`)
