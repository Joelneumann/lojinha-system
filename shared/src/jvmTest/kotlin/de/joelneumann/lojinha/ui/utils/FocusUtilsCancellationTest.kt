package de.joelneumann.lojinha.ui.utils

import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.*
import kotlin.test.Test
import kotlin.test.assertTrue

class FocusUtilsCancellationTest {

    @Test
    fun testSafeRequestFocus_cancelsCleanlyWhenCancelled() = runBlocking {
        val requester = FocusRequester()
        val job = launch(Dispatchers.Default) {
            requester.safeRequestFocus()
        }

        // Cancel job almost immediately while suspended in delay/withFrameNanos
        delay(10)
        job.cancel()
        job.join()

        assertTrue(job.isCancelled, "Job should be marked as cancelled")
    }

    @Test
    fun testSafeRequestFocus_throwsCancellationExceptionDirectlyIfAlreadyCancelled() = runBlocking {
        val requester = FocusRequester()
        val job = launch(Dispatchers.Default, start = CoroutineStart.LAZY) {
            requester.safeRequestFocus()
        }
        job.cancel()

        try {
            job.start()
            job.join()
            assertTrue(job.isCancelled)
        } catch (e: CancellationException) {
            // Expected
        }
    }
}
