# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is an e-commerce platform (이커머스) built with Spring Boot 3.5.7 and Java 17. The project is in its initial setup phase and is designed to implement core e-commerce functionality including product management, order/payment systems, coupon management, and external data integration.

## Build & Development Commands

### Build
```bash
./gradlew build
```

### Run Application
```bash
./gradlew bootRun
```

### Run Tests
```bash
./gradlew test
```

### Run Single Test
```bash
./gradlew test --tests <FullyQualifiedTestClassName>
# Example: ./gradlew test --tests sample.hhplus_w2.HhplusW2ApplicationTests
```

### Clean Build
```bash
./gradlew clean build
```

## Core Requirements & Architecture

### Key Business Domains

The system is organized around five core domains as outlined in `docs/requirements.md`:

1. **Product Management** - Products with SKU-level inventory tracking
2. **Order/Payment System** - Cart, order creation, payment processing with wallet balance
3. **Coupon System** - First-come-first-served coupon issuance with concurrent control
4. **Data Integration** - External data platform integration via Outbox pattern
5. **User & Analytics** - User authentication, wallet management, and sales statistics

### Critical Concurrency & Data Consistency Patterns

**SKU (Stock Keeping Unit)**
- Products are managed at the SKU level (e.g., "T-Shirt-Black-M", "T-Shirt-White-L")
- Each SKU has independent price, stock quantity, and version tracking
- Relationship: Product 1:N SKU

**Optimistic Locking**
- Use version-based optimistic locking for stock management
- Pattern: `UPDATE sku SET stock_qty = stock_qty - 1, version = version + 1 WHERE id = ? AND version = ?`
- Preferred over pessimistic locking for performance

**Atomic Operations**
- Stock deduction must be atomic at payment approval: `WHERE stock >= qty`
- Order-Payment-Stock operations must succeed together or rollback entirely
- Use `@Transactional` for business operations spanning multiple entities

**Idempotency**
- Payment and coupon issuance APIs must use Idempotency-Key headers to prevent duplicate processing
- Pattern: `Idempotency-Key: <unique-request-id>`

**Outbox Pattern**
- Order completion events (`ORDER_PAID`) must be recorded in an Outbox table
- Separate worker process handles external transmission
- Supports retry and Dead Letter Queue (DLQ) for failed transmissions
- Order success is decoupled from external transmission success

### Database Schema Considerations

When implementing entities, follow these patterns:

- **SKU table**: product_id (FK), attributes (JSON), price, stock_qty, version
- **Coupon**: Track states - DRAFT → PUBLISHED → PAUSED → EXPIRED
- **Order**: TTL for incomplete orders, status transitions
- **Outbox**: event_type, payload, status (PENDING/SENT)
- **DLQ**: event_id, reason, retry_count

### Key Feature Requirements

**Stock Management**
- Real-time stock checking at cart/checkout
- Stock adjustment with audit trail (before/after quantity, user, reason)
- Popular products: Top 5 by sales/revenue for last 3 days (5-minute cache/summary table)

**Coupon System**
- First-come-first-served issuance within total quantity limit
- Prevent duplicate issuance: unique constraint on (userId, couponId)
- Atomic counter for concurrent requests
- Validation: status, validity period, minimum order amount, applicable products

**Payment Flow**
- Wallet-based internal balance system
- Validation sequence: stock → price → coupon validity
- On failure: balance restoration and order cancellation
- Payment status tracking and order history

**Data Security**
- Mask personal information (address, phone) for external transmission
- Event schema versioning
- Idempotent event_id for external consumers

## Technology Stack

- **Framework**: Spring Boot 3.5.7
- **Language**: Java 17
- **Build Tool**: Gradle
- **ORM**: Spring Data JPA
- **Web**: Spring Web (REST API)
- **Utilities**: Lombok for boilerplate reduction

## Project Structure

```
src/
├── main/
│   ├── java/sample/hhplus_w2/    # Main application code
│   └── resources/
│       ├── application.properties # Configuration
│       ├── static/                # Static resources
│       └── templates/             # Templates
└── test/
    └── java/sample/hhplus_w2/     # Test code
```

## Implementation Guidelines

**Domain-Driven Design**
- Organize code by business domain (product, order, coupon, payment, user)
- Separate domain entities, repositories, services, and controllers
- Use service layer for business logic, keep controllers thin

**Concurrency Control**
- Default to optimistic locking for inventory operations
- Use database-level constraints for uniqueness (coupon issuance)
- Implement proper transaction boundaries with `@Transactional`

**Event-Driven Integration**
- Use Outbox pattern for reliable event publishing
- Decouple business operations from external integrations
- Implement retry logic and DLQ for failed events

**Testing Strategy**
- Unit tests for business logic
- Integration tests for repository and transaction behavior
- Test concurrent scenarios for stock/coupon operations