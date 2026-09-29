# ☁️ Microsoft OneDrive Cloud Backup Setup Guide

This guide walks you through setting up Microsoft OneDrive cloud backups in **Lojinha**. 

Lojinha uses the **Microsoft Graph API** with **OAuth 2.0 PKCE (Proof Key for Code Exchange)**. This enables secure, 1-click browser login without requiring hardcoded client secrets or passwords.

---

## 📋 Prerequisites

* A Microsoft account (Personal Outlook/Hotmail, Microsoft 365, or Work/School account).
* Access to the [Microsoft Entra admin center](https://entra.microsoft.com) or [Azure Portal](https://portal.azure.com).

---

## 🛠️ Step-by-Step Setup in Microsoft Azure

### Step 1: Register a New Application

1. Sign in to the [Azure Portal - App Registrations](https://portal.azure.com/#view/Microsoft_AAD_RegisteredApps/ApplicationsListBlade) (or [Microsoft Entra admin center](https://entra.microsoft.com/#view/Microsoft_AAD_IAM/ActiveDirectoryMenuBlade/~/RegisteredApps)).
2. Click **+ New registration** at the top.
3. Configure the registration:
   * **Name:** Enter a recognizable name (e.g., `Lojinha POS Backup`).
   * **Supported account types:** Select:
     > **Accounts in any organizational directory (Any Microsoft Entra ID tenant - Multitenant) and personal Microsoft accounts (e.g. Skype, Xbox)**
   * **Redirect URI (optional during initial screen):**
     * Select platform: **Mobile and desktop applications** (or Public client / native).
     * Enter URI: `http://localhost:8989/callback`
4. Click **Register** at the bottom.

---

### Step 2: Configure Redirect URI & Public Client Flow

If you did not add the redirect URI in Step 1:
1. In your app registration, click **Authentication** in the left sidebar.
2. Under **Platform configurations**, click **+ Add a platform** $\rightarrow$ select **Mobile and desktop applications**.
3. Check the custom redirect URI box and enter:
   ```
   http://localhost:8989/callback
   ```
4. Scroll down to **Advanced settings** $\rightarrow$ **Allow public client flows**:
   * Set **Enable the following mobile and desktop flows** to **Yes**.
5. Click **Save** at the bottom.

> [!NOTE]
> Lojinha uses PKCE authentication. **Do NOT generate a client secret** under *Certificates & secrets*. Public client apps do not need or use client secrets.

---

### Step 3: Configure API Permissions

1. Click **API permissions** in the left sidebar.
2. Click **+ Add a permission** $\rightarrow$ select **Microsoft Graph** $\rightarrow$ **Delegated permissions**.
3. Search for and check the following permissions:
   * `Files.ReadWrite` — Allows Lojinha to create and upload backup files to your OneDrive.
   * `User.Read` — Allows Lojinha to read your account display name and email address.
   * `offline_access` — Allows Lojinha to refresh access tokens so automated background backups continue running without requiring repeated logins.
4. Click **Add permissions**.

---

### Step 4: Copy Your Application (Client) ID

1. Click **Overview** in the left sidebar.
2. Locate and copy the **Application (client) ID** (a UUID string formatted like `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`).

---

## 🖥️ Connecting in Lojinha

1. Launch Lojinha on your desktop terminal.
2. Click **Admin Login** on the Main Screen and enter your admin password (default: `admin`).
3. Navigate to the **Settings** tab (or press `F5`).
4. Scroll to the **Microsoft OneDrive Integration** section.
5. Paste your copied **Application (client) ID** into the **Azure Application (Client) ID** field.
6. Click **Connect OneDrive**:
   * Lojinha temporarily starts a secure loopback listener on port `8989`.
   * Your default web browser will automatically open Microsoft's official login screen.
7. Sign in to your Microsoft account and click **Accept** to authorize the permissions.
8. Once authorized, the browser will display:
   > 🟢 **Connected to OneDrive!**  
   > *You can now close this browser tab and return to Lojinha.*
9. Close the browser tab. In Lojinha, the status badge will change to **Connected** showing your Microsoft account name and email.

---

## ⏰ Configuring Automated OneDrive Backup Routines

Once connected:
1. Under **Configured Backup Routines** on the Settings tab, click **+ Create Routine**.
2. Fill in the routine details:
   * **Name:** e.g., `Daily Cloud Backup`.
   * **Destination Type:** Select **OneDrive**.
   * **File Type:** Choose **Database (.db)**, **CSV Files**, or **Both**.
   * **Write Mode:** Choose **Create New File** (timestamped versions) or **Overwrite Latest**.
   * **Schedule:**
     * **Timed:** Executes daily at a fixed time (e.g., `02:00`).
     * **Interval:** Executes every $X$ hours or minutes.
     * **On Data Change:** Executes automatically whenever purchases, deposits, or catalog edits occur.
3. Click **Save Routine**.
4. You can click **Run Now** at any time to verify immediate upload to the `/LojinhaBackups` folder in your OneDrive.

---

## ❓ Troubleshooting & FAQs

* **Port 8989 in use:** If an error occurs stating port 8989 is occupied, ensure no other application is bound to `8989` while logging in.
* **Consent prompt not appearing:** Ensure you selected *Accounts in any organizational directory and personal Microsoft accounts* during App Registration.
* **Disconnecting:** You can click **Disconnect** at any time to revoke local tokens and remove the account association.
