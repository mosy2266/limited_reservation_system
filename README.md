# Limited Reservation System

한정 수량 상품의 주문 진입, 예약, 결제, 재고 차감, 예약 확정 이벤트 발행을 처리하는 Spring Boot 기반 예약 시스템입니다. 

2대 이상의 애플리케이션 서버가 동시에 요청을 처리하는 분산 환경을 가정하며,

Redis Cluster와 DB 조건부 업데이트를 함께 사용해 트래픽 집중 상황에서 초과 판매를 방지합니다.

## 시스템 아키텍처

```mermaid
flowchart LR
    Client[Client] --> API[Spring Boot API Server]
    API --> Redis[(Redis Cluster)]
    API --> MySQL[(MySQL)]
    API --> Kafka[(Kafka)]

    subgraph Application
        Checkout[Checkout Domain]
        Booking[Booking Domain]
        Payment[Payment Domain]
        Inventory[Inventory Domain]
        Outbox[Transactional Outbox]
    end

    API --> Checkout
    API --> Booking
    Booking --> Payment
    Booking --> Inventory
    Booking --> Outbox
    Inventory --> Redis
    Inventory --> MySQL
    Outbox --> Kafka
```

- `checkout`: 주문 진입 화면에 필요한 상품/재고 정보를 조회합니다.
- `booking`: 예약 생성, 멱등성 키 검증, 중복 예약 방지, 예약 확정 흐름을 담당합니다.
- `inventory`: Redis 재고 선점과 DB 최종 재고 차감을 담당합니다.
- `payment`: 신용카드, 페이, 포인트 결제 검증과 결제 실패 사유를 처리합니다.
- `outbox`: 예약 확정 이벤트를 DB 트랜잭션에 함께 저장하고 Kafka로 발행합니다.

## 예약 생성 시퀀스

```mermaid
sequenceDiagram
    participant C as Client
    participant B as Booking API
    participant R as Redis Cluster
    participant DB as MySQL
    participant P as Payment
    participant K as Kafka

    C->>B: POST /api/v1/bookings<br/>Idempotency-Key
    B->>R: 멱등성/처리중/rate limit 확인
    B->>R: 재고 선점
    B->>DB: 상품/기존 예약 확인
    B->>DB: 조건부 재고 차감 UPDATE
    DB-->>B: updated row count
    B->>P: 결제 검증
    B->>DB: booking/payment/outbox 저장
    DB-->>B: commit
    B-->>C: 예약 확정 응답
    B->>K: outbox publisher가 예약 확정 이벤트 발행
```

## 예약 API 플로우차트

```mermaid
flowchart TD
    Start([POST /api/v1/bookings]) --> Header{Idempotency-Key 존재?}
    Header -- No --> MissingKey[400 MISSING_IDEMPOTENCY_KEY]
    Header -- Yes --> Guard[Redis 멱등성/처리중/rate limit 확인]
    Guard --> Product{상품 존재 및 판매 가능?}
    Product -- No --> ProductError[404 또는 409 응답]
    Product -- Yes --> PaymentAmount{상품 금액과 결제 금액 일치?}
    PaymentAmount -- No --> InvalidAmount[400 INVALID_PAYMENT_AMOUNT]
    PaymentAmount -- Yes --> Existing{같은 멱등성 키 예약 존재?}
    Existing -- Yes --> SameRequest{요청 내용 동일?}
    SameRequest -- Yes --> ReturnExisting[기존 예약 결과 반환]
    SameRequest -- No --> IdempotencyConflict[409 IDEMPOTENCY_KEY_CONFLICT]
    Existing -- No --> Duplicate{사용자 상품 중복 예약?}
    Duplicate -- Yes --> Duplicated[409 DUPLICATED_BOOKING]
    Duplicate -- No --> RedisStock[Redis 재고 선점]
    RedisStock --> DbStock[DB 조건부 재고 차감 UPDATE]
    DbStock --> StockUpdated{차감 성공?}
    StockUpdated -- No --> SoldOut[410 SOLD_OUT]
    StockUpdated -- Yes --> Payment[결제 수단 및 잔액 검증]
    Payment --> PaymentOk{결제 성공?}
    PaymentOk -- No --> Restore[Redis 선점 재고 복구]
    Restore --> PaymentFail[422 결제 실패 응답]
    PaymentOk -- Yes --> Save[Booking/Payment/Outbox 저장]
    Save --> Success[예약 확정 응답]
```

