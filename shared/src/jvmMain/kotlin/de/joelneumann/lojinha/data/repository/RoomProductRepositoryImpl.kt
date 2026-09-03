package de.joelneumann.lojinha.data.repository

import de.joelneumann.lojinha.data.dao.ProductDao
import de.joelneumann.lojinha.data.entity.ProductEntity
import de.joelneumann.lojinha.domain.model.Product
import de.joelneumann.lojinha.domain.repository.ProductRepository
import de.joelneumann.lojinha.ui.utils.sortedByAccentInsensitive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomProductRepositoryImpl(
    private val productDao: ProductDao,
    private val onDataChanged: (() -> Unit)? = null
) : ProductRepository {

    override fun getProductsFlow(): Flow<List<Product>> {
        return productDao.getProductsFlow().map { entities ->
            entities.map { it.toDomain() }.sortedByAccentInsensitive { it.name }
        }
    }

    override suspend fun getAllProducts(): List<Product> {
        return productDao.getAllProducts().map { it.toDomain() }.sortedByAccentInsensitive { it.name }
    }

    override suspend fun getProductById(id: String): Product? {
        return productDao.getProductById(id)?.toDomain()
    }

    override suspend fun getProductByBarcode(barcode: String): Product? {
        val all = productDao.getAllProducts()
        return all.firstOrNull { entity ->
            entity.barcodes.any { it.code.equals(barcode, ignoreCase = true) }
        }?.toDomain()
    }

    override suspend fun saveProduct(product: Product) {
        productDao.insertOrUpdateProduct(ProductEntity.fromDomain(product))
        onDataChanged?.invoke()
    }

    override suspend fun deactivateProduct(id: String) {
        productDao.deactivateProduct(id)
        onDataChanged?.invoke()
    }

    override suspend fun hardDeleteProduct(id: String) {
        productDao.deleteProduct(id)
        onDataChanged?.invoke()
    }

    override suspend fun updateStock(productId: String, delta: Long) {
        productDao.updateStock(productId, delta)
        onDataChanged?.invoke()
    }
}
