# 📊 CSV Import & Export Format Specification

Lojinha supports importing and exporting user accounts, product catalogs, billing lists, and transactions via standard Comma-Separated Values (CSV) files.

Admins can access CSV operations from the **Admin Panel** (`F5` Settings $\rightarrow$ **Import Data from CSV Files** or **Configured Backup Routines** with CSV format).

---

## 🛍️ 1. Products CSV Specification (`products.csv`)

### Column Schema

| Column Name | Aliases Accepted | Type / Format | Required? | Description & Examples |
|---|---|---|---|---|
| `id` | `productId` | UUID / String | Optional | If omitted, Lojinha generates a unique ID automatically. |
| `name` | `productName`, `title` | String | **Yes** | Product name (e.g., `Club Mate 330ml`). Cannot be blank. |
| `barcodes` | `barcode` | String | Optional | Pipe-separated list of barcodes with optional descriptions: `code:desc\|code:desc` (e.g., `4029764001807:Single Bottle\|4029764001814:Crate 20x`). |
| `basePrice` | `price`, `base_price` | Currency / Number | **Yes** | Unit price in BRL. Accepts `R$ 8,50`, `8,50`, `8.50`, or integer cents `850`. Negative numbers default to `0,00`. |
| `unitType` | `unit`, `unit_type` | Enum | Optional | `PIECE` (sold per item) or `WEIGHT` (sold by weight, priced per kg). Default: `PIECE`. |
| `stockQuantity` | `stock`, `quantity`, `stock_quantity` | Integer | Optional | Current stock. Exact pieces for `PIECE`, grams for `WEIGHT` (e.g., `1500` for 1,5 kg). Default: `0`. |
| `customMarkupPercent` | `markup`, `custom_markup_percent` | Decimal | Optional | Custom product markup override percentage (e.g., `15.0`). If empty, global markup applies. |
| `isActive` | `active`, `is_active` | Boolean | Optional | `true` or `false` (`1` or `0`). Default: `true`. |

### Example `products.csv`

```csv
name,barcodes,basePrice,unitType,stockQuantity,customMarkupPercent,isActive
Club Mate (330ml),4029764001807:Bottle|4029764001814:Crate,R$ 8,00,PIECE,48,,true
Snickers Bar,5000159461122,4.50,PIECE,25,10.0,true
Fresh Gala Apples,,5,00,WEIGHT,15000,,true
Organic Coffee Beans,4012345678901:500g Bag,35.00,PIECE,12,,true
Discontinued Soda,7891234567890,3.00,PIECE,0,,false
```

---

## 👤 2. Users CSV Specification (`users.csv`)

### Column Schema

| Column Name | Aliases Accepted | Type / Format | Required? | Description & Examples |
|---|---|---|---|---|
| `id` | `userId`, `user_id` | UUID / String | Optional | If omitted, a unique UUID is generated automatically. |
| `name` | `userName`, `user_name` | String | **Yes** | Full user name (e.g., `Maria Silva`). Cannot be blank. |
| `balance` | `kontostand`, `saldo` | Currency / Number | Optional | Starting account balance. Accepts `R$ 50,00`, `50,00`, `50.00`, `-15.20`, or integer cents `5000`. Default: `0`. |
| `language` | `lang`, `sprache` | String | Optional | `pt-BR` / `BR`, `en` / `EN`, `de` / `DE`. Default: `DE`. |
| `secondaryCurrency` | `secondary_currency`, `secCurr` | Enum | Optional | `NONE`, `USD`, `EUR`. Default: `NONE`. |
| `pin` | `password` | String | Optional | Plaintext 4-6 digit numeric PIN or existing SHA-256 hash. Plaintext entries are hashed automatically. |
| `userBarcode` | `user_barcode`, `barcode` | String | Optional | Scanned barcode value assigned to the user badge. |
| `userBarcodeNumber` | `user_barcode_number`, `barcodeNumber` | String | Optional | Visual identifier matching `userBarcode` (both must be present or both null). |
| `isActive` | `active`, `is_active` | Boolean | Optional | `true` or `false` (`1` or `0`). Default: `true`. |
| `isDeleted` | `deleted`, `is_deleted` | Boolean | Optional | Soft-delete status (`true` or `false`). Default: `false`. |
| `avatarType` | `avatar_type` | Enum | Optional | `INITIALS` or `EMOJI`. Default: `INITIALS`. |
| `avatarEmoji` | `avatar_emoji`, `emoji` | String | Optional | Emoji icon if avatarType is `EMOJI` (e.g., `☕`, `🚀`, `⭐`). |
| `avatarColor` | `avatar_color`, `color` | Hex Color | Optional | Background hex color (e.g., `#1E293B`, `#2563EB`). |

### Example `users.csv`

```csv
name,balance,language,secondaryCurrency,pin,userBarcode,userBarcodeNumber,isActive,avatarType,avatarEmoji,avatarColor
Maria Silva,R$ 45,50,pt-BR,USD,1234,USER001,USER001,true,INITIALS,,#2563EB
João Santos,-12.30,en,EUR,,USER002,USER002,true,EMOJI,☕,#059669
Hans Müller,100.00,de,NONE,9999,USER003,USER003,true,EMOJI,🚀,#7C3AED
Ana Costa,0.00,pt-BR,NONE,,,true,INITIALS,,#DC2626
```

---

## ⚡ Import Behavior & Conflict Resolution Rules

### 1. Barcode Deduplication Safeguard
* Barcodes must be unique across all products and users.
* If a CSV row contains a barcode that is **already assigned** to another existing entity:
  * The conflicting barcode is **stripped** from the incoming row.
  * A clear warning is reported in the import summary dialog.
  * The rest of the entity data (name, price, stock, balance) is safely created or updated.

### 2. Balance Adjustments & Audit Trail
* If an existing user's balance in the CSV differs from their current balance in the database:
  * Lojinha calculates the exact difference ($\Delta = \text{newBalance} - \text{currentBalance}$).
  * An automated balancing transaction (`ADMIN_DEPOSIT` or `ADMIN_WITHDRAWAL`) is created with the note `SYSNOTE|CSV Import Balance Adjustment`.
  * This guarantees that account balances never change without an audit trail record.

### 3. Dry-Run Preview Modal
* When selecting a CSV file in the Admin panel, Lojinha first parses the file in **Dry Run mode**.
* A preview dialog displays:
  * Number of new records to be added.
  * Number of existing records to be updated.
  * Number of stripped conflicting barcodes.
  * Warnings and validation errors.
* The admin can review and confirm before any data is written to the database.

---

## 📜 3. Export Formats (Backup Routines)

When running a CSV backup routine, Lojinha creates a folder (`lojinha_csv_export_<timestamp>/` or `lojinha_csv_export_latest/`) containing:

1. **`products.csv`**: Full product catalog.
2. **`users.csv`**: All accounts including balances, avatar configs, and hashed PINs.
3. **`transactions.csv`**: Complete audit ledger with columns:
   ```csv
   id,userId,userNameSnapshot,timestamp,type,referenceTransactionId,note,totalAmount,itemCount,items,userBalanceBefore,userBalanceAfter
   ```
4. **`billing_lists.csv`**: Bulk billing groups and assigned member quantities with columns:
   ```csv
   listId,listName,type,basePrice,comment,isDeleted,userId,userName,quantity
   ```
