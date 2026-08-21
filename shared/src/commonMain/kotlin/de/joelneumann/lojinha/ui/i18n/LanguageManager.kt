package de.joelneumann.lojinha.ui.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.joelneumann.lojinha.domain.model.Language

object LanguageManager {
    var currentLanguage: Language by mutableStateOf(Language.BR)
        private set

    fun setLanguage(language: Language) {
        currentLanguage = language
    }

    fun resetToDefault() {
        currentLanguage = Language.BR
    }
}
