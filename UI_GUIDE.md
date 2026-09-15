# 🎨 Lojinha UI & UX Design Guide

Welcome to the **Lojinha UI/UX Guide**. This document defines the design principles, visual hierarchy, layout structures, color systems, component specs, and interaction patterns for the **Self-Service POS System**.

---

## 🎯 Core UX Principles

1. **Mouse & Barcode-Centric (Desktop Hardware Ergonomics)**
   - Target environment: Local desktop POS terminal running Windows with a USB Barcode Scanner & Mouse.
   - **Automatic Input Focus**: Input fields (User Search, PIN inputs, Product Search, Weight fields) automatically receive focus on screen load or dialog display, eliminating unnecessary mouse clicks or keyboard navigation.
   - **Minimal Keyboard Friction**: Mouse clicking and scanning handle almost all interactions. Typing is kept to an absolute minimum.

2. **Strict Privacy on Main Screen**
   - **Hidden Balances**: User account balances are **never** displayed on the public Main Screen.
   - **Avatar & Name Only**: User cards display only the user's **Name** and their **Initials in a circular avatar badge**. Account balances are revealed only after the user logs into their shopping session.

3. **High Visual Clarity & Instant Feedback**
   - **Light Mode Only (Clean White & Navy Blue)**: Crisp, professional light palette with Navy Blue accents for high contrast and readability.
   - **Immediate State Feedback**: Barcode scans trigger instant visual highlights and audible confirmation chimes.

4. **Non-Intrusive Inactivity Auto-Logout**
   - **1-Minute Countdown Warning**: A prominent warning modal with an active running countdown appears only when **1 minute remains** before automatic logout, allowing the user to extend their session or log out immediately.

---

## 🎨 Color System & Tokens (Navy Blue & White Theme)

Lojinha uses a clean, light **Navy Blue & White** color system.

| Token Name | Hex Value | Purpose |
|---|---|---|
| `surface` | `#FFFFFF` | Main screen background |
| `surfaceContainer` | `#F8FAFC` | Cards, panels, hover areas |
| `surfaceContainerHigh` | `#F1F5F9` | Card borders, secondary containers |
| `primaryNavy` | `#0F172A` | Primary text, main headers, dark navy branding |
| `accentNavy` | `#1E3A8A` / `#2563EB` | Primary buttons, active tabs, highlight borders |
| `onPrimary` | `#FFFFFF` | Text/Icons on navy background |
| `secondary` | `#475569` | Subtitles, muted labels |
| `success` / `deposit` | `#059669` (Emerald) | Positive balance, deposits, checkout completion |
| `danger` / `negative` | `#DC2626` (Crimson) | Negative balance, withdrawal, delete actions |
| `warning` / `lowBalance` | `#D97706` (Amber) | Low balance alerts, 1-minute logout popup |
| `textPrimary` | `#0F172A` | Primary titles, product names, BRL amounts |
| `textSecondary` | `#64748B` | Labels, secondary currency `(≈ $ 2,79)` |
| `divider` | `#E2E8F0` | Structural dividers & borders |

---

## 📐 Typography & Formatting Guidelines

### Hierarchy
- **Display / Big Totals**: `28sp` - `36sp` Bold (e.g., Checkout Total `R$ 45,90`)
- **Screen Titles & Names**: `20sp` - `24sp` SemiBold (e.g., User Name, Header)
- **Section Subtitles / Card Headers**: `16sp` - `18sp` Medium
- **Body & Cart Items**: `14sp` - `16sp` Regular
- **Captions & Secondary Currency**: `12sp` - `14sp` Regular (Muted text)

### Monetary & Unit Formatting Rules
1. **Primary Currency (BRL)**:
   - Formatted with `R$` prefix and comma decimal separator: `R$ 10,50`.
   - Positive balance: Deep Navy or Success Emerald (`#059669`).
   - Negative balance: Crimson Red (`#DC2626`) (e.g., `-R$ 12,30`).
2. **Secondary Currency (USD / EUR Helper)**:
   - Displayed adjacent or below the BRL amount in muted text: `R$ 15,50` `(≈ $ 2,79)`.
3. **Quantities & Weights**:
   - Piece items: `1x`, `2x` or `2 pcs`.
   - Weighted items: Decimal formatted with comma and unit: `1,5 kg` or `250 g`.

---

## 🖥️ Screen Layout Specifications

