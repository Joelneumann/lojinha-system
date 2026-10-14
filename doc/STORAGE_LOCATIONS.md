# 📁 Data Directory & File Storage Locations

This document outlines where **Lojinha** persists databases, rolling log files, configuration snapshots, and diagnostic bundles across operating systems.

---

## 🗄️ Standard Storage Directory (`.lojinha`)

Lojinha stores all application data inside a dedicated `.lojinha` directory located in the current user's home directory (`~/.lojinha`).

### Platform Paths

| Operating System | Default Data Directory Path |
|---|---|
| **Windows** | `C:\Users\<Username>\.lojinha\` |
| **macOS** | `/Users/<Username>/.lojinha/` |
| **Linux** | `/home/<username>/.lojinha/` |

---

## 📄 File Inventory & Purposes

```
~/.lojinha/
├── lojinha_room.db                    # Primary SQLite Database (Room KMP)
├── lojinha_room.db-wal                # SQLite Write-Ahead Logging buffer (active sessions)
├── lojinha_room.db-shm                # SQLite Shared-Memory index
├── lojinha_room.db.startup_backup     # Safety snapshot created before every startup
├── lojinha_room.db.startup_backup-wal # WAL companion for safety backup
├── lojinha_room.db.startup_backup-shm # SHM companion for safety backup
├── lojinha_room_pre_restore_safety.db # Atomic safety snapshot created before database restore
├── lojinha_room_pre_wipe_safety.db    # Atomic safety snapshot created before factory reset / wipe
├── .lojinha.lock                      # OS kernel-level single-instance file lock
└── logs/
    ├── lojinha.log                    # Current active log file
    ├── lojinha_2026-09-29_12-00-00.log# Archived rolling log file (up to 5x 5MB)
    └── ...
```

---

### 1. Primary Database (`lojinha_room.db`)
* **Format:** Standard SQLite 3 relational database managed by **AndroidX Room (KMP)** using the bundled C SQLite driver.
* **Tables:** `users`, `products`, `transactions`, `billing_lists`, `billing_list_users`, `settings`, `backups`, `admin_notes`.
* **WAL Mode:** Operates in Write-Ahead Logging (`PRAGMA journal_mode=WAL;`). When backing up manually, ensure you copy `lojinha_room.db`, `lojinha_room.db-wal`, and `lojinha_room.db-shm` together, or perform a checkpoint before copying.

### 2. Pre-Startup, Pre-Restore & Pre-Wipe Safety Backups
* **Pre-Startup Snapshot (`lojinha_room.db.startup_backup`):** Before initializing Room and executing any database migrations on application launch, Lojinha automatically creates a point-in-time snapshot of the `.db`, `-wal`, and `-shm` files. If an unexpected power failure or migration error occurs, this file acts as an immediate fallback recovery point.
* **Pre-Restore & Pre-Wipe Snapshots (`lojinha_room_pre_restore_safety.db`, `lojinha_room_pre_wipe_safety.db`):** Captured via SQLite native `VACUUM INTO` immediately before restoring an external backup or performing a factory reset, ensuring zero risk of unrecoverable data loss.

### 3. Single-Instance File Lock (`.lojinha.lock`)
* **Purpose:** Ensures only a single instance of Lojinha can run or start on the machine at any given time.
* **Mechanism:** Handled via OS kernel file locking (`FileChannel.tryLock()`). If a secondary instance is launched (e.g. rapid double-clicking on a slow machine), the OS denies the lock and the secondary process immediately terminates before initializing SQLite or opening a window.
* **Lifecycle:** The file remains permanently on disk (0 bytes) to avoid Windows file-sharing violations; the OS automatically releases the kernel lock if the app terminates or is killed.

### 4. Persistent Rolling Logs (`logs/`)
* **Location:** `~/.lojinha/logs/lojinha.log`
* **Size Cap:** Maximum **5 MB** per log file.
* **Retention Policy:** Retains the **5 most recent** archived log files (total log storage is capped at ~25 MB).
* **Crash Interception:** Uncaught JVM exceptions and system termination signals are caught by [`CrashHandler`](../desktopApp/src/main/kotlin/de/joelneumann/lojinha/util/CrashHandler.kt) and flushed to disk before exit.

### 5. Diagnostics Support Bundle (`.zip`)
* When an admin clicks **Export Diagnostic Bundle** in the Settings tab (or via `GET /api/admin/diagnostics/export`), a timestamped ZIP archive (`lojinha_diagnostic_bundle_<timestamp>.zip`) is generated containing:
  * System metrics (Java version, OS architecture, memory usage, display resolution).
  * The current database file snapshot.
  * All rolling log files.
  * Summary table counts (users, products, transactions).

---

## 💾 Manual Backup & Disaster Recovery

### Manual Backup (File Copy)
To make a quick cold backup while Lojinha is closed:
1. Exit the Lojinha desktop application cleanly (this triggers a database checkpoint and closes the WAL file).
2. Copy the entire `~/.lojinha/` directory to an external drive, USB stick, or secure cloud storage.

### Manual Disaster Recovery
If you need to restore an older database manually:
1. Close Lojinha.
2. In `~/.lojinha/`, remove or rename `lojinha_room.db-wal` and `lojinha_room.db-shm`.
3. Replace `lojinha_room.db` with your backup `.db` file.
4. Launch Lojinha. Room will validate schema version compatibility and apply any pending migrations automatically.
