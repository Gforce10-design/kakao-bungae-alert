package app.bungaealert.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal enum class MainTab(val route: String, val label: String, val icon: Int) {
    Home("home", "홈", Sym.home),
    Rules("rules", "규칙", Sym.checklist),
    Settings("settings", "설정", Sym.settings),
}

internal val CardShape = RoundedCornerShape(20.dp)
internal val ButtonShape = RoundedCornerShape(12.dp)
internal val AlarmButtonShape = RoundedCornerShape(28.dp)

@Composable
internal fun Symbol(
    icon: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    size: Dp = 24.dp,
) {
    Icon(
        painter = painterResource(icon),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint,
    )
}

@Composable
internal fun SymbolButton(
    icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
        Symbol(
            icon = icon,
            contentDescription = contentDescription,
            tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
internal fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: Int? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = scheme.primary,
            contentColor = scheme.onPrimary,
            disabledContainerColor = scheme.surfaceVariant,
            disabledContentColor = scheme.onSurfaceVariant,
        ),
    ) {
        if (icon != null) {
            Symbol(icon, null, tint = if (enabled) scheme.onPrimary else scheme.onSurfaceVariant, size = 18.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = ButtonShape,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (enabled) scheme.outline else scheme.outlineVariant),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = scheme.onSurface,
            disabledContentColor = scheme.outline,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun QuietButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    val tint = if (color == Color.Unspecified) MaterialTheme.colorScheme.primary else color
    androidx.compose.material3.TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = tint)
    }
}

@Composable
internal fun SelectChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val bg = if (selected) scheme.primary else scheme.surface
    val fg = if (selected) scheme.onPrimary else scheme.onSurface
    val stroke = if (selected) scheme.primary else scheme.outline
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            modifier = Modifier
                .clip(CircleShape)
                .border(1.dp, stroke, CircleShape)
                .background(bg)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            maxLines = 1,
        )
    }
}

@Composable
internal fun KindChip(text: String, container: Color, content: Color) {
    Text(
        text = text,
        modifier = Modifier
            .clip(CircleShape)
            .background(container)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelSmall,
        color = content,
        maxLines = 1,
    )
}

@Composable
internal fun Modifier.bungaeCard(onClick: (() -> Unit)? = null): Modifier {
    val shape = CardShape
    val scheme = MaterialTheme.colorScheme
    val styled = this
        .shadow(
            elevation = 2.dp,
            shape = shape,
            ambientColor = Color(0x12000000),
            spotColor = Color(0x16000000),
        )
        .clip(shape)
        .border(1.dp, scheme.outlineVariant, shape)
        .background(scheme.surface)
    return if (onClick == null) styled else styled.clickable(role = Role.Button, onClick = onClick)
}

@Composable
internal fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.semantics { heading() },
        style = MaterialTheme.typography.headlineLarge,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
internal fun SectionTitle(title: String, caption: String? = null) {
    Text(
        text = title,
        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
        style = MaterialTheme.typography.titleLarge,
    )
    if (caption != null) {
        Text(
            text = caption,
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun SurfaceCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .bungaeCard()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        content = content,
    )
}

@Composable
internal fun EmptyState(title: String, body: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Symbol(Sym.inbox, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 32.dp)
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            PrimaryButton(action, onAction)
        }
    }
}

@Composable
internal fun ErrorState(title: String, body: String, action: String? = null, onAction: (() -> Unit)? = null) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(72.dp).background(scheme.errorContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Symbol(Sym.error, null, tint = scheme.error, size = 32.dp)
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, color = scheme.onSurface)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            PrimaryButton(action, onAction)
        }
    }
}

@Composable
internal fun LoadingState(label: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Symbol(Sym.progress_activity, null, tint = MaterialTheme.colorScheme.primary, size = 36.dp)
        Spacer(Modifier.height(12.dp))
        Text(label, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.42f)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
internal fun ListenerBanner(online: Boolean, title: String, subtitle: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val background = if (online) scheme.surface else tones.banner
    val icon = if (online) Sym.check_circle else Sym.notifications_off
    val tint = if (online) tones.good else scheme.error
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bungaeCard(onClick = onClick)
            .background(background)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(36.dp).background(scheme.surfaceContainerHigh, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Symbol(icon, null, tint = tint, size = 20.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Symbol(Sym.chevron_right, null, tint = scheme.onSurfaceVariant, size = 20.dp)
    }
}

@Composable
internal fun ProgressMarks(index: Int, count: Int) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(count) { mark ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(if (mark <= index) scheme.primary else scheme.outlineVariant),
            )
        }
    }
}

@Composable
internal fun BungaeBottomBar(selected: MainTab, onSelect: (MainTab) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.surface)
            .padding(bottom = 0.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(scheme.outlineVariant))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            MainTab.entries.forEach { tab ->
                val on = tab == selected
                val color = if (on) scheme.primary else scheme.onSurfaceVariant
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.Tab, onClick = { onSelect(tab) })
                        .padding(vertical = 8.dp)
                        .semantics { if (on) contentDescription = "${tab.label}, 선택됨" },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Symbol(tab.icon, if (on) null else tab.label, tint = color, size = 24.dp)
                    Spacer(Modifier.height(2.dp))
                    Text(tab.label, style = MaterialTheme.typography.labelSmall, color = color)
                }
            }
        }
    }
}

@Composable
internal fun BungaeScaffold(
    tab: MainTab?,
    onTab: (MainTab) -> Unit,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(
            Modifier
                .weight(1f)
                .statusBarsPadding(),
        ) { content() }
        if (tab != null) {
            Column(Modifier.navigationBarsPadding()) {
                BungaeBottomBar(tab, onTab)
            }
        } else {
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}
