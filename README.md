# 🛒 Lojinha - Self-Service Kiosk & POS System

A local, offline-first Point-of-Sale (POS) and prepaid balance management system built with **Kotlin Multiplatform (KMP)** and **Compose Multiplatform**. Designed for small communities, stores, sports clubs, or shared spaces running on a local desktop machine (Windows, macOS, Linux) with an integrated **Ktor web server** providing remote web administration via **Kotlin/Wasm** across the local network.

---

## 🌟 Key Features

* **Prepaid Balance System & Visual Warning Banners:** Users deposit funds with an administrator and shop on credit. Negative balances are supported with clear debit tracking. Customizable **amber (low balance)** and **red (negative balance)** warning banners notify users in shopping and account history views based on configurable threshold settings.
* **100% Offline-First Architecture:** Operates completely independently of an internet connection using local SQLite storage with WAL (Write-Ahead Logging) checkpointing via AndroidX Room.
* **Dual Interface (Desktop Kiosk & Remote Web Portals):**
  * **Desktop POS Terminal:** Fullscreen Compose Multiplatform kiosk optimized for keyboard-wedge USB barcode scanners and rapid touchscreen or mouse navigation.
  * **Remote Web Admin, Customer & Buyer Portals (Kotlin/Wasm):** Embedded Ktor Netty server running on port `8080` (or `8081`) serving a production WebAssembly SPA over LAN, complete with `HttpOnly` session cookie authentication, brute-force rate limiting, real-time Server-Sent Events (SSE), Gzip compression, and RFC-compliant HTTP `ETag` / `Cache-Control` asset delivery.
* **Weekly Veggie Orders, Customer Portal & Buyer Portal:** End-to-end community produce ordering with dual-unit catalog products, automated weekly round opening, mobile-friendly **Customer Network Portal** self-ordering (PWA), a scoped **Buyer Portal** for market-run PDF downloads and live draft price entry, Largest Remainder proportional settlement, and Buyer / Worker / Order Form PDFs (see [Veggie Orders Guide](doc/VEGGIE_ORDERS.md)).
* **Product Suggestions & Admin Notes:** Kiosk users can submit wishlist requests, restock suggestions, or messages to the administration directly from the shopping screen (via a lightbulb icon in the header). In addition, when a purchase depletes a product's stock to `0`, Lojinha automatically generates a localized **stock-zero system alert** (`STOCK_ZERO`). Admins can search, review, delete, or forward stock-zero alerts directly to the Shopping List in a dedicated Admin Notes tab with full CSV export support.
* **Persistent Admin Shopping List & PDF Checklist Export:** Maintain a persistent restocking list across desktop and Web Admin with catalog-linked product search, custom items, live current-stock badges, inline editing, drag-and-drop reordering, weekly review status tracking (`New`, `Edited`, `Confirmed`, `Unadjusted`), and compact localized A4 PDF checklist export (localized filename such as `ShoppingList_<date>.pdf` / `Einkaufsliste_<date>.pdf` / `ListaDeCompras_<date>.pdf`).
* **Product History & Moving-Average Stock Tracking:** Inspect an item's paginated audit timeline combining purchases, cancellations, and manual stock adjustments with a running `Stock After` column, plus optional moving-average purchase price recalculation on restock.
* **Printable PDF Transaction Statements:** Admins can generate and export localized A4 PDF account statements for any user directly from the desktop app or Web Admin. Features flexible date filters (Last 30/60/90 days, Current Month, All Time, or Custom Range), language selection (PT-BR, EN, DE), and one-click "Open PDF" / "Reveal in Folder" actions.
* **Bulk Billing Lists:** Create recurring charge groups (fixed fees or variable usage) with per-user multiplier quantities and execute batch charges in a single atomic transaction.
* **Automated & Cloud Backups (OneDrive Integration):**
  * Multi-routine backup engine supporting local directories and 1-click **Microsoft OneDrive cloud backup via PKCE OAuth2** (see [Setup Guide](doc/ONEDRIVE_SETUP.md)).
  * Flexible schedules: **Timed** (daily at specific hours), **Interval** (every $X$ hours/minutes), or real-time **On Data Change**.
  * Formats: Full SQLite database snapshot (`.db`), CSV exports, or both; write modes: `OVERWRITE_LATEST` or `CREATE_NEW_FILE`.
  * **On-Demand Local Backup ("Backup Now Local"):** Immediate one-click snapshot to any selected directory without creating a permanent routine.
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
  * **Dynamic Exchange Rates API:** Automatically fetches latest live USD and EUR rates on startup (configurable) or on-demand in Admin Settings, rendering secondary reference amounts (e.g. `R$ 15,50` `(≈ US$ 2,79)`).
  * Fractional weight support with comma decimal representations (e.g., `1,5 kg` → `1500 g`).
