package de.joelneumann.lojinha.ui.viewmodel

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.joelneumann.lojinha.data.service.OneDriveBackupService
import de.joelneumann.lojinha.domain.model.CsvImportResult
import de.joelneumann.lojinha.domain.repository.*
import de.joelneumann.lojinha.ui.utils.PlatformFile
import de.joelneumann.lojinha.ui.viewmodel.admin.*

object LojinhaViewModelFactory {

    fun createAppViewModelFactory(
        userRepository: UserRepository,
        settingsRepository: SettingsRepository
    ): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            AppViewModel(userRepository, settingsRepository)
        }
    }

    fun createUserSelectionViewModelFactory(
        userRepository: UserRepository
    ): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            UserSelectionViewModel(userRepository)
        }
    }

    fun createUserSessionViewModelFactory(
        user: de.joelneumann.lojinha.domain.model.User,
        settingsRepository: SettingsRepository,
        onLogoutRequest: () -> Unit
    ): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            UserSessionViewModel(user, settingsRepository, onLogoutRequest)
        }
    }

    fun createShoppingViewModelFactory(
        productRepository: ProductRepository,
        userRepository: UserRepository,
        transactionRepository: TransactionRepository
    ): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            ShoppingViewModel(productRepository, userRepository, transactionRepository)
        }
    }

    fun createTransactionHistoryViewModelFactory(
        transactionRepository: TransactionRepository,
        userRepository: UserRepository
    ): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            TransactionHistoryViewModel(transactionRepository, userRepository)
        }
    }

    fun createAdminProductsViewModelFactory(
        productRepository: ProductRepository,
        settingsRepository: SettingsRepository
    ): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            AdminProductsViewModel(productRepository, settingsRepository)
        }
    }

    fun createAdminUsersViewModelFactory(
        userRepository: UserRepository,
        transactionRepository: TransactionRepository
    ): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            AdminUsersViewModel(userRepository, transactionRepository)
        }
    }

    fun createAdminTransactionsViewModelFactory(
        transactionRepository: TransactionRepository,
        userRepository: UserRepository,
        productRepository: ProductRepository
    ): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            AdminTransactionsViewModel(transactionRepository, userRepository, productRepository)
        }
    }

    fun createAdminSettingsViewModelFactory(
        settingsRepository: SettingsRepository,
        backupRepository: BackupRepository,
        onRunRoutineNow: (de.joelneumann.lojinha.domain.model.BackupRoutine) -> Unit,
        oneDriveBackupService: OneDriveBackupService,
        onPreviewCsvImport: (suspend (PlatformFile, String) -> CsvImportResult)? = null,
        onExecuteCsvImport: (suspend (PlatformFile, String) -> CsvImportResult)? = null,
        onExecuteDbRestore: (suspend (PlatformFile) -> Unit)? = null,
        onExecuteWipeData: (suspend () -> Unit)? = null
    ): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            AdminSettingsViewModel(
                settingsRepository = settingsRepository,
                backupRepository = backupRepository,
                onRunRoutineNow = onRunRoutineNow,
                oneDriveBackupService = oneDriveBackupService,
                onPreviewCsvImport = onPreviewCsvImport,
                onExecuteCsvImport = onExecuteCsvImport,
                onExecuteDbRestore = onExecuteDbRestore,
                onExecuteWipeData = onExecuteWipeData
            )
        }
    }
}
