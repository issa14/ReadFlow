package com.readflow.ui.screen.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus

class HighlightTextToolbar(
    private val defaultToolbar: TextToolbar,
    private val onShow: (Rect, (() -> Unit)?, (() -> Unit)?) -> Unit,
    private val onHide: () -> Unit
) : TextToolbar {

    override val status: TextToolbarStatus
        get() = defaultToolbar.status

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) {
        onShow(rect, onCopyRequested, onSelectAllRequested)
    }

    override fun hide() {
        defaultToolbar.hide()
        onHide()
    }
}

@Composable
fun HighlightSelectionWrapper(
    onSelection: (Rect?, (() -> Unit)?, (() -> Unit)?) -> Unit,
    content: @Composable () -> Unit
) {
    val defaultToolbar = LocalTextToolbar.current

    val customToolbar = remember {
        HighlightTextToolbar(
            defaultToolbar = defaultToolbar,
            onShow = { rect, onCopy, onSelectAll -> onSelection(rect, onCopy, onSelectAll) },
            onHide = { onSelection(null, null, null) }
        )
    }

    CompositionLocalProvider(
        LocalTextToolbar provides customToolbar
    ) {
        content()
    }
}
