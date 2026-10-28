package de.joelneumann.lojinha.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import de.joelneumann.lojinha.data.entity.ProductStockAdjustmentEntity

@Dao
interface ProductStockAdjustmentDao {
    @Query("SELECT * FROM product_stock_adjustments WHERE productId = :productId ORDER BY timestamp DESC")
    suspend fun getAdjustmentsByProductId(productId: String): List<ProductStockAdjustmentEntity>

    @Query("SELECT * FROM product_stock_adjustments ORDER BY timestamp DESC")
    suspend fun getAllAdjustments(): List<ProductStockAdjustmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAdjustment(adjustment: ProductStockAdjustmentEntity)

    @Query("DELETE FROM product_stock_adjustments WHERE productId = :productId")
    suspend fun deleteAdjustmentsByProductId(productId: String)

    @Query("DELETE FROM product_stock_adjustments")
    suspend fun deleteAllAdjustments()
}
