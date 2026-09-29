# 🛒 Lojinha - Self-Service Kiosk & POS System

A local, offline-first Point-of-Sale (POS) and prepaid balance management system built with **Kotlin Multiplatform (KMP)** and **Compose Multiplatform**. Designed for small communities, stores, sports clubs, or shared spaces running on a local desktop machine (Windows, macOS, Linux) with an integrated **Ktor web server** providing remote web administration via **Kotlin/Wasm** across the local network.

---

## 🌟 Key Features

* **Prepaid Balance System:** Users deposit funds with an administrator and shop on credit. Negative balances are supported with clear debit tracking.
* **100% Offline-First Architecture:** Operates completely independently of an internet connection using local SQLite storage with WAL (Write-Ahead Logging) checkpointing via AndroidX Room.
* **Dual Interface (Desktop Kiosk & Remote Web Admin):**
  * **Desktop POS Terminal:** Fullscreen Compose Multiplatform kiosk optimized for keyboard-wedge USB barcode scanners and rapid touchscreen or mouse navigation.
  * **Remote Web Admin (Kotlin/Wasm):** Embedded Ktor Netty server running on port `8080` (or `8081`) serving a production WebAssembly admin application over LAN, complete with token session authentication, brute-force rate limiting, and real-time Server-Sent Events (SSE).
* **Bulk Billing Lists:** Create recurring charge groups (fixed fees or variable usage) with per-user multiplier quantities and execute batch charges in a single atomic transaction.
* **Automated & Cloud Backups (OneDrive Integration):**
  * Multi-routine backup engine supporting local directories and 1-click **Microsoft OneDrive cloud backup via PKCE OAuth2** (see [Setup Guide](doc/ONEDRIVE_SETUP.md)).
  * Flexible schedules: **Timed** (daily at specific hours), **Interval** (every $X$ hours/minutes), or real-time **On Data Change**.
  * Formats: Full SQLite database snapshot (`.db`), CSV exports, or both; write modes: `OVERWRITE_LATEST` or `CREATE_NEW_FILE`.
* **Safe Database Restore, Reset & CSV Import:**
  * Table-level SQLite database restore from `.db` backup files to avoid open file locks.
  * Factory reset / data wipe with double-confirmation safeguards.
  * CSV import for accounts and catalog with barcode de-duplication and conflict handling (see [CSV Format Specification](doc/CSV_IMPORT_EXPORT.md)).
* **Immutable Audit Trail & Granular Reversals:**
  * Strict append-only ledger for all transactions (purchases, deposits, withdrawals, cancellations, corrections).
  * **Purchase Corrections:** Modify item quantities in existing purchases while recording differential stock and balance adjustments.
  * **Storno:** Reverse admin deposits or withdrawals with linked audit records.
  * Historical balance snapshots (`userBalanceBefore` and `userBalanceAfter`) on every transaction.
* **Multi-Language Support (i18n):**
  * Complete localized UI in **Brazilian Portuguese (🇧🇷 BR)**, **English (🇺🇸 EN)**, and **German (🇩🇪 DE)** with instant flag-based switching in the header.
* **Currency & Unit Localization:**
  * Primary accounting strictly in **Brazilian Real (BRL / R$)** using integer cents (`Long`) to prevent floating-point rounding errors.
  * Optional **Secondary Reference Currency** (`USD` or `EUR`) calculated from configurable static exchange rates and displayed in muted text (e.g. `R$ 15,50` `(≈ US$ 2,79)`).
  * Fractional weight support with comma decimal representations (e.g., `1,5 kg` → `1500 g`).
* **Diagnostics & Reliability:**
  * Persistent rolling file logger and uncaught JVM exception crash interceptor (see [Storage Locations](doc/STORAGE_LOCATIONS.md)).
  * One-click **Diagnostic Support Bundle** export (`.zip`) containing logs, database stats, and system environment info.

---

## 🛠️ Technology Stack

| Layer | Technology | Description |
|---|---|---|
| **Core & Business Logic** | Kotlin Multiplatform (KMP 2.1.20) | Shared domain models, validation, ViewModels, and state management |
| **Desktop UI (Terminal)** | Compose Multiplatform (Desktop / JVM) | Hardware-accelerated desktop kiosk interface |
| **Remote Web Admin UI** | Compose Multiplatform for Web (**Kotlin/Wasm**) | Browser-based administrative single-page application |
| **Embedded Web Server** | Ktor Server (Netty engine) | Content negotiation, CORS, token authentication, static asset serving, and SSE |
| **HTTP & Cloud Client** | Ktor Client (CIO engine) | Microsoft Graph / OneDrive PKCE OAuth2 cloud backup integration |
| **Database & Storage** | AndroidX Room (KMP) with SQLite Bundled Driver | Local relational persistence with Room migrations |
| **Serialization & Time** | `kotlinx.serialization` & `kotlinx.datetime` | JSON serialization and timezone-aware schedule calculations |
| **Hardware & Input** | USB Barcode Scanner (Keyboard Wedge) / Mouse / Keyboard | Fast auto-focus text fields and accent-insensitive search |
| **Packaging & Targets** | Compose Desktop Packager | Native distributions: Windows (`.msi`), macOS (`.dmg`), Linux (`.deb`) |

