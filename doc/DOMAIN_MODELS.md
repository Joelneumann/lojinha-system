# 📐 Domain Models & Lifecycle Rules

This document specifies the core domain models, accounting constraints, entity lifecycles, and transaction mechanics of the **Lojinha** system.

All domain models reside in the shared Kotlin Multiplatform (KMP) module under `shared/src/commonMain/kotlin/de/joelneumann/lojinha/domain/model/`.

---

## 🏛️ Standard Architecture Rules

1. **UUID Primary & Foreign Keys:** All entities use UUID strings (v4) for primary and foreign key references.
2. **Integer Monetary Values (Cents in BRL):**
   * Monetary amounts are stored strictly as **`Long` representing integer cents** (e.g., `R$ 10,50` → `1050L`).
   * No floating-point math is used for financial calculations to eliminate IEEE-754 precision errors.
   * All internal accounting records remain strictly in **Brazilian Real (BRL)**.
3. **Discrete Quantities & Weights:**
   * Quantities are stored as **`Long`**: exact unit count for piece items (`PIECE`), and **integer grams** for weighted items (`WEIGHT`, e.g., `1,5 kg` → `1500L`).
   * Inputs accept both comma `,` and period `.` decimal separators (e.g., `1,5` and `1.5` both parse to `1500 g`).
   * Outputs are formatted with `,` and unit suffixes (e.g., `1,5 kg`).
4. **Scoped Barcode Scanning:**
   * **Main Screen (User Selection):** Listens strictly for user barcodes (`userBarcode` / `userBarcodeNumber`). Requires an `Enter` / carriage-return suffix to prevent premature substring matching.
   * **Shopping Screen:** Listens strictly for product barcodes (`Product.barcodes`).
5. **Secondary Reference Currency (Visual Helper):**
   * Users can select an optional secondary display currency (`USD` `US$` or `EUR` `€`) in their User Settings (default: `NONE`).
   * Wherever BRL amounts are rendered, the secondary currency conversion is computed using the admin-configured static exchange rate and displayed in muted text (e.g., `R$ 15,50` `(≈ US$ 2,79)`).
6. **Accent & Case Insensitive Collation:**
   * User search, product filtering, and sorting ignore accents and case (e.g., searching `joao` matches `João`).

---

## 👤 1. User

Defined in [`User.kt`](../shared/src/commonMain/kotlin/de/joelneumann/lojinha/domain/model/User.kt).

### Fields
| Field | Type | Description |
|---|---|---|
| `id` | `String` (UUID) | Unique primary key. |
| `name` | `String` | Unique user name displayed in kiosk and admin views. |
| `balance` | `Long` | Current account balance in cents. Can be negative (no hard credit limit enforced). |
| `language` | `Language` | Preferred UI language (`BR`, `EN`, `DE`). Default: `DE`. |
| `secondaryCurrency` | `SecondaryCurrency` | Visual reference currency (`NONE`, `USD`, `EUR`). Default: `NONE`. |
| `pin` | `String?` | Optional numeric PIN, hashed using SHA-256 with salt. |
| `userBarcode` | `String?` | Optional scanned barcode string (e.g., Code 128 / EAN). |
| `userBarcodeNumber` | `String?` | Optional visual barcode identifier displayed on badges/cards. |
| `isActive` | `Boolean` | Whether the user is active on the POS terminal. Default: `true`. |
| `isDeleted` | `Boolean` | Soft-delete flag. Default: `false`. |
| `avatar` | `UserAvatarConfig` | Visual avatar configuration (`type`: `INITIALS` or `EMOJI`, `emoji`: String, `colorHex`: String). |

### Lifecycle Rules
* **Barcode Pair Constraint:** `userBarcode` and `userBarcodeNumber` must either **both be present** or **both be null**.
* **Deactivation (`isActive = false`):**
  * Hides the user from the POS Main Screen terminal.
  * Preserves full transaction history and ledger integrity.
  * Deactivated users can be reactivated at any time in the Admin panel.
* **Soft Delete (`isDeleted = true`):**
  * Moves the user to the "Deleted Accounts" section in the Admin panel.
  * Excludes user from active and deactivated counts and bulk billing lists.
  * Soft-deleted users can be restored at any time in the Admin panel.
* **Hard Delete (Permanent Removal):**
  * Allowed **only** if the user has **zero** historical transactions.
  * If transactions exist, hard delete is blocked and soft delete is enforced to preserve the immutable audit ledger.

---

## 📦 2. Product

Defined in [`Product.kt`](../shared/src/commonMain/kotlin/de/joelneumann/lojinha/domain/model/Product.kt).