* **Ergonomic Shopping Cart:**
  * Newly scanned or selected items are automatically placed at the **top of the cart** (index 0) for immediate visibility on POS touchscreens and kiosk displays.
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
| **Embedded Web Server** | Ktor Server (Netty engine) | Content negotiation, session cookie authentication, static asset serving, and SSE |
| **HTTP & Cloud Client** | Ktor Client (CIO engine) | Microsoft Graph / OneDrive PKCE OAuth2 and dynamic currency exchange rates API |
| **Document Generation** | OpenPDF (JVM) | Server/Desktop PDF rendering for printable transaction account statements and compact shopping list checklists |
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
* **Product Suggestions & Notes:** Lightbulb icon opens a modal dialog allowing users to leave notes, restocking requests, or suggestions for the admin directly from the kiosk.
* **Inactivity Auto-Logout:** Listens for user inactivity (mouse movement, clicks, keypresses). Triggers a **1-minute warning countdown modal** before automatic logout to the Main Screen.
* **Logout & Main Menu:** Always accessible to terminate user sessions and return to the Main Screen.

---

### User Flow
```
[Main Screen (User List)]
│ (Select / Scan Barcode + Enter / PIN)
▼
[Shopping Screen] ──(Checkout)──► [Purchase Overview Modal]
│   │      │                                   │                 │
│   │      │                                   ▼                 ▼
│   │      └─────────────► [Main Screen] ◄── [Logout]  [Transaction History]
│   ▼                                                                    │
│ [Note Input Dialog]                                                    │
▼                                                                        │
[User Settings Modal] ◄──────────────────────────────────────────────────┘
```

#### 1. Main Screen (User Selection & Instant Login)
* Displays active users in a responsive grid.
* **Search / Filter:** Fast text filter for user names with accent insensitivity and auto-focus.
* **Instant Login:** Scans **User Barcodes** (with `Enter` suffix) or clicks a user card.
* **PIN Authentication:** If a user has configured a PIN, a masked input dialog appears. **Admin Bypass:** Admins can unlock any user account by entering the master admin password into the PIN prompt.
* **Admin Login Button:** Password-protected gateway to Admin Management.

#### 2. Shopping Screen (Landing Screen After Login)
* **Top Bar Header:** Displays logged-in user name and **Current Account Balance** (with secondary currency if enabled).
* **Balance Warning Banner:** Automatically displays an **amber warning** (if balance $\le$ yellow threshold, e.g. R$ 300,00) or **red warning** (if balance $<$ red threshold, e.g. R$ 0,00) to keep users aware of their prepaid account standing.
* **Product Input:**
  * Catalog items are displayed dynamically on search or barcode scan to keep the view clean.
  * **Piece Goods (`PIECE`):** Adds 1 unit on scan/click; repeated scans increment quantity (+1).
  * **Weighted Goods (`WEIGHT`):** Prompts a dialog for weight input in kg on scan/click (accepts `,` and `.` decimal points, e.g. `1,5` → `1500 g`), with quick preset buttons (`250g`, `500g`, `1kg`, `1.5kg`).
* **Cart View (Right Side):**
  * Newly added items appear at the **top of the cart** (index 0) for instant verification.
  * Shows item name, quantity, unit price (with markup applied), and total line price.
  * Stepper controls for adjusting quantity or removing items.
  * Bottom summary displays total cart amount and projected **Balance After Purchase**.
* **Checkout & Overview Modal:**
  * "Buy" button prompts a confirmation dialog.
  * On confirmation: Atomically writes transaction, deducts balance, and updates inventory.
  * Displays the **Purchase Overview Modal** with receipt details and choices to **Logout** or **View Transactions**.
* **Abandon Cart Protection:** If a user attempts to log out with items remaining in the cart, a warning dialog prompts confirmation before discarding the cart.

