# 🥬 Veggie Orders & Customer Network Portal

Lojinha includes an integrated **Veggie Orders** subsystem for managing recurring community produce orders—from catalog setup and customer self-ordering via the **Customer Network Portal** to market settlement, ledger billing, and printable PDFs.

---

## 🌟 Overview & Workflow

```
[Admin: Catalog & Units] ──► [Round OPEN] ──► [Customers Order via Portal]
                                 │
                                 ▼ (Close Round)
                           [Round CLOSED] ──► [Buyer PDF & Market Purchase]
                                 │
                                 ▼ (Enter Actuals, Spent & Fees)
                          [Round SETTLED] ──► [Worker PDF + Atomic Ledger Debits]
```

1. **Catalog Setup (`F6: Veggie Orders`):** Admins configure unit types (`VeggieUnitType`) and produce items (`VeggieProduct`), with optional product images, estimated prices, and delivery units.
2. **Round Opening (`OPEN`):** A round is opened manually or automatically by `VeggieRoundAutoScheduler`. Active customers place, edit, or cancel orders in the **Customer Network Portal** (or via Admin).
3. **Round Closing (`CLOSED`):** Orders are locked. The admin exports the **Buyer Shopping List PDF** for the market run.
4. **Settlement (`SETTLED`):** After shopping, the admin enters actual delivered quantities, market unit prices or total spent per product, buyer operation fee, transport fee, and optional transporter user. Settling atomically debits each participating customer's Lojinha balance and credits the transporter (if assigned).

---

## 📐 Domain & Scaling Rules

Defined in [`VeggieModels.kt`](../shared/src/commonMain/kotlin/de/joelneumann/lojinha/domain/model/VeggieModels.kt).

* **Fixed-Point Quantity Scaling (`VEGGIE_QTY_SCALE = 1000L`):**
  * All quantities (`orderedQuantityScaled`, `actualQuantityScaled`) are stored as `Long` scaled by `1000L` (e.g., `1.5 kg` → `1500L`, `2 pcs` → `2000L`) to avoid floating-point precision issues.
  * `VeggieQuantityScaling` handles parsing (accepting both `,` and `.` decimal separators) and locale-friendly formatting.
* **Dual Unit Support (`unitTypeId` vs. `deliveryUnitTypeId`):**
  * Each `VeggieProduct` has a primary ordering `unitTypeId` (e.g., `unit-piece` or `unit-kg`) and an optional `deliveryUnitTypeId` (e.g., ordered by bunch, settled/delivered by kg).
  * `VeggieOrderEntry` snapshots both ordering and delivery unit metadata at order time.
* **Buyer PDF Exclusion (`excludeFromBuyerPdf`):**
  * Products with `excludeFromBuyerPdf = true` remain available for ordering and settlement but are omitted from the exported **Buyer Shopping List PDF** (useful for internal/non-market items).
* **Product Images:**
  * Uploaded via Desktop or Web Admin (`POST /api/admin/veggie/products/{id}/image`) and stored under `~/.lojinha/veggie_images/`.
  * Served to clients via `GET /api/veggie/images/{id}`.

---

## 🔄 Round Lifecycle & Auto-Open Scheduler

### Round States (`VeggieRoundStatus`)
| Status | Description |
|---|---|
| `OPEN` | Customers can place, update, or cancel orders in the Customer Network Portal. |
| `CLOSED` | Ordering is closed for customers. Admin can adjust entries, export Buyer PDF, save draft settlements, or reopen to `OPEN`. |
| `SETTLED` | Finalized. Customer balances and transporter credit are committed to the `transactions` ledger. Can be re-settled to issue differential corrections. |

### Automatic Weekly Opening
Managed by [`VeggieRoundAutoScheduler`](../shared/src/jvmMain/kotlin/de/joelneumann/lojinha/data/service/VeggieRoundAutoScheduler.kt) and configured in `SystemSettings`:
* `veggieAutoOpenEnabled`: Enables/disables the weekly scheduler.
* `veggieAutoOpenDayOfWeek` & `veggieAutoOpenTime`: Target day of week and `"HH:mm"` local time slot.
* `defaultVeggieBuyerFeeCents` & `defaultVeggieTransportFeeCents`: Default fees pre-filled on newly auto-opened rounds.
* **Safety Guards:** Never auto-opens if an `OPEN` or `CLOSED` round already exists, or if a round was already opened at or after the current week's scheduled slot.

---

## 🧮 Settlement Math & Ledger Integration

Implemented in [`VeggieSettlementCalculator.kt`](../shared/src/commonMain/kotlin/de/joelneumann/lojinha/domain/service/VeggieSettlementCalculator.kt) and [`RoomVeggieOrderRepositoryImpl.kt`](../shared/src/jvmMain/kotlin/de/joelneumann/lojinha/data/repository/RoomVeggieOrderRepositoryImpl.kt).

1. **Product Cost Distribution (`distributeProductTotalSpent`):**
   * For each product, the admin (or buyer via draft prices) provides either `totalSpentCents` or `marketUnitPriceCents`. When only `marketUnitPriceCents` is supplied and `totalDeliveredQtyScaled > 0`:
     * For **dual-unit** products (e.g., ordered in pieces, settled/delivered in kg), `totalSpent = round((unitPrice * totalDeliveredQtyScaled) / 1000)`.
     * For **single-unit** products, `totalSpent = round((unitPrice * totalOrderedQtyScaled) / 1000)` (so adjustments to delivered quantities do not shift the market total unless `totalSpentCents` is explicitly entered).
     * If `totalDeliveredQtyScaled == 0`, `totalSpent` is `0` so zero-delivery items never generate phantom charges.
   * `totalSpentCents` is distributed proportionally across customers with `actualQuantityScaled > 0` using the **Largest Remainder Method**, guaranteeing that customer shares sum to `totalSpentCents` to the exact cent.
   * `effectiveUnitPriceCents = round((totalSpentCents * 1000) / totalDeliveredQtyScaled)`.
