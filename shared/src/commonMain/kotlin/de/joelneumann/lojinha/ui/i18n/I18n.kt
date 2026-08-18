package de.joelneumann.lojinha.ui.i18n

import de.joelneumann.lojinha.domain.model.Language

object I18n {
    fun get(language: Language): AppStrings = when (language) {
        Language.DE -> GermanStrings
        Language.EN -> EnglishStrings
        Language.BR -> PortugueseStrings
    }
}
