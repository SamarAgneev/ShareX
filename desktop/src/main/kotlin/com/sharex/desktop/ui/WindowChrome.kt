package com.sharex.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Minimize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import java.awt.Cursor
import java.awt.MouseInfo
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.Window

/** Height of the title bar; caption buttons fill it edge to edge like native Windows 11 ones. */
private val TitleBarHeight = 36.dp

/**
 * Frameless window shell: a title bar in the sidebar colour carrying the mark and name on the left, and on the right a
 * "…" menu followed by Windows 11 style caption buttons (minimise, maximise/restore, close) drawn with thin strokes.
 * Invisible resize edges surround the window while it is not maximised.
 */
@Composable
fun FrameWindowScope.WindowChrome(
    state: WindowState,
    onClose: () -> Unit,
    menu: List<MenuAction> = emptyList(),
    content: @Composable () -> Unit,
) {
    val c = Theme.colors
    val maximized = state.placement == WindowPlacement.Maximized
    val toggleMaximize = {
        if (!maximized) window.fitMaximizedBoundsToScreen()
        state.placement = if (maximized) WindowPlacement.Floating else WindowPlacement.Maximized
    }
    Box(Modifier.fillMaxSize().background(c.background)) {
        Column(Modifier.fillMaxSize()) {
            WindowDraggableArea(Modifier.fillMaxWidth().height(TitleBarHeight)) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .background(c.sidebar)
                        .pointerInput(maximized) { detectTapGestures(onDoubleTap = { toggleMaximize() }) }
                        .padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ShareXMark(15.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("ShareX", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = c.textMuted, modifier = Modifier.weight(1f))
                    if (menu.isNotEmpty()) MoreMenuButton(menu)
                    CaptionButton("Minimise", onClick = { state.isMinimized = true }) { tint -> drawMinimize(tint) }
                    CaptionButton(if (maximized) "Restore" else "Maximise", onClick = toggleMaximize) { tint ->
                        if (maximized) drawRestore(tint) else drawMaximize(tint)
                    }
                    CaptionButton("Close", onClick = onClose, danger = true) { tint -> drawClose(tint) }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        }
        if (!maximized) ResizeEdges(window)
    }
}

@Composable
private fun CaptionButton(description: String, onClick: () -> Unit, danger: Boolean = false, glyph: androidx.compose.ui.graphics.drawscope.DrawScope.(Color) -> Unit) {
    val c = Theme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val background = when {
        !hovered -> Color.Transparent
        danger -> Color(0xFFC42B1C)
        else -> c.text.copy(alpha = 0.09f)
    }
    Box(
        Modifier
            .size(width = 46.dp, height = TitleBarHeight)
            .background(background)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        val tint = if (hovered && danger) Color.White else c.text
        Canvas(Modifier.size(10.dp)) { glyph(tint) }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMinimize(tint: Color) {
    val w = 1.dp.toPx()
    drawLine(tint, androidx.compose.ui.geometry.Offset(0f, size.height / 2), androidx.compose.ui.geometry.Offset(size.width, size.height / 2), strokeWidth = w)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMaximize(tint: Color) {
    val w = 1.dp.toPx()
    drawRoundRect(
        tint,
        topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2),
        size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(1.5.dp.toPx()),
        style = androidx.compose.ui.graphics.drawscope.Stroke(w),
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRestore(tint: Color) {
    val w = 1.dp.toPx()
    val off = 2.dp.toPx()
    val side = size.width - off - w
    val stroke = androidx.compose.ui.graphics.drawscope.Stroke(w)
    // Front square.
    drawRoundRect(tint, topLeft = androidx.compose.ui.geometry.Offset(w / 2, off + w / 2), size = androidx.compose.ui.geometry.Size(side, side), cornerRadius = CornerRadius(1.5.dp.toPx()), style = stroke)
    // Back square: only the part that peeks out above and to the right.
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(off + w / 2, off + w / 2 - w)
        lineTo(off + w / 2, w / 2)
        lineTo(size.width - w / 2, w / 2)
        lineTo(size.width - w / 2, side + w / 2)
        lineTo(side + w / 2 + 1.dp.toPx(), side + w / 2)
    }
    drawPath(path, tint, style = stroke)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawClose(tint: Color) {
    val w = 1.dp.toPx()
    drawLine(tint, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, size.height), strokeWidth = w)
    drawLine(tint, androidx.compose.ui.geometry.Offset(size.width, 0f), androidx.compose.ui.geometry.Offset(0f, size.height), strokeWidth = w)
}

/** The "…" button before the caption buttons: opens a menu of app-level actions. */
@Composable
private fun MoreMenuButton(actions: List<MenuAction>) {
    val c = Theme.colors
    var open by remember { androidx.compose.runtime.mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box {
        Box(
            Modifier
                .size(width = 40.dp, height = TitleBarHeight)
                .background(if (hovered || open) c.text.copy(alpha = 0.09f) else Color.Transparent)
                .hoverable(interaction)
                .clickable(interactionSource = interaction, indication = null, role = androidx.compose.ui.semantics.Role.Button) { open = true }
                .semantics { contentDescription = "More options" },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(width = 12.dp, height = 3.dp)) {
                val r = 1.1.dp.toPx()
                for (i in 0..2) drawCircle(c.text, r, androidx.compose.ui.geometry.Offset(r + i * (size.width - 2 * r) / 2, size.height / 2))
            }
        }
        androidx.compose.material3.DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(12.dp),
            containerColor = c.surfaceHigh,
            border = androidx.compose.foundation.BorderStroke(1.dp, c.outline),
            tonalElevation = 0.dp,
            modifier = Modifier.width(230.dp),
        ) {
            actions.forEach { action ->
                val tint = if (action.destructive) c.danger else c.text
                val leading: (@Composable () -> Unit)? = action.icon?.let { icon ->
                    @Composable { Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp)) }
                }
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(action.label, fontSize = 13.sp, color = tint) },
                    leadingIcon = leading,
                    onClick = {
                        open = false
                        action.onClick()
                    },
                )
            }
        }
    }
}

