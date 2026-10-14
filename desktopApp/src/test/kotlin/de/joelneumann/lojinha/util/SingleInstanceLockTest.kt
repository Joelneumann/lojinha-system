package de.joelneumann.lojinha.util

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.OverlappingFileLockException

class SingleInstanceLockTest {

    private lateinit var tempLockFile: File

    @Before
    fun setUp() {
        tempLockFile = File.createTempFile("lojinha_test_lock_", ".lock")
        tempLockFile.deleteOnExit()
        SingleInstanceLock.release()
    }

    @After
    fun tearDown() {
        SingleInstanceLock.release()
        if (tempLockFile.exists()) {
            tempLockFile.delete()
        }
    }

    @Test
    fun testAcquireAndRelease() {
        assertFalse("Lock should not be held initially", SingleInstanceLock.isLocked())

        val acquired = SingleInstanceLock.acquire(tempLockFile)
        assertTrue("Should acquire lock on fresh file", acquired)
        assertTrue("isLocked() should report true", SingleInstanceLock.isLocked())

        SingleInstanceLock.release()
        assertFalse("isLocked() should report false after release", SingleInstanceLock.isLocked())
    }

    @Test
    fun testSubsequentAcquireOnSameFileFromAnotherChannelFails() {
        val acquired = SingleInstanceLock.acquire(tempLockFile)
        assertTrue("Primary lock must succeed", acquired)

        // Attempt to acquire lock on the same file via a separate file channel
        val secondaryRaf = RandomAccessFile(tempLockFile, "rw")
        val secondaryChannel = secondaryRaf.channel
        try {
            val secondaryLock = try {
                secondaryChannel.tryLock()
            } catch (_: OverlappingFileLockException) {
                null
            }
            assertNull("Secondary channel should NOT be able to acquire lock while primary holds it", secondaryLock)
        } finally {
            secondaryChannel.close()
            secondaryRaf.close()
        }
    }

    @Test
    fun testCanReacquireAfterRelease() {
        val firstAcquire = SingleInstanceLock.acquire(tempLockFile)
        assertTrue("First acquire must succeed", firstAcquire)

        SingleInstanceLock.release()
        assertFalse("Lock must be reported as released", SingleInstanceLock.isLocked())

        val secondAcquire = SingleInstanceLock.acquire(tempLockFile)
        assertTrue("Re-acquiring after release must succeed", secondAcquire)
    }
}
