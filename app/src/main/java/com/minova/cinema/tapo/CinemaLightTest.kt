package com.minova.cinema.tapo

import kotlinx.coroutines.*

internal interface TestableCinemaLight {
    suspend fun snapshot(): TapoDeviceInfo
    suspend fun brightness(value: Int)
}

/** Each bulb owns its snapshot. Cancellation, a timeout or a failed dim still restores it. */
internal suspend fun testCinemaLight(light: TestableCinemaLight, report: (String) -> Unit,
    wait: suspend (Long) -> Unit = { delay(it) }) {
    val original = try { withTimeout(8_000) { light.snapshot() } }
    catch (cancelled: CancellationException) {
        if (cancelled !is TimeoutCancellationException) throw cancelled
        report("No response — check power, LAN and Third-Party Compatibility")
        return
    } catch (_: Exception) {
        report("Cannot connect — check Tapo login and Third-Party Compatibility")
        return
    }
    if (!original.isOn) { report("Responded · already off, left unchanged"); return }
    var dimmed = false
    try {
        report("Dimming from ${original.brightness}%…")
        withTimeout(12_000) {
            for (step in 1..20) {
                light.brightness(easedBrightness(original.brightness, 0, step, 20))
                wait(200)
            }
        }
        dimmed = true
    } finally {
        // Restoration cannot inherit cancellation from a closed screen or a new playback request.
        withContext(NonCancellable) {
            report("Restoring ${original.brightness}%…")
            val restored = runCatching {
                withTimeout(10_000) {
                    val current = light.snapshot()
                    for (step in 1..10) {
                        light.brightness(easedBrightness(if (current.isOn) current.brightness else 0,
                            original.brightness, step, 10))
                        wait(150)
                    }
                    val confirmed = light.snapshot()
                    check(confirmed.isOn && kotlin.math.abs(confirmed.brightness - original.brightness) <= 2)
                }
            }.isSuccess || runCatching {
                withTimeout(5_000) {
                    light.brightness(original.brightness)
                    val confirmed = light.snapshot()
                    check(confirmed.isOn && kotlin.math.abs(confirmed.brightness - original.brightness) <= 2)
                }
            }.isSuccess
            report(when {
                !restored -> "Restore failed — set ${original.brightness}% in the Tapo app"
                dimmed -> "Passed · restored ${original.brightness}%"
                else -> "Dim interrupted or failed · restored ${original.brightness}%"
            })
        }
    }
}
