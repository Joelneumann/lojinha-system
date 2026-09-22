package de.joelneumann.lojinha.data.service

import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DataChangeNotifierTest {

    @BeforeTest
    @AfterTest
    fun cleanup() {
        DataChangeNotifier.clearListeners()
    }

    @Test
    fun testListenerRegistrationAndNotification() {
        var callCount = 0
        val listener: () -> Unit = { callCount++ }

        DataChangeNotifier.addListener(listener)
        DataChangeNotifier.notifyDataChanged()
        assertEquals(1, callCount)

        DataChangeNotifier.notifyDataChanged()
        assertEquals(2, callCount)

        DataChangeNotifier.removeListener(listener)
        DataChangeNotifier.notifyDataChanged()
        assertEquals(2, callCount)
    }

    @Test
    fun testMultipleListeners() {
        var count1 = 0
        var count2 = 0
        val l1: () -> Unit = { count1++ }
        val l2: () -> Unit = { count2++ }

        DataChangeNotifier.addListener(l1)
        DataChangeNotifier.addListener(l2)

        DataChangeNotifier.notifyDataChanged()
        assertEquals(1, count1)
        assertEquals(1, count2)

        DataChangeNotifier.removeListener(l1)
        DataChangeNotifier.notifyDataChanged()
        assertEquals(1, count1)
        assertEquals(2, count2)
    }

    @Test
    fun testConcurrentNotifications() {
        val totalCalls = AtomicInteger(0)
        val listener: () -> Unit = { totalCalls.incrementAndGet() }

        DataChangeNotifier.addListener(listener)

        val threads = (1..10).map {
            thread {
                repeat(50) {
                    DataChangeNotifier.notifyDataChanged()
                }
            }
        }
        threads.forEach { it.join() }

        assertEquals(500, totalCalls.get())
    }
}
