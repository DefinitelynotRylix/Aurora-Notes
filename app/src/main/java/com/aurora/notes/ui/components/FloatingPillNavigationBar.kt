package com.aurora.notes.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aurora.notes.ui.theme.AuroraAmethyst
import com.aurora.notes.ui.theme.AuroraRoyalViolet
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class NavTabItem(
    val index: Int,
    val label: String,
    val icon: ImageVector,
    val badge: Int = 0
)

fun triggerNavbarHaptic(view: View, context: Context) {
    try {
        view.isHapticFeedbackEnabled = true
        view.performHapticFeedback(
            HapticFeedbackConstants.KEYBOARD_TAP,
            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
        )
    } catch (_: Exception) {
    }

    try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        if (vibrator?.hasVibrator() == true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val attrs = android.os.VibrationAttributes.Builder()
                    .setUsage(android.os.VibrationAttributes.USAGE_TOUCH)
                    .build()
                vibrator.vibrate(
                    VibrationEffect.createOneShot(24L, 210),
                    attrs
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                val audioAttrs = android.media.AudioAttributes.Builder()
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .build()
                @Suppress("DEPRECATION")
                vibrator.vibrate(
                    VibrationEffect.createOneShot(24L, 210),
                    audioAttrs
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(24L)
            }
        }
    } catch (_: Exception) {
    }
}

@Composable
fun FloatingPillNavigationBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    pinnedCount: Int = 0,
    trashCount: Int = 0,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val context = LocalContext.current

    val items = listOf(
        NavTabItem(0, "Notes", Icons.Default.Description),
        NavTabItem(1, "Pinned", Icons.Default.PushPin, badge = pinnedCount),
        NavTabItem(2, "Trash", Icons.Default.Delete, badge = trashCount),
        NavTabItem(3, "Settings", Icons.Default.Settings)
    )

    // Critically damped (non-bouncy) dual springs:
    // The leading edge glides out smoothly while the trailing edge follows more gently,
    // causing the pill to fluidly stretch out across menus before contracting back to its original size.
    val leadTabPos by animateFloatAsState(
        targetValue = selectedTab.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = 210f
        ),
        label = "pill_lead_pos"
    )

    val trailTabPos by animateFloatAsState(
        targetValue = selectedTab.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = 85f
        ),
        label = "pill_trail_pos"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(100.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            tonalElevation = 12.dp,
            shadowElevation = 20.dp,
            border = BorderStroke(
                width = 1.5.dp,
                brush = Brush.horizontalGradient(
                    listOf(
                        AuroraRoyalViolet.copy(alpha = 0.55f),
                        AuroraAmethyst.copy(alpha = 0.75f),
                        AuroraRoyalViolet.copy(alpha = 0.55f)
                    )
                )
            ),
            modifier = Modifier
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .height(62.dp)
                .shadow(
                    elevation = 24.dp,
                    shape = RoundedCornerShape(100.dp),
                    ambientColor = AuroraRoyalViolet,
                    spotColor = AuroraAmethyst
                )
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(5.dp)
                    .clip(RoundedCornerShape(100.dp))
            ) {
                val density = LocalDensity.current
                val containerWidthPx = with(density) { maxWidth.toPx() }
                val containerHeightPx = with(density) { maxHeight.toPx() }
                val tabWidthPx = containerWidthPx / items.size.toFloat()

                // Strictly clamp left and right edges inside [0, containerWidthPx]
                val minTabPos = min(leadTabPos, trailTabPos).coerceIn(0f, (items.size - 1).toFloat())
                val maxTabPos = max(leadTabPos, trailTabPos).coerceIn(0f, (items.size - 1).toFloat())

                val pillLeftPx = (minTabPos * tabWidthPx).coerceIn(0f, containerWidthPx - tabWidthPx)
                val pillRightPx = ((maxTabPos * tabWidthPx) + tabWidthPx).coerceIn(pillLeftPx + tabWidthPx, containerWidthPx)

                // Continuous, smooth stretch-based waist pinch (0 when at rest, peaks smoothly while stretched)
                val stretchDelta = abs(leadTabPos - trailTabPos)
                val pinchFactor = (stretchDelta / (stretchDelta + 0.55f)).coerceIn(0f, 1f)

                Spacer(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            val w = (pillRightPx - pillLeftPx).coerceAtLeast(containerHeightPx)
                            val h = containerHeightPx
                            val r = h / 2f
                            val left = pillLeftPx.coerceIn(0f, size.width - w)
                            val right = (left + w).coerceAtMost(size.width)

                            // Middle waist pinches inward while stretched, then relaxes to 0 at rest
                            val maxPinchPx = h * 0.28f * pinchFactor
                            val midX = (left + right) / 2f

                            val pillPath = Path().apply {
                                // Left semicircular end cap
                                arcTo(
                                    rect = Rect(left, 0f, left + h, h),
                                    startAngleDegrees = 90f,
                                    sweepAngleDegrees = 180f,
                                    forceMoveTo = false
                                )
                                // Top edge: smoothly pinches inward at the middle waist while stretching
                                quadraticBezierTo(
                                    x1 = midX,
                                    y1 = maxPinchPx,
                                    x2 = right - r,
                                    y2 = 0f
                                )
                                // Right semicircular end cap
                                arcTo(
                                    rect = Rect(right - h, 0f, right, h),
                                    startAngleDegrees = 270f,
                                    sweepAngleDegrees = 180f,
                                    forceMoveTo = false
                                )
                                // Bottom edge: smoothly pinches inward at the middle waist while stretching
                                quadraticBezierTo(
                                    x1 = midX,
                                    y1 = h - maxPinchPx,
                                    x2 = left + r,
                                    y2 = h
                                )
                                close()
                            }

                            // Outer soft neon purple glow inside navbar
                            drawPath(
                                path = pillPath,
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        AuroraRoyalViolet.copy(alpha = 0.35f),
                                        AuroraAmethyst.copy(alpha = 0.45f),
                                        AuroraRoyalViolet.copy(alpha = 0.35f)
                                    ),
                                    startX = left,
                                    endX = right
                                )
                            )

                            // Main Neon Purple Pill Fill
                            drawPath(
                                path = pillPath,
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        AuroraRoyalViolet,
                                        Color(0xFF7B35E8),
                                        AuroraAmethyst
                                    ),
                                    startX = left,
                                    endX = right
                                )
                            )

                            // Subtle luminous inner rim highlight
                            drawPath(
                                path = pillPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.42f),
                                        Color.White.copy(alpha = 0.08f)
                                    ),
                                    startY = 0f,
                                    endY = h
                                ),
                                style = Stroke(width = with(density) { 1.dp.toPx() })
                            )
                        }
                )

                // Symmetric 4-Column Tab Items
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items.forEach { item ->
                        val isSelected = selectedTab == item.index

                        val contentColor by animateColorAsState(
                            targetValue = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                            animationSpec = tween(durationMillis = 280),
                            label = "tab_content_color"
                        )

                        val iconScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.08f else 1.0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            label = "tab_icon_scale"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    triggerNavbarHaptic(view, context)
                                    onTabSelected(item.index)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label,
                                    tint = contentColor,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .scale(iconScale)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = item.label,
                                    color = contentColor,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1
                                )
                                if (item.badge > 0) {
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isSelected) Color.White.copy(alpha = 0.28f) else AuroraRoyalViolet.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = item.badge.toString(),
                                            color = if (isSelected) Color.White else AuroraRoyalViolet,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
