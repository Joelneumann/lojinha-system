package de.joelneumann.lojinha.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import de.joelneumann.lojinha.domain.model.*

@Entity(
    tableName = "veggie_unit_types",
    indices = [
        Index(value = ["name"])
    ]
)
data class VeggieUnitTypeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val allowDecimals: Boolean,
    val isActive: Boolean,
    val updatedAt: Long
) {
    fun toDomain(): VeggieUnitType = VeggieUnitType(
        id = id,
        name = name,
        allowDecimals = allowDecimals,
        isActive = isActive,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(unitType: VeggieUnitType): VeggieUnitTypeEntity = VeggieUnitTypeEntity(
            id = unitType.id,
            name = unitType.name,
            allowDecimals = unitType.allowDecimals,
            isActive = unitType.isActive,
            updatedAt = unitType.updatedAt
        )
    }
}

@Entity(
    tableName = "veggie_products",
    indices = [
        Index(value = ["name"]),
        Index(value = ["unitTypeId"])
    ]
)
data class VeggieProductEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String?,
    val estimatedPriceCents: Long?,
    val unitTypeId: String,
    val imageFileName: String?,
    val isActive: Boolean,
    val isDeleted: Boolean,
    val updatedAt: Long
) {
    fun toDomain(): VeggieProduct = VeggieProduct(
        id = id,
        name = name,
        description = description,
        estimatedPriceCents = estimatedPriceCents,
        unitTypeId = unitTypeId,
        imageFileName = imageFileName,
        isActive = isActive,
        isDeleted = isDeleted,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(product: VeggieProduct): VeggieProductEntity = VeggieProductEntity(
            id = product.id,
            name = product.name,
            description = product.description,
            estimatedPriceCents = product.estimatedPriceCents,
            unitTypeId = product.unitTypeId,
            imageFileName = product.imageFileName,
            isActive = product.isActive,
            isDeleted = product.isDeleted,
            updatedAt = product.updatedAt
        )
    }
}

@Entity(
    tableName = "veggie_order_rounds",
    indices = [
        Index(value = ["status"]),
        Index(value = ["openedAt"])
    ]
)
data class VeggieOrderRoundEntity(
    @PrimaryKey val id: String,
    val openedAt: Long,
    val closedAt: Long?,
    val settledAt: Long?,
    val status: String,
    val buyerOperationFeeCents: Long,
    val transportFeeCents: Long,
    val transporterUserId: String?,
    val buyerPdfExportedAt: Long? = null,
    val workerPdfExportedAt: Long? = null,
    val updatedAt: Long
) {
    fun toDomain(): VeggieOrderRound = VeggieOrderRound(
        id = id,
        openedAt = openedAt,
        closedAt = closedAt,
        settledAt = settledAt,
        status = try { VeggieRoundStatus.valueOf(status) } catch (_: Exception) { VeggieRoundStatus.OPEN },
        buyerOperationFeeCents = buyerOperationFeeCents,
        transportFeeCents = transportFeeCents,
        transporterUserId = transporterUserId,
        buyerPdfExportedAt = buyerPdfExportedAt,
        workerPdfExportedAt = workerPdfExportedAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(round: VeggieOrderRound): VeggieOrderRoundEntity = VeggieOrderRoundEntity(
            id = round.id,
            openedAt = round.openedAt,
            closedAt = round.closedAt,
            settledAt = round.settledAt,
            status = round.status.name,
            buyerOperationFeeCents = round.buyerOperationFeeCents,
            transportFeeCents = round.transportFeeCents,
            transporterUserId = round.transporterUserId,
            buyerPdfExportedAt = round.buyerPdfExportedAt,
            workerPdfExportedAt = round.workerPdfExportedAt,
            updatedAt = round.updatedAt
        )
    }
}

@Entity(
    tableName = "veggie_round_product_settlements",
    primaryKeys = ["roundId", "veggieProductId"],
    indices = [
        Index(value = ["roundId"])
    ]
)
data class VeggieRoundProductSettlementEntity(
    val roundId: String,
    val veggieProductId: String,
    val marketUnitPriceCents: Long?,
    val totalSpentCents: Long?,
    val updatedAt: Long
) {
    fun toDomain(): VeggieRoundProductSettlement = VeggieRoundProductSettlement(
        roundId = roundId,
        veggieProductId = veggieProductId,
        marketUnitPriceCents = marketUnitPriceCents,
        totalSpentCents = totalSpentCents,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(settlement: VeggieRoundProductSettlement): VeggieRoundProductSettlementEntity =
            VeggieRoundProductSettlementEntity(
                roundId = settlement.roundId,
                veggieProductId = settlement.veggieProductId,
                marketUnitPriceCents = settlement.marketUnitPriceCents,
                totalSpentCents = settlement.totalSpentCents,
                updatedAt = settlement.updatedAt
            )
    }
}

@Entity(
    tableName = "veggie_order_entries",
    indices = [
        Index(value = ["roundId", "userId", "veggieProductId"], unique = true),
        Index(value = ["roundId"]),
        Index(value = ["userId"]),
        Index(value = ["veggieProductId"])
    ]
)
data class VeggieOrderEntryEntity(
    @PrimaryKey val id: String,
    val roundId: String,
    val userId: String,
    val userNameSnapshot: String,
    val veggieProductId: String,
    val veggieNameSnapshot: String,
    val unitNameSnapshot: String,
    val allowDecimalsSnapshot: Boolean,
    val orderedQuantityScaled: Long,
    val actualQuantityScaled: Long?,
    val effectiveUnitPriceCents: Long?,
    val finalItemTotalCents: Long?,
    val updatedAt: Long
) {
    fun toDomain(): VeggieOrderEntry = VeggieOrderEntry(
        id = id,
        roundId = roundId,
        userId = userId,
        userNameSnapshot = userNameSnapshot,
        veggieProductId = veggieProductId,
        veggieNameSnapshot = veggieNameSnapshot,
        unitNameSnapshot = unitNameSnapshot,
        allowDecimalsSnapshot = allowDecimalsSnapshot,
        orderedQuantityScaled = orderedQuantityScaled,
        actualQuantityScaled = actualQuantityScaled,
        effectiveUnitPriceCents = effectiveUnitPriceCents,
        finalItemTotalCents = finalItemTotalCents,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(entry: VeggieOrderEntry): VeggieOrderEntryEntity = VeggieOrderEntryEntity(
            id = entry.id,
            roundId = entry.roundId,
            userId = entry.userId,
            userNameSnapshot = entry.userNameSnapshot,
            veggieProductId = entry.veggieProductId,
            veggieNameSnapshot = entry.veggieNameSnapshot,
            unitNameSnapshot = entry.unitNameSnapshot,
            allowDecimalsSnapshot = entry.allowDecimalsSnapshot,
            orderedQuantityScaled = entry.orderedQuantityScaled,
            actualQuantityScaled = entry.actualQuantityScaled,
            effectiveUnitPriceCents = entry.effectiveUnitPriceCents,
            finalItemTotalCents = entry.finalItemTotalCents,
            updatedAt = entry.updatedAt
        )
    }
}
