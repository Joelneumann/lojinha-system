package de.joelneumann.lojinha.ui.utils

import java.util.UUID

actual fun generateUuid(): String = UUID.randomUUID().toString()