---

## 📐 Domain Models & Lifecycle Rules

Lojinha's business logic, relational schemas, and financial rules are strictly modeled in the shared KMP layer. Key accounting and data integrity rules include:

* **UUID Primary & Foreign Keys:** Standardized UUID strings (v4) across all relational entities.
* **Monetary Values in Integer Cents:** Stored strictly as `Long` cents in Brazilian Real (`R$ 10,50` → `1050L`) to eliminate floating-point rounding errors.
* **Discrete Units & Grams:** Exact integer units for piece goods (`PIECE`) and integer grams for weighted goods (`WEIGHT`, e.g., `1,5 kg` → `1500L`).
* **Immutable Audit Trail:** Append-only ledger for all transactions (purchases, deposits, withdrawals, cancellations, corrections) with snapshotted prices and before/after balance flow (`userBalanceBefore` → `userBalanceAfter`).
* **Soft & Hard Delete Integrity:** Users with historical transactions can only be soft-deleted; catalog items can be deactivated or deleted without altering past transaction records.
* **Accent-Insensitive Collation:** User search, product filtering, and sorting ignore accents and case.

👉 **For the complete domain entity schemas, lifecycle rules, mathematical pricing formulas, and reversal mechanics, see [Domain Models & Lifecycle Rules](doc/DOMAIN_MODELS.md).**

---

## 🖥️ Screen & Workflow Specification

### Header Bar (Present on all screens)
* **Language Switcher:** Instant vector flag buttons (🇧🇷 BR / 🇺🇸 EN / 🇩🇪 DE) to switch UI localization dynamically.
* **Inactivity Auto-Logout:** Listens for user inactivity (mouse movement, clicks, keypresses). Triggers a **1-minute warning countdown modal** before automatic logout to the Main Screen.
* **Logout & Main Menu:** Always accessible to terminate user sessions and return to the Main Screen.

---

### User Flow
```
[Main Screen (User List)]
│ (Select / Scan Barcode + Enter / PIN)
▼
[Shopping Screen] ──(Checkout)──► [Purchase Overview Modal]
│          │                                   │                 │
│ (Account)│                                   ▼                 ▼
│          └─────────────► [Main Screen] ◄── [Logout]  [Transaction History]
▼                                                                │
[User Settings Modal] ◄──────────────────────────────────────────┘
```

#### 1. Main Screen (User Selection & Instant Login)
* Displays active users in a responsive grid.
* **Search / Filter:** Fast text filter for user names with accent insensitivity and auto-focus.
* **Instant Login:** Scans **User Barcodes** (with `Enter` suffix) or clicks a user card.
* **PIN Authentication:** If a user has configured a PIN, a masked input dialog appears. **Admin Bypass:** Admins can unlock any user account by entering the master admin password into the PIN prompt.
* **Admin Login Button:** Password-protected gateway to Admin Management.

#### 2. Shopping Screen (Landing Screen After Login)
* **Top Bar Header:** Displays logged-in user name and **Current Account Balance** (with secondary currency if enabled).
* **Product Input:**
  * Catalog items are displayed dynamically on search or barcode scan to keep the view clean.
  * **Piece Goods (`PIECE`):** Adds 1 unit on scan/click; repeated scans increment quantity (+1).
  * **Weighted Goods (`WEIGHT`):** Prompts a dialog for weight input in kg on scan/click (accepts `,` and `.` decimal points, e.g. `1,5` → `1500 g`), with quick preset buttons (`250g`, `500g`, `1kg`, `1.5kg`).
* **Cart View (Right Side):**
  * Shows item name, quantity, unit price (with markup applied), and total line price.
  * Stepper controls for adjusting quantity or removing items.
  * Bottom summary displays total cart amount and projected **Balance After Purchase**.
* **Checkout & Overview Modal:**
  * "Buy" button prompts a confirmation dialog.
  * On confirmation: Atomically writes transaction, deducts balance, and updates inventory.
  * Displays the **Purchase Overview Modal** with receipt details and choices to **Logout** or **View Transactions**.
