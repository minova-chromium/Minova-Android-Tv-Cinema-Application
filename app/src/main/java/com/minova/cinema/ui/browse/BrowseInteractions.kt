package com.minova.cinema.ui.browse

import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import com.minova.cinema.domain.MediaContent

internal class BrowseFocusMemory(var lastKey: String?, var restore: Boolean) {
    var onFocus: (String) -> Unit = {}
}
internal val LocalBrowseFocus = staticCompositionLocalOf { BrowseFocusMemory(null, false) }
internal val LocalQuickActions = staticCompositionLocalOf<(MediaContent) -> Unit> { {} }
internal val LocalArtworkNeighbours = staticCompositionLocalOf<(List<MediaContent>, MediaContent) -> Unit> { { _, _ -> } }
internal val LocalArtworkSequence = staticCompositionLocalOf<List<MediaContent>> { emptyList() }
internal val LocalShelfIdentity = staticCompositionLocalOf { "catalog" }

/** Long OK uses Foundation's key long-press handling: releasing it never opens details too. */
internal fun Modifier.mediaInteraction(key: String, content: MediaContent, onOpen: (MediaContent) -> Unit): Modifier = composed {
    val memory = LocalBrowseFocus.current
    val uniqueKey = LocalShelfIdentity.current + ":" + key
    val quickActions = LocalQuickActions.current
    val neighbours = LocalArtworkNeighbours.current
    val sequence = LocalArtworkSequence.current
    val focus = remember { FocusRequester() }
    this.focusRequester(focus)
        .onFocusChanged {
            if (it.isFocused) {
                memory.lastKey = uniqueKey
                memory.onFocus(uniqueKey)
                neighbours(sequence, content)
            }
        }
        .onGloballyPositioned {
            if (memory.restore && memory.lastKey == uniqueKey) {
                memory.restore = false
                focus.requestFocus()
            }
        }
        .combinedClickable(role = Role.Button, onClick = { onOpen(content) }, onLongClickLabel = "Title actions",
            onLongClick = { quickActions(content) })
}
