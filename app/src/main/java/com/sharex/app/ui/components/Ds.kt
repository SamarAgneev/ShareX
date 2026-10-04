package com.sharex.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Verified
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sharex.app.ui.lastSeenText
import com.sharex.app.ui.theme.ShareX
import com.sharex.core.engine.DeviceEntry
import com.sharex.core.engine.DeviceFilter

/** Top-level destinations shown in the bottom bar. */
enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Rounded.Home),
    DEVICES("Devices", Icons.Rounded.People),
    PROFILE("Profile", Icons.Rounded.Person),
}

private val AvatarPalette = listOf(
    Color(0xFF1E88E5), Color(0xFF8E24AA), Color(0xFFE53935), Color(0xFF43A047),
    Color(0xFFFFB300), Color(0xFF00897B), Color(0xFFF4511E), Color(0xFF5E6B7C),
)

/** Stable per-device colour so the same device looks the same on every screen. */
fun deviceTint(id: String): Color = AvatarPalette[(id.hashCode() and Int.MAX_VALUE) % AvatarPalette.size]

@Composable
fun DeviceBadge(
    entry: DeviceEntry,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
) {
    val colors = ShareX.colors
    val tint = deviceTint(entry.id)
    Box(modifier.size(size)) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(tint.copy(alpha = if (colors.isDark) 0.28f else 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(entry.type.icon(), contentDescription = entry.type.name.lowercase(), tint = tint, modifier = Modifier.size(size * 0.46f))
        }
        if (entry.trusted) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.36f)
                    .clip(CircleShape)
                    .background(colors.background)
                    .padding(2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Verified, contentDescription = "Trusted", tint = colors.success, modifier = Modifier.size(size * 0.3f))
            }
        }
    }
}

@Composable
fun StatusDot(online: Boolean, modifier: Modifier = Modifier, size: Dp = 9.dp) {
    val colors = ShareX.colors
    val color by animateColorAsState(if (online) colors.online else colors.offline, tween(300), label = "status-dot")
    Box(modifier.size(size).clip(CircleShape).background(color))
}

/** Dot + word. The word keeps the state readable without relying on colour alone. */
@Composable
fun OnlineStatus(online: Boolean, modifier: Modifier = Modifier, label: String = if (online) "Online" else "Offline") {
    val colors = ShareX.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        StatusDot(online)
        Spacer(Modifier.width(7.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = if (online) colors.online else colors.textMuted, maxLines = 1)
    }
}

class MenuAction(
    val label: String,
    val icon: ImageVector? = null,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
fun OverflowMenu(actions: List<MenuAction>, modifier: Modifier = Modifier, description: String = "More options") {
    val colors = ShareX.colors
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = description, tint = colors.text)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = colors.surfaceHigh,
            border = BorderStroke(1.dp, colors.outline),
            tonalElevation = 0.dp,
            modifier = Modifier.widthIn(min = 200.dp),
        ) {
            actions.forEach { action ->
                val tint = if (action.destructive) colors.danger else colors.text
                val leading: (@Composable () -> Unit)? = action.icon?.let { icon ->
                    @Composable { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
                }
                DropdownMenuItem(
                    text = { Text(action.label, style = MaterialTheme.typography.bodyLarge, color = tint) },
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

/**
 * One device row: avatar, name, online state, last seen, then either a trailing "Connect" pill
 * ([onConnect]) or nothing, followed by the overflow menu ([menu]).
 */
@Composable
fun DeviceListItem(
    entry: DeviceEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    menu: List<MenuAction> = emptyList(),
    onConnect: (() -> Unit)? = null,
    showLastSeen: Boolean = true,
) {
    val colors = ShareX.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DeviceBadge(entry)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.name, style = MaterialTheme.typography.titleMedium, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            OnlineStatus(entry.online)
            if (showLastSeen && !entry.online) {
                Text(lastSeenText(entry.lastSeen), style = MaterialTheme.typography.bodySmall, color = colors.textMuted, maxLines = 1)
            }
        }
        if (entry.trusted) {
            Pill("Trusted", color = colors.accent, modifier = Modifier.padding(end = 6.dp))
        }
        if (onConnect != null) {
            Box(
                Modifier
                    .heightIn(min = 40.dp)
                    .clip(CircleShape)
                    .background(colors.accentContainer)
                    .clickable(role = Role.Button, onClick = onConnect)
                    .padding(horizontal = 18.dp)
                    .semantics { contentDescription = "Connect to ${entry.name}" },
                contentAlignment = Alignment.Center,
            ) {
                Text("Connect", style = MaterialTheme.typography.labelLarge, color = colors.accent)
            }
        }
        if (menu.isNotEmpty()) OverflowMenu(menu, description = "Options for ${entry.name}")
        else Spacer(Modifier.width(10.dp))
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = ShareX.colors.text, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** Icon over label, used for "Scan QR" / "Refresh". */
@Composable
fun QRButton(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = ShareX.colors
    Column(
        modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 56.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .alpha(if (enabled) 1f else 0.5f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(24.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.text, maxLines = 1)
    }
}

@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "Search devices") {
    val colors = ShareX.colors
    val shape = RoundedCornerShape(18.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        cursorBrush = SolidColor(colors.accent),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.text),
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(shape)
                    .background(colors.surfaceHigh)
                    .border(1.dp, colors.outline, shape)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = colors.textMuted)
                    inner()
                }
                if (value.isNotEmpty()) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Clear search",
                        tint = colors.textMuted,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button) { onValueChange("") }
                            .padding(8.dp),
                    )
                }
            }
        },
    )
}

@Composable
fun FilterChips(selected: DeviceFilter, onSelect: (DeviceFilter) -> Unit, modifier: Modifier = Modifier) {
    val colors = ShareX.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DeviceFilter.entries.forEach { filter ->
            val active = filter == selected
            Box(
                Modifier
                    .heightIn(min = 40.dp)
                    .clip(CircleShape)
                    .background(if (active) colors.accentContainer else colors.surfaceHigh)
                    .border(1.dp, if (active) colors.accent.copy(alpha = 0.6f) else colors.outline, CircleShape)
                    .clickable(role = Role.Tab, onClick = { onSelect(filter) })
                    .padding(horizontal = 18.dp)
                    .semantics { contentDescription = "${filter.name.lowercase()} devices" + if (active) ", selected" else "" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    filter.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) colors.accent else colors.textMuted,
                )
            }
        }
    }
}

@Composable
fun ShareXBottomBar(selected: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val colors = ShareX.colors
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.outline.copy(alpha = 0.7f), shape)
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab.entries.forEach { tab ->
            val active = tab == selected
            val background by animateColorAsState(if (active) colors.accentContainer else Color.Transparent, tween(250), label = "tab-bg")
            val tint by animateColorAsState(if (active) colors.accent else colors.textMuted, tween(250), label = "tab-tint")
            val widthFraction by animateFloatAsState(if (active) 1f else 0.9f, tween(250), label = "tab-scale")
            Column(
                Modifier
                    .widthIn(min = 76.dp)
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(background)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Tab,
                        onClick = { onSelect(tab) },
                    )
                    .padding(horizontal = 18.dp, vertical = 8.dp)
                    .alpha(widthFraction)
                    .semantics { contentDescription = tab.label + if (active) ", selected" else "" },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(tab.icon, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
                Text(tab.label, style = MaterialTheme.typography.labelMedium, color = tint, maxLines = 1)
            }
        }
    }
}