### Fields
| Field | Type | Description |
|---|---|---|
| `id` | `String` (UUID) | Unique primary key. |
| `name` | `String` | Product title displayed in catalog and cart. |
| `barcodes` | `List<Barcode>` | List of associated barcodes (`code`: String, `description`: String?, e.g., "Single Can", "6-Pack"). |
| `basePrice` | `Long` | Base price in cents (without markup). |
| `unitType` | `UnitType` | `PIECE` (sold per unit) or `WEIGHT` (sold by weight, priced per kilogram). |
| `stockQuantity` | `Long` | Current inventory count (exact units for `PIECE`, grams for `WEIGHT`). |
| `customMarkupPercent` | `Double?` | Optional product-specific markup percentage overriding global markup. |
| `isActive` | `Boolean` | Catalog visibility status. Default: `true`. |

### Pricing & Lifecycle Rules
* **Effective Unit Price Calculation:**
  $$\text{effectivePrice} = \text{round}\left(\text{basePrice} \times \left(1 + \frac{\text{markupPercent}}{100}\right)\right)$$
  Where $\text{markupPercent}$ is `customMarkupPercent` if set, otherwise `globalMarkupPercent`.
* **Catalog Deactivation:** Deactivating a product (`isActive = false`) hides it from the shopping catalog while retaining historical consistency.
* **Catalog Deletion:** Permanently deleting a product removes it from the catalog, but **never alters or corrupts past transactions**, because past transactions snapshot product names, unit types, and prices.

---

## 📜 3. Transaction (Append-Only Ledger)

Defined in [`Transaction.kt`](../shared/src/commonMain/kotlin/de/joelneumann/lojinha/domain/model/Transaction.kt).

Transactions represent an **immutable, append-only ledger**. Historical records are never modified or deleted. Reversals and corrections create new linked transaction entries.

### Fields
| Field | Type | Description |
|---|---|---|
| `id` | `String` (UUID) | Unique primary key. |
| `userId` | `String` (UUID) | Foreign reference to the associated user. |
| `userNameSnapshot` | `String` | Snapshot of the user's name at the time of the transaction. |
| `timestamp` | `Long` | Epoch milliseconds when the transaction occurred. |
| `type` | `TransactionType` | `PURCHASE`, `ADMIN_DEPOSIT`, `ADMIN_WITHDRAWAL`, `CANCELLATION`, `CORRECTION`. |
| `referenceTransactionId` | `String?` | References the original transaction ID for cancellations and corrections. |
| `note` | `String?` | Human-readable note or structured system note identifier. |
| `totalAmount` | `Long` | Amount in cents (negative for purchases/debits, positive for deposits/refunds). |
| `items` | `List<TransactionItem>` | Itemized line items for purchases, cancellations, and corrections. |
| `userBalanceBefore` | `Long?` | User account balance immediately before this transaction. |
| `userBalanceAfter` | `Long?` | User account balance immediately after this transaction. |

### Transaction Item (`TransactionItem`)
| Field | Type | Description |
|---|---|---|
| `productId` | `String` (UUID) | UUID of the purchased product (retained without foreign key cascade delete). |
| `productName` | `String` | Snapshot of the product name at purchase time. |
| `unitType` | `UnitType` | Snapshot of `PIECE` or `WEIGHT`. |
| `quantity` | `Long` | Units count for `PIECE`, weight in grams for `WEIGHT`. |
| `unitPriceAtPurchase` | `Long` | Price per unit in cents **including** markup snapshot. |
| `previousQuantity` | `Long?` | Quantity prior to correction (used for auditing purchase revisions). |

### Line Total Calculation
$$\text{lineTotal} = \begin{cases} \text{unitPriceAtPurchase} \times \text{quantity} & \text{if } \text{unitType} = \text{PIECE} \\ \text{round}\left(\frac{\text{unitPriceAtPurchase} \times \text{quantity}}{1000}\right) & \text{if } \text{unitType} = \text{WEIGHT} \end{cases}$$

### Reversal & Correction Mechanics
1. **Full Purchase Cancellation (`CANCELLATION`):**
   * Creates a new `CANCELLATION` transaction referencing `referenceTransactionId`.
   * Restores inventory for all items in the original purchase (if the products still exist in the catalog).
   * Fully credits the original purchase amount back to the user's balance.
2. **Purchase Correction (`CORRECTION`):**
   * Used when a customer or admin needs to correct item quantities post-purchase (e.g., 3 cans were scanned instead of 2).
   * Computes differential quantities: $\Delta \text{qty} = \text{newQuantity} - \text{originalQuantity}$.
   * Creates a new `CORRECTION` transaction with the line items showing previous and new quantities.
   * Adjusts inventory by $-\Delta \text{qty}$ and adjusts user balance by $-\Delta \text{amount}$.