* **Abandon Cart Protection:** If a user attempts to log out with items remaining in the cart, a warning dialog prompts confirmation before discarding the cart.

#### 3. User Account & Transaction History Screen
* **Paginated Ledger:** Database-backed pagination (`PaginationBar`) showing purchases, deposits, cancellations, and corrections.
* **Line Item Breakdown:** Purchased items and quantities are visible directly within each transaction entry.
* **Balance Flow Tracker:** Displays before-and-after balance progression on every entry (e.g. `R$ 50,00 → R$ 34,50`).
* **Search & Filters:** Instant search filter (searches items, notes, IDs, and dates) and transaction type chips (`ALL`, `PURCHASE`, `DEPOSIT`, `WITHDRAWAL`, `CORRECTION`, `CANCELLATION`).
* **User Settings Modal:**
  * Change or remove numeric PIN with show/hide toggle.
  * Change preferred language (`BR`, `EN`, `DE`).
  * Select optional **Secondary Display Currency** (`NONE`, `USD`, `EUR`).
  * **Avatar Customization:** Choose between initials avatar or emoji avatar with a customizable color palette.
  * View assigned Barcode ID (read-only).

---

### Admin Management

*Protected by the master Admin Password.* Accessible via the desktop app or the remote Kotlin/Wasm web console. Includes an **Unsaved Changes Guard** modal preventing accidental tab navigation or logout with uncommitted edits.

```
[ F1: Products ]  [ F2: Users ]  [ F3: Bulk Billing ]  [ F4: Transactions ]  [ F5: Settings ]
```

#### 1. Products Tab (`F1`)
* Create, edit, deactivate, or permanently delete products.
* Add multiple barcodes per product with descriptions (e.g. "Single Can", "6-Pack").
* Interactive sorting controls (Name A-Z/Z-A, Stock Low-High/High-Low, Price Low-High/High-Low).
* Accordion view with inline quick stock adjustments (`+` / `-`) and Enter key support.
* Separate "Disabled Products" collapsible section.
* Edit modal with "Revert Changes" and unsaved changes tracking.

#### 2. Users Tab (`F2`)
* Create and edit users (Name, Language, PIN, Barcode, Barcode ID, and Avatar config).
* Segregated collapsible sections: **Active Accounts**, **Deactivated Accounts**, and **Deleted Accounts** with 1-click reactivate/restore workflows.
* Inline quick balance adjustment (`+` / `-`) with Enter key support.
* **Custom Income / Expense Dialogs:** Add deposits or deductions with customizable note descriptions.
* Admin Reset PIN checkbox and PIN visibility toggle.

#### 3. Bulk Billing Tab (`F3`)
* Create and manage recurring billing lists:
  * **Fixed Lists:** Fixed fee per billing unit (e.g., Monthly Locker Fee).
  * **Variable Lists:** Custom individual amounts per member.
* Assign active users with custom quantity multipliers.
* 1-click **Execute Billing** runs an atomic batch transaction across all assigned members, logs individual debit transactions, and updates the `lastExecutionTime` tracker.

#### 4. Transactions Tab (`F4`)
* Searchable, filterable system-wide transaction history backed by Room database pagination.
* Visual link badges connecting parent transactions to cancellations or corrections.
* **Purchase Correction:** Opens an interactive editor allowing admins to alter line item quantities, calculating delta balance and stock adjustments automatically.
* **Non-Purchase Storno:** 1-click reversal of admin deposits and withdrawals.

#### 5. Settings Tab (`F5`)
* **Admin Security:** Update master admin password with double-confirmation.
* **Pricing & Exchange Rates:** Configure Global Markup %, USD exchange rate, and EUR exchange rate.
* **Kiosk Timers:** Configure user inactivity auto-logout timeout.
* **Support Email:** Configure diagnostic contact address with security lock/unlock flow.
* **Remote Web Administration:** Displays LAN server URL (`http://<ip>:8080`), QR code, and token status.
* **Automated & Cloud Backups:**
  * Configure multiple backup routines (Local folder or Microsoft OneDrive).
  * 1-click Microsoft OneDrive PKCE OAuth2 authentication with popup authorization (see [OneDrive Setup Guide](doc/ONEDRIVE_SETUP.md)).
  * Manual "Run Now" trigger, edit, and delete routines.
* **Database Restore & Factory Reset:**
  * Safe table-level SQLite database restore from `.db` files without schema drops or file locks (see [Storage Locations & Database Architecture](doc/STORAGE_LOCATIONS.md)).
  * Factory reset data wipe with safety challenge prompt.
