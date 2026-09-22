package de.joelneumann.lojinha.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class PagedResult<T>(
    val items: List<T>,
    val totalCount: Int,
    val page: Int,
    val pageSize: Int,
    val totalPages: Int
) {
    val hasNextPage: Boolean get() = page < totalPages - 1
    val hasPreviousPage: Boolean get() = page > 0
}
