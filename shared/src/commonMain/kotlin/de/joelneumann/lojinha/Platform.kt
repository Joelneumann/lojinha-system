package de.joelneumann.lojinha

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform