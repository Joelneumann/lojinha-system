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
}