재고 차감은 아래 조건부 업데이트로 처리합니다. 영향 받은 row 수가 `0`이면 품절로 판단합니다.

```sql
UPDATE inventories
   SET sold_quantity = sold_quantity + 1,
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE product_id = ?
   AND sold_quantity < total_quantity;
```

## 실행 방법

### 요구 사항

- Java 21
- MySQL 8.x
- Redis Cluster: master 3개, replica 3개 권장
- Kafka

### 애플리케이션 실행

```bash
./gradlew bootRun
```

Windows PowerShell:

```powershell
.\gradlew.bat bootRun
```

### 테스트 실행

```bash
./gradlew test
```

Windows PowerShell:

```powershell
.\gradlew.bat test
```

테스트는 H2와 mock을 활용해 Redis/Kafka 외부 인프라 없이 실행되도록 구성되어 있습니다.

## 주요 API

### 주문 진입 조회

```http
GET /api/v1/products/{productId}/checkout?userId=1
```

### 예약 생성

```http
POST /api/v1/bookings
Idempotency-Key: booking-request-001
Content-Type: application/json
```

```json
{
  "userId": 1,
  "productId": 1,
  "payment": {
    "primaryMethod": "CARD",
    "paymentAmount": 70000,
    "pointAmount": 30000
  }
}
```

지원 결제 조합:

- `CARD`
- `PAY`
- `POINT`
- `CARD + POINT`
- `PAY + POINT`

`CARD + PAY` 조합은 지원하지 않습니다.

## ERD

```mermaid
erDiagram
    BOOKINGS ||--o{ PAYMENTS : has
    BOOKINGS ||--o{ OUTBOX_EVENTS : publishes

    BOOKINGS {
        bigint id PK
        varchar booking_no UK
        bigint product_id
        bigint user_id
        varchar status
        bigint total_amount
        bigint payment_amount
        bigint point_amount
        varchar payment_method
        varchar idempotency_key UK
        datetime created_at
        datetime updated_at
    }

    PAYMENTS {
        bigint id PK
        bigint booking_id
        varchar payment_method
        bigint requested_amount
        bigint approved_amount
        varchar status
        varchar failure_reason
        datetime created_at
        datetime updated_at
    }

    OUTBOX_EVENTS {
        bigint id PK
        varchar aggregate_type
        bigint aggregate_id
        varchar event_type
        text payload
        varchar status
        int retry_count
        datetime next_retry_at
        datetime published_at
        datetime created_at
        datetime updated_at
    }
```

## 주문/결제 중심 DDL

```sql
CREATE TABLE bookings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    booking_no VARCHAR(50) NOT NULL,
    product_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    total_amount BIGINT NOT NULL,
    payment_amount BIGINT NOT NULL,
    point_amount BIGINT NOT NULL,
    payment_method VARCHAR(30) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_booking_no (booking_no),
    UNIQUE KEY uq_booking_user_product (user_id, product_id),
    UNIQUE KEY uq_booking_idempotency (idempotency_key)
);

CREATE TABLE payments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    booking_id BIGINT NOT NULL,
    payment_method VARCHAR(30) NOT NULL,
    requested_amount BIGINT NOT NULL,
    approved_amount BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    failure_reason VARCHAR(255),
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_payments_booking_id (booking_id)
);

CREATE TABLE outbox_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(30) NOT NULL,
    retry_count INT NOT NULL,
    next_retry_at DATETIME,
    published_at DATETIME,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_outbox_status_retry (status, next_retry_at)
);
```
