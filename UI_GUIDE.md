# 🎨 Lojinha UI & UX Design Guide

Welcome to the **Lojinha UI/UX Guide**. This document defines the design principles, visual hierarchy, layout structures, color systems, component specs, and interaction patterns for the **Self-Service Kiosk & POS System**.

---

## 🎯 Core UX Principles

1. **Touch & Scanner First (Kiosk Ergonomics)**
   - Target environment: Local POS / Self-service kiosk terminal (Touchscreen, Barcode Scanner, Mouse/Keyboard).
   - **Min Touch Target**: Minimum `48dp` (preferably `56dp` to `64dp` for primary kiosk buttons like "Checkout" or "Select User").
   - **Zero Friction**: Common actions (scanning a user barcode $\rightarrow$ scanning products $\rightarrow$ checkout) should take **under 10 seconds** without requiring keyboard input.

2. **High Visual Clarity & Instant Feedback**
   - **High Contrast**: Ensure clear readability from a distance of 1–2 meters.
   - **Immediate State Feedback**: Barcode scans trigger instant visual highlights and audible audio chimes.
   - **Balance Awareness**: User balance is always prominently visible on all user screens.

3. **Safe & Forgiving Operations**
   - **Confirmation Modals**: Require confirmation for destructive actions or balance-altering operations (e.g., checkout, admin withdrawals, reversals).
   - **Clear Undo & Reversal Flows**: Admin cancellation flow explicitly shows original vs reversed transaction details.

---

## 🎨 Color System & Tokens

Lojinha uses a sleek, modern Material 3 design system with vibrant dark and light palettes optimized for kiosk screens.

### Light & Dark Color Palette

| Token Name | Dark Mode (Default Kiosk) | Light Mode | Purpose |
|---|---|---|---|
| `surface` | `#121318` | `#F8FAFC` | Main background |
| `surfaceContainer` | `#1E2028` | `#FFFFFF` | Cards, panels, modal dialogs |
| `surfaceContainerHigh` | `#282A36` | `#F1F5F9` | Hover states, active items |
| `primary` | `#6366F1` (Indigo) | `#4F46E5` | Primary buttons, active selections |
| `onPrimary` | `#FFFFFF` | `#FFFFFF` | Text/Icon on primary elements |
| `secondary` | `#A855F7` (Purple) | `#7E22CE` | Secondary accents, user badges |
| `success` / `deposit` | `#10B981` (Emerald) | `#059669` | Positive balance, deposits, checkout success |
| `danger` / `negative` | `#EF4444` (Crimson) | `#DC2626` | Negative balance, withdrawal, delete actions |
| `warning` / `lowBalance` | `#F59E0B` (Amber) | `#D97706` | Low balance warnings |
| `textPrimary` | `#F8FAFC` | `#0F172A` | Primary titles, names, prices |
| `textSecondary` | `#94A3B8` | `#64748B` | Labels, secondary currency `(≈ $ 2,79)` |
| `divider` | `#2D313E` | `#E2E8F0` | Border separators |

---

## 📐 Typography & Formatting Guidelines

### Hierarchy
- **Display / Big Totals**: `28sp` - `36sp` Bold (e.g., Checkout Total `R$ 45,90`)
- **Screen Titles & Names**: `20sp` - `24sp` SemiBold (e.g., User Name, Screen Header)
- **Section Subtitles / Card Headers**: `16sp` - `18sp` Medium
- **Body & Cart Items**: `14sp` - `16sp` Regular
- **Captions & Secondary Currency**: `12sp` - `14sp` Regular (Muted text)

### Monetary & Unit Formatting Rules
1. **Primary Currency (BRL)**:
   - Always formatted with `R$` prefix and comma decimal separator: `R$ 10,50`.
   - Positive balances: Standard primary color or success green.
   - Negative balances: Crisp danger crimson (e.g., `-R$ 12,30`).
2. **Secondary Currency (USD / EUR Helper)**:
   - Appears adjacent or directly below the BRL amount in muted text:
     - Single-line: `R$ 15,50` `(≈ $ 2,79)`
     - Stacked:
       - **R$ 15,50**
       - *(≈ € 2,55)*
3. **Quantities & Weights**:
   - Piece items: Integer suffix `x1`, `x2` or `2 pcs`.
   - Weighted items: Decimal with comma and unit: `1,5 kg` or `250 g`.

---

## 🖥️ Screen Layout Specifications

### 1. Header Bar (Universal Component)
Present at the top of every screen.

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ 🛒 Lojinha    [🇩🇪 DE] [🇬🇧 EN] [🇧🇷 BR]       ⏱️ Auto-logout: 02:45   [👤 User]  [🚪 Logout] │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

- **Left**: App Logo / Branding.
- **Center**: Language flags (instant 1-tap language switch).
- **Right**:
  - Inactivity timer countdown (when logged in).
  - Current logged-in user indicator.
  - Quick Logout button (always accessible).

---

### 2. Main Screen (User Selection & Instant Login)

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│  🔍 Search user by name...                      [📷 Scan User Barcode]  [🔒 Admin Login]│
├─────────────────────────────────────────────────────────────────────────────────────────┤
│ ┌──────────────────────┐  ┌──────────────────────┐  ┌──────────────────────┐             │
│ │ 👤 Maria Silva       │  │ 👤 João Santos       │  │ 👤 Ana Costa         │             │
│ │ R$ 45,50 (≈ $8.19)   │  │ -R$ 12,00 (≈ -$2.16) │  │ R$ 120,00 (≈ $21.60) │             │
│ └──────────────────────┘  └──────────────────────┘  └──────────────────────┘             │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

- **Autofocus Search**: Text field focused by default for quick keyboard filtering.
- **Barcode Listener**: Active background listener for user barcode scans. Scanning immediately logs the user in (or opens PIN modal if protected).
- **User Cards Grid**:
  - User avatar / initials icon.
  - Large readable name.
  - Color-coded current balance (Green if positive, Red if negative).

---

### 3. Shopping Screen (Product Grid + Sticky Cart)

```
┌───────────────────────────────────────────────┬─────────────────────────────────────────┐
│  🛍️ Product Catalog                           │ 🛒 Shopping Cart (Maria Silva)          │
│  🔍 Search product...                         │ Balance: R$ 45,50                       │
├───────────────────────────────────────────────┼─────────────────────────────────────────┤
│ ┌───────────────┐ ┌───────────────┐           │ 1. Club Mate (330ml)                   │
│ │ 🥤 Club Mate  │ │ 🍫 Snickers   │           │    1 x R$ 8,00              R$ 8,00    │
│ │ R$ 8,00       │ │ R$ 4,50       │           │ 2. Apples (Weighted)                    │
│ └───────────────┘ └───────────────┘           │    1,5 kg x R$ 5,00/kg      R$ 7,50    │
│ ┌───────────────┐ ┌───────────────┐           ├─────────────────────────────────────────┤
│ │ 🍎 Apples (kg)│ │ ☕ Coffee      │           │ Total Amount:          R$ 15,50         │
│ │ R$ 5,00/kg    │ │ R$ 3,00       │           │                        (≈ $ 2,79)       │
│ └───────────────┘ └───────────────┘           │ Balance After:         R$ 30,00         │
│                                               │                                         │
│                                               │ [ 🛍️ COMPLETE PURCHASE (R$ 15,50) ]     │
└───────────────────────────────────────────────┴─────────────────────────────────────────┘
```

- **Product Grid (Left)**:
  - Big visual buttons with clear product titles and prices.
  - Badge indicator for weighted items (`KG` or `g`).
  - Scanning a barcode adds item directly to cart.
- **Weight Input Dialog (Triggered on weighted product scan/click)**:
  - Numeric input keypad supporting both `,` and `.` decimal values.
  - Quick preset weight buttons (`250g`, `500g`, `1kg`, `1.5kg`).
- **Cart Side Panel (Right)**:
  - Scrollable list of added products with `+` / `-` / `Delete` controls.
  - **Live Bottom Summary**:
    - Cart Total
    - Secondary Currency Conversion
    - Projected Balance After Purchase (`Current Balance - Total`)
  - **Checkout Button**: Large high-contrast primary button (Min height `56dp`).

---

### 4. Checkout & Confirmation Modals

```
┌──────────────────────────────────────────────────────────┐
│  Confirm Purchase                                    [X] │
├──────────────────────────────────────────────────────────┤
│  Customer: Maria Silva                                   │
│  Total Items: 2                                          │
│  Total Price: R$ 15,50 (≈ $ 2,79)                        │
│                                                          │
│  Current Balance: R$ 45,50                               │
│  New Balance:     R$ 30,00                               │
│                                                          │
│  [ Cancel ]                  [ ✅ Confirm & Pay ]       │
└──────────────────────────────────────────────────────────┘
```

- Clear breakdown of items, total cost, and resulting user balance.
- Confirmation redirects immediately to the **Transaction History Screen**.

---

### 5. Transaction History & User Settings

- **Ledger Cards**: Clean timeline list showing:
  - Transaction Type Badge (`PURCHASE`, `DEPOSIT`, `CANCELLATION`).
  - Date & Timestamp.
  - Amount (`-R$ 15,50` for purchases, `+R$ 50,00` for deposits).
  - Expandable detail drawer for purchase item snapshots.
- **User Settings Modal**:
  - 1-Tap language selector (`DE`, `EN`, `BR`).
  - Secondary Currency selector dropdown (`NONE`, `USD`, `EUR`).
  - PIN configuration / change.

---

### 6. Admin Management UI Guidelines

- **Distinct Admin Theme Banner**: Top subtle amber or purple accent bar to immediately signpost Admin Mode.
- **Tab Navigation**:
  - 📦 **Products**: Catalog management, stock updates, barcode assignments, global & custom markups.
  - 👤 **Users**: User creation, PIN reset, barcode linking, Quick Deposit/Withdrawal UI.
  - 📜 **Ledger**: System-wide transaction audit trail with 1-click reversal workflow.
  - ⚙️ **System Settings**: Admin password change, exchange rate configuration (`1 BRL = X USD`), inactivity auto-logout timeout.

---

## ⚡ Micro-Interactions & Animations

1. **Cart Item Added**:
   - Soft scale animation (`1.0` $\rightarrow$ `1.08` $\rightarrow$ `1.0`) on cart summary badge when scanning/adding products.
2. **Barcode Scan Confirmation**:
   - Brief green flash border around the shopping screen container on valid product scan.
   - Gentle error shake effect on invalid/unrecognized barcode.
3. **Screen Navigation**:
   - Smooth horizontal slide or fade transition (`200ms` duration).

---

## ♿ Accessibility & Edge Case Rules

- **Zero Mouse Dependency**: All core shopping flows can be navigated via Barcode Scanner + Numeric Keypad / Touchscreen.
- **Empty States**: Friendly visual illustrations/icons when Cart is empty, search has no results, or user list is empty.
- **Offline Reliability Indicator**: Display a subtle green "Offline Mode Active (SQLite)" status badge in Admin view.
