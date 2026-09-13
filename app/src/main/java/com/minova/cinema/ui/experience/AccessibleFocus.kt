package com.minova.cinema.ui.experience

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal fun Modifier.accessibleFocus(): Modifier = composed {
    val enabled = LocalExperienceSettings.current.strongFocus
    var focused by remember { mutableStateOf(false) }
    onFocusChanged { focused = it.hasFocus }.then(
        if (enabled && focused) Modifier.border(3.dp, Color(0xFF16D8E4), RoundedCornerShape(12.dp)) else Modifier,
    )
}
