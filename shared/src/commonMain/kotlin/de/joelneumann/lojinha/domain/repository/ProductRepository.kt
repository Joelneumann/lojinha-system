package de.joelneumann.lojinha.domain.repository

import de.joelneumann.lojinha.domain.model.Product
import kotlinx.coroutines.flow.Flow

interface ProductRepository {
    fun getProductsFlow(): Flow<List<Product>>
    suspend fun getAllProducts(): List<Product>
    suspend fun getProductById(id: String): Product?
    suspend fun getProductByBarcode(barcode: String): Product?
    suspend fun saveProduct(product: Product)
    suspend fun deactivateProduct(id: String)
    suspend fun hardDeleteProduct(id: String)
    suspend fun updateStock(productId: String, delta: Long)
}
