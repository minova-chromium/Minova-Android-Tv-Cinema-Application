package com.minova.cinema.ui.browse

import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.request.SuccessResult
import com.minova.cinema.data.local.artworkRequest
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** Real HTTP/Coil decode/cache checks with deterministic latency, no Plex account required. */
class ArtworkPerformanceTest {
    @Test fun coldImagesMeetBudgetAndWarmRevisitUsesMemoryCache() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val server = ServerSocket(0)
        val executor = Executors.newCachedThreadPool()
        val count = AtomicInteger()
        val bitmap = Bitmap.createBitmap(400, 600, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.CYAN) }
        val bytes = ByteArrayOutputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); output.toByteArray() }
        bitmap.recycle()
        executor.submit {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                executor.submit {
                    socket.use {
                        val reader = it.getInputStream().bufferedReader()
                        while (!reader.readLine().isNullOrEmpty()) { /* headers */ }
                        count.incrementAndGet()
                        Thread.sleep(250)
                        it.getOutputStream().apply {
                            write("HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nContent-Length: ${bytes.size}\r\nCache-Control: max-age=3600\r\nConnection: close\r\n\r\n".toByteArray())
                            write(bytes); flush()
                        }
                    }
                }
            }
        }
        val loader = ImageLoader.Builder(context).build()
        try {
            val requests = (1..8).map { artworkRequest(context, "http://127.0.0.1:${server.localPort}/$it.png", false) }
            val coldStart = SystemClock.elapsedRealtime()
            val cold = requests.map { async { loader.execute(it) } }.awaitAll()
            assertTrue(cold.all { it is SuccessResult })
            val coldMs = SystemClock.elapsedRealtime() - coldStart
            assertTrue("8 visible images took ${coldMs}ms (budget 3500ms)", coldMs < 3500)
            val beforeWarm = count.get()
            val warmStart = SystemClock.elapsedRealtime()
            val warm = requests.map { loader.execute(it) as SuccessResult }
            val warmMs = SystemClock.elapsedRealtime() - warmStart
            assertTrue(warm.all { it.dataSource == DataSource.MEMORY_CACHE })
            assertEquals(beforeWarm, count.get())
            assertTrue("Warm revisit took ${warmMs}ms (budget 750ms)", warmMs < 750)
            loader.memoryCache?.clear()
            val diskStart = SystemClock.elapsedRealtime()
            val disk = requests.map { loader.execute(it) as SuccessResult }
            val diskMs = SystemClock.elapsedRealtime() - diskStart
            assertTrue(disk.all { it.dataSource == DataSource.DISK })
            assertEquals(beforeWarm, count.get())
            assertTrue("Disk revisit took ${diskMs}ms (budget 1500ms)", diskMs < 1500)
            android.util.Log.i("MinovaPerformance", "fixture cold8=${coldMs}ms warm8=${warmMs}ms disk8=${diskMs}ms networkRequests=${count.get()}")
            Unit
        } finally {
            loader.shutdown(); server.close(); executor.shutdownNow()
        }
    }
}
