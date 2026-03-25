---
globs: core/application/**/*.kt
---

# Application Layer Rules

이 디렉토리는 유스케이스 구현 레이어입니다.

## 의존성 규칙

- domain 패키지만 참조 가능
- adapter 패키지 직접 참조 금지 — port 인터페이스를 통해서만 접근
- Spring `@Service`, `@Transactional` 어노테이션 허용

## 이 레이어에 위치하는 것

- inbound port(유스케이스 인터페이스) 구현체
- outbound port를 조합하여 비즈니스 로직 수행
- 트랜잭션 경계 관리
