package de.joelneumann.lojinha.data.service

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import de.joelneumann.lojinha.data.database.AppDatabase
import de.joelneumann.lojinha.data.database.AppDatabase_Impl
import de.joelneumann.lojinha.data.database.DatabaseFactory
import de.joelneumann.lojinha.data.repository.RoomSettingsRepositoryImpl
import de.joelneumann.lojinha.data.repository.RoomVeggieOrderRepositoryImpl
import de.joelneumann.lojinha.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.*

class VeggieServicesJvmTest {

    private lateinit var tempDir: File

    @BeforeTest
    fun setup() {
        tempDir = Files.createTempDirectory("veggie_services_test").toFile()
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testVeggieImageService_resizesTo96x96ThumbAnd800x600Full_andCleansUpOldImages() {
        val imageDir = File(tempDir, "veggie_images")
        val imageService = VeggieImageService(imageDir)

        // Create a synthetic 1200x900 PNG image in memory
        val rawPngBytes = createSyntheticPngBytes(width = 1200, height = 900, color = Color(34, 197, 94))

        val oldUuidKey = "90e3251b-old-uuid"
        // Pretend an old image file existed for previous key
        val oldThumb = File(imageDir, "${oldUuidKey}_thumb.jpg").apply { writeText("old") }
        val oldFull = File(imageDir, "${oldUuidKey}_full.jpg").apply { writeText("old") }
        assertTrue(oldThumb.exists())
        assertTrue(oldFull.exists())

        // Save new image with product name "Tomato"
        val key = imageService.saveProductImage(
            productId = "vp-tomato",
            productName = "Tomato",
            rawImageBytes = rawPngBytes,
            previousImageFileName = oldUuidKey
        )
        assertEquals("tomato", key)

        val thumbFile1 = imageService.getThumbnailFile(key)
        val fullFile1 = imageService.getFullImageFile(key)
        assertNotNull(thumbFile1)
        assertNotNull(fullFile1)
        assertTrue(thumbFile1.exists())
        assertTrue(fullFile1.exists())

        // Old UUID files should have been cleaned up
        assertFalse(oldThumb.exists())
        assertFalse(oldFull.exists())

        val decodedThumb = ImageIO.read(ByteArrayInputStream(imageService.readThumbnailBytes(key)!!))
        assertEquals(96, decodedThumb.width)
        assertEquals(96, decodedThumb.height)

        val decodedFull = ImageIO.read(ByteArrayInputStream(imageService.readFullImageBytes(key)!!))
        assertEquals(800, decodedFull.width)
        assertEquals(600, decodedFull.height)

        // Delete product images
        imageService.deleteProductImages(key)
        assertNull(imageService.getThumbnailFile(key))
        assertNull(imageService.getFullImageFile(key))
    }

    @Test
    fun testVeggieImageService_usesProductName_andSuffixesDuplicates() {
        val imageDir = File(tempDir, "veggie_images_naming")
        val imageService = VeggieImageService(imageDir)
        val rawPngBytes = createSyntheticPngBytes(width = 100, height = 100, color = Color.RED)

        // 1. First product named "Tomate"
        val key1 = imageService.saveProductImage(
            productId = "prod-1",
            productName = "Tomate",
            rawImageBytes = rawPngBytes
        )
        assertEquals("tomate", key1)
        assertTrue(File(imageDir, "tomate_thumb.jpg").exists())
        assertTrue(File(imageDir, "tomate_full.jpg").exists())

        // 2. Second product also named "Tomate" -> should get _2
        val key2 = imageService.saveProductImage(
            productId = "prod-2",
            productName = "Tomate",
            rawImageBytes = rawPngBytes,
            existingImageKeys = listOf(key1)
        )
        assertEquals("tomate_2", key2)
        assertTrue(File(imageDir, "tomate_2_thumb.jpg").exists())
        assertTrue(File(imageDir, "tomate_2_full.jpg").exists())

        // 3. Third product also named "Tomate" -> should get _3
        val key3 = imageService.saveProductImage(
            productId = "prod-3",
            productName = "Tomate",
            rawImageBytes = rawPngBytes,
            existingImageKeys = listOf(key1, key2)
        )
        assertEquals("tomate_3", key3)
        assertTrue(File(imageDir, "tomate_3_thumb.jpg").exists())
        assertTrue(File(imageDir, "tomate_3_full.jpg").exists())

        // 4. Updating product 1's image should maintain "tomate" (not increment to _4)
        val key1Updated = imageService.saveProductImage(
            productId = "prod-1",
            productName = "Tomate",
            rawImageBytes = rawPngBytes,
            previousImageFileName = "tomate",
            existingImageKeys = listOf(key2, key3)
        )
        assertEquals("tomate", key1Updated)

        // 5. Special characters and accents normalization: "Açafrão & Maçã"
        val keyAccents = imageService.saveProductImage(
            productId = "prod-4",
            productName = "Açafrão & Maçã (100g)",
            rawImageBytes = rawPngBytes
        )
        assertEquals("acafrao_maca_100g", keyAccents)
    }

    @Test
    fun testVeggieRoundAutoScheduler_opensRoundWhenDueAndAvoidsDuplicatesSameWeek() = runTest {
        val dbFile = File(tempDir, "scheduler_test.db")
        val db = Room.databaseBuilder<AppDatabase>(
            name = dbFile.absolutePath,
            factory = { AppDatabase_Impl() }
        ).setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

        try {
            db.veggieOrderDao().insertOrUpdateUnitTypes(DatabaseFactory.defaultVeggieUnitTypes(1000L))
            val veggieRepo = RoomVeggieOrderRepositoryImpl(
                veggieOrderDao = db.veggieOrderDao(),
                userDao = db.userDao(),
                transactionDao = db.transactionDao()
            )
            val settingsRepo = RoomSettingsRepositoryImpl(db.settingsDao())

            // Configure auto-open for Monday (1) at 08:00 with standard fees R$ 15,00 and R$ 25,00
            settingsRepo.updateSettings(
                SystemSettings(
                    veggieAutoOpenEnabled = true,
                    veggieAutoOpenDayOfWeek = 1,
                    veggieAutoOpenTime = "08:00",
                    defaultVeggieBuyerFeeCents = 15_00L,
                    defaultVeggieTransportFeeCents = 25_00L
                )
            )

            val scheduler = VeggieRoundAutoScheduler(veggieRepo, settingsRepo, backgroundScope)
            val utc = TimeZone.UTC

            // 1. Monday 2026-04-20 at 07:30 UTC (before 08:00 slot) -> should NOT open
            val beforeSlot = LocalDateTime(2026, 4, 20, 7, 30, 0).toInstant(utc).toEpochMilliseconds()
            assertFalse(scheduler.evaluateAndAutoOpenIfDue(beforeSlot, utc))
            assertNull(veggieRepo.getActiveOrLatestRound())

            // 2. Monday 2026-04-20 at 08:05 UTC -> SHOULD auto-open with default fees!
            val afterSlot = LocalDateTime(2026, 4, 20, 8, 5, 0).toInstant(utc).toEpochMilliseconds()
            assertTrue(scheduler.evaluateAndAutoOpenIfDue(afterSlot, utc))

            val openedRound = veggieRepo.getActiveOrLatestRound()
            assertNotNull(openedRound)
            assertEquals(VeggieRoundStatus.OPEN, openedRound.status)
            assertEquals(15_00L, openedRound.buyerOperationFeeCents)
            assertEquals(25_00L, openedRound.transportFeeCents)

            // 3. Evaluate again while round is OPEN -> should NOT open a second round
            val mondayLater = LocalDateTime(2026, 4, 20, 9, 0, 0).toInstant(utc).toEpochMilliseconds()
            assertFalse(scheduler.evaluateAndAutoOpenIfDue(mondayLater, utc))

            // 4. Close and Settle the round on Wednesday 2026-04-22, then evaluate on Thursday 2026-04-23 ->
            //    Still in the same ISO week, so it must NOT open another round for this week!
            val wednesday = LocalDateTime(2026, 4, 22, 12, 0, 0).toInstant(utc).toEpochMilliseconds()
            veggieRepo.closeRound(openedRound.id, wednesday)
            veggieRepo.executeRoundSettlement(
                roundId = openedRound.id,
                buyerOperationFeeCents = 15_00L,
                transportFeeCents = 25_00L,
                transporterUserId = null,
                productSettlements = emptyList(),
                actualQuantitiesByEntryId = emptyMap(),
                settledAt = wednesday + 1000L
            )

            val thursday = LocalDateTime(2026, 4, 23, 10, 0, 0).toInstant(utc).toEpochMilliseconds()
            assertFalse(scheduler.evaluateAndAutoOpenIfDue(thursday, utc))
            assertEquals(1, veggieRepo.getAllRounds().size)

            // 5. Next Monday 2026-04-27 at 08:01 UTC -> SHOULD open Week 18's round!
            val nextMonday = LocalDateTime(2026, 4, 27, 8, 1, 0).toInstant(utc).toEpochMilliseconds()
            assertTrue(scheduler.evaluateAndAutoOpenIfDue(nextMonday, utc))
            assertEquals(2, veggieRepo.getAllRounds().size)

            // 6. Deleting the Week 18 round must NOT cause the scheduler to recreate it for the same slot!
            val week18Round = veggieRepo.getActiveOrLatestRound()
            assertNotNull(week18Round)
            assertTrue(veggieRepo.deleteRound(week18Round.id))
            val nextMondayLater = LocalDateTime(2026, 4, 27, 8, 5, 0).toInstant(utc).toEpochMilliseconds()
            assertFalse(scheduler.evaluateAndAutoOpenIfDue(nextMondayLater, utc))
            assertEquals(1, veggieRepo.getAllRounds().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun testVeggieOrderPdfGenerator_generatesBuyerListAndWorkerMatrixPdfs() {
        val round = VeggieOrderRound(
            id = "round-pdf-1",
            openedAt = 1_776_672_000_000L, // Week 17 of 2026
            status = VeggieRoundStatus.CLOSED,
            buyerOperationFeeCents = 15_00L,
            transportFeeCents = 20_00L
        )

        // Create entries across 12 customers (to also test > 10 customers horizontal pagination in Worker Matrix)
        val entries = (1..12).flatMap { idx ->
            val userId = "u-$idx"
            val userName = "Customer ${idx.toString().padStart(2, '0')}"
            listOf(
                VeggieOrderEntry(
                    id = "e-tom-$idx",
                    roundId = round.id,
                    userId = userId,
                    userNameSnapshot = userName,
                    veggieProductId = "vp-tomato",
                    veggieNameSnapshot = "Tomato",
                    unitNameSnapshot = "kg",
                    allowDecimalsSnapshot = true,
                    orderedQuantityScaled = 1500L
                ),
                VeggieOrderEntry(
                    id = "e-let-$idx",
                    roundId = round.id,
                    userId = userId,
                    userNameSnapshot = userName,
                    veggieProductId = "vp-lettuce",
                    veggieNameSnapshot = "Lettuce",
                    unitNameSnapshot = "Unit",
                    allowDecimalsSnapshot = false,
                    orderedQuantityScaled = 2000L
                )
            )
        }

        for (lang in Language.entries) {
            val buyerFile = File(tempDir, "buyer_list_${lang.code}.pdf")
            val workerFile = File(tempDir, "worker_matrix_${lang.code}.pdf")

            VeggieOrderPdfGenerator.generateBuyerShoppingListPdf(buyerFile, round, entries, lang)
            VeggieOrderPdfGenerator.generateWorkerSortingMatrixPdf(workerFile, round, entries, lang)

            assertTrue(buyerFile.exists() && buyerFile.length() > 500L)
            assertTrue(workerFile.exists() && workerFile.length() > 500L)

            val buyerHeader = String(buyerFile.readBytes().take(5).toByteArray())
            val workerHeader = String(workerFile.readBytes().take(5).toByteArray())
            assertEquals("%PDF-", buyerHeader)
            assertEquals("%PDF-", workerHeader)
        }
    }

    private fun createSyntheticPngBytes(width: Int, height: Int, color: Color): ByteArray {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        try {
            g.color = color
            g.fillRect(0, 0, width, height)
        } finally {
            g.dispose()
        }
        val out = ByteArrayOutputStream()
        ImageIO.write(img, "png", out)
        return out.toByteArray()
    }
}
