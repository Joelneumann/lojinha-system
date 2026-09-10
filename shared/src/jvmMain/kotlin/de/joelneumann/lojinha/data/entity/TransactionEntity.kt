package de.joelneumann.lojinha.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import de.joelneumann.lojinha.domain.model.Transaction
import de.joelneumann.lojinha.domain.model.TransactionItem
import de.joelneumann.lojinha.domain.model.TransactionType

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["referenceTransactionId"]),
        Index(value = ["userId"]),
        Index(value = ["timestamp"]),
        Index(value = ["type"])
    ]
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val userNameSnapshot: String,
    val timestamp: Long,
    val type: String,
    val referenceTransactionId: String?,
    val note: String?,
    val totalAmount: Long,
    val items: List<TransactionItem>,
    val userBalanceBefore: Long? = null,
    val userBalanceAfter: Long? = null
) {
    fun toDomain(): Transaction = Transaction(
        id = id,
        userId = userId,
        userNameSnapshot = userNameSnapshot,
        timestamp = timestamp,
        type = try { TransactionType.valueOf(type) } catch (e: Exception) { TransactionType.PURCHASE },
        referenceTransactionId = referenceTransactionId,
        note = note,
        totalAmount = totalAmount,
        items = items,
        userBalanceBefore = userBalanceBefore,
        userBalanceAfter = userBalanceAfter
    )

    companion object {
        fun fromDomain(tx: Transaction): TransactionEntity = TransactionEntity(
            id = tx.id,
            userId = tx.userId,
            userNameSnapshot = tx.userNameSnapshot,
            timestamp = tx.timestamp,
            type = tx.type.name,
            referenceTransactionId = tx.referenceTransactionId,
            note = tx.note,
            totalAmount = tx.totalAmount,
            items = tx.items,
            userBalanceBefore = tx.userBalanceBefore,
            userBalanceAfter = tx.userBalanceAfter
        )
    }
}
