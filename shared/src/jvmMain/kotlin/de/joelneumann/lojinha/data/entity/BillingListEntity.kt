package de.joelneumann.lojinha.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey

@Entity(tableName = "billing_lists")
data class BillingListEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val basePrice: Long?,
    val comment: String?,
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "billing_list_users",
    foreignKeys = [
        ForeignKey(
            entity = BillingListEntity::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        androidx.room.Index(value = ["listId"]),
        androidx.room.Index(value = ["userId"])
    ]
)
data class BillingListUserEntity(
    @PrimaryKey val id: String,
    val listId: String,
    val userId: String,
    val quantity: Int
)
