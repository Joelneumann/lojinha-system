package de.joelneumann.lojinha.ui.components.admin.products

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import de.joelneumann.lojinha.domain.model.Barcode
import de.joelneumann.lojinha.domain.model.Product
import de.joelneumann.lojinha.domain.model.UnitType
import de.joelneumann.lojinha.ui.components.admin.AdminLabeledField
import de.joelneumann.lojinha.ui.components.admin.AdminSegmentedOptionsRow
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.Formatting

@Composable
fun ProductEditDialog(
    product: Product,
    allProducts: List<Product>,
    onSave: (Product) -> Unit,
    onCancel: () -> Unit
) {
    val strings = I18n.current
    val isNewProduct = remember(product.id) { product.id.isBlank() || product.name.isBlank() }

    var name by remember { mutableStateOf(product.name) }
    var basePriceBrl by remember { mutableStateOf(if (isNewProduct) "0,00" else (product.basePrice.toDouble() / 100.0).toString().replace('.', ',')) }
    var unitType by remember { mutableStateOf(product.unitType) }
    var stockQuantity by remember { mutableStateOf(if (isNewProduct) "0" else Formatting.formatStockForAdmin(product.stockQuantity, product.unitType)) }
    var customMarkup by remember { mutableStateOf(product.customMarkupPercent?.toString() ?: "") }
    var barcodeCode by remember { mutableStateOf("") }
    var barcodeDesc by remember { mutableStateOf("") }
    var barcodeList by remember { mutableStateOf(product.barcodes) }

    val newBarcodeConflictProduct = remember(barcodeCode, allProducts, product.id) {
        val trimmed = barcodeCode.trim()
        if (trimmed.isBlank()) null
        else allProducts.firstOrNull { p -> p.id != product.id && p.barcodes.any { b -> b.code.equals(trimmed, ignoreCase = true) } }
    }

    val assignedBarcodeConflictProduct = remember(barcodeList, allProducts, product.id) {
        allProducts.firstOrNull { p -> p.id != product.id && p.barcodes.any { b -> barcodeList.any { bl -> bl.code.equals(b.code, ignoreCase = true) } } }
    }

    Dialog(onDismissRequest = onCancel) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SurfaceWhite,
            shadowElevation = 8.dp,
            modifier = Modifier.width(660.dp).wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = if (isNewProduct) strings.addProduct else strings.editProduct,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryNavy
                )

                HorizontalDivider(color = DividerBorder)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AdminLabeledField(
                        label = "Product Name",
                        value = name,
                        onValueChange = { name = it },
                        placeholder = "Product Name",
                        modifier = Modifier.weight(1.5f)
                    )

                    AdminLabeledField(
                        label = "Base Price (R$)",
                        value = basePriceBrl,
                        onValueChange = { basePriceBrl = it },
                        placeholder = "0,00",
                        modifier = Modifier.weight(1f)
                    )
                }

                AdminSegmentedOptionsRow(
                    label = "Unit Type",
                    options = UnitType.entries,
                    selected = unitType,
                    onSelect = { unitType = it },
                    optionLabel = { it.name }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AdminLabeledField(
                        label = if (unitType == UnitType.PIECE) "Stock Quantity (Units):" else "Stock Quantity (kg):",
                        value = stockQuantity,
                        onValueChange = { stockQuantity = it },
                        placeholder = if (unitType == UnitType.PIECE) "e.g. 25 (Full numbers)" else "e.g. 2.500 (Decimal in kg)",
                        modifier = Modifier.weight(1f)
                    )

                    AdminLabeledField(
                        label = "Custom Markup % (Optional)",
                        value = customMarkup,
                        onValueChange = { customMarkup = it },
                        placeholder = "Standard",
                        modifier = Modifier.weight(1f)
                    )
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Associated Barcodes (${barcodeList.size})", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)
                    Spacer(modifier = Modifier.height(6.dp))

                    if (barcodeList.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            barcodeList.forEach { b ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = SurfaceContainerHighLight,
                                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DividerBorder))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text("🏷️ ${b.code}${if (b.description != null) " (${b.description})" else ""}", fontSize = 12.sp, color = PrimaryNavy)
                                        Text(
                                            text = "✕",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ColorDangerCrimson,
                                            modifier = Modifier.clickable {
                                                barcodeList = barcodeList.filter { it.code != b.code }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = barcodeCode,
                            onValueChange = { barcodeCode = it },
                            placeholder = { Text("Barcode Code", fontSize = 12.sp) },
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = barcodeDesc,
                            onValueChange = { barcodeDesc = it },
                            placeholder = { Text("Description (Optional)", fontSize = 12.sp) },
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true
                        )

                        Button(
                            onClick = {
                                if (barcodeCode.isNotBlank() && newBarcodeConflictProduct == null) {
                                    val code = barcodeCode.trim()
                                    if (barcodeList.none { it.code.equals(code, ignoreCase = true) }) {
                                        barcodeList = barcodeList + Barcode(code, barcodeDesc.trim().ifBlank { null })
                                        barcodeCode = ""
                                        barcodeDesc = ""
                                    }
                                }
                            },
                            enabled = barcodeCode.isNotBlank() && newBarcodeConflictProduct == null,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
                            modifier = Modifier.height(52.dp)
                        ) {
                            Text("+ Add", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (newBarcodeConflictProduct != null) {
                        Text(
                            text = "❌ Barcode '${barcodeCode.trim()}' is already assigned to product '${newBarcodeConflictProduct.name}'!",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorDangerCrimson,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                HorizontalDivider(color = DividerBorder)

                if (assignedBarcodeConflictProduct != null) {
                    Text(
                        text = "❌ Contains barcode assigned to product '${assignedBarcodeConflictProduct.name}'!",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorDangerCrimson,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                val initialPriceBrl = Formatting.formatBrl(product.basePrice).removePrefix("R$ ").trim()
                val initialStockAdmin = Formatting.formatStockForAdmin(product.stockQuantity, product.unitType)
                val hasDialogChanges = name != product.name ||
                        basePriceBrl != initialPriceBrl ||
                        unitType != product.unitType ||
                        stockQuantity != initialStockAdmin ||
                        customMarkup != (product.customMarkupPercent?.toString() ?: "") ||
                        barcodeList != product.barcodes

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(strings.cancel, fontSize = 14.sp)
                    }

                    if (!isNewProduct && hasDialogChanges) {
                        OutlinedButton(
                            onClick = {
                                name = product.name
                                basePriceBrl = initialPriceBrl
                                unitType = product.unitType
                                stockQuantity = Formatting.formatStockForAdmin(product.stockQuantity, product.unitType)
                                customMarkup = product.customMarkupPercent?.toString() ?: ""
                                barcodeList = product.barcodes
                                barcodeCode = ""
                                barcodeDesc = ""
                            },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("↩️ Revert", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)
                        }
                    }

                    Button(
                        onClick = {
                            val priceCents = kotlin.math.round((basePriceBrl.replace(',', '.').toDoubleOrNull() ?: 0.0) * 100).toLong()
                            val stock = Formatting.parseAdminStockToDb(stockQuantity, unitType) ?: 0L
                            val markup = customMarkup.toDoubleOrNull()
                            val updated = product.copy(
                                name = name.trim(),
                                basePrice = priceCents,
                                unitType = unitType,
                                stockQuantity = stock,
                                customMarkupPercent = markup,
                                barcodes = barcodeList
                            )
                            onSave(updated)
                        },
                        enabled = name.isNotBlank() && assignedBarcodeConflictProduct == null,
                        modifier = Modifier.weight(1f).height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (isNewProduct) strings.addProduct else strings.save, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
