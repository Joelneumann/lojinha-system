package de.joelneumann.lojinha.ui.components.general

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.joelneumann.lojinha.ui.theme.*

@Composable
fun PaginationBar(
    currentPage: Int,
    totalPages: Int,
    pageSize: Int,
    totalCount: Int,
    onPageChange: (Int) -> Unit,
    onPageSizeChange: (Int) -> Unit,
    pageSizeOptions: List<Int> = listOf(25, 50, 100),
    modifier: Modifier = Modifier
) {
    val startItem = if (totalCount == 0) 0 else (currentPage * pageSize) + 1
    val endItem = kotlin.math.min((currentPage + 1) * pageSize, totalCount)

    var pageSizeDropdownExpanded by remember { mutableStateOf(false) }

    Surface(
        color = SurfaceWhite,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, DividerBorder, RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Item range & count
            Text(
                text = if (totalCount > 0) "Showing $startItem–$endItem of $totalCount" else "0 items",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondaryMuted
            )

            // Right: Page Size Selector & Controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Page Size Picker
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Per page:",
                        fontSize = 13.sp,
                        color = TextSecondaryMuted
                    )

                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(SurfaceContainerLight)
                                .border(1.dp, DividerBorder, RoundedCornerShape(6.dp))
                                .clickable { pageSizeDropdownExpanded = true }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "$pageSize",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryNavy
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = PrimaryNavy
                            )
                        }

                        DropdownMenu(
                            expanded = pageSizeDropdownExpanded,
                            onDismissRequest = { pageSizeDropdownExpanded = false }
                        ) {
                            pageSizeOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "$option",
                                            fontWeight = if (option == pageSize) FontWeight.Bold else FontWeight.Normal,
                                            color = if (option == pageSize) PrimaryNavy else PrimaryNavy
                                        )
                                    },
                                    onClick = {
                                        pageSizeDropdownExpanded = false
                                        if (option != pageSize) {
                                            onPageSizeChange(option)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // Page Navigation Controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // First Page
                    IconButton(
                        onClick = { onPageChange(0) },
                        enabled = currentPage > 0,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FirstPage,
                            contentDescription = "First Page",
                            modifier = Modifier.size(18.dp),
                            tint = if (currentPage > 0) PrimaryNavy else TextSecondaryMuted.copy(alpha = 0.4f)
                        )
                    }

                    // Previous Page
                    IconButton(
                        onClick = { onPageChange(currentPage - 1) },
                        enabled = currentPage > 0,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "Previous Page",
                            modifier = Modifier.size(18.dp),
                            tint = if (currentPage > 0) PrimaryNavy else TextSecondaryMuted.copy(alpha = 0.4f)
                        )
                    }

                    // Page Indicator
                    Text(
                        text = "Page ${currentPage + 1} of ${kotlin.math.max(1, totalPages)}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryNavy,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )

                    // Next Page
                    IconButton(
                        onClick = { onPageChange(currentPage + 1) },
                        enabled = currentPage < totalPages - 1,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Next Page",
                            modifier = Modifier.size(18.dp),
                            tint = if (currentPage < totalPages - 1) PrimaryNavy else TextSecondaryMuted.copy(alpha = 0.4f)
                        )
                    }

                    // Last Page
                    IconButton(
                        onClick = { onPageChange(totalPages - 1) },
                        enabled = currentPage < totalPages - 1,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.LastPage,
                            contentDescription = "Last Page",
                            modifier = Modifier.size(18.dp),
                            tint = if (currentPage < totalPages - 1) PrimaryNavy else TextSecondaryMuted.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }
    }
}
