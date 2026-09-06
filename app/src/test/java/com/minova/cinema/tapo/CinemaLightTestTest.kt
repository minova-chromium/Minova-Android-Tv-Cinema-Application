package com.minova.cinema.tapo

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class CinemaLightTestTest {
    private class Bulb(var level: Int = 73, var on: Boolean = true, var failDim: Boolean = false) : TestableCinemaLight {
        val commands = mutableListOf<Int>()
        override suspend fun snapshot() = TapoDeviceInfo("Fixture", "L630", on, level)
        override suspend fun brightness(value: Int) {
            if (failDim && value < 60) { failDim = false; error("simulated network failure") }
            commands.add(value); level = value; on = value > 0
        }
    }
    @Test fun successfulTestRestoresExactBrightness() = runBlocking {
        val bulb = Bulb(); val reports = mutableListOf<String>()
        testCinemaLight(bulb, reports::add, wait = {})
        assertEquals(73, bulb.level)
        assertTrue(bulb.commands.contains(0))
        assertTrue(reports.last().startsWith("Passed"))
    }
    @Test fun originallyOffBulbIsNotTurnedOn() = runBlocking {
        val bulb = Bulb(on = false)
        testCinemaLight(bulb, {}, wait = {})
        assertTrue(bulb.commands.isEmpty())
    }
    @Test fun failedDimStillRestoresOriginalBrightness() = runBlocking {
        val bulb = Bulb(failDim = true)
        runCatching { testCinemaLight(bulb, {}, wait = {}) }
        assertEquals(73, bulb.level)
    }
    @Test fun cancellationStillRestoresOriginalBrightness() = runBlocking {
        val bulb = Bulb()
        val job = launch { testCinemaLight(bulb, {}, wait = { yield() }) }
        yield(); job.cancelAndJoin()
        assertEquals(73, bulb.level)
    }
}
