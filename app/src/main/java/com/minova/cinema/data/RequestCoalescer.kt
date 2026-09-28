package com.minova.cinema.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Coalesces identical in-flight Plex reads and retains a small, short-lived LRU.
 * Failures are never cached, so Retry always reaches Plex again.
 */
internal class RequestCoalescer<K, V>(
    private val ttlMs: Long,
    private val maxEntries: Int,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private val mutex = Mutex()
    private val cached = LinkedHashMap<K, CacheEntry<V>>(16, 0.75f, true)
    private val inFlight = mutableMapOf<K, CompletableDeferred<V>>()

    init {
        require(ttlMs >= 0L)
        require(maxEntries > 0)
    }

    suspend fun get(key: K, loader: suspend () -> V): V {
        val lookup = mutex.withLock {
            val entry = cached[key]
            if (entry != null && nowMs() - entry.savedAtMs <= ttlMs) {
                Lookup.Cached(entry.value)
            } else {
                if (entry != null) cached.remove(key)
                val existing = inFlight[key]
                if (existing != null) {
                    Lookup.Pending(existing, owner = false)
                } else {
                    val created = CompletableDeferred<V>()
                    inFlight[key] = created
                    Lookup.Pending(created, owner = true)
                }
            }
        }
        if (lookup is Lookup.Cached) return lookup.value
        lookup as Lookup.Pending
        if (!lookup.owner) return lookup.deferred.await()

        try {
            val value = loader()
            mutex.withLock {
                cached[key] = CacheEntry(value, nowMs())
                while (cached.size > maxEntries) cached.remove(cached.entries.first().key)
                inFlight.remove(key, lookup.deferred)
            }
            lookup.deferred.complete(value)
            return value
        } catch (error: Throwable) {
            mutex.withLock { inFlight.remove(key, lookup.deferred) }
            lookup.deferred.completeExceptionally(error)
            throw error
        }
    }

    suspend fun invalidate(key: K) {
        mutex.withLock { cached.remove(key) }
    }

    private data class CacheEntry<V>(val value: V, val savedAtMs: Long)

    private sealed interface Lookup<out V> {
        data class Cached<V>(val value: V) : Lookup<V>
        data class Pending<V>(val deferred: CompletableDeferred<V>, val owner: Boolean) : Lookup<V>
    }
}
