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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.joelneumann.lojinha.ui.i18n.I18n
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
    val strings = I18n.current
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
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val isMobile = maxWidth < 600.dp

            val pageSizePicker: @Composable () -> Unit = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = strings.perPageLabel,
                        fontSize = if (isMobile) 12.sp else 13.sp,
                        color = TextSecondaryMuted,
                        softWrap = false
                    )

                    Box {
                        Surface(
                            onClick = { pageSizeDropdownExpanded = true },
                            shape = RoundedCornerShape(8.dp),
                            color = SurfaceWhite,
                            border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(DividerBorder)),
                            shadowElevation = 1.dp
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "$pageSize",
                                    fontSize = if (isMobile) 12.sp else 13.sp,
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
                        }

                        DropdownMenu(
                            expanded = pageSizeDropdownExpanded,
                            onDismissRequest = { pageSizeDropdownExpanded = false },
                            containerColor = SurfaceWhite,
                            modifier = Modifier
                                .background(SurfaceWhite)
                                .border(1.dp, DividerBorder, RoundedCornerShape(8.dp))
                        ) {
                            pageSizeOptions.forEach { option ->
                                val isSelected = option == pageSize
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "$option",
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) AccentNavy else PrimaryNavy
                                        )
                                    },
                                    leadingIcon = {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = AccentNavy,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        } else {
                                            Spacer(modifier = Modifier.size(16.dp))
                                        }
                                    },
                                    colors = MenuDefaults.itemColors(
                                        textColor = PrimaryNavy,
                                        leadingIconColor = AccentNavy
                                    ),
                                    onClick = {
                                        pageSizeDropdownExpanded = false
                                        if (option != pageSize) {
                                            onPageSizeChange(option)
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            val navigationControls: @Composable (Modifier) -> Unit = { navModifier ->
                val buttonSize = if (isMobile) 36.dp else 32.dp
                val iconSize = if (isMobile) 20.dp else 18.dp

                Row(
                    modifier = navModifier,
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (isMobile) Arrangement.Center else Arrangement.spacedBy(4.dp)
                ) {
                    // First Page
                    IconButton(
                        onClick = { onPageChange(0) },
                        enabled = currentPage > 0,
                        modifier = Modifier.size(buttonSize)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FirstPage,
                            contentDescription = strings.firstPage,
                            modifier = Modifier.size(iconSize),
                            tint = if (currentPage > 0) PrimaryNavy else TextSecondaryMuted.copy(alpha = 0.4f)
                        )
                    }

                    // Previous Page
                    IconButton(
                        onClick = { onPageChange(currentPage - 1) },
                        enabled = currentPage > 0,
                        modifier = Modifier.size(buttonSize)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = strings.previousPage,
                            modifier = Modifier.size(iconSize),
                            tint = if (currentPage > 0) PrimaryNavy else TextSecondaryMuted.copy(alpha = 0.4f)
                        )
                    }

                    // Page Indicator
                    Text(
                        text = strings.pageOf(currentPage + 1, kotlin.math.max(1, totalPages)),
                        fontSize = if (isMobile) 12.sp else 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryNavy,
                        modifier = Modifier.padding(horizontal = if (isMobile) 8.dp else 6.dp),
                        softWrap = false
                    )

                    // Next Page
                    IconButton(
                        onClick = { onPageChange(currentPage + 1) },
                        enabled = currentPage < totalPages - 1,
                        modifier = Modifier.size(buttonSize)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = strings.nextPage,
                            modifier = Modifier.size(iconSize),
                            tint = if (currentPage < totalPages - 1) PrimaryNavy else TextSecondaryMuted.copy(alpha = 0.4f)
                        )
                    }

                    // Last Page
                    IconButton(
                        onClick = { onPageChange(totalPages - 1) },
                        enabled = currentPage < totalPages - 1,
                        modifier = Modifier.size(buttonSize)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.LastPage,
                            contentDescription = strings.lastPage,
                            modifier = Modifier.size(iconSize),
                            tint = if (currentPage < totalPages - 1) PrimaryNavy else TextSecondaryMuted.copy(alpha = 0.4f)
                        )
                    }
                }
            }

            if (isMobile) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (totalCount > 0) strings.paginationShowing(startItem, endItem, totalCount) else strings.paginationZeroItems,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondaryMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        pageSizePicker()
                    }

                    navigationControls(Modifier.fillMaxWidth())
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Item range & count
                    Text(
                        text = if (totalCount > 0) strings.paginationShowing(startItem, endItem, totalCount) else strings.paginationZeroItems,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondaryMuted,
                        softWrap = false
                    )

                    // Right: Page Size Selector & Controls
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        pageSizePicker()
                        navigationControls(Modifier)
                    }
                }
            }
        }
    }
}