* **CSV Data Import & Export:** Full CSV import/export for users, products, billing lists, and transactions with duplicate barcode validation and audit tracking (see [CSV Import/Export Specification](doc/CSV_IMPORT_EXPORT.md)).
* **Diagnostics & Logs:** Export comprehensive Diagnostic Support Bundle (`.zip`) and inspect persistent log storage metrics (see [Storage Locations & Diagnostics](doc/STORAGE_LOCATIONS.md)).

---

## 🌐 Remote Web Admin Console (Kotlin/Wasm)

The desktop application includes an integrated **Ktor Netty Web Server** serving a **Compose Multiplatform for Web (Kotlin/Wasm)** Single-Page Application:

* **Automatic Binding:** Starts on `http://0.0.0.0:8080` (falls back to `8081` if busy) when the desktop kiosk launches.
* **LAN Accessibility:** Any device (phone, tablet, PC) on the same local network can access the admin portal via browser.
* **Security & Hardening:**
  * Session token authentication with Bearer tokens.
  * Brute-force rate limiting (temporary lockout after 5 consecutive failed attempts).
  * Canonical path validation preventing path traversal attacks.
* **Real-time Synchronization:** Built-in **Server-Sent Events (SSE)** endpoint (`/api/admin/events`) notifies connected web clients whenever database changes occur on the kiosk or another client.

---

## 🚀 Build & Development

### Prerequisites
* **JDK 17** or higher
* **Gradle 8.x** (included via `./gradlew`)
* A modern browser supporting WebAssembly with Garbage Collection (**Wasm GC**) (Chrome 119+, Firefox 120+, Safari 17.4+) for the Web Admin.

### Running the Desktop Kiosk
```bash
./gradlew :desktopApp:run
```

### Building the Kotlin/Wasm Web Admin Distribution
```bash
./gradlew :shared:wasmJsBrowserDistribution
```
The compiled assets will be placed in `shared/build/dist/wasmJs/productionExecutable` and bundled automatically into the desktop app resources.

### Packaging Native Installers
Package native standalone desktop installers for your operating system:

```bash
# Windows MSI installer
./gradlew :desktopApp:packageMsi

# macOS DMG disk image
./gradlew :desktopApp:packageDmg

# Linux Debian package
./gradlew :desktopApp:packageDeb
```
Generated installers will be located in `desktopApp/build/compose/binaries/main/`.

### Running Tests
Execute unit and domain rule tests across common and JVM source sets:
```bash
./gradlew check
```

### 🤖 CI/CD & Automated GitHub Releases
The repository includes an automated GitHub Actions workflow ([`.github/workflows/build-windows.yml`](.github/workflows/build-windows.yml)) for building and releasing the Windows desktop application:
* **Trigger:** Runs automatically on every push or merge to the `main` branch, or on-demand via manual `workflow_dispatch`.
* **Automated Version Tagging & Release:** Dynamically reads `app.version` from `gradle.properties`. If the corresponding tag (`v<version>`) does not yet exist on the remote repository, the runner:
  1. Sets up JDK 21 and the WiX Toolset environment.
  2. Compiles the production Wasm web bundle and packages the standalone Windows MSI installer (`:desktopApp:packageMsi`).
  3. Publishes a new GitHub Release tagged `v<version>` with auto-generated release notes and attaches the `.msi` installer.
* **Safety Guard:** If `app.version` was not bumped before merging into `main`, the workflow validates the build but skips publishing to prevent tag collision errors.

---

## 📚 Documentation

Detailed architecture specifications, configuration guides, and data formats are organized in the [`doc/`](doc/) directory:

| Document | Description |
| :--- | :--- |
| [📐 Domain Models & Lifecycle Rules](doc/DOMAIN_MODELS.md) | Database entities, state transitions, barcode encoding, balance calculations, and immutability invariants. |
| [☁️ OneDrive Setup Guide](doc/ONEDRIVE_SETUP.md) | Azure App Registration, OAuth2 PKCE configuration, MS Graph permissions, and automated routine setup. |
| [💾 Storage Locations & Diagnostics](doc/STORAGE_LOCATIONS.md) | SQLite database paths, WAL mode, rolling log specifications, startup safety snapshots, and disaster recovery. |
| [📊 CSV Import & Export Specification](doc/CSV_IMPORT_EXPORT.md) | CSV formats for products, users, transactions, and billing lists, schema validation, barcode syntax, and balance adjustment audits. |

---

## 📄 License

This project is licensed under the **GNU General Public License v3.0 (GPLv3)** — see the [LICENSE](LICENSE) file for details.
