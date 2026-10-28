package de.joelneumann.lojinha.ui.i18n

interface AppStrings {
    val appTitle: String
    val searchUserPlaceholder: String
    val adminLoginBtn: String
    val selectUserTitle: String
    val enterPinTitle: String
    val enterPinPrompt: String
    val pinIncorrect: String
    val confirm: String
    val cancel: String
    val logout: String
    val shopping: String
    val products: String
    val searchProductPlaceholder: String
    val cart: String
    val cartIsEmpty: String
    val balance: String
    val balanceAfter: String
    val total: String
    val completePurchase: String
    val confirmPurchaseTitle: String
    fun confirmPurchaseMsg(amount: String): String
    val purchaseOverviewTitle: String
    val goToTransactions: String
    fun logoutWithTimer(seconds: Int): String
    val previousBalance: String
    val newBalance: String
    val weightDialogTitle: String
    fun weightDialogMsg(productName: String): String
    val weightInputHint: String
    val weightTooltip: String
    val invalidWeightFormat: String
    val unitDialogTitle: String
    fun unitDialogMsg(productName: String): String
    val unitPieceBadge: String
    val invalidQuantityFormat: String
    fun pieceUnitSuffix(quantity: Long): String
    val history: String
    val account: String
    val historyFilterPlaceholder: String
    val historyFilterAll: String
    val historyTypePurchase: String
    val historyTypeDeposit: String
    val historyTypeDebit: String
    val historyTypeCustomExpense: String
    val historyTypeCustomIncome: String
    val historyTypeExpenses: String
    val historyTypeCancellation: String
    val historyTypeCorrection: String
    fun cancellationNote(type: String, date: String): String
    val continueShopping: String
    val lowBalanceWarningTitle: String
    val lowBalanceWarningMsg: String
    val negativeBalanceWarningTitle: String
    val negativeBalanceWarningMsg: String
    val userSettings: String
    val userSettingsTitle: String
    val setPin: String
    val preferredLanguage: String
    val secondaryCurrency: String
    val secondaryCurrencyNone: String
    val secondaryCurrencyUsd: String
    val secondaryCurrencyEur: String
    val assignedBarcodeId: String
    val save: String
    val adminPanel: String
    val tabProducts: String
    val tabUsers: String
    val tabTransactions: String
    val tabSettings: String
    val adminPasswordPrompt: String
    val adminPasswordIncorrect: String
    val addProduct: String
    val editProduct: String
    val addUser: String
    val editUser: String
    val depositWithdraw: String
    val softDelete: String
    val hardDelete: String
    val deleteConfirm: String
    val reversalBtn: String
    val reversalConfirmTitle: String
    val reversalConfirmMsg: String
    val inactivityWarningTitle: String
    val inactivityWarningMsg: String
    val inactivityWarningWithCartMsg: String
    val stayLoggedIn: String
    val stock: String
    val unitPiece: String
    val unitWeight: String
    val basePrice: String
    val markupPercent: String
    val barcodeCode: String
    val barcodeDesc: String
    val abandonCartTitle: String
    val abandonCartMsg: String
    val discardAndLogout: String
    val keepShopping: String

