---
globs: infra/kafka/**/*.kt
---

# Kafka Module Rules

## 공식 문서

- Spring for Apache Kafka: https://docs.spring.io/spring-kafka/reference/
- @RetryableTopic (Non-Blocking Retries): https://docs.spring.io/spring-kafka/reference/retrytopic.html
- Apache Kafka 공식: https://kafka.apache.org/documentation/

## 현재 구현 패턴

- Producer/Consumer 모두 `JacksonJsonSerializer`/`JacksonJsonDeserializer`에 커스텀 `JsonMapper` 주입
- `trustedPackages`를 `com.example.imageprocess.adapter.kafka`로 제한 — 임의 역직렬화 방지
- Topic: `task-submit` (3 파티션, 1 레플리카), taskId를 key로 사용하여 파티션별 순서 보장
- Consumer: `@RetryableTopic`으로 지수 백오프 재시도 (2s → 4s → 8s, max 30s, 3회)
- DLT(Dead Letter Topic) 핸들러에서 Task를 FAILED로 전환

## 주의사항

- `KafkaConfig`의 `JsonMapper` 빈은 이 모듈에서 정의하지 않음 — 앱 모듈에서 제공 필요
- Consumer 메서드는 `@Transactional` — DB 쓰기와 오프셋 커밋이 원자적으로 처리됨
- `ConsumerRecord<K, V>`를 직접 받아 메타데이터(파티션, 오프셋) 접근 가능