2. **Fee Distribution (`distributeCombinedFeesEqually`):**
   * `combinedFeeCents = buyerOperationFeeCents + transportFeeCents`.
   * Split equally across all participating users, with any 1-cent remainder distributed deterministically by sorted `userId`.
3. **Atomic Ledger Transactions:**
   * **Initial Settlement:** Creates `VEGGIE` (debit) transactions for each participating customer with note `SYSNOTE|VEGGIE_ORDER|<week>|<year>|<roundId>`.
   * **Transporter Credit:** If `transporterUserId` is set and `transportFeeCents > 0`, creates a `VEGGIE` credit with note `SYSNOTE|VEGGIE_TRANSPORT|<week>|<year>|<roundId>`.
   * **Re-Settlement Corrections:** Re-settling an already `SETTLED` round computes the delta against previously settled amounts and posts differential `VEGGIE` adjustments (`SYSNOTE|VEGGIE_CORRECTION|<week>|<year>|<roundId>`).
   * **Catalog Price Sync:** Settlement updates each settled product's `estimatedPriceCents` for future rounds.

---

## 📄 PDF Exports

Generated on the JVM via [`VeggieOrderPdfGenerator.kt`](../shared/src/jvmMain/kotlin/de/joelneumann/lojinha/data/service/VeggieOrderPdfGenerator.kt) and exposed on both Desktop and Web Admin:

1. **Buyer Shopping List PDF (`GET /api/admin/veggie/rounds/{id}/buyer-pdf`):**
   * Aggregated market shopping list grouped by product and total ordered quantity, excluding products where `excludeFromBuyerPdf = true`.
2. **Worker Sorting Matrix PDF (`GET /api/admin/veggie/rounds/{id}/worker-pdf`):**
   * Per-product, per-customer distribution matrix used when packing shares. Uses `User.veggieDisplayName` (`nickname` if set, otherwise `name`) for compact column/row headers.
3. **Customer Order Form PDF (`GET /api/admin/veggie/order-form-pdf`):**
   * Blank printable order sheet listing active veggie products for offline/paper ordering.

---

## 📱 Customer Network Portal (PWA)

The embedded Ktor server serves a mobile-first **Customer Network Portal** alongside the Web Admin console:

* **Authentication:** Native `/login.html` flow issuing an `HttpOnly` session cookie (`lojinha_customer_session`) backed by SHA-256 server-side token hashing, persisted hashed sessions, and tiered brute-force rate limiting. Logout is handled via `POST /api/customer/logout`.
* **Veggie Ordering & Favorites:**
  * Load active round state, catalog, and current user entries via `GET /api/customer/veggie/state`, toggle personal favorites (`/api/customer/veggie/favorites`), and submit or update quantities (`/api/customer/veggie/order`).
  * Inspect past veggie order history (`/api/customer/veggie/history`).
* **Account & Settings:**
  * View current prepaid balance (with low/negative balance warning banners), paginated transaction history (`/api/customer/transactions`), and download A4 PDF account statements (`/api/customer/transactions-pdf`).
  * Submit product suggestions/notes to admins (`/api/customer/notes`).
  * Update personal settings (`/api/customer/me/settings`), including `nickname` (max 10 chars, unique), preferred language, secondary currency, PIN, and avatar.
* **Real-Time Sync & Mobile UX:**
  * Subscribes to `/api/customer/events` (SSE) for live round and balance updates.
  * Includes mobile viewport/keyboard helpers to prevent unwanted horizontal scrolling on iOS/Android web browsers.

---

## 🧺 Buyer Portal (Market Run Interface)

For the dedicated market buyer role (configured with `buyerLoginName` — defaulting to `"Buyer"` — and `buyerPasswordHash` in `SystemSettings`), Lojinha provides a scoped **Buyer Portal** accessible from `/login.html`:

* **Authentication:** Issues an `HttpOnly` session cookie (`lojinha_buyer_session`) via `POST /api/login` using the configured `buyerLoginName` (case/accent-insensitive; reserved alongside `"Admin"` against all user names and nicknames) and logs out via `POST /api/buyer/logout`. Changing either `buyerLoginName` or `buyerPasswordHash` immediately revokes active Buyer sessions. Buyer sessions cannot access Admin or Customer data (transport fees, transporter user, and customer identities are stripped from buyer payloads).
* **Closed-Round Shopping View (`GET /api/buyer/veggie/state`):**
  * Shows aggregated item quantities when the current round is `CLOSED` (excluding items with `excludeFromBuyerPdf = true`).
  * Displays an outdated-PDF banner whenever orders change after the buyer's last PDF download (`ordersUpdatedAt > buyerPortalPdfExportedAt`).
* **Buyer PDF Export (`GET /api/buyer/veggie/round/buyer-pdf`):**
  * Downloads the localized Buyer Shopping List PDF and records `buyerPortalPdfExportedAt`.
* **Draft Market Prices & Buyer Fee (`POST /api/buyer/veggie/round/draft-settlement`):**
  * Allows the buyer to enter and save `marketUnitPriceCents`, `totalSpentCents`, and `buyerOperationFeeCents` while at the market; saved values sync live via `/api/buyer/events` and `/api/admin/events` (SSE) into the Admin Settlement Matrix.
