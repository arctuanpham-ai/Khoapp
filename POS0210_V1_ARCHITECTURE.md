# POS 0210 V1 — Architecture Specification

Brand: **0210 — BREAKFAST • COFFEE • DRINKS**

## Product principles
- Android-first, offline-first, zero mandatory recurring fee.
- Warm cream/off-white UI, black/dark-brown typography; follow the approved 5-screen mockup rather than generic POS styling.
- Single-device deployment first, but domain/data model must be multi-user and multi-device safe from day one.
- Room/SQLite is the local source of truth. Compose state is UI state only.
- History and reports are projections of closed bills/payments; never separate editable stores.
- Sent kitchen batches and paid bills are immutable; corrections are explicit adjustment events.

## Main navigation
Bottom: **BÁN HÀNG | LỊCH SỬ | BÁO CÁO**

Management drawer:
- Menu
- Bàn & khu vực
- Nhân viên
- Nhập đầu vào
- Nhà cung cấp
- Máy in
- VietQR
- Cài đặt / Sao lưu

## Sales flow
`Area/Table -> TableSession -> OrderBatch -> KitchenPrint -> More batches -> Checkout -> Payment -> Closed Bill -> History/Reports`

A table has at most one OPEN session. A session can contain many batches. Sending a batch prints only items in that batch. Batch states: DRAFT -> PRINTING -> SENT; print failure -> FAILED and explicit retry.

## Payment atomicity
Checkout must atomically persist payment method, cashier, closed timestamp, bill snapshot and CLOSED session state. A second device attempting to pay an already CLOSED session must not create a second payment.

## Multi-user / conflict strategy
Every persisted record has a globally unique ID, createdAt, updatedAt/version and actor/device identity where applicable. Clients append operations rather than overwriting the whole table/session. Already-sent batches are immutable. Cancellation creates an adjustment batch/event. Payment is protected by database transaction/compare-and-set state transition OPEN/PAYMENT_PENDING -> PAID/CLOSED.

Printing uses unique PrintJob IDs and states PENDING/CLAIMED/PRINTING/PRINTED/FAILED so two devices cannot print the same kitchen batch concurrently.

## Core entities
- Employee(id, name, active)
- Device(id, name)
- Area(id, name, sortOrder, active)
- DiningTable(id, areaId, name, sortOrder, active)
- MenuCategory(id, name, sortOrder, active)
- MenuItem(id, categoryId, name, price, imageUri, sortOrder, active)
- TableSession(id, tableId, openedAt, openedBy, status, version)
- OrderBatch(id, sessionId, sequence, ordererId, createdAt, sentAt, status)
- OrderItem(id, batchId, menuItemId, itemNameSnapshot, unitPriceSnapshot, qty, note, adjustmentOfItemId)
- PrintJob(id, batchId/billId, type, status, claimedByDeviceId, attempts, createdAt, printedAt, error)
- Bill(id, sessionId, billNo, openedAt, closedAt, subtotal, total, status)
- Payment(id, billId, method, amount, cashierId, paidAt, reference)
- Supplier(id, name, phone, note, active)
- Purchase(id, supplierId, enteredBy, purchasedAt, total, note)
- PurchaseItem(id, purchaseId, name, qty, unit, unitPrice, amount)
- AuditEvent(id, entityType, entityId, action, actorId, deviceId, occurredAt, payload)

## Menu management
Create/edit/reorder categories and items. Item fields: photo from Android gallery, name, category, price, active/hidden, sort order. Order screen reads the database directly; no hard-coded product list.

## Input purchases
This is purchase/expense input, not inventory depletion. A purchase voucher stores date/time, supplier, employee, rows (item, quantity, unit, unit price, amount), total and note. Optional invoice photo can be added later. Reports call `Revenue - Purchase input` a **thu - nhập difference**, not profit.

## History
Closed bills show table/area, opened/closed time and duration, every batch and timestamp, item snapshots, notes, orderer per batch, kitchen-send/print status, payment method, cashier and bill ID. Paid bill facts cannot silently mutate/delete.

## Reports
Periods: today / 7 days / 30 days / custom. Derived from closed bills/payments/purchases:
- revenue, bill count, average bill
- cash vs transfer
- top items
- hourly revenue/bill counts and automatic peak-hour analysis
- cashier reconciliation, especially cash responsibility
- purchase input and thu - nhập difference

## VietQR
Settings: bank, account number, account holder, content prefix, enable QR, print QR. Checkout transfer mode must render QR for the exact current bill total. Switching to cash before final confirmation persists CASH.

## Android UI requirements
- Respect system safe drawing/navigation/status bar insets on every screen.
- Main sales UI follows approved warm minimalist mockup.
- Order products use a compact image grid (target 3 columns on phone where width permits), category chips/tabs and persistent bottom action summary.
- Launcher mark must read **0210**, not 0510; use the supplied canonical brand asset rather than an improvised approximation.

## Delivery sequence
1. Refactor project structure + Room schema/repositories.
2. Areas/tables/session lifecycle.
3. Menu CRUD + image persistence + order grid.
4. Order batches, adjustments, staff attribution.
5. Print-job state machine and Bluetooth adapter boundary.
6. Checkout + VietQR + atomic payment close.
7. History from real closed transactions.
8. Reports + peak hour + cashier reconciliation.
9. Purchase input + supplier history + thu/nhập report.
10. Audit log, backup/export and multi-device LAN synchronization layer.

## Acceptance rules
No screen may display demo revenue/history as if real. Restarting the app must preserve operational data. Sending the same batch twice requires an explicit reprint action. Paying a session twice must be impossible at repository/database level. Reports must reconcile exactly to their underlying bills and payments.