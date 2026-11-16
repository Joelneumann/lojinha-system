package de.joelneumann.lojinha.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import de.joelneumann.lojinha.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface VeggieOrderDao {

    // -------------------------------------------------------------------------
    // Unit Types
    // -------------------------------------------------------------------------
    @Query("SELECT * FROM veggie_unit_types ORDER BY name ASC")
    fun getUnitTypesFlow(): Flow<List<VeggieUnitTypeEntity>>

    @Query("SELECT * FROM veggie_unit_types ORDER BY name ASC")
    suspend fun getAllUnitTypes(): List<VeggieUnitTypeEntity>

    @Query("SELECT * FROM veggie_unit_types WHERE id = :id")
    suspend fun getUnitTypeById(id: String): VeggieUnitTypeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateUnitType(unitType: VeggieUnitTypeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateUnitTypes(unitTypes: List<VeggieUnitTypeEntity>)

    @Query("UPDATE veggie_unit_types SET isActive = 0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun deactivateUnitType(id: String, updatedAt: Long)

    @Query("DELETE FROM veggie_unit_types")
    suspend fun deleteAllUnitTypes()

    // -------------------------------------------------------------------------
    // Veggie Products Catalog
    // -------------------------------------------------------------------------
    @Query("SELECT * FROM veggie_products WHERE isDeleted = 0 ORDER BY name ASC")
    fun getVeggieProductsFlow(): Flow<List<VeggieProductEntity>>

    @Query("SELECT * FROM veggie_products WHERE (:includeDeleted = 1 OR isDeleted = 0) ORDER BY name ASC")
    suspend fun getAllVeggieProducts(includeDeleted: Boolean = false): List<VeggieProductEntity>

    @Query("SELECT * FROM veggie_products WHERE id = :id")
    suspend fun getVeggieProductById(id: String): VeggieProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateVeggieProduct(product: VeggieProductEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateVeggieProducts(products: List<VeggieProductEntity>)

    @Query("UPDATE veggie_products SET estimatedPriceCents = :estimatedPriceCents, updatedAt = :updatedAt WHERE id = :productId")
    suspend fun updateEstimatedPrice(productId: String, estimatedPriceCents: Long, updatedAt: Long)

    @Query("UPDATE veggie_products SET imageFileName = :imageFileName, updatedAt = :updatedAt WHERE id = :productId")
    suspend fun updateProductImage(productId: String, imageFileName: String?, updatedAt: Long)

    @Query("UPDATE veggie_products SET isActive = 0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun deactivateVeggieProduct(id: String, updatedAt: Long)

    @Query("UPDATE veggie_products SET isDeleted = 1, isActive = 0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDeleteVeggieProduct(id: String, updatedAt: Long)

    @Query("UPDATE veggie_products SET isDeleted = 0, isActive = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun restoreVeggieProduct(id: String, updatedAt: Long)

    @Query("DELETE FROM veggie_products WHERE id = :id")
    suspend fun hardDeleteVeggieProduct(id: String)

    @Query("SELECT COUNT(*) FROM veggie_order_entries WHERE veggieProductId = :productId")
    suspend fun getEntryCountForProduct(productId: String): Int

    @Query("SELECT COUNT(*) FROM veggie_products WHERE unitTypeId = :unitTypeId")
    suspend fun getProductCountForUnitType(unitTypeId: String): Int

    @Query("DELETE FROM veggie_products")
    suspend fun deleteAllVeggieProducts()

    // -------------------------------------------------------------------------
    // Order Rounds
    // -------------------------------------------------------------------------
    @Query("SELECT * FROM veggie_order_rounds WHERE status != 'SETTLED' ORDER BY openedAt DESC LIMIT 1")
    fun getActiveRoundFlow(): Flow<VeggieOrderRoundEntity?>

    @Query("SELECT * FROM veggie_order_rounds WHERE status != 'SETTLED' ORDER BY openedAt DESC LIMIT 1")
    suspend fun getActiveRound(): VeggieOrderRoundEntity?

    @Query("SELECT * FROM veggie_order_rounds ORDER BY openedAt DESC")
    fun getAllRoundsFlow(): Flow<List<VeggieOrderRoundEntity>>

    @Query("SELECT * FROM veggie_order_rounds ORDER BY openedAt DESC")
    suspend fun getAllRounds(): List<VeggieOrderRoundEntity>

    @Query("SELECT * FROM veggie_order_rounds WHERE id = :roundId")
    suspend fun getRoundById(roundId: String): VeggieOrderRoundEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateRound(round: VeggieOrderRoundEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateRounds(rounds: List<VeggieOrderRoundEntity>)

    @Query("UPDATE veggie_order_rounds SET buyerPdfExportedAt = :timestamp, updatedAt = :updatedAt WHERE id = :roundId")
    suspend fun markBuyerPdfExported(roundId: String, timestamp: Long, updatedAt: Long)

    @Query("UPDATE veggie_order_rounds SET workerPdfExportedAt = :timestamp, updatedAt = :updatedAt WHERE id = :roundId")
    suspend fun markWorkerPdfExported(roundId: String, timestamp: Long, updatedAt: Long)

    @Query("DELETE FROM veggie_order_rounds WHERE id = :roundId")
    suspend fun deleteRound(roundId: String)

    @Query("DELETE FROM veggie_round_product_settlements WHERE roundId = :roundId")
    suspend fun deleteProductSettlementsForRound(roundId: String)

    @Query("DELETE FROM veggie_order_entries WHERE roundId = :roundId")
    suspend fun deleteEntriesForRound(roundId: String)

    @Query("DELETE FROM veggie_order_rounds")
    suspend fun deleteAllRounds()

    // -------------------------------------------------------------------------
    // Per-Product Settlements
    // -------------------------------------------------------------------------
    @Query("SELECT * FROM veggie_round_product_settlements WHERE roundId = :roundId")
    fun getRoundProductSettlementsFlow(roundId: String): Flow<List<VeggieRoundProductSettlementEntity>>

    @Query("SELECT * FROM veggie_round_product_settlements WHERE roundId = :roundId")
    suspend fun getRoundProductSettlements(roundId: String): List<VeggieRoundProductSettlementEntity>

    @Query("SELECT * FROM veggie_round_product_settlements")
    suspend fun getAllProductSettlements(): List<VeggieRoundProductSettlementEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProductSettlement(settlement: VeggieRoundProductSettlementEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProductSettlements(settlements: List<VeggieRoundProductSettlementEntity>)

    @Query("DELETE FROM veggie_round_product_settlements")
    suspend fun deleteAllProductSettlements()

    // -------------------------------------------------------------------------
    // Customer Order Entries
    // -------------------------------------------------------------------------
    @Query("SELECT * FROM veggie_order_entries WHERE roundId = :roundId ORDER BY userNameSnapshot ASC, veggieNameSnapshot ASC")
    fun getRoundEntriesFlow(roundId: String): Flow<List<VeggieOrderEntryEntity>>

    @Query("SELECT * FROM veggie_order_entries WHERE roundId = :roundId ORDER BY userNameSnapshot ASC, veggieNameSnapshot ASC")
    suspend fun getRoundEntries(roundId: String): List<VeggieOrderEntryEntity>

    @Query("SELECT * FROM veggie_order_entries ORDER BY updatedAt DESC")
    suspend fun getAllEntries(): List<VeggieOrderEntryEntity>

    @Query("SELECT * FROM veggie_order_entries WHERE roundId = :roundId AND userId = :userId ORDER BY veggieNameSnapshot ASC")
    suspend fun getEntriesForUserInRound(roundId: String, userId: String): List<VeggieOrderEntryEntity>

    @Query("SELECT * FROM veggie_order_entries WHERE userId = :userId ORDER BY updatedAt DESC")
    suspend fun getEntriesForUserAcrossRounds(userId: String): List<VeggieOrderEntryEntity>

    @Query("SELECT * FROM veggie_order_entries WHERE roundId = :roundId AND userId = :userId AND veggieProductId = :veggieProductId LIMIT 1")
    suspend fun getEntryByRoundUserProduct(roundId: String, userId: String, veggieProductId: String): VeggieOrderEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateEntry(entry: VeggieOrderEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateEntries(entries: List<VeggieOrderEntryEntity>)

    @Query("DELETE FROM veggie_order_entries WHERE id = :id")
    suspend fun deleteEntryById(id: String)

    @Query("DELETE FROM veggie_order_entries WHERE roundId = :roundId AND userId = :userId AND veggieProductId = :veggieProductId")
    suspend fun deleteEntryByRoundUserProduct(roundId: String, userId: String, veggieProductId: String)

    @Query("DELETE FROM veggie_order_entries WHERE roundId = :roundId AND veggieProductId = :productId")
    suspend fun deleteEntriesForProductInRound(roundId: String, productId: String)

    @Query("SELECT COUNT(*) FROM veggie_order_entries WHERE userId = :userId")
    suspend fun getEntryCountForUser(userId: String): Int

    @Query("DELETE FROM veggie_order_entries")
    suspend fun deleteAllEntries()

    // -------------------------------------------------------------------------
    // Atomic Composite Persistence Helpers
    // -------------------------------------------------------------------------
    @Transaction
    suspend fun replaceUserOrderEntriesForRound(
        entriesToUpsert: List<VeggieOrderEntryEntity>,
        entryIdsToDelete: List<String>
    ) {
        for (id in entryIdsToDelete) {
            deleteEntryById(id)
        }
        if (entriesToUpsert.isNotEmpty()) {
            insertOrUpdateEntries(entriesToUpsert)
        }
    }

    @Transaction
    suspend fun saveSettlementState(
        round: VeggieOrderRoundEntity,
        updatedEntries: List<VeggieOrderEntryEntity>,
        updatedProductSettlements: List<VeggieRoundProductSettlementEntity>,
        catalogPriceUpdates: Map<String, Long>,
        timestamp: Long
    ) {
        insertOrUpdateRound(round)
        if (updatedEntries.isNotEmpty()) {
            insertOrUpdateEntries(updatedEntries)
        }
        if (updatedProductSettlements.isNotEmpty()) {
            insertOrUpdateProductSettlements(updatedProductSettlements)
        }
        for ((productId, priceCents) in catalogPriceUpdates) {
            if (priceCents > 0L) {
                updateEstimatedPrice(productId, priceCents, timestamp)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Customer Veggie Favorites
    // -------------------------------------------------------------------------
    @Query("SELECT veggieProductId FROM veggie_user_favorites WHERE userId = :userId")
    suspend fun getFavoriteProductIdsForUser(userId: String): List<String>

    @Query("DELETE FROM veggie_user_favorites WHERE userId = :userId")
    suspend fun deleteFavoritesForUser(userId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorites(favorites: List<VeggieUserFavoriteEntity>)

    @Transaction
    suspend fun replaceFavoritesForUser(userId: String, favorites: List<VeggieUserFavoriteEntity>) {
        deleteFavoritesForUser(userId)
        if (favorites.isNotEmpty()) {
            insertFavorites(favorites)
        }
    }

    @Query("DELETE FROM veggie_user_favorites WHERE veggieProductId = :productId")
    suspend fun deleteFavoritesForProduct(productId: String)

    @Query("DELETE FROM veggie_user_favorites")
    suspend fun deleteAllFavorites()
}