#### 3. User Account & Transaction History Screen
* **Balance Warning Banner:** Persistently indicates low or negative balance status based on admin thresholds.
* **Paginated Ledger:** Database-backed pagination (`PaginationBar`) showing purchases, deposits, cancellations, and corrections.
* **Line Item Breakdown:** Purchased items and quantities are visible directly within each transaction entry.
* **Balance Flow Tracker:** Displays before-and-after balance progression on every entry (e.g. `R$ 50,00 → R$ 34,50`).
* **Search & Filters:** Instant search filter (searches items, notes, IDs, and dates) and transaction type chips (`ALL`, `PURCHASE`, `DEPOSIT`, `WITHDRAWAL`, `VEGGIE`, `CORRECTION`, `CANCELLATION`).
* **User Settings Modal:**
  * Set or edit a unique short **Nickname** (max 10 characters, displayed in Veggie Worker PDFs and sorting matrices).
  * Change or remove numeric PIN with show/hide toggle.
  * Change preferred language (`BR`, `EN`, `DE`).
  * Select optional **Secondary Display Currency** (`NONE`, `USD`, `EUR`).
  * **Avatar Customization:** Choose between initials avatar or emoji avatar with a customizable color palette.
  * View assigned Barcode ID (read-only).

---

### Admin Management

*Protected by the master Admin Password.* Accessible via the desktop app or the remote Kotlin/Wasm web console. Includes an **Unsaved Changes Guard** modal (plus browser `beforeunload` unload warning on the Web Admin page) preventing accidental tab navigation, page close, or logout with uncommitted edits, persistent scrollbars, and keyboard-first workflows across all admin views (`F1`–`F8` tab switching, `Ctrl+F` / `Cmd+F` search focus, `Esc` to collapse open cards, `PageUp` / `PageDown` list scrolling, and `Enter` / `Tab` rapid entry flows).

```
[ F1: Notes ]  [ F2: Products ]  [ F3: Shopping List ]  [ F4: Users ]  [ F5: Bulk Billing ]  [ F6: Veggie Orders ]  [ F7: Transactions ]  [ F8: Settings ]
```
*(Note: `F8: Settings` is available on the Desktop POS application and omitted from the Remote Web Admin console.)*