    // --- Extended i18n Properties ---
    val scanOrTypeSearch: String
    fun noProductsFoundMatching(query: String): String
    val noTransactionsFound: String
    val unsavedChangesTitle: String
    fun unsavedChangesMsg(targetName: String): String
    val discardChangesTitle: String
    val discardChangesMsg: String
    val discard: String
    val discardAndSwitch: String
    val keepEditing: String
    fun productsCountText(count: Int, total: Int? = null): String
    val noMatchingProducts: String
    val disabledProducts: String
    val showDisabledProducts: String
    val hideDisabledProducts: String
    fun accountsCountText(count: Int, total: Int? = null): String
    val noMatchingAccounts: String
    val deactivatedUsers: String
    val showDeactivatedUsers: String
    val hideDeactivatedUsers: String
    val deletedUsers: String
    val showDeletedUsers: String
    val hideDeletedUsers: String
    val auditLedgerTitle: String
    val ok: String
    fun transactionsCountText(count: Int, total: Int? = null): String
    val searchProductAdminPlaceholder: String
    val sortBy: String
    val sortNameAsc: String
    val sortNameDesc: String
    val sortStockAsc: String
    val sortStockDesc: String
    val sortPriceAsc: String
    val sortPriceDesc: String
    val sortTypePiece: String
    val sortTypeWeight: String
    val searchAccountAdminPlaceholder: String
    val searchTransactionAdminPlaceholder: String
    val systemAdminSettingsTitle: String
    val unsavedEditsBadge: String
    val revertChanges: String
    val saveSettings: String
    val saved: String
    val adminMasterPasswordTitle: String
    val newPasswordLabel: String
    val newPasswordPlaceholder: String
    val confirmNewPasswordLabel: String
    val confirmNewPasswordPlaceholder: String
    val passwordsDoNotMatch: String
    val passwordsMatch: String
    val productPricingRulesTitle: String
    val globalProductMarkupLabel: String
    val globalProductMarkupPlaceholder: String
    val balanceWarningsCardTitle: String
    val balanceWarningsCardSubtitle: String
    val balanceWarningYellowLabel: String
    val balanceWarningYellowPlaceholder: String
    val balanceWarningYellowHelper: String
    val balanceWarningRedLabel: String
    val balanceWarningRedPlaceholder: String
    val balanceWarningRedHelper: String
    val invalidThresholdAmountError: String
    val yellowMustBeGreaterThanRedError: String
    val currencyExchangeRatesTitle: String
    val usdRateLabel: String
    val usdRatePlaceholder: String
    val eurRateLabel: String
    val eurRatePlaceholder: String
    val kioskSystemTimersTitle: String
    val inactivityTimeoutLabel: String
    val inactivityTimeoutPlaceholder: String
    val inactivityTimeoutMinError: String
    val oneDriveIntegrationTitle: String
    val connected: String
    val disconnected: String
    val azureClientIdLabel: String
    val azureClientIdPlaceholder: String
    val connectOneDrive: String
    val disconnectOneDrive: String
    val connectOneDriveDesc: String
    val configuredBackupRoutinesTitle: String
    fun routinesCountBadge(count: Int): String
    val createRoutine: String
    val noRoutinesYet: String
    val noRoutinesYetSub: String
    val never: String
    val onRealtimeEvent: String
    fun targetPathLabel(path: String): String
    fun lastBackupNextDueLabel(last: String, next: String): String
    val runNow: String
    val edit: String
    val delete: String
    val databaseRestoreResetTitle: String
    val selectDbBackupFile: String
    val restoreDatabaseBtn: String
    val factoryResetBtn: String
    val importCsvDataTitle: String
    val importCsvDataDesc: String
    val selectProductsCsvFile: String
    val importProductsCsvBtn: String
    val selectUsersCsvFile: String
    val importUsersCsvBtn: String
    val enableProduct: String
    val confirmProductActivationTitle: String
    fun confirmProductActivationMsg(productName: String): String
    val yesEnableProduct: String
    val activateUser: String
    val confirmUserActivationTitle: String
    fun confirmUserActivationMsg(userName: String): String
    val yesActivateUser: String
    val restoreUserBtn: String
    val confirmUserRestorationTitle: String
    fun confirmUserRestorationMsg(userName: String): String
    val yesRestoreUser: String
    val balanceChangeLabel: String
    val adjustBalanceBtn: String
    val confirmBalanceAdjustmentTitle: String
    fun confirmBalanceAdjustmentMsg(isDeposit: Boolean, formattedAmount: String, userName: String): String
    val confirmUserStatusChangeTitle: String
    fun confirmUserStatusChangeMsg(isDeactivating: Boolean, userName: String): String
    val confirmDeleteUserTitle: String
    fun confirmDeleteUserMsg(userName: String): String
    val yesDeleteUser: String
    val stornoEverything: String
    val applyStornoChanges: String
    val stornoTransactionBtn: String
    val approvalStornoEverythingTitle: String
    val approveCompleteStorno: String
    val approvalStornoSelectedTitle: String
    val approveSelectedItemChanges: String
    fun approvalStornoTxTitle(type: String): String
    val approveStorno: String
    fun confirmStornoNonPurchaseBody(type: String, user: String, balanceAdjustment: String, currentNote: String): String
    fun confirmStornoEntirePurchaseBody(user: String, refundAmount: String, itemCount: Int): String
    
    // System Notes
    fun sysNoteCompleteStorno(date: String, amount: String): String
    fun sysNotePartialStorno(date: String, amount: String): String
    fun sysNoteNonPurchaseStorno(type: String, date: String, amount: String): String

