package de.joelneumann.lojinha.ui.utils

import kotlin.random.Random

actual fun generateUuid(): String {
    val hexDigits = "0123456789abcdef"
    fun randomHex(len: Int) = CharArray(len) { hexDigits[Random.nextInt(16)] }.concatToString()
    val r1 = randomHex(8)
    val r2 = randomHex(4)
    val r3 = "4" + randomHex(3)
    val r4 = hexDigits[8 + Random.nextInt(4)] + randomHex(3)
    val r5 = randomHex(12)
    return "$r1-$r2-$r3-$r4-$r5"
}