### 1. Header Bar (Universal Component)
Clean top navigation bar present on all screens.

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ 🛒 Lojinha POS    [🇩🇪 DE] [🇬🇧 EN] [🇧🇷 BR]                        [👤 User Name] [🚪 Logout]│
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

- **Left**: App Logo & Title in Navy Blue.
- **Center**: Language flags (1-click language switch).
- **Right**: Active User Indicator & Logout button.

---

### 2. Main Screen (User Selection & Instant Login)

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│  🔍 Search user by name... (Auto-Focused)                       [🔒 Admin Login]        │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│ ┌──────────────────────┐  ┌──────────────────────┐  ┌──────────────────────┐             │
│ │   ( MS )             │  │   ( JS )             │  │   ( AC )             │             │
│ │  Maria Silva         │  │  João Santos         │  │  Ana Costa           │             │
│ └──────────────────────┘  └──────────────────────┘  └──────────────────────┘             │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

- **Auto-Focused Search Input**: Focused automatically on load so scanning a barcode or typing immediately filters users.
- **Privacy-First User Cards**:
  - Circular avatar container with user's initials (e.g., `( MS )` for Maria Silva).
  - Clear, readable user name.
  - **No account balances displayed on the Main Screen**.
- **Instant Scan Login**: Scanning a user barcode logs the user in immediately.

---

### 3. Shopping Screen (Product Grid + Sticky Cart)

```
┌───────────────────────────────────────────────┬─────────────────────────────────────────┐
│  🛍️ Products                                  │ 🛒 Cart (Maria Silva)                   │
│  🔍 Search product... (Auto-Focused)          │ Balance: R$ 45,50                       │
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

- **Product Input**:
  - Scanning product barcode adds 1 piece (or opens weight prompt for weight items).
  - Manual search field is auto-focused for instant text search.
- **Weight Input Dialog**:
  - Weight input field is **auto-focused** with preset click buttons (`250g`, `500g`, `1kg`, `1.5kg`). Accepts `,` and `.`.
- **Right Cart Panel**:
  - Shows line items, total price, secondary currency, and projected **Balance After Purchase**.
  - Single-click **Complete Purchase** button.

---

### 4. Transaction History & Filterable Ledger

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ 📜 Transaction History                                                                  │
│ 🔍 Filter transactions (Product name, date, type...): [ All Types  ▼ ]                 │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐ │
│ │ 🛒 PURCHASE  •  17 Aug 2026, 14:30                          Total: -R$ 15,50       │ │
│ │    • 1x Club Mate (330ml) @ R$ 8,00 = R$ 8,00                                      │ │
│ │    • 1,5 kg Apples @ R$ 5,00/kg = R$ 7,50                                          │ │
│ └─────────────────────────────────────────────────────────────────────────────────────┘ │
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐ │
│ │ 💵 ADMIN DEPOSIT  •  15 Aug 2026, 10:15                      Total: +R$ 50,00       │ │
│ │    • Note: Cash deposit via Admin                                                   │ │
│ └─────────────────────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

- **Direct Visibility**: Purchased items are **clearly visible directly within each transaction entry** (no need to open collapsed drawers to see what was bought).
- **Search & Filter Options**:
  - Text search filter (filters by product name, note, or transaction ID).
  - Transaction Type Dropdown Filter (`ALL`, `PURCHASE`, `DEPOSIT`, `WITHDRAWAL`, `CANCELLATION`).

---

### 5. Inactivity Auto-Logout Warning Popup

```
┌──────────────────────────────────────────────────────────┐
│  ⚠️ Inactivity Warning                               [X] │
├──────────────────────────────────────────────────────────┤
│                                                          │
│     You will be logged out automatically in:             │
│                                                          │
│                      ⏰ 00:59                            │
│                                                          │
│                 [ ✅ Stay Logged In ]                    │
└──────────────────────────────────────────────────────────┘
```

- Triggers automatically when **1 minute remains** on the inactivity timer (minimum inactivity setting is 2 minutes).
- Pressing any key, clicking the screen, or clicking "Stay Logged In" resets the timer and closes the popup.

---

## ⚡ Summary of Key Interactions

1. **Main Screen**: Scan User Barcode or click User Card $\rightarrow$ Immediate Login. (Balances are hidden).
2. **Shopping Screen**: Scan Product Barcode or click Product Card $\rightarrow$ Item added to cart.
3. **Weight Prompt**: Auto-focused input field + 1-click preset buttons.
4. **History Screen**: Instant text search filter + clear item list view.
5. **Auto-Logout**: Silent background timer; 1-minute remaining triggers warning dialog.