    fun originalOrderTotalLabel(amount: String): String
    fun newOrderTotalLabel(amount: String): String
    val headerProduct: String
    val headerUnitPrice: String
    val headerOriginal: String
    val headerAdjustedQtyWeight: String
    val headerQtyWeight: String
    val headerLineTotal: String
    val editStornoItemsHeader: String
    val correctPurchaseBtn: String
    fun correctPurchaseDialogTitle(userName: String): String
    val headerCurrent: String
    val headerCorrection: String
    fun currentOrderTotalLabel(amount: String): String
    fun refundAdjustmentLabel(amount: String): String
    fun chargeAdjustmentLabel(amount: String): String
    val noChangesLabel: String
    val applyCorrectionBtn: String
    val stornoEntirePurchaseBtn: String
    val badgeReversedCanceled: String
    val badgeCorrected: String
    val headerModifier: String
    val headerPrevious: String
    val headerNew: String
    val headerAdjustment: String
    fun updatedItemsHeader(count: Int): String
    val amountBrlLabel: String
    val amountPlaceholder: String
    val noteReasonLabel: String
    val noteReasonPlaceholder: String
    val depositActionBtn: String
    val withdrawActionBtn: String
    val initialPinOptional: String
    val noPinPlaceholder: String
    val newPinOptionalPlaceholder: String
    val barcodeSymbolLabel: String
    val barcodeSymbolPlaceholder: String
    val barcodeNumberIdLabel: String
    val barcodeNumberIdPlaceholder: String
    val profileAvatarColorTitle: String
    val initialsLabel: String
    val emojiLabel: String
    val avatarColorTitle: String
    val basePriceBrlLabel: String
    val zeroPricePlaceholder: String
    val unitTypeLabel: String
    val stockQtyUnitsLabel: String
    val stockQtyKgLabel: String
    val stockUnitsPlaceholder: String
    val stockKgPlaceholder: String
    val customMarkupOptionalLabel: String
    val standardPlaceholder: String
    fun associatedBarcodesTitle(count: Int): String
    val barcodeCodePlaceholder: String
    val descriptionOptionalPlaceholder: String
    val addBtn: String
    val createNewBackupRoutineTitle: String
    fun editRoutineTitle(name: String): String
    val editingRoutineBannerText: String
    val routineNameLabel: String
    val routineNamePlaceholder: String
    val destinationTargetLabel: String
    val localFolderOption: String
    val oneDriveOption: String
    val oneDriveRemotePathLabel: String
    val hostSaveLocationLabel: String
    val browseBtn: String
    val backupFileFormatLabel: String
    val dbFileOption: String
    val csvFilesOption: String
    val bothOption: String
    val fileOverwriteStrategyLabel: String
    val timestampedNewFileOption: String
    val overwriteSingleFileOption: String
    val scheduleTypeLabel: String
    val fixedTimeDailyOption: String
    val recurringIntervalOption: String
    val onRealtimeChangeOption: String
    val dailyFixedTimeLabel: String
    val hoursLabel: String
    val minutesLabel: String
    val realtimeBackupDesc: String
    val routineNameCannotBeEmpty: String
    val specifySaveLocation: String
    val invalidTimeFormat: String
    val invalidIntervalFormat: String
    val saveRoutineBtn: String
    val deleteBackupRoutineConfirmTitle: String
    val deleteBackupRoutineConfirmMsg: String
    val deleteRoutineBtn: String
    val backupNowLocalBtn: String
    val backupNowLocalTitle: String
    val backupNowLocalDescription: String
    val backupNowActionBtn: String
    val backingUpInProgress: String
    val pleaseSelectDestinationFolder: String
    fun statusBackupNowSuccess(count: Int, path: String): String
    val activateBackupRoutineTitle: String
    val deactivateBackupRoutineTitle: String
    fun activateBackupRoutineMsg(name: String): String
    fun deactivateBackupRoutineMsg(name: String): String
    fun yesAction(actionText: String): String
    val criticalWarning: String
    val backupFileDetails: String
    fun filenameLabel(name: String): String
    fun filePathLabel(path: String): String
    fun routineLabel(name: String): String
    fun routineFormatLabel(format: String): String
    fun saveLocationLabel(location: String): String
    val typeRestoreToConfirmLabel: String
    val proceedToAuthorization: String
    val restoreDatabaseNowBtn: String
    val backBtn: String
    val permanentDataWipeWarning: String
    val permanentDataWipeBulletPoints: String
    val thisActionCannotBeUndone: String
    val typeWipeToConfirmLabel: String
    val proceedToApproval: String
    val wipeAllSystemDataBtn: String
    fun csvFileLabel(name: String): String
    fun totalRowsFoundLabel(count: Int): String
    fun newRecordsToAddLabel(count: Int): String
    fun existingRecordsToUpdateLabel(count: Int): String
    val warningsNotesTitle: String
    val executeCsvImportBtn: String
    val connectOneDriveTitle: String
    val openingBrowserSignIn: String
    val completeSignInPrompt: String
    val disconnectOneDriveTitle: String
    fun disconnectOneDriveMsg(email: String): String
    val scheduledCloudBackupsPaused: String
    val oneDriveConnectedTitle: String
    fun successfullyConnectedAs(email: String): String
    val oneDriveAutoSyncDesc: String
    val greatBtn: String
    fun initialsWithVal(initials: String): String
    fun emojiWithVal(emoji: String): String
    val avatarBackgroundColorTitle: String
    val statusSettingsUpdated: String
    fun statusRoutineSaved(name: String): String
    fun statusRoutineRemoved(name: String): String
    fun statusRoutineStateChanged(name: String, enabled: Boolean): String
    fun statusRoutineTriggered(name: String): String
    fun statusImportExecuted(name: String): String
    fun statusRestoreRequested(name: String): String
    val statusFactoryResetCompleted: String
    fun statusConnectedToOneDrive(email: String): String
    val adminConsoleWebTitle: String
    val adminConsoleWebDesc: String
    val adminPasswordLabelWeb: String
    val loginBtn: String
    val invalidAdminPassword: String

