---
globs: infra/mockworker/**/*.kt
---

# Mock Worker Module Rules

## 공식 문서

- Spring RestClient: https://docs.spring.io/spring-framework/reference/integration/rest-clients.html#rest-restclient
- JdkClientHttpRequestFactory: https://docs.spring.io/spring-framework/reference/integration/rest-clients.html#rest-http-interface-jdkclient
- Spring Retry (@Retryable): https://docs.spring.io/spring-retry/reference/

## 현재 구현 패턴

### HTTP 클라이언트
- `RestClient` + `JdkClientHttpRequestFactory` (Java HttpClient 기반)
- 타임아웃: `connect-timeout` 5s, `read-timeout` 10s (`@Value`로 주입)
- 인증: `X-API-KEY` 헤더 기본 설정

### 재시도 & 예외 계층
- `@Retryable`: max-retries 1, delay 100ms, multiplier 2.0 (지수 백오프)
- `RetryableWorkerException` — 500, 502, 503, 빈 응답 → 재시도 대상
- `NonRetryableWorkerException` — 400, 401, 404, 422 → 즉시 실패
- `RateLimitedWorkerException` — 429 → `retry-after` 헤더 파싱, CircuitBreaker 연동

### Adapter 변환
- `MockWorkerAdapter`가 예외를 도메인 sealed class(`SubmitResult`/`StatusResult`)로 변환
- Client → 예외 throw, Adapter → catch 후 도메인 결과 반환

## 주의사항

- `@EnableResilientMethods`가 앱 모듈에서 활성화되어야 `@Retryable` 동작
- 응답 본문이 null/empty면 `RetryableWorkerException` throw — 불완전 응답 자동 재시도
- 에러 응답의 `detail` 필드를 파싱하여 실패 사유 추출 (JSON 파싱 실패 시 원본 body 사용)
