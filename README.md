# 🛒 Self-Service Kiosk & POS System

A local, offline-first Point-of-Sale (POS) and prepaid balance management system built with **Kotlin Multiplatform (KMP)**. Designed for small communities, stores, or shared spaces running on a local Windows machine with remote network administration capabilities.

---

## 🌟 Key Features

* **Prepaid Balance System:** Users deposit funds with an admin and shop on credit. Negative balances are supported.
* **100% Offline-First:** Operates completely independently of an internet connection using local SQLite storage.
* **Admin Interface:** Admins can manage the store directly on the System.
* **Multi-Language Support (i18n):** Complete localized UI in **German (🇩🇪 DE)**, **English (🇬🇧 EN)**, and **Brazilian Portuguese (🇧🇷 BR)** with instant flag-based switching in the header.
* **Currency & Localization:** Native support for **Brazilian Real (BRL / R$)** using integer-based cents to prevent floating-point precision issues. Comma decimal representations for quantities (e.g., `1,5 kg`).
* **Immutable Audit Trail:** Append-only ledger for all transactions (purchases, deposits, cancellations).

---

## 🛠️ Technology Stack

| Layer | Technology |
|---|---|
| **Core & Business Logic** | Kotlin Multiplatform (KMP) |
| **Desktop UI (Terminal)** | Compose Multiplatform (Desktop / JVM) |
| **Database** | Room (KMP) with SQLite |
| **Identification & Input** | USB Barcode Scanner (Keyboard Wedge) / Manual Search |

If there is code not related to the JVM it should be implemented in the common section of kmp

---

## 📐 Domain Models & Lifecycle Rules

> **Standard Rules:**
> 1. All primary and foreign keys use **UUID strings**.
> 2. Monetary values are stored as **`Long` (in cents)** (e.g., `R$ 10,50` $\rightarrow$ `1050`). All accounting records remain strictly in **BRL**.
> 3. Quantities and weights are stored as **`Long`** (exact unit count for `PIECE`, **integer grams** for `WEIGHT`, e.g., `1,5 kg` $\rightarrow$ `1500`). Numbers/weights accept both `,` and `.` on input and are formatted with `,` and unit (e.g., `1,5 kg`).
> 4. **Scoped Barcode Scanning:**
     >    * **Main Screen (User Selection):** Listens strictly for user barcodes (`userBarcode` / `userBarcodeNumber`).
>    * **Shopping Screen:** Listens strictly for product barcodes (`Product.barcodes`).
> 5. **Secondary Reference Currency (Visual Helper):**
     >    * Users can optionally select an additional display currency (`USD` `$` or `EUR` `€`) in their User Settings (default: `NONE`).
>    * When enabled, wherever BRL amounts are rendered, the secondary currency conversion is displayed beside/underneath in muted text (e.g. `R$ 15,50` `(≈ $ 2,79)`).
>    * Static exchange rates are configured by the Admin in System Settings (e.g. `1 BRL = 0.18 USD`).

### 1. User
* `id`: UUID (Primary Key)
* `name`: String (Unique)
* `balance`: Long (In cents, can be negative; no credit limit enforced)
* `language`: Enum (`DE`, `EN`, `BR`)
* `secondaryCurrency`: Enum (`NONE`, `USD`, `EUR`) (Default: `NONE`)
* `pin`: String? (Optional, numeric, hashed)
* `userBarcode`: String? (Optional)
* `userBarcodeNumber`: String? (Optional, this tells the user and the admin what barcode the user is using)
* `isActive`: Boolean (Default: `true`)

> **User Rules:**
> * `userBarcode` and `userBarcodeNumber` must either **both** be present or **both** be `null`.
> * **Soft Delete:** Deactivating an account hides the user from the POS terminal while retaining all ledger history.
> * **Hard Delete:** Allowed only if the user has **zero** historical transactions. If transactions exist, soft delete is enforced to preserve the audit ledger.

### 2. Product
* `id`: UUID (Primary Key)
* `name`: String
* `barcodes`: List of Barcode entries (`code`: String, `description`: String?)
* `basePrice`: Long (Base price in cents)
* `unitType`: Enum (`PIECE`, `WEIGHT`)
* `stockQuantity`: Long (Units for `PIECE`, grams for `WEIGHT`)
* `customMarkupPercent`: Double? (Optional override for global markup)
* `isActive`: Boolean (Default: `true`)

> **Product Lifecycle:** Products can be soft-deleted (deactivated) or permanently deleted. Deleting a product removes it from the catalog but **does not** corrupt past transaction records.

### 3. Transaction (Append-Only Ledger)
Transactions are strictly immutable. Past transactions are never altered or deleted.