    fun oneDriveConnectedSuccessMsg(email: String): String
    val oneDriveConnectedDesc: String
    val disabled: String
    val deactivated: String
    val deleted: String
    val productNameLabel: String
    val productNamePlaceholder: String
    val adjustItemQuantitiesHeader: String
    fun purchasedItemsHeader(count: Int): String
    val userNameLabel: String
    val userNamePlaceholder: String
    val resetPin: String
    val removePin: String
    val noPinSet: String
    val hide: String
    val pinPlaceholder: String
    val enterNewPinPlaceholder: String
    val confirmPinPlaceholder: String
    val pinMismatchError: String
    val pinWillBeRemovedNotice: String
    val pinRemovedWarningDesc: String
    val noBarcodeAssigned: String

    val step1DbRestoreTitle: String
    val step2SecurityTitle: String
    val criticalWarningHeader: String
    val dbRestoreWarning1: String
    val dbRestoreWarning2: String
    val backupFileDetailsHeader: String
    val dbRestoreAuthMsg: String
    val adminMasterPasswordLabel: String
    val typeRestoreConfirmLabel: String
    val proceedToAuthBtn: String
    val incorrectAdminPassword: String
    val typeRestoreExactly: String
    val step1WipeDataTitle: String
    val permanentWipeWarningHeader: String
    val wipeWarning1: String
    val wipeWarningDetails: String
    val actionCannotBeUndone: String
    val wipeAuthMsg: String
    val typeWipeConfirmLabel: String
    val typeWipeExactly: String
    val preserveBackupRoutinesLabel: String
    val preserveBackupRoutinesDesc: String
    val preserveAdminPasswordLabel: String
    val preserveAdminPasswordDesc: String
    val adminPasswordWillResetNotice: String
    val adminPasswordWillBeKeptNotice: String
    val keepCurrentRoutinesOnRestoreLabel: String
    val keepCurrentRoutinesOnRestoreDesc: String
    val keepCurrentAdminPasswordOnRestoreLabel: String
    val keepCurrentAdminPasswordOnRestoreDesc: String
    fun step1CsvImportTitle(importType: String): String
    val step2AdminApprovalTitle: String
    val csvUserSecurityNote: String
    fun strippedBarcodesNotice(count: Int): String
    val warningsNotesHeader: String
    val csvImportAuthMsg: String
    val proceedToApprovalBtn: String
    val oneDriveAuthOpeningBrowser: String
    val oneDriveAuthCompleteInstruction: String
    fun disconnectOneDriveConfirmMsg(account: String): String
    val disconnectOneDriveWarningMsg: String
    val disconnectBtn: String

