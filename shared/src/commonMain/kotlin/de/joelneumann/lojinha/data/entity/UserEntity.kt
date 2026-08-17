package de.joelneumann.lojinha.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import de.joelneumann.lojinha.domain.model.Language
import de.joelneumann.lojinha.domain.model.SecondaryCurrency
import de.joelneumann.lojinha.domain.model.User

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val name: String,
    val balance: Long,
    val language: String,
    val secondaryCurrency: String,
    val pin: String?,
    val userBarcode: String?,
    val userBarcodeNumber: String?,
    val isActive: Boolean
) {
    fun toDomain(): User = User(
        id = id,
        name = name,
        balance = balance,
        language = Language.fromCode(language),
        secondaryCurrency = try { SecondaryCurrency.valueOf(secondaryCurrency) } catch (e: Exception) { SecondaryCurrency.NONE },
        pin = pin,
        userBarcode = userBarcode,
        userBarcodeNumber = userBarcodeNumber,
        isActive = isActive
    )

    companion object {
        fun fromDomain(user: User): UserEntity = UserEntity(
            id = user.id,
            name = user.name,
            balance = user.balance,
            language = user.language.code,
            secondaryCurrency = user.secondaryCurrency.name,
            pin = user.pin,
            userBarcode = user.userBarcode,
            userBarcodeNumber = user.userBarcodeNumber,
            isActive = user.isActive
        )
    }
}
