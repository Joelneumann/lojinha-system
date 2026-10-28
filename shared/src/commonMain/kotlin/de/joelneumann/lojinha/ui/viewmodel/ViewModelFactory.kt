package de.joelneumann.lojinha.ui.viewmodel

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.joelneumann.lojinha.domain.repository.*

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
        userRepository: UserRepository,
        productRepository: ProductRepository? = null
    ): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            UserSelectionViewModel(userRepository, productRepository)
        }
    }

    fun createUserSessionViewModelFactory(
        user: de.joelneumann.lojinha.domain.model.User,
        settingsRepository: SettingsRepository,
        onLogoutRequest: () -> Unit,
        onInactivityTimeout: (() -> Unit)? = null,
        initialSettings: de.joelneumann.lojinha.domain.model.SystemSettings? = null
    ): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            UserSessionViewModel(
                user = user,
                settingsRepository = settingsRepository,
                onLogoutRequest = onLogoutRequest,
                onInactivityTimeout = onInactivityTimeout,
                initialSettings = initialSettings
            )
        }
    }

    fun createShoppingViewModelFactory(
        productRepository: ProductRepository,
        userRepository: UserRepository,
        transactionRepository: TransactionRepository,
        adminNoteRepository: AdminNoteRepository? = null
    ): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            ShoppingViewModel(productRepository, userRepository, transactionRepository, adminNoteRepository)
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
}