3. **Non-Purchase Storno (`ADMIN_DEPOSIT` / `ADMIN_WITHDRAWAL`):**
   * Reverses accidental manual balance adjustments.
   * Creates an opposing transaction linked via `referenceTransactionId` and system note.

---

## 📋 4. Billing List (Bulk Billing)

Defined in [`BillingList.kt`](../shared/src/commonMain/kotlin/de/joelneumann/lojinha/domain/model/BillingList.kt).

Manages community recurring charges (e.g., monthly gym/locker dues, shared expenses).

### Fields
| Field | Type | Description |
|---|---|---|
| `id` | `String` (UUID) | Unique primary key. |
| `name` | `String` | List title (e.g., "Monthly Locker Fee"). |
| `type` | `BillingListType` | `FIXED` (uniform base price per unit) or `VARIABLE` (individual user amounts). |
| `basePrice` | `Long?` | Unit price in cents for `FIXED` lists. |
| `comment` | `String?` | Optional admin description or notes. |
| `isDeleted` | `Boolean` | Soft-delete flag. |
| `users` | `List<BillingListUser>` | Assigned users and their multiplier quantities. |
| `lastExecutionTime` | `Long?` | Epoch milliseconds when this list was last batch-executed. |

### Batch Execution Rules
* When executing a billing list, the system generates an atomic batch of individual debit transactions for all assigned active users.
* Deducts each member's balance and records `lastExecutionTime`.
* If a member was soft-deleted or deactivated prior to execution, they are excluded from the batch run.

---

## ⚙️ 5. System Settings

Defined in [`SystemSettings.kt`](../shared/src/commonMain/kotlin/de/joelneumann/lojinha/domain/model/SystemSettings.kt).

Single-row configuration entity persisted in Room SQLite.

### Fields
| Field | Type | Default | Description |
|---|---|---|---|
| `adminPasswordHash` | `String` | SHA-256 of `"admin"` | Master admin password hash. |
| `globalMarkupPercent` | `Double` | `0.0` | Default markup percentage applied to catalog items without custom markup. |
| `usdExchangeRate` | `Double` | `0.18` | Static conversion rate: 1 BRL = X USD. |
| `eurExchangeRate` | `Double` | `0.16` | Static conversion rate: 1 BRL = X EUR. |
| `inactivityTimeoutMinutes` | `Int` | `3` | Kiosk idle timeout before auto-logout (min: 2 min). |
| `supportEmail` | `String?` | `null` | Administrator/developer email for crash reports and diagnostics. |
| `oneDriveClientId` | `String` | `"202e1c94-..."` | Microsoft Entra Application (Client) ID. |
| `oneDriveRefreshToken` | `String?` | `null` | Persisted OAuth2 refresh token for cloud uploads. |
| `oneDriveAccountEmail` | `String?` | `null` | Display email of connected Microsoft account. |
| `oneDriveAccountName` | `String?` | `null` | Display name of connected Microsoft account. |
| `oneDriveDefaultFolder` | `String` | `"/LojinhaBackups"` | Target folder in user's OneDrive. |

---

## 🔄 6. Backup Routine

Defined in [`BackupRoutine.kt`](../shared/src/commonMain/kotlin/de/joelneumann/lojinha/domain/model/BackupRoutine.kt).

Configures automated and manual backup jobs.

### Fields
| Field | Type | Description |
|---|---|---|
| `id` | `String` (UUID) | Unique primary key. |
| `name` | `String` | Routine title (e.g., "Daily Nightly Backup"). |
| `isEnabled` | `Boolean` | Whether the routine is active in the scheduler. |
| `type` | `BackupType` | `LOCAL` (local disk directory) or `ONEDRIVE` (cloud). |
| `fileType` | `BackupFileType` | `DB` (SQLite file), `CSV` (table exports), or `BOTH`. |
| `writeMode` | `BackupWriteMode` | `CREATE_NEW_FILE` (timestamped versions) or `OVERWRITE_LATEST`. |
| `scheduleConfig` | `BackupScheduleConfig` | Schedule trigger strategy (see below). |
| `backupLocationPath` | `String` | Local directory path or OneDrive folder path. |
| `lastBackupTimestamp` | `Long?` | Epoch milliseconds when routine was last executed. |

### Schedule Trigger Strategies (`BackupScheduleConfig`)
1. **`Timed(timeOfDay = "HH:mm")`:** Fires once daily at the specified 24-hour time.
2. **`Interval(intervalHours, intervalMinutes)`:** Fires periodically at the specified elapsed interval.
3. **`OnDataChange(debounceMs = 1000)`:** Listens to database mutation events and fires automatically after a short debounce window.
