package me.rhul.loudr.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rhul.loudr.R
import me.rhul.loudr.engine.AudioStream
import me.rhul.loudr.safety.SafetyEvent
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDisableLimiterDialog by remember { mutableStateOf(false) }

    state.safetyEvent?.let { event ->
        SafetyWarningDialog(event = event, onDismiss = { viewModel.dismissSafetyEvent() })
    }
    if (showDisableLimiterDialog) {
        DisableLimiterDialog(
            onConfirm = { viewModel.setSafetyLimiterEnabled(false); showDisableLimiterDialog = false },
            onDismiss = { showDisableLimiterDialog = false },
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color    = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top bar
            TopBar(
                isActive      = state.isActive,
                enabledStreams = state.enabledStreams,
                onToggle      = { viewModel.toggleActive() },
            )

            Spacer(Modifier.height(20.dp))

            // Arc — anchored from top, natural size
            BoostArcControl(
                boostLevel           = state.boostLevel,
                isActive             = state.isActive,
                safetyLimiterEnabled = state.safetyLimiterEnabled,
                onBoostChange        = { viewModel.setBoost(it) },
            )

            Spacer(Modifier.height(20.dp))

            StreamChips(
                enabledStreams = state.enabledStreams,
                onToggle      = { stream, enabled -> viewModel.toggleStream(stream, enabled) },
            )

            Spacer(Modifier.height(16.dp))

            ControlCard(
                bassBoostEnabled     = state.bassBoostEnabled,
                safetyLimiterEnabled = state.safetyLimiterEnabled,
                autoBoostOnHeadphone = state.autoBoostOnHeadphone,
                currentTheme         = state.theme,
                onBassBoostToggle    = { viewModel.setBassBoostEnabled(it) },
                onLimiterToggle      = { enabled ->
                    if (!enabled) showDisableLimiterDialog = true
                    else viewModel.setSafetyLimiterEnabled(true)
                },
                onAutoBoostToggle    = { viewModel.setAutoBoostOnHeadphone(it) },
                onThemeChange        = { viewModel.setTheme(it) },
            )

            // Push footer to bottom
            Spacer(Modifier.weight(1f))

            PrivacyFooter()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top bar (sticky)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TopBar(
    isActive:      Boolean,
    enabledStreams: Set<AudioStream>,
    onToggle:      () -> Unit,
    modifier:      Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val pillBg by animateColorAsState(
        targetValue   = if (isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label         = "pillBg",
    )
    val pillScale by animateFloatAsState(
        targetValue   = if (isActive) 1.06f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label         = "pillScale",
    )

    // Build a short subtitle: streams in declaration order when active, state label when not
    val subtitle = if (isActive) {
        enabledStreams.sortedBy { it.ordinal }.joinToString(" · ") { it.label }
            .ifEmpty { "Boosting audio" }
    } else {
        "Inactive"
    }

    Row(
        modifier              = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Wave Sine logo mark
            Icon(
                painter           = painterResource(R.drawable.ic_wave_sine),
                contentDescription = null,
                tint              = MaterialTheme.colorScheme.primary,
                modifier          = Modifier
                    .size(22.dp)
                    .padding(end = 0.dp),
            )
            Spacer(Modifier.size(10.dp))
            Column {
                Text(
                    text  = "Loudr",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text  = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                )
            }
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .graphicsLayer { scaleX = pillScale; scaleY = pillScale }
                .size(width = 80.dp, height = 36.dp)
                .clip(CircleShape)
                .background(pillBg)
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggle()
                }
                .semantics {
                    contentDescription = if (isActive) "Boost on, tap to disable" else "Boost off, tap to enable"
                },
        ) {
            Text(
                text  = if (isActive) "ON" else "OFF",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = if (isActive) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Boost arc — lag-free gesture + snap animation while dragging
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BoostArcControl(
    boostLevel:           Float,
    isActive:             Boolean,
    safetyLimiterEnabled: Boolean,
    onBoostChange:        (Float) -> Unit,
) {
    // Skip animation while the finger is down so the arc tracks instantly.
    var isDragging by remember { mutableStateOf(false) }

    val animatedLevel by animateFloatAsState(
        targetValue   = boostLevel,
        animationSpec = if (isDragging) snap() else tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label         = "boostLevel",
    )

    // Arc fill colors are semantic (safe/caution/danger), not brand colors,
    // so the safe zone is clearly distinct from the danger red.
    val safeGreen  = Color(0xFF26D97F)
    val surface    = MaterialTheme.colorScheme.surfaceVariant
    val background = MaterialTheme.colorScheme.background
    val amber      = Color(0xFFFFAB40)
    val danger     = Color(0xFFE53935)
    // Display values — full range is 0–300%
    val boostPct = (animatedLevel * 300).toInt()
    val gainDb   = String.format("%.1f", animatedLevel * 20f)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(220.dp)
            .semantics { contentDescription = "Boost level $boostPct percent" }
            // Raw pointer input — no touch slop, tracks finger from the very first pixel
            .pointerInput(safetyLimiterEnabled) {
                val ceiling = if (safetyLimiterEnabled) 0.5f else 1.0f
                awaitPointerEventScope {
                    while (true) {
                        // Wait for the first finger down — no slop, no delay
                        val down = awaitPointerEvent()
                        val pressed = down.changes.firstOrNull { it.pressed } ?: continue
                        pressed.consume()
                        isDragging = true

                        // Track all subsequent moves until finger lifts
                        var stillDown = true
                        while (stillDown) {
                            val event  = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            change.consume()
                            if (!change.pressed) {
                                stillDown  = false
                                isDragging = false
                                break
                            }
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            var angleDeg = Math.toDegrees(
                                atan2(
                                    (change.position.y - cy).toDouble(),
                                    (change.position.x - cx).toDouble(),
                                )
                            ).toFloat()
                            if (angleDeg < 0f) angleDeg += 360f
                            val arcStart = 150f
                            val arcSweep = 240f
                            val relative = (angleDeg - arcStart + 360f) % 360f
                            if (relative <= arcSweep) {
                                val raw = relative / arcSweep
                                val snapped = when {
                                    raw < 0.03f            -> 0f
                                    raw > ceiling - 0.03f  -> ceiling
                                    raw > 0.97f            -> ceiling
                                    else                   -> raw
                                }
                                onBoostChange(snapped.coerceIn(0f, ceiling))
                            }
                        }
                        isDragging = false
                    }
                }
            }
            .drawWithCache {
                val strokeWidth = 22.dp.toPx()
                val arcSize     = Size(size.width - strokeWidth, size.height - strokeWidth)
                val topLeft     = Offset(strokeWidth / 2f, strokeWidth / 2f)
                val startAngle  = 150f
                val sweepTotal  = 240f

                onDrawBehind {
                    // Track
                    drawArc(
                        color      = surface,
                        startAngle = startAngle,
                        sweepAngle = sweepTotal,
                        useCenter  = false,
                        topLeft    = topLeft,
                        size       = arcSize,
                        style      = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                    // Safety ceiling tick at 50% of arc
                    if (safetyLimiterEnabled) {
                        val ceilingAngRad = Math.toRadians(
                            (startAngle + sweepTotal * 0.5f).toDouble()
                        )
                        val radius = (size.width - strokeWidth) / 2f
                        val cx = size.width / 2f; val cy = size.height / 2f
                        val inner = radius - strokeWidth * 0.6f
                        val outer = radius + strokeWidth * 0.6f
                        drawLine(
                            color       = Color.White.copy(alpha = 0.8f),
                            start       = Offset(
                                (cx + inner * cos(ceilingAngRad).toFloat()),
                                (cy + inner * sin(ceilingAngRad).toFloat()),
                            ),
                            end         = Offset(
                                (cx + outer * cos(ceilingAngRad).toFloat()),
                                (cy + outer * sin(ceilingAngRad).toFloat()),
                            ),
                            strokeWidth = 3.dp.toPx(),
                        )
                    }
                    val safeEnd    = 0.5f
                    val cautionEnd = 0.75f
                    if (animatedLevel > 0f) {
                        val strokeRadius = strokeWidth / 2f
                        val r  = (size.width - strokeWidth) / 2f
                        val cx = size.width / 2f; val cy = size.height / 2f

                        fun seg(from: Float, to: Float, color: Color) {
                            if (animatedLevel <= from) return
                            drawArc(
                                color      = color,
                                startAngle = startAngle + sweepTotal * from,
                                sweepAngle = sweepTotal * (minOf(animatedLevel, to) - from),
                                useCenter  = false,
                                topLeft    = topLeft,
                                size       = arcSize,
                                style      = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                            )
                        }
                        seg(0f,         safeEnd,    safeGreen)
                        seg(safeEnd,    cautionEnd, amber)
                        seg(cautionEnd, 1f,         danger)

                        val startRad = Math.toRadians(startAngle.toDouble())
                        drawCircle(
                            color  = safeGreen,
                            radius = strokeRadius,
                            center = Offset((cx + r * cos(startRad)).toFloat(), (cy + r * sin(startRad)).toFloat()),
                        )
                        val endColor = when {
                            animatedLevel <= safeEnd    -> safeGreen
                            animatedLevel <= cautionEnd -> amber
                            else                        -> danger
                        }
                        val endRad = Math.toRadians((startAngle + sweepTotal * animatedLevel).toDouble())
                        drawCircle(
                            color  = background,
                            radius = strokeRadius + 4.dp.toPx(),
                            center = Offset((cx + r * cos(endRad)).toFloat(), (cy + r * sin(endRad)).toFloat()),
                        )
                        drawCircle(
                            color  = endColor,
                            radius = strokeRadius,
                            center = Offset((cx + r * cos(endRad)).toFloat(), (cy + r * sin(endRad)).toFloat()),
                        )
                    }
                }
            },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when {
                // Boost is off — small muted label
                !isActive -> Text(
                    text  = "Off",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                )
                // Active but not yet dragged — two-line hint, both small
                boostLevel == 0f -> {
                    Text(
                        text      = "Drag arc",
                        style     = MaterialTheme.typography.labelLarge,
                        color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text      = "to boost",
                        style     = MaterialTheme.typography.labelSmall,
                        color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                        textAlign = TextAlign.Center,
                    )
                }
                // Active with a real value — big number + dB sub-label
                else -> {
                    Text(
                        text  = "+$boostPct%",
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize   = 48.sp,
                        ),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text  = "+$gainDb dB",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                    if (safetyLimiterEnabled) {
                        Text(
                            text  = "max +150%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Stream chips
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StreamChips(
    enabledStreams: Set<AudioStream>,
    onToggle:      (AudioStream, Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text     = "Boost applies to",
            style    = MaterialTheme.typography.labelMedium,
            color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.padding(bottom = 10.dp),
        )
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            AudioStream.entries.forEach { stream ->
                val selected = stream in enabledStreams
                FilterChip(
                    selected = selected,
                    onClick  = { onToggle(stream, !selected) },
                    label    = { Text(stream.label, style = MaterialTheme.typography.labelLarge) },
                    shape    = RoundedCornerShape(50),
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor     = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Inline control card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ControlCard(
    bassBoostEnabled:     Boolean,
    safetyLimiterEnabled: Boolean,
    autoBoostOnHeadphone: Boolean,
    currentTheme:         String,
    onBassBoostToggle:    (Boolean) -> Unit,
    onLimiterToggle:      (Boolean) -> Unit,
    onAutoBoostToggle:    (Boolean) -> Unit,
    onThemeChange:        (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)),
    ) {
        CardToggleRow("Bass Boost", "Enhance bass frequencies", bassBoostEnabled, onBassBoostToggle)
        Divider()
        CardToggleRow(
            title          = "Safety Limiter",
            description    = if (safetyLimiterEnabled) "Hearing-safe mode · max +150%"
                             else "Full +20 dB range active",
            checked        = safetyLimiterEnabled,
            onChecked      = onLimiterToggle,
            warningWhenOff = true,
        )
        Divider()
        CardToggleRow("Auto-boost on headphone", "Activates automatically on connect", autoBoostOnHeadphone, onAutoBoostToggle)
        Divider()
        ThemeRow(current = currentTheme, onChange = onThemeChange)
    }
}

@Composable
private fun ThemeRow(current: String, onChange: (String) -> Unit) {
    val themes = listOf("dynamic" to "Dynamic", "dark" to "Dark", "amoled" to "AMOLED")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Text(
            text  = "Theme",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            themes.forEach { (key, label) ->
                val selected = current == key
                FilterChip(
                    selected = selected,
                    onClick  = { onChange(key) },
                    label    = { Text(label, style = MaterialTheme.typography.labelMedium) },
                    shape    = RoundedCornerShape(50),
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor     = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            }
        }
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(
        modifier  = Modifier.padding(horizontal = 16.dp),
        thickness = 0.5.dp,
        color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
    )
}

@Composable
private fun CardToggleRow(
    title:          String,
    description:    String,
    checked:        Boolean,
    onChecked:      (Boolean) -> Unit,
    warningWhenOff: Boolean = false,
) {
    val descColor = if (warningWhenOff && !checked) MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChecked(!checked) }
            .padding(horizontal = 20.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(description, style = MaterialTheme.typography.bodySmall, color = descColor)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Privacy footer
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PrivacyFooter() {
    val uriHandler = LocalUriHandler.current
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text      = "No data collected · No internet · No analytics",
            style     = MaterialTheme.typography.labelSmall,
            color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier         = Modifier.size(width = 120.dp, height = 44.dp),
            contentAlignment = Alignment.Center,
        ) {
            TextButton(onClick = { uriHandler.openUri("https://github.com/loudr-app/loudr/blob/main/PRIVACY.md") }) {
                Text(
                    text  = "Privacy Policy",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Dialogs
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DisableLimiterDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove hearing protection?") },
        text = {
            Text(
                "This unlocks the full +20 dB range. " +
                "Sustained listening above 85 dB can cause permanent hearing loss.",
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("I understand, disable") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep limiter on") } },
    )
}

@Composable
private fun SafetyWarningDialog(event: SafetyEvent, onDismiss: () -> Unit) {
    val minutes = (event as? SafetyEvent.ExposureWarning)?.minutes ?: 0L
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Default.Warning, contentDescription = null,
                tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp))
        },
        title = { Text("Take a break?") },
        text = {
            Text(
                "You've been listening at high volume for $minutes minutes. " +
                "Sustained exposure above 85 dB can cause permanent hearing loss.",
            )
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors  = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text("Got it")
            }
        },
    )
}
