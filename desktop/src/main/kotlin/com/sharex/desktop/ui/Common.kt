package com.sharex.desktop.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.awt.FileDialog
import java.awt.Frame
import java.awt.datatransfer.DataFlavor
import java.io.File

/** True while files are being dragged over the window. */
val LocalDragging = androidx.compose.runtime.compositionLocalOf { false }

/** Scroll commands from the window's key handler; provided once in [App]. */
val LocalScrollCommands = androidx.compose.runtime.staticCompositionLocalOf<kotlinx.coroutines.flow.SharedFlow<com.sharex.desktop.ScrollCommand>?> { null }

@Composable
fun scrollbarStyle(): androidx.compose.foundation.ScrollbarStyle {
    val c = Theme.colors
    return androidx.compose.foundation.LocalScrollbarStyle.current.copy(
        minimalHeight = 40.dp,
        thickness = 8.dp,
        shape = RoundedCornerShape(4.dp),
        hoverDurationMillis = 200,
        unhoverColor = c.textMuted.copy(alpha = 0.35f),
        hoverColor = c.textMuted.copy(alpha = 0.75f),
    )
}

/** Scrollable page with a header; [onBack] adds the back arrow used on every pushed screen. */
@Composable
fun PageScaffold(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    maxWidth: androidx.compose.ui.unit.Dp = 920.dp,
    trailing: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Theme.colors
    val scrollState = rememberScrollState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var viewport by remember { mutableStateOf(600) }
    val commands = LocalScrollCommands.current
    androidx.compose.runtime.LaunchedEffect(commands) {
        commands?.collect { command ->
            val line = 90f
            val page = viewport * 0.9f
            when (command) {
                com.sharex.desktop.ScrollCommand.LINE_UP -> scrollState.animateScrollBy(-line)
                com.sharex.desktop.ScrollCommand.LINE_DOWN -> scrollState.animateScrollBy(line)
                com.sharex.desktop.ScrollCommand.PAGE_UP -> scrollState.animateScrollBy(-page)
                com.sharex.desktop.ScrollCommand.PAGE_DOWN -> scrollState.animateScrollBy(page)
                com.sharex.desktop.ScrollCommand.TOP -> scrollState.animateScrollTo(0)
                com.sharex.desktop.ScrollCommand.BOTTOM -> scrollState.animateScrollTo(scrollState.maxValue)
            }
        }
    }
    Box(Modifier.fillMaxSize().onSizeChanged { viewport = it.height }) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = maxWidth).fillMaxSize().verticalScroll(scrollState).padding(start = 32.dp, end = 32.dp, top = 24.dp, bottom = 32.dp),
            ) {
                Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (onBack != null) {
                        IconButton(onClick = onBack, modifier = Modifier.size(40.dp).pointerHoverIcon(PointerIcon.Hand)) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = c.text)
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.headlineSmall, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                    }
                    trailing()
                }
                content()
            }
        }
        if (scrollState.maxValue > 0) {
            androidx.compose.foundation.VerticalScrollbar(
                adapter = androidx.compose.foundation.rememberScrollbarAdapter(scrollState),
                style = scrollbarStyle(),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 6.dp, horizontal = 3.dp),
            )
        }
    }
}

/** Drag-and-drop target for files/folders from Explorer. [content] gets whether a drag is hovering. */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun FileDropArea(onDrop: (List<File>) -> Unit, modifier: Modifier = Modifier, content: @Composable BoxScope.(dragging: Boolean) -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    val target = remember(onDrop) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {
                dragging = true
            }

            override fun onExited(event: DragAndDropEvent) {
                dragging = false
            }

            override fun onEnded(event: DragAndDropEvent) {
                dragging = false
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                dragging = false
                val transferable = event.awtTransferable
                if (!transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return false
                val files = (transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>).orEmpty().filterIsInstance<File>()
                onDrop(files)
                return files.isNotEmpty()
            }
        }
    }
    Box(modifier.dragAndDropTarget(shouldStartDragAndDrop = { true }, target = target)) { content(dragging) }
}

fun chooseFiles(window: java.awt.Window, title: String = "Choose files to send"): List<File> {
    val dialog = FileDialog(window as? Frame, title, FileDialog.LOAD).apply { isMultipleMode = true }
    dialog.isVisible = true
    return dialog.files?.toList().orEmpty()
}

fun chooseImage(window: java.awt.Window): File? {
    val dialog = FileDialog(window as? Frame, "Choose a screenshot of the QR code", FileDialog.LOAD).apply {
        setFilenameFilter { _, name -> name.lowercase().let { it.endsWith(".png") || it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".bmp") || it.endsWith(".gif") } }
    }
    dialog.isVisible = true
    return dialog.files?.firstOrNull()
}

/** Pairs of vertical spacing used between stacked cards. */
@Composable
fun Gap(height: androidx.compose.ui.unit.Dp = 12.dp) = Spacer(Modifier.height(height))

@Composable
fun FullRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) =
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, content = content)
