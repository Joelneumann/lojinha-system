package de.joelneumann.lojinha.ui.i18n

import androidx.compose.runtime.Composable
import de.joelneumann.lojinha.domain.model.Language

object I18n {
    fun get(language: Language = LanguageManager.currentLanguage): AppStrings = when (language) {
        Language.DE -> GermanStrings
        Language.EN -> EnglishStrings
        Language.BR -> PortugueseStrings
    }

    val current: AppStrings
        @Composable
        get() = get(LanguageManager.currentLanguage)
}
