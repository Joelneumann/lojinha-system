package de.joelneumann.lojinha.ui.components.admin.veggie

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.joelneumann.lojinha.domain.model.VeggieProduct
import de.joelneumann.lojinha.domain.model.VeggieUnitType
import de.joelneumann.lojinha.ui.i18n.AppStrings
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.AccentNavy
import de.joelneumann.lojinha.ui.theme.DividerBorder
import de.joelneumann.lojinha.ui.theme.PrimaryNavy
import de.joelneumann.lojinha.ui.theme.SurfaceWhite
import de.joelneumann.lojinha.ui.theme.TextSecondaryMuted
import de.joelneumann.lojinha.ui.utils.ACCENT_INSENSITIVE_COMPARATOR
import de.joelneumann.lojinha.ui.utils.sortedByAccentInsensitive

enum class VeggieSortOption {
    NAME_ASC,
    NAME_DESC,
    PRICE_ASC,
    PRICE_DESC;

    fun getLabel(strings: AppStrings): String = when (this) {
        NAME_ASC -> strings.sortNameAsc
        NAME_DESC -> strings.sortNameDesc
        PRICE_ASC -> strings.sortPriceAsc
        PRICE_DESC -> strings.sortPriceDesc
    }
}

fun List<VeggieProduct>.sortWithOption(sortOption: VeggieSortOption): List<VeggieProduct> {
    return when (sortOption) {
        VeggieSortOption.NAME_ASC -> sortedByAccentInsensitive { it.name }
        VeggieSortOption.NAME_DESC -> sortedWith(compareByDescending(ACCENT_INSENSITIVE_COMPARATOR) { it.name })
        VeggieSortOption.PRICE_ASC -> sortedWith(
            compareBy<VeggieProduct> { it.estimatedPriceCents ?: Long.MAX_VALUE }
                .thenBy(ACCENT_INSENSITIVE_COMPARATOR) { it.name }
        )
        VeggieSortOption.PRICE_DESC -> sortedWith(
            compareByDescending<VeggieProduct> { it.estimatedPriceCents ?: -1L }
                .thenBy(ACCENT_INSENSITIVE_COMPARATOR) { it.name }
        )
    }
}

@Composable
fun VeggieSortButton(
    selectedOption: VeggieSortOption,
    onOptionSelected: (VeggieSortOption) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val strings = I18n.current

    Box(modifier = modifier) {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(12.dp),
            color = SurfaceWhite,
            border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(DividerBorder)),
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxHeight()
        ) {
            Row(
                modifier = Modifier.fillMaxHeight().padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Sort,
                    contentDescription = strings.sortBy,
                    tint = PrimaryNavy,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = selectedOption.getLabel(strings),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryNavy,
                    maxLines = 1
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = TextSecondaryMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(SurfaceWhite)
        ) {
            val options = VeggieSortOption.entries
            options.forEach { option ->
                val isSelected = option == selectedOption

                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.getLabel(strings),
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
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun VeggieUnitFilterButton(
    selectedUnitId: String?,
    unitTypes: List<VeggieUnitType>,
    onUnitSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val strings = I18n.current
    val activeUnits = remember(unitTypes) { unitTypes.filter { it.isActive }.sortedBy { it.name.lowercase() } }
    val selectedUnitName = remember(selectedUnitId, activeUnits) {
        if (selectedUnitId == null) strings.allUnitsFilter
        else activeUnits.firstOrNull { it.id == selectedUnitId }?.name ?: strings.allUnitsFilter
    }

    Box(modifier = modifier) {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(12.dp),
            color = SurfaceWhite,
            border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(DividerBorder)),
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxHeight()
        ) {
            Row(
                modifier = Modifier.fillMaxHeight().padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = strings.filterByUnit,
                    tint = if (selectedUnitId != null) AccentNavy else PrimaryNavy,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = selectedUnitName,
                    fontSize = 13.sp,
                    fontWeight = if (selectedUnitId != null) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (selectedUnitId != null) AccentNavy else PrimaryNavy,
                    maxLines = 1
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = TextSecondaryMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(SurfaceWhite)
        ) {
            val isAllSelected = selectedUnitId == null
            DropdownMenuItem(
                text = {
                    Text(
                        text = strings.allUnitsFilter,
                        fontSize = 13.sp,
                        fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isAllSelected) AccentNavy else PrimaryNavy
                    )
                },
                leadingIcon = {
                    if (isAllSelected) {
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
                onClick = {
                    onUnitSelected(null)
                    expanded = false
                }
            )

            if (activeUnits.isNotEmpty()) {
                HorizontalDivider(color = DividerBorder.copy(alpha = 0.5f))
                activeUnits.forEach { unit ->
                    val isSelected = unit.id == selectedUnitId
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = unit.name,
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
                        onClick = {
                            onUnitSelected(unit.id)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
