package de.joelneumann.lojinha.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import de.joelneumann.lojinha.data.entity.ProductEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY name ASC")
    fun getProductsFlow(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products ORDER BY name ASC")
    suspend fun getAllProducts(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getProductById(id: String): ProductEntity?

    @Query("SELECT * FROM products WHERE barcodes LIKE '%' || :barcode || '%' LIMIT 1")
    suspend fun getProductByBarcode(barcode: String): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProduct(product: ProductEntity)

    @Query("""
        UPDATE products 
        SET name = :name, 
            barcodes = :barcodes, 
            basePrice = :basePrice, 
            unitType = :unitType, 
            customMarkupPercent = :customMarkupPercent, 
            isActive = :isActive 
        WHERE id = :id
    """)
    suspend fun updateProductMetadata(
        id: String,
        name: String,
        barcodes: List<de.joelneumann.lojinha.domain.model.Barcode>,
        basePrice: Long,
        unitType: String,
        customMarkupPercent: Double?,
        isActive: Boolean
    )

    @Query("UPDATE products SET isActive = 0 WHERE id = :id")
    suspend fun deactivateProduct(id: String)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteProduct(id: String)

    @Query("DELETE FROM products")
    suspend fun deleteAllProducts()

    @Query("UPDATE products SET stockQuantity = MAX(0, stockQuantity + :delta) WHERE id = :id")
    suspend fun updateStock(id: String, delta: Long)
}
