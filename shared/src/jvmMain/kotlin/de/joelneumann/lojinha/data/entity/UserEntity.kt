package de.joelneumann.lojinha.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import de.joelneumann.lojinha.domain.model.AvatarType
import de.joelneumann.lojinha.domain.model.Language
import de.joelneumann.lojinha.domain.model.SecondaryCurrency
import de.joelneumann.lojinha.domain.model.User
import de.joelneumann.lojinha.domain.model.UserAvatarConfig

@Entity(
    tableName = "users",
    indices = [
        Index(value = ["name"]),
        Index(value = ["userBarcode"]),
        Index(value = ["userBarcodeNumber"])
    ]
)
data class UserEntity(
    @PrimaryKey val id: String,
    val name: String,
    val balance: Long,
    val language: String,
    val secondaryCurrency: String,
    val pin: String?,
    val userBarcode: String?,
    val userBarcodeNumber: String?,
    val isActive: Boolean,
    @ColumnInfo(defaultValue = "0") val isDeleted: Boolean = false,
    @ColumnInfo(defaultValue = "'INITIALS'") val avatarType: String = "INITIALS",
    @ColumnInfo(defaultValue = "'😀'") val avatarEmoji: String = "😀",
    @ColumnInfo(defaultValue = "'#1E293B'") val avatarColor: String = "#1E293B"
) {

    fun toDomain(): User {
        val safeBarcode = if (userBarcode != null && userBarcodeNumber != null) {
            userBarcode to userBarcodeNumber
        } else if (userBarcode != null) {
            userBarcode to userBarcode
        } else if (userBarcodeNumber != null) {
            userBarcodeNumber to userBarcodeNumber
        } else {
            null to null
        }
        return User(
            id = id,
            name = name,
            balance = balance,
            language = Language.fromCode(language),
            secondaryCurrency = try { SecondaryCurrency.valueOf(secondaryCurrency) } catch (e: Exception) { SecondaryCurrency.NONE },
            pin = pin,
            userBarcode = safeBarcode.first,
            userBarcodeNumber = safeBarcode.second,
            isActive = isActive,
            isDeleted = isDeleted,
            avatar = UserAvatarConfig(
                type = try { AvatarType.valueOf(avatarType) } catch (e: Exception) { AvatarType.INITIALS },
                emoji = avatarEmoji,
                colorHex = avatarColor
            )
        )
    }

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
            isActive = user.isActive,
            isDeleted = user.isDeleted,
            avatarType = user.avatar.type.name,
            avatarEmoji = user.avatar.emoji,
            avatarColor = user.avatar.colorHex
        )
    }
}