* `id`: UUID (Primary Key)
* `userId`: UUID (Foreign reference to User)
* `userNameSnapshot`: String (Snapshot of user name at transaction time)
* `timestamp`: Instant
* `type`: Enum (`PURCHASE`, `ADMIN_DEPOSIT`, `ADMIN_WITHDRAWAL`, `CANCELLATION`, `CORRECTION`)
* `referenceTransactionId`: UUID? (Links to original transaction in case of cancellation/reversal)
* `note`: String?
* `totalAmount`: Long (In cents; negative for purchases, positive for deposits/credits)
* `items`: List of Transaction Items *(for purchases & cancellations)*:
    * `productId`: UUID (retains UUID without foreign key cascade delete)
    * `productName`: String (Snapshot at purchase time)
    * `quantity`: Long (Unit count for `PIECE`, weight in grams for `WEIGHT`)
    * `unitPriceAtPurchase`: Long (Price per unit in cents **including** markup)

> **Decoupling Rule:** `totalItemPrice` is calculated on the fly ($\text{unitPriceAtPurchase} \times \text{quantity}$ for pieces, or $\text{unitPriceAtPurchase} \times \frac{\text{quantity}}{1000}$ for weight in grams). Product name and unit price are snapshotted so subsequent catalog changes or product deletions never alter transaction history.

---

## 🖥️ Screen & Workflow Specification

### Header Bar (Present on all screens)
* **Language Switcher:** Instant flag buttons (🇩🇪 DE / 🇬🇧 EN / 🇧🇷 BR) to switch UI localization dynamically.
* **Inactivity Auto-Logout Daemon:** Listens for user inactivity on terminal screens. If active, automatically logs out user and returns to Main Screen after the configured timeout (in minutes).
* **Main Menu** From every screen it should be possible to logout and go back to the main screen.

---

### User Flow
```
[Main Screen (User List)]
│ (Select / Scan Barcode)
▼
[Shopping Screen] ──(Confirm Checkout)──► [Transaction History Screen]
│          │                                   │                 │
│ (Account)└─────────────► [Main Screen] ◄─────┴─────────────────┘
▼
[User Settings]
```

#### 1. Main Screen (User Selection)
* Displays active users in a responsive grid/list.
* **Search / Filter:** Fast text filter for user names.
* **Instant Login:** Scans **User Barcodes** to immediately log the matching user in.
* **Admin Login Button:** Password-protected gateway to Admin Management.

#### 2. Shopping Screen (Landing Screen After Login)
* Opened immediately upon user login (and optional PIN entry).
* **Top Bar Header (User Info & Balance):** Displays the logged-in user name and **Current Account Balance** prominently at the top (with secondary currency in muted text if enabled).
* **Product Input:**
    * Scans **Product Barcodes** OR searches/selects products manually by name (for items without barcodes).
    * **Piece Goods (`PIECE`):** Adds 1 unit on scan/click; repeated scans or clicks increment quantity (+1).
    * **Weighted Goods (`WEIGHT`):** Prompts a dialog for weight input in kg on scan/click. Accepts both `,` and `.` decimal points (e.g., `1,5` and `1.5` $\rightarrow$ `1,5 kg` / `1500 g`).
* **Cart View (Right Side):**
    * Shows item name, quantity, unit price (with markup applied), and total line price.
    * *Customers only see final prices; markups are completely transparent.*
    * Adjust quantities (piece items only) or remove items.
    * **Cart Bottom Summary:** Displays the total cart amount and **Balance After Purchase** (with secondary currency in muted text if enabled).
* **Checkout:**
    * "Buy" button opens a **confirmation modal** (e.g., *"Confirm purchase for R$ 15,50?"*).
    * On confirmation: Deducts balance, updates stock, writes transaction record, and redirects directly to the **Transaction History Screen**.

#### 3. User Account & Transaction History Screen
* **Ledger View:** Chronological list of purchases (with expandable items), deposits by admin, and cancellations.
* **Actions:**
    * `Continue Shopping` $\rightarrow$ Returns to shopping screen.
    * `Logout` $\rightarrow$ Returns to main user selection screen.
    * `Settings` $\rightarrow$ Opens settings modal.
* **User Settings:**
    * Change/Set numeric PIN.
    * Change preferred language (`DE`, `EN`, `BR`).
    * Select optional **Secondary Display Currency** (`NONE`, `USD`, `EUR`).
    * View assigned Barcode ID (read-only).

---

### Admin Management

*Protected by central Admin Password.*

#### 1. Product Management
* Create, edit, soft-delete (activate/deactivate), or permanently delete products.
* Add multiple barcodes per product with custom descriptions (e.g., "Single Can", "6-Pack").
* Adjust inventory levels.
* Configure **Global Markup %** or specific **Product Markup %**.

#### 2. User Management
* Create users (Name, Language, optional PIN, optional Barcode + Barcode ID).
* Activate, deactivate, or delete user accounts (subject to lifecycle rules).
* Reset or remove user PINs.
* **Quick Deposit / Withdrawal UI:** Fast input for adding or withdrawing money with automatic transaction logging.

#### 3. Transaction History & Reversals
* Searchable and filterable system-wide transaction history.
* **Strict Reversal Workflow:** Selecting a transaction and clicking "Cancel" creates a new `CANCELLATION` entry referencing `referenceTransactionId`, restores inventory (if the product still exists), and refunds user balance.
