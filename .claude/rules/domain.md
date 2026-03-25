---
globs: core/domain/**/*.kt
---

# Domain Layer Rules

이 디렉토리는 헥사고날 아키텍처의 핵심 도메인 레이어입니다.

## 절대 금지

- Spring 어노테이션 (`@Component`, `@Service`, `@Entity`, `@Repository` 등)
- JPA 어노테이션 (`@Id`, `@Column`, `@Table` 등)
- 외부 라이브러리 import (Spring, Jackson, Resilience4j 등)
- adapter, application 패키지 참조

## 허용

- 순수 Kotlin 코드만 사용
- kotlin stdlib, java stdlib만 import 가능
- domain 내부 패키지 간 참조 가능

## 이 레이어에 위치하는 것

- `model/`: 도메인 엔티티, Value Object, 상태 머신 (TaskStatus)
- `port/inbound/`: 유스케이스 인터페이스 (application이 구현)
- `port/outbound/`: 인프라 인터페이스 (adapter가 구현)
