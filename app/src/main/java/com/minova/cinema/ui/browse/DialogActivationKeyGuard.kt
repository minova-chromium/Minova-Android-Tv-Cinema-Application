package com.minova.cinema.ui.browse

/**
 * A popup can receive repeats/UP from the gesture that opened it, even though
 * its initial DOWN belonged to the previous window. Only accept complete new
 * activation gestures that began here; never use a timing-based debounce.
 */
internal class DialogActivationKeyGuard {
    private val pressedHere = mutableSetOf<Int>()

    fun consume(keyCode: Int, isDown: Boolean, repeatCount: Int, canceled: Boolean = false): Boolean {
        if (canceled) {
            pressedHere.remove(keyCode)
            return true
        }
        if (isDown) {
            if (repeatCount == 0) pressedHere.add(keyCode)
            return keyCode !in pressedHere
        }
        return !pressedHere.remove(keyCode)
    }
}
