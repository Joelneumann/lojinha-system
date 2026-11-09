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

        val key1 = imageService.saveProductImage("vp-tomato", rawPngBytes)
        val thumbFile1 = imageService.getThumbnailFile(key1)
        val fullFile1 = imageService.getFullImageFile(key1)
        assertNotNull(thumbFile1)
        assertNotNull(fullFile1)
        assertTrue(thumbFile1.exists())
        assertTrue(fullFile1.exists())

        val decodedThumb = ImageIO.read(ByteArrayInputStream(imageService.readThumbnailBytes(key1)!!))
        assertEquals(96, decodedThumb.width)
        assertEquals(96, decodedThumb.height)

        val decodedFull = ImageIO.read(ByteArrayInputStream(imageService.readFullImageBytes(key1)!!))
        assertEquals(800, decodedFull.width)
        assertEquals(600, decodedFull.height)

        // Replace image and verify previous files are deleted
        Thread.sleep(5)
        val key2 = imageService.saveProductImage("vp-tomato", rawPngBytes, previousImageFileName = key1)
        assertNotEquals(key1, key2)
        assertNull(imageService.getThumbnailFile(key1))
        assertNull(imageService.getFullImageFile(key1))
        assertNotNull(imageService.getThumbnailFile(key2))
        assertNotNull(imageService.getFullImageFile(key2))

        // Delete product images
        imageService.deleteProductImages(key2)
        assertNull(imageService.getThumbnailFile(key2))
        assertNull(imageService.getFullImageFile(key2))
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