/** Makes an undecorated window maximise to the work area instead of covering the taskbar. */
fun Window.fitMaximizedBoundsToScreen() {
    val frame = this as? java.awt.Frame ?: return
    val config = graphicsConfiguration ?: return
    val insets = Toolkit.getDefaultToolkit().getScreenInsets(config)
    val screen = config.bounds
    frame.maximizedBounds = Rectangle(
        screen.x + insets.left,
        screen.y + insets.top,
        screen.width - insets.left - insets.right,
        screen.height - insets.top - insets.bottom,
    )
}

private enum class Edge(val cursor: Int, val left: Boolean = false, val top: Boolean = false, val right: Boolean = false, val bottom: Boolean = false) {
    N(Cursor.N_RESIZE_CURSOR, top = true),
    S(Cursor.S_RESIZE_CURSOR, bottom = true),
    W(Cursor.W_RESIZE_CURSOR, left = true),
    E(Cursor.E_RESIZE_CURSOR, right = true),
    NW(Cursor.NW_RESIZE_CURSOR, left = true, top = true),
    NE(Cursor.NE_RESIZE_CURSOR, right = true, top = true),
    SW(Cursor.SW_RESIZE_CURSOR, left = true, bottom = true),
    SE(Cursor.SE_RESIZE_CURSOR, right = true, bottom = true),
}

@Composable
private fun BoxScope.ResizeEdges(window: Window) {
    val thickness = 5.dp
    val corner = 10.dp
    ResizeHandle(window, Edge.N, Modifier.align(Alignment.TopCenter).fillMaxWidth().height(thickness).padding(horizontal = corner))
    ResizeHandle(window, Edge.S, Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(thickness).padding(horizontal = corner))
    ResizeHandle(window, Edge.W, Modifier.align(Alignment.CenterStart).fillMaxHeight().width(thickness).padding(vertical = corner))
    ResizeHandle(window, Edge.E, Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(thickness).padding(vertical = corner))
    ResizeHandle(window, Edge.NW, Modifier.align(Alignment.TopStart).size(corner))
    ResizeHandle(window, Edge.NE, Modifier.align(Alignment.TopEnd).size(corner))
    ResizeHandle(window, Edge.SW, Modifier.align(Alignment.BottomStart).size(corner))
    ResizeHandle(window, Edge.SE, Modifier.align(Alignment.BottomEnd).size(corner))
}

@Composable
private fun ResizeHandle(window: Window, edge: Edge, modifier: Modifier) {
    Box(
        modifier
            .pointerHoverIcon(PointerIcon(Cursor(edge.cursor)))
            .pointerInput(edge) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    val start = MouseInfo.getPointerInfo()?.location ?: return@awaitEachGesture
                    val bounds = window.bounds
                    val min = window.minimumSize
                    while (true) {
                        val event = awaitPointerEvent()
                        val now = MouseInfo.getPointerInfo()?.location ?: break
                        val dx = now.x - start.x
                        val dy = now.y - start.y
                        var x = bounds.x
                        var y = bounds.y
                        var w = bounds.width
                        var h = bounds.height
                        if (edge.right) w = maxOf(min.width, bounds.width + dx)
                        if (edge.bottom) h = maxOf(min.height, bounds.height + dy)
                        if (edge.left) {
                            w = maxOf(min.width, bounds.width - dx)
                            x = bounds.x + bounds.width - w
                        }
                        if (edge.top) {
                            h = maxOf(min.height, bounds.height - dy)
                            y = bounds.y + bounds.height - h
                        }
                        window.setBounds(x, y, w, h)
                        event.changes.forEach { it.consume() }
                        if (event.changes.none { it.pressed }) break
                    }
                }
            },
    )
}