    // --- Product Admin Page Confirmations & Actions ---
    val adjustStockBtn: String
    val stockDeltaPieceLabel: String
    val stockDeltaWeightLabel: String
    val stockDeltaPiecePlaceholder: String
    val stockDeltaWeightPlaceholder: String
    val confirmStockAdjustmentTitle: String
    fun confirmStockAdjustmentMsg(isAddition: Boolean, formattedDelta: String, productName: String): String
    val confirmProductStatusChangeTitle: String
    fun confirmProductStatusChangeMsg(isDisabling: Boolean, productName: String): String
    val confirmDeleteProductTitle: String
    fun confirmDeleteProductMsg(productName: String): String
    val yesDeleteProduct: String
    val saveChanges: String
    val disable: String
    val enable: String
    val deactivate: String
    val activate: String
    fun barcodeConflictAlreadyAssigned(code: String, productName: String): String
    fun barcodeConflictContainsAssigned(productName: String): String

    // --- Settings OneDrive & Backup Solution Badges ---
    fun oneDriveAccountLabel(email: String): String
    val microsoftAccount: String
    val badgeOverwrite: String
    val badgeNewFile: String
    val badgeRealtime: String
    fun badgeTimed(time: String): String
    fun badgeInterval(hours: Int, minutes: Int): String

    // --- Admin User Actions & Warnings ---
    val depositViaAdmin: String
    val debitViaAdmin: String
    fun barcodeConflictAlreadyAssignedToUser(code: String, userName: String): String
    fun userBarcodeIncompleteWarning(isSymbolFilled: Boolean): String
    val restoredUserBarcodeCollisionNotice: String

    // --- Custom Expense & Custom Income Modals ---
    val customExpense: String
    val customExpenseDialogTitle: String
    val customExpenseDescriptionLabel: String
    val customExpenseDescriptionPlaceholder: String
    val customExpenseConfirmBtn: String
    val customDepositConfirmBtn: String
    val customIncome: String
    val customIncomeDialogTitle: String
    val customIncomeDescriptionLabel: String
    val customIncomeDescriptionPlaceholder: String
    val customIncomeConfirmBtn: String

    // --- Bulk Billing ---
    val tabBulkBilling: String
    val bulkBillingTitle: String
    val createNewList: String
    val editList: String
    val selectUsersTitle: String
    val billingListCommentLabel: String
    val billingListCommentPlaceholder: String
    val listTypeFixed: String
    val listTypeVariable: String
    val executeChargesBtn: String
    fun confirmExecuteChargesMsg(count: Int, total: String): String
    val noListsCreated: String
    val listNameLabel: String
    val listNamePlaceholder: String
    val listTypeLabel: String
    val addUserToList: String
    val searchUserToAdd: String
    val noUsersInList: String
    val noUsersFound: String
    val noUserOrProductFound: String
    val priceCheckLabel: String
    val quantity: String
    val amount: String
    val deleteListTitle: String
    val deleteListMsg: String

    val supportContactTitle: String
    val supportEmailLabel: String
    val supportEmailPlaceholder: String
    val unlockSupportEmailTitle: String
    val unlockSupportEmailMessage: String
    val unlockSupportEmailConfirm: String
    val contactSupportBtn: String
    val githubRepoBtn: String
    val diagnosticsSectionTitle: String
    val exportSupportBundleBtn: String
    val exportAndEmailSupportBundleBtn: String
    val openLogFolderBtn: String
    val logFolderLabel: String
    fun logSizeLabel(size: String): String
    fun supportBundleCreatedSuccess(path: String): String
    fun supportBundleEmailOpenedNotice(path: String): String

    val fieldDescription: String
    val referenceLabel: String

    val sellingPriceLabel: String
    val markupCustomSuffix: String
    val markupStandardSuffix: String
    val invalidMarkupError: String
    val invalidPriceError: String
    val invalidStockError: String
    val invalidExchangeRateError: String
    val updateRatesFromApiButton: String
    val updatingRatesButton: String
    val autoUpdateRatesOnStartupLabel: String
    fun ratesLastUpdated(timestamp: String): String
    val ratesNeverUpdated: String
    fun statusRatesUpdatedSuccess(usd: Double, eur: Double, provider: String): String
    val errorFailedToFetchRates: String
    val fatalImportErrorsHeader: String
    val lastExecutionTimeLabel: String

    val remoteAdminTitle: String
    val remoteAdminSubtitle: String
    val remoteAdminFirewallNotice: String
    val copyUrlBtn: String
    val urlCopiedToast: String
    val kioskActionsTitle: String
    fun appVersionLabel(version: String): String
    val exitKioskBtn: String
    val exitKioskConfirmTitle: String
    val exitKioskConfirmMessage: String
    val exitConfirmBtn: String
    val firstRunTitle: String
    val firstRunMessage: String
    val firstRunAdminLoginBtn: String
    val errorTitle: String

