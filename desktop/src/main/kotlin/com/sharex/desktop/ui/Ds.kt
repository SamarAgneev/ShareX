package com.sharex.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sharex.core.engine.DeviceEntry
import com.sharex.core.engine.DeviceFilter
import java.text.DateFormat
import java.util.Date

private val AvatarPalette = listOf(
    Color(0xFF1E88E5), Color(0xFF8E24AA), Color(0xFFE53935), Color(0xFF43A047),
    Color(0xFFFFB300), Color(0xFF00897B), Color(0xFFF4511E), Color(0xFF5E6B7C),
)

/** Stable per-device colour so the same device looks the same on every screen (and matches Android). */
fun deviceTint(id: String): Color = AvatarPalette[(id.hashCode() and Int.MAX_VALUE) % AvatarPalette.size]

@Composable
fun DeviceBadge(entry: DeviceEntry, size: Dp = 48.dp) {
    val c = Theme.colors
    val tint = deviceTint(entry.id)
    Box(Modifier.size(size)) {
        Box(
            Modifier.size(size).clip(CircleShape).background(tint.copy(alpha = if (c.isDark) 0.28f else 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(entry.type.icon(), entry.type.name.lowercase(), tint = tint, modifier = Modifier.size(size * 0.46f))
        }
        if (entry.trusted) {
            Box(
                Modifier.align(Alignment.BottomEnd).size(size * 0.36f).clip(CircleShape).background(c.background).padding(2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Verified, "Trusted", tint = c.success, modifier = Modifier.size(size * 0.3f))
            }
        }
    }
}

@Composable
fun StatusDot(online: Boolean, size: Dp = 9.dp) {
    val c = Theme.colors
    val color by animateColorAsState(if (online) c.online else c.offline, tween(300), label = "dot")
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

/** Dot + word, so the state never depends on colour alone. */
@Composable
fun OnlineStatus(online: Boolean, label: String = if (online) "Online" else "Offline") {
    val c = Theme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusDot(online)
        Spacer(Modifier.width(7.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = if (online) c.online else c.textMuted, maxLines = 1)
    }
}

fun relativeTime(time: Long): String {
    val diff = System.currentTimeMillis() - time
    val minute = 60_000L
    return when {
        diff < minute -> "just now"
        diff < 60 * minute -> "${diff / minute} min ago"
        diff < 24 * 60 * minute -> "${diff / (60 * minute)} hr ago"
        diff < 2 * 24 * 60 * minute -> "yesterday"
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(time))
    }
}

/** Uses the real stored timestamp; says so when none was ever recorded. */
fun lastSeenText(lastSeen: Long): String = if (lastSeen <= 0L) "Not seen yet" else "Last seen ${relativeTime(lastSeen)}"

fun clockTime(time: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(time))

fun dayLabel(time: Long): String {
    val cal = java.util.Calendar.getInstance()
    val today = cal.get(java.util.Calendar.YEAR) * 1000 + cal.get(java.util.Calendar.DAY_OF_YEAR)
    cal.timeInMillis = time
    val day = cal.get(java.util.Calendar.YEAR) * 1000 + cal.get(java.util.Calendar.DAY_OF_YEAR)
    return when {
        day == today -> "Today"
        day == today - 1 -> "Yesterday"
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(time))
    }
}

class MenuAction(val label: String, val icon: ImageVector? = null, val destructive: Boolean = false, val onClick: () -> Unit)

@Composable
fun OverflowMenu(actions: List<MenuAction>, description: String = "More options") {
    val c = Theme.colors
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(36.dp).pointerHoverIcon(PointerIcon.Hand)) {
            Icon(Icons.Rounded.MoreVert, description, tint = c.text)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(14.dp),
            containerColor = c.surfaceHigh,
            border = BorderStroke(1.dp, c.outline),
            tonalElevation = 0.dp,
            modifier = Modifier.widthIn(min = 200.dp),
        ) {
            actions.forEach { action ->
                val tint = if (action.destructive) c.danger else c.text
                val leading: (@Composable () -> Unit)? = action.icon?.let { icon ->
                    @Composable { Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp)) }
                }
                DropdownMenuItem(
                    text = { Text(action.label, style = MaterialTheme.typography.bodyMedium, color = tint) },
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

@Composable
fun DeviceListItem(
    entry: DeviceEntry,
    onClick: () -> Unit,
    menu: List<MenuAction> = emptyList(),
    onConnect: (() -> Unit)? = null,
) {
    val c = Theme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (hovered) c.surfaceHigh.copy(alpha = 0.6f) else Color.Transparent)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .heightIn(min = 68.dp)
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DeviceBadge(entry, 46.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.name, style = MaterialTheme.typography.titleMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            OnlineStatus(entry.online)
            if (!entry.online) Text(lastSeenText(entry.lastSeen), style = MaterialTheme.typography.bodySmall, color = c.textMuted, maxLines = 1)
        }
        if (entry.trusted) {
            Pill("Trusted", color = c.accent)
            Spacer(Modifier.width(8.dp))
        }
        if (onConnect != null) {
            Box(
                Modifier
                    .height(34.dp)
                    .clip(CircleShape)
                    .background(c.accentContainer)
                    .clickable(onClick = onConnect)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Connect", style = MaterialTheme.typography.labelLarge, color = c.accent) }
            Spacer(Modifier.width(4.dp))
        }
        if (menu.isNotEmpty()) OverflowMenu(menu, "Options for ${entry.name}") else Spacer(Modifier.width(8.dp))
    }
}

@Composable
fun SectionHeader(title: String, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Theme.colors.text, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** Icon-over-label button used for "Scan QR" / "Refresh". */
@Composable
fun QuickAction(label: String, icon: ImageVector, onClick: () -> Unit, enabled: Boolean = true) {
    val c = Theme.colors
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .alpha(if (enabled) 1f else 0.5f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = c.accent, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.text, maxLines = 1)
    }
}

@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "Search devices") {
    val c = Theme.colors
    val shape = RoundedCornerShape(14.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        cursorBrush = SolidColor(c.accent),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.text),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxWidth().height(44.dp).clip(shape).background(c.surfaceHigh).border(1.dp, c.outline, shape).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = c.textMuted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = c.textMuted)
                    inner()
                }
                if (value.isNotEmpty()) {
                    IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(28.dp).pointerHoverIcon(PointerIcon.Hand)) {
                        Icon(Icons.Rounded.Close, "Clear search", tint = c.textMuted, modifier = Modifier.size(16.dp))
                    }
                }
            }
        },
    )
}

