package com.minova.cinema.data

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class RequestCoalescerTest {
    @Test
    fun `concurrent identical reads share one Plex request`() = runBlocking {
        val cache = RequestCoalescer<String, String>(ttlMs = 30_000L, maxEntries = 8)
        val calls = AtomicInteger()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        suspend fun load(): String {
            calls.incrementAndGet()
            entered.complete(Unit)
            release.await()
            return "details"
        }

        val first = async { cache.get("42", ::load) }
        entered.await()
        val second = async { cache.get("42", ::load) }
        release.complete(Unit)

        assertEquals("details", first.await())
        assertEquals("details", second.await())
        assertEquals(1, calls.get())
    }

    @Test
    fun `expired and invalidated entries reload`() = runBlocking {
        var clock = 0L
        var calls = 0
        val cache = RequestCoalescer<String, Int>(ttlMs = 100L, maxEntries = 2) { clock }

        assertEquals(1, cache.get("movie") { ++calls })
        clock = 100L
        assertEquals(1, cache.get("movie") { ++calls })
        clock = 101L
        assertEquals(2, cache.get("movie") { ++calls })
        cache.invalidate("movie")
        assertEquals(3, cache.get("movie") { ++calls })
    }

    @Test
    fun `failed reads are not cached`() = runBlocking {
        var calls = 0
        val cache = RequestCoalescer<String, String>(ttlMs = 30_000L, maxEntries = 2)

        try {
            cache.get("episode") {
                calls++
                error("temporary Plex failure")
            }
            fail("The Plex failure should reach the caller.")
        } catch (_: IllegalStateException) {
            // A retry must own a fresh load instead of inheriting the failed result.
        }

        assertEquals("recovered", cache.get("episode") { calls++; "recovered" })
        assertEquals(2, calls)
    }
}