    // Navigation & Destination Targets
    val mainScreen: String

    // Pagination
    fun paginationShowing(start: Int, end: Int, total: Int): String
    val paginationZeroItems: String
    val perPageLabel: String
    fun pageOf(current: Int, total: Int): String
    val firstPage: String
    val previousPage: String
    val nextPage: String
    val lastPage: String

    // Accessibility & Inputs
    val clear: String
    val togglePasswordVisibility: String
    val togglePinVisibility: String
    fun userBarcodeIdBadge(id: String): String

    // ViewModel Error & Status Messages
    val errCorrectionFailedOrUnchanged: String
    val errTransactionAlreadyCanceled: String
    val errStornoFailed: String
    val errUnexpectedCorrection: String
    val errUnexpectedCancellation: String

    val errFailedToSaveUser: String
    val errFailedToUpdateUserStatus: String
    val errFailedToDeleteUser: String
    val errFailedToRestoreUser: String

    val errFailedToSaveProduct: String
    val errFailedToUpdateProductStatus: String
    val errFailedToDeleteProduct: String
    val errFailedToAdjustStock: String

    val errNoValidChargesToExecute: String
    val errFailedToExecuteBatchCharges: String
    fun errFailedToSaveBillingList(error: String): String
    fun errFailedToDeleteBillingList(error: String): String

    val statusDiagnosticBundleExportedWithEmail: String
    val statusDiagnosticBundleExported: String
    val statusSystemSettingsUpdated: String
    fun statusCsvImportSuccess(count: Int, type: String, added: Int, updated: Int): String
    val csvTypeProducts: String
    val csvTypeUsers: String

    val errFailedToCompletePurchase: String

    // Stock Limits & Feedback
    fun errProductOutOfStock(productName: String): String
    fun errProductMaxStockReached(productName: String, maxQuantity: String): String
    fun errWeightExceedsStock(maxQuantity: String): String
    fun errQuantityExceedsStock(maxQuantity: String): String
    fun errProductNotFound(barcode: String): String
    fun availableStockLabel(quantity: String): String

    // Desktop & Formatting
    val appWindowTitle: String
    val monthNames: List<String>

    // CSV Import Validation
    val csvEmptyError: String
    val csvMissingHeadersError: String
    val csvMissingNameHeaderError: String

    // Printable Transactions & PDF Export
    val exportPdf: String
    val printTransactions: String
    val exportTransactionsTitle: String
    val statementLanguage: String
    val destinationFolder: String
    val selectDestinationFolder: String
    val filterPeriod: String
    val periodLast30Days: String
    val periodLast60Days: String
    val periodLast90Days: String
    val periodCurrentMonth: String
    val periodAllTime: String
    val periodCustom: String
    val dateFrom: String
    val dateTo: String
    fun transactionsCountPreview(count: Int): String
    val initialBalance: String
    val finalBalance: String
    val totalDeposits: String
    val totalPurchases: String
    val periodNetLabel: String
    val pdfExportSuccess: String
    val openPdf: String
    val revealInFolder: String
    val statementPdfTitle: String
    val statementPdfAccountStatus: String
    val statementPdfActive: String
    val statementPdfInactive: String
    val statementPdfDisclaimer: String
    fun statementPdfFooterGenerated(date: String): String
    val statementPdfColDateTime: String
    val statementPdfColType: String
    val statementPdfColDescription: String
    val statementPdfColAmount: String
    val statementPdfColBalance: String
    val errInvalidDateRange: String
    val errNoTransactionsToExport: String
    val errPdfExportFailed: String

    // Admin Notes & Suggestions
    val tabNotes: String
    fun notesCountText(count: Int): String
    val suggestProductOrNoteBtn: String
    val noteDialogTitle: String
    fun noteDialogSubmittingAs(name: String): String
    val noteDialogPlaceholder: String
    val noteDialogEmptyError: String
    val noteDialogSubmit: String
    val noteDialogSuccess: String
    val noNotesTitle: String
    val noNotesMsg: String
    val deleteNote: String
    val confirmDeleteNoteTitle: String
    val confirmDeleteNoteMsg: String
    val searchNotesPlaceholder: String
}