@Composable
fun FilterChips(selected: DeviceFilter, onSelect: (DeviceFilter) -> Unit) {
    val c = Theme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DeviceFilter.entries.forEach { filter ->
            val active = filter == selected
            Box(
                Modifier
                    .height(36.dp)
                    .clip(CircleShape)
                    .background(if (active) c.accentContainer else c.surfaceHigh)
                    .border(1.dp, if (active) c.accent.copy(alpha = 0.6f) else c.outline, CircleShape)
                    .clickable { onSelect(filter) }
                    .pointerHoverIcon(PointerIcon.Hand)
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    filter.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) c.accent else c.textMuted,
                )
            }
        }
    }
}

@Composable
fun LoadingState(title: String, body: String? = null) {
    val c = Theme.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(Modifier.size(32.dp), strokeWidth = 3.dp, color = c.accent)
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.text, textAlign = TextAlign.Center)
        if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium, color = c.textMuted, textAlign = TextAlign.Center)
    }
}

@Composable
fun EmptyState(title: String, body: String, icon: ImageVector, action: (@Composable () -> Unit)? = null) {
    val c = Theme.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        IconBubble(icon, size = 64.dp)
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = c.text)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = c.textMuted, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 420.dp))
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

@Composable
fun ErrorState(title: String, body: String, icon: ImageVector, retryLabel: String = "Try again", onRetry: (() -> Unit)? = null) {
    val c = Theme.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        IconBubble(icon, size = 64.dp, tint = c.warning, background = c.warning.copy(alpha = 0.14f))
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = c.text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = c.textMuted, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 420.dp))
        if (onRetry != null) {
            Spacer(Modifier.height(16.dp))
            PrimaryButton(retryLabel, onClick = onRetry)
        }
    }
}