#### 1. Notes Tab (`F1`)
* Review customer wishlist requests, restocking suggestions, and automatic **stock-zero system alerts** (`STOCK_ZERO`, triggered atomically whenever a purchase reduces a product's stock to `0`).
* Search filter for quick discovery by decoded message content, user name, or `System` label.
* **1-Click Shopping List Handoff:** Stock-zero system notes include a **Shopping List** button that opens the quantity prompt for the depleted product and optionally removes the note once added.
* One-click note deletion with confirmation and real-time reactive updates via `DataChangeNotifier` and LAN Server-Sent Events.

#### 2. Products Tab (`F2`)
* Create, edit, deactivate, or permanently delete products.
* Add multiple barcodes per product with descriptions (e.g. "Single Can", "6-Pack").
* Interactive sorting controls (Name A-Z/Z-A, Stock Low-High/High-Low, Price Low-High/High-Low).
* Accordion view with inline quick stock adjustments (`+` / `-`, optional incoming purchase price for weighted moving-average cost recalculation, and `Enter` key submission).
* **Product History Modal:** View a paginated audit ledger of manual stock adjustments, purchases, and cancellations for any product, including a running **Stock After** column.
* **Add to Shopping List:** Quick action button on product cards to queue an item onto the Admin Shopping List.
* Separate "Disabled Products" collapsible section.
* Edit modal with "Revert Changes" and unsaved changes tracking.

#### 3. Shopping List Tab (`F3`)
* **Search & Quick-Add Bar:** Type to search active catalog products (ranked autocomplete suggestions with `Tab`, `Up`/`Down`, and `Enter` keyboard selection) or press `Enter` / click **Add** to create a custom non-catalog item.
* **Quantity & Duplicate Handling:** Adding an item opens a unit-aware quantity modal (`pcs` for piece goods, `kg` for weighted goods, or freeform text for custom items). If the product or custom item is already on the list, the modal warns and pre-fills the existing quantity to update it in place.
* **Interactive List Management:**
  * Inline editing for both **Item / Product** name and **Quantity**, with `Tab` / `Shift+Tab` / `Enter` navigation across rows and automatic select-all on focus.
  * **Current Stock** column displaying live catalog stock (highlighted in crimson when stock is `<= 0`).
  * **Drag-and-Drop Reordering** via drag handles with persistent `sortOrder` synchronization across desktop and Web Admin.
  * **Clear All** and per-row delete actions.
* **Weekly Review Lifecycle & Status Badges:**
  * Items track their review state relative to the previous PDF export (`New`, `Edited`, `Confirmed`, `Unadjusted`).
  * Existing items carried over from a previous export start as **Unadjusted** (`isPendingReview`) until edited or explicitly confirmed with the check toggle.
* **Compact PDF Checklist Export:**
  * Clicking **Export PDF** opens an approval dialog listing any remaining `Unadjusted` items before generating a compact, localized A4 checklist (localized filename by language, e.g., `ShoppingList_<date>.pdf`) with checkboxes, current stock, quantities, and item names (available on both Desktop and Web Admin download).
  * Exporting commits the new baseline (`lastExportedName`, `lastExportedQuantity`) so items transition cleanly into the next weekly review cycle.

#### 4. Users Tab (`F4`)
* Create and edit users (Name, unique Nickname up to 10 chars, Language, PIN, Kiosk PIN enforcement toggle `requirePinOnKiosk`, Barcode, Barcode ID, and Avatar config).
* Segregated collapsible sections: **Active Accounts**, **Deactivated Accounts**, and **Deleted Accounts** with 1-click reactivate/restore workflows.
* Inline quick balance adjustment (`+` / `-`) with Enter key support.
* **Custom Income / Expense Dialogs:** Add deposits or deductions with customizable note descriptions.
* **Printable PDF Transaction Statement:** Generate localized A4 statements for any user with date range presets (Last 30/60/90 days, Current Month, All Time, or Custom Range), language selection, custom destination folder, and one-click "Open PDF" / "Reveal in Folder" actions.
* Admin Reset PIN checkbox and PIN visibility toggle.

#### 5. Bulk Billing Tab (`F5`)
* Create and manage recurring billing lists:
  * **Fixed Lists:** Fixed fee per billing unit (e.g., Monthly Locker Fee).
  * **Variable Lists:** Custom individual amounts per member.
* Assign active users with custom quantity multipliers.
* 1-click **Execute Billing** runs an atomic batch transaction across all assigned members, logs individual debit transactions, and updates the `lastExecutionTime` tracker.

#### 6. Veggie Orders Tab (`F6`)
* Manage weekly **Veggie Order Rounds** (manual open/close/reopen/delete, or automated weekly opening via `VeggieRoundAutoScheduler` with configurable day, time, and default fees), dual-unit catalog products with thumbnails, custom unit types, buyer PDF exclusion (`excludeFromBuyerPdf`), and CSV import (`veggie_products.csv`).
* Searchable **Add / Edit Order** modal for managing customer entries directly in the round.
* Interactive settlement matrix with draft persistence, Largest Remainder proportional cost distribution, equal fee splitting, transporter credit, and re-settlement differential corrections.
* Export **Buyer Shopping List PDF** (respecting `excludeFromBuyerPdf`), **Worker Sorting Matrix PDF** (using `User.veggieDisplayName` / `nickname`), and blank **Customer Order Form PDF**.
* Customers can place and review orders directly through the mobile-friendly **Customer Network Portal** (see [Veggie Orders & Customer Portal Guide](doc/VEGGIE_ORDERS.md)).

#### 7. Transactions Tab (`F7`)
* Searchable, filterable system-wide transaction history backed by Room database pagination.
* Visual link badges connecting parent transactions to cancellations or corrections.
* **Purchase Correction:** Opens an interactive editor allowing admins to alter line item quantities, calculating delta balance and stock adjustments automatically.
* **Non-Purchase Storno:** 1-click reversal of admin deposits and withdrawals.

#### 8. Settings Tab (`F8`)
* **Admin & Buyer Security:** Update the master admin password and the dynamic **Buyer Portal** login name (`buyerLoginName`, reserved against user names/nicknames) & password (`buyerPasswordHash`) with validation and confirmation prompts.
* **Veggie Orders Schedule & Defaults:** Configure automated weekly round opening (`veggieAutoOpenEnabled`, day of week, local time) and default Buyer/Transport fees.
* **Pricing & Currency Exchange Rates:**
  * Configure Global Markup %, USD exchange rate, and EUR exchange rate.
  * **Dynamic Exchange Rates API:** Toggle automatic rates update on startup, or click **Fetch Latest Rates** for immediate live conversion rate updates.
* **Balance Warning Thresholds:** Configure the **Yellow Warning Threshold** (default: R$ 300,00) and **Red Warning Threshold** (default: R$ 0,00) to control warning banners throughout the app.
* **Kiosk Timers:** Configure user inactivity auto-logout timeout.
* **Support Email:** Configure diagnostic contact address with security lock/unlock flow.
* **Remote Web Administration:** Displays LAN server URL (`http://<ip>:8080`), QR code, and token status.
* **Automated & On-Demand Backups:**
  * **Backup Now Local:** Create an immediate on-demand local backup (`.db`, CSV, or both) to any selected folder.
  * Configure multiple recurring backup routines (Local folder or Microsoft OneDrive).
  * 1-click Microsoft OneDrive PKCE OAuth2 authentication with popup authorization (see [OneDrive Setup Guide](doc/ONEDRIVE_SETUP.md)).
  * Manual "Run Now" trigger, edit, and delete routines.
* **Database Restore & Factory Reset:**
  * Safe table-level SQLite database restore from `.db` files without schema drops or file locks, with automatic safety snapshot creation (see [Storage Locations & Database Architecture](doc/STORAGE_LOCATIONS.md)).
  * Factory reset data wipe with safety challenge prompt.
* **CSV Data Import & Export:** Full CSV import/export (scheduled backup routines and on-demand per-tab exports) for users, products, veggie products/orders, billing lists, transactions, and notes with duplicate barcode/nickname validation and audit tracking (see [CSV Import/Export Specification](doc/CSV_IMPORT_EXPORT.md)).
* **Diagnostics & Logs:** Export comprehensive Diagnostic Support Bundle (`.zip`) and inspect persistent log storage metrics (see [Storage Locations & Diagnostics](doc/STORAGE_LOCATIONS.md)).

---

## 🌐 Remote Web Admin Console, Customer Portal & Buyer Portal (Kotlin/Wasm)

The desktop application includes an integrated **Ktor Netty Web Server** serving a **Compose Multiplatform for Web (Kotlin/Wasm)** Single-Page Application for administrators, customers, and market buyers:

* **Automatic Binding:** Starts on `http://0.0.0.0:8080` (falls back to `8081` if busy) when the desktop kiosk launches.
* **LAN Accessibility:** Any device (phone, tablet, PC) on the same local network can access the admin or customer portal via browser.
* **Security & Hardening:**
  * Native HTML login (`/login.html`) issuing `HttpOnly` session cookies (`lojinha_admin_session`, `lojinha_customer_session`, `lojinha_buyer_session`) with SHA-256 token hashing on the server.
  * Tiered brute-force rate limiting (IP + account lockout) and strict `CSP` / `X-Frame-Options` / `X-Content-Type-Options` / `Referrer-Policy` headers.
  * Canonical path validation preventing path traversal attacks.
* **Fast Asset Delivery & Caching:**
  * Automatic **Gzip compression** for Wasm, JS, and static assets when supported by the client.
  * RFC-compliant **`ETag` validation** and **`Cache-Control`** policies (1-year immutable caching for content-hashed `.wasm` chunks; `no-cache` with ETag revalidation for entry HTML/JS).
  * Deferred script loading for faster initial paint.
* **Real-time Synchronization:** Built-in **Server-Sent Events (SSE)** endpoints (`/api/admin/events`, `/api/customer/events`, and `/api/buyer/events`) notify connected web clients whenever database changes occur on the kiosk or another client.

### 📱 Customer Network Portal & Buyer Portal (PWA)
* **Veggie Ordering & Favorites:** Browse active produce items, toggle personal favorites, place or update orders during `OPEN` rounds, and inspect past round history.
* **Buyer Portal (Market Run):** Optional password-protected role (`buyerPasswordHash`) allowing a market buyer to download the Buyer Shopping List PDF for a `CLOSED` round and save draft market prices/operation fees directly into the Admin Settlement Matrix via live SSE sync.
* **Account & Statements:** View live prepaid balance (with low/negative balance warning banners), paginated transaction history, and export A4 PDF account statements directly from the browser.
* **Self-Service Settings & Notes:** Update personal nickname, PIN, language, secondary currency, and avatar, or submit product suggestions/notes to admins.
* **Mobile UX:** Responsive layout with mobile keyboard/viewport handling to prevent horizontal viewport scrolling on iOS and Android browsers.

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
| [🥬 Veggie Orders, Customer & Buyer Portals](doc/VEGGIE_ORDERS.md) | Weekly produce ordering rounds, auto-open scheduling, Largest Remainder settlement math, PDFs, Customer Network Portal (PWA), and Buyer Portal. |
| [☁️ OneDrive Setup Guide](doc/ONEDRIVE_SETUP.md) | Azure App Registration, OAuth2 PKCE configuration, MS Graph permissions, and automated routine setup. |
| [💾 Storage Locations & Diagnostics](doc/STORAGE_LOCATIONS.md) | SQLite database paths, WAL mode, rolling log specifications, startup safety snapshots, and disaster recovery. |
| [📊 CSV Import & Export Specification](doc/CSV_IMPORT_EXPORT.md) | CSV formats for products, users, veggie products/orders, transactions, and billing lists, on-demand exports, schema validation, and balance audits. |

---

## 📄 License

This project is licensed under the **GNU General Public License v3.0 (GPLv3)** — see the [LICENSE](LICENSE) file for details.
