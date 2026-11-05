package com.aurora.notes.ui.components

import android.content.Context
import android.view.View
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aurora.notes.ui.theme.AuroraAmethyst
import com.aurora.notes.ui.theme.AuroraDangerRed
import com.aurora.notes.ui.theme.AuroraRoyalViolet
import com.aurora.notes.ui.theme.LocalAuroraDarkTheme
import com.aurora.notes.util.HapticHelper
import kotlin.math.abs
import kotlin.math.roundToInt

data class NavTabItem(
    val index: Int,
    val label: String,
    val icon: ImageVector,
    val badge: Int = 0
)

data class SelectionActionBarState(
    val isVisible: Boolean = false,
    val selectedCount: Int = 0,
    val isArchiveTab: Boolean = false,
    val onShareSelected: () -> Unit = {},
    val onArchiveOrRestoreSelected: () -> Unit = {},
    val onEditSelected: () -> Unit = {},
    val onDeleteSelected: () -> Unit = {},
    val onClearSelection: () -> Unit = {}
)

fun triggerNavbarHaptic(view: View, context: Context, enabled: Boolean = true) {
    HapticHelper.triggerHaptic(view = view, context = context, enabled = enabled, durationMs = 30L)
}

/**
 * Animated bottom pill bar container that swaps between the standard 4-tab FloatingPillNavigationBar
 * and the FloatingSelectionActionBar when holding down a note.
 * When selection mode activates, the original navbar hides down under the screen and the new
 * selection navbar pops up from under the screen to take its place.
 */
@Composable
fun AnimatedBottomPillBar(
    selectedTab: Int,
    pendingTab: Int = selectedTab,
    isNavTransitionPending: Boolean = false,
    onTabSelected: (Int) -> Unit,
    archivedCount: Int = 0,
    trashCount: Int = 0,
    isHapticsEnabled: Boolean = true,
    selectionState: SelectionActionBarState = SelectionActionBarState(),
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val slideDistancePx = with(density) { 104.dp.toPx() }

    // 0f = normal navigation bar visible; 1f = selection action bar visible
    val selectionProgress by animateFloatAsState(
        targetValue = if (selectionState.isVisible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.78f,
            stiffness = 520f
        ),
        label = "bottom_bar_selection_swap"
    )

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter
    ) {
        if (selectionProgress < 0.999f) {
            FloatingPillNavigationBar(
                selectedTab = selectedTab,
                pendingTab = pendingTab,
                isNavTransitionPending = isNavTransitionPending,
                onTabSelected = onTabSelected,
                archivedCount = archivedCount,
                trashCount = trashCount,
                isHapticsEnabled = isHapticsEnabled,
                modifier = Modifier.graphicsLayer {
                    translationY = selectionProgress * slideDistancePx
                    alpha = (1f - selectionProgress * 1.35f).coerceIn(0f, 1f)
                }
            )
        }

        if (selectionProgress > 0.001f) {
            FloatingSelectionActionBar(
                state = selectionState,
                isHapticsEnabled = isHapticsEnabled,
                modifier = Modifier.graphicsLayer {
                    translationY = (1f - selectionProgress) * slideDistancePx
                    alpha = ((selectionProgress - 0.15f) / 0.85f).coerceIn(0f, 1f)
                }
            )
        }
    }
}

@Composable
fun FloatingSelectionActionBar(
    state: SelectionActionBarState,
    isHapticsEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val context = LocalContext.current
    val isDarkTheme = LocalAuroraDarkTheme.current
    val pillSurfaceColor = if (isDarkTheme) Color(0xFF1B1524) else Color(0xFFFFFFFF)
    val itemColor = if (isDarkTheme) Color(0xFFD8CFE8) else Color(0xFF2B194D)

    // Keep track of the last active count so the bar doesn't change width mid-exit when count drops to 0
    var lastActiveCount by remember { mutableIntStateOf(1) }
    if (state.selectedCount > 0) {
        lastActiveCount = state.selectedCount
    }
    val effectiveCount = if (state.selectedCount > 0) state.selectedCount else lastActiveCount
    val showEditButton = !state.isArchiveTab && effectiveCount == 1

    val barMaxWidth by animateDpAsState(
        targetValue = if (showEditButton) 396.dp else 320.dp,
        animationSpec = spring(
            dampingRatio = 0.78f,
            stiffness = 500f
        ),
        label = "selection_bar_max_width"
    )

    val editButtonProgress by animateFloatAsState(
        targetValue = if (showEditButton) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = 520f
        ),
        label = "selection_bar_edit_btn_progress"
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
            color = pillSurfaceColor,
            contentColor = itemColor,
            tonalElevation = 0.dp,
            shadowElevation = 12.dp,
            border = BorderStroke(
                width = 1.5.dp,
                brush = Brush.horizontalGradient(
                    listOf(
                        AuroraRoyalViolet.copy(alpha = 0.65f),
                        AuroraAmethyst.copy(alpha = 0.85f),
                        AuroraRoyalViolet.copy(alpha = 0.65f)
                    )
                )
            ),
            modifier = Modifier
                .widthIn(max = barMaxWidth)
                .fillMaxWidth()
                .height(62.dp)
                .clip(RoundedCornerShape(100.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Close / Exit Hold Selection Mode pill button on the left
                Surface(
                    shape = CircleShape,
                    color = AuroraRoyalViolet.copy(alpha = 0.14f),
                    modifier = Modifier
                        .height(44.dp)
                        .clip(CircleShape)
                        .clickable {
                            triggerNavbarHaptic(view, context, isHapticsEnabled)
                            state.onClearSelection()
                        }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Exit Selection",
                            tint = AuroraRoyalViolet,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = effectiveCount.toString(),
                            color = AuroraRoyalViolet,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Share Button
                SelectionPillActionItem(
                    label = "Share",
                    icon = Icons.Default.Share,
                    tint = itemColor,
                    onClick = {
                        triggerNavbarHaptic(view, context, isHapticsEnabled)
                        state.onShareSelected()
                    },
                    modifier = Modifier.weight(1f)
                )

                // Archive (or Restore when in Archive tab) Button
                SelectionPillActionItem(
                    label = if (state.isArchiveTab) "Restore" else "Archive",
                    icon = if (state.isArchiveTab) Icons.Default.Unarchive else Icons.Default.Archive,
                    tint = itemColor,
                    onClick = {
                        triggerNavbarHaptic(view, context, isHapticsEnabled)
                        state.onArchiveOrRestoreSelected()
                    },
                    modifier = Modifier.weight(1f)
                )

                // Edit Button (visible only when a single note is selected; disappears when multiple notes are selected, and expands the bar when returning to 1 note)
                if (editButtonProgress > 0.01f) {
                    SelectionPillActionItem(
                        label = "Edit",
                        icon = Icons.Default.Edit,
                        tint = itemColor,
                        onClick = {
                            if (showEditButton) {
                                triggerNavbarHaptic(view, context, isHapticsEnabled)
                                state.onEditSelected()
                            }
                        },
                        modifier = Modifier
                            .weight(editButtonProgress.coerceAtLeast(0.01f))
                            .graphicsLayer {
                                alpha = editButtonProgress.coerceIn(0f, 1f)
                                val s = 0.8f + 0.2f * editButtonProgress.coerceIn(0f, 1f)
                                scaleX = s
                                scaleY = s
                            }
                    )
                }

                // Delete Button
                SelectionPillActionItem(
                    label = "Delete",
                    icon = Icons.Default.Delete,
                    tint = AuroraDangerRed,
                    onClick = {
                        triggerNavbarHaptic(view, context, isHapticsEnabled)
                        state.onDeleteSelected()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SelectionPillActionItem(
    label: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(100.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = label,
                color = tint,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = tint,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
fun FloatingPillNavigationBar(
    selectedTab: Int,
    pendingTab: Int = selectedTab,
    isNavTransitionPending: Boolean = false,
    onTabSelected: (Int) -> Unit,
    archivedCount: Int = 0,
    trashCount: Int = 0,
    isHapticsEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val context = LocalContext.current
    val isDarkTheme = LocalAuroraDarkTheme.current
    val currentOnTabSelected by rememberUpdatedState(onTabSelected)
    val currentHapticsEnabled by rememberUpdatedState(isHapticsEnabled)
    val currentIsTransitionPending by rememberUpdatedState(isNavTransitionPending)
    val activeTargetTab = if (isNavTransitionPending) pendingTab else selectedTab

    val items = remember(archivedCount, trashCount) {
        listOf(
            NavTabItem(0, "Notes", Icons.Default.Description),
            NavTabItem(1, "Archive", Icons.Default.Archive, badge = archivedCount),
            NavTabItem(2, "Trash", Icons.Default.Delete, badge = trashCount),
            NavTabItem(3, "Settings", Icons.Default.Settings)
        )
    }

    // Hold-and-slide state for dragging the purple pill across the menus
    var isHoldingPill by remember { mutableStateOf(false) }
    var dragTabPos by remember { mutableFloatStateOf(activeTargetTab.toFloat()) }

    LaunchedEffect(activeTargetTab) {
        if (!isHoldingPill) {
            dragTabPos = activeTargetTab.toFloat()
        }
    }

    val targetPillPos = if (isHoldingPill) dragTabPos else activeTargetTab.toFloat()

    // Single stretching pill animation with arrival overshoot
    val dropTabPos by animateFloatAsState(
        targetValue = targetPillPos,
        animationSpec = spring(
            dampingRatio = if (isHoldingPill) Spring.DampingRatioNoBouncy else 0.62f,
            stiffness = if (isHoldingPill) 520f else 360f
        ),
        label = "pill_lead_pos"
    )

    val bodyTabPos by animateFloatAsState(
        targetValue = targetPillPos,
        animationSpec = spring(
            dampingRatio = if (isHoldingPill) Spring.DampingRatioNoBouncy else 0.68f,
            stiffness = if (isHoldingPill) 340f else 240f
        ),
        label = "pill_trail_pos"
    )

    // Highlight the hovered or selected menu immediately
    val visualSelectedTab = if (isHoldingPill) {
        dragTabPos.roundToInt().coerceIn(0, items.size - 1)
    } else {
        activeTargetTab
    }

    val pillSurfaceColor = if (isDarkTheme) Color(0xFF1B1524) else Color(0xFFFFFFFF)
    val unselectedItemColor = if (isDarkTheme) Color(0xFFD8CFE8) else Color(0xFF2B194D)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(100.dp),
            color = pillSurfaceColor,
            contentColor = unselectedItemColor,
            tonalElevation = 0.dp,
            shadowElevation = 12.dp,
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
                .clip(RoundedCornerShape(100.dp))
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(5.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .pointerInput(items.size, selectedTab) {
                        val touchSlopPx = viewConfiguration.touchSlop
                        val holdTimeoutMs = 150L

                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            if (currentIsTransitionPending) {
                                // Respect button wait time while far menus are loading/transitioning
                                return@awaitEachGesture
                            }

                            val trackWidthPx = size.width.toFloat().coerceAtLeast(1f)
                            val tabWidthPx = trackWidthPx / items.size.toFloat()
                            val startX = down.position.x
                            var currentX = startX
                            val initialTappedTab = (startX / tabWidthPx).toInt().coerceIn(0, items.size - 1)

                            if (initialTappedTab != selectedTab) {
                                triggerNavbarHaptic(view, context, currentHapticsEnabled)
                                currentOnTabSelected(initialTappedTab)
                            }

                            var holdModeActive = false
                            var pointerReleased = false

                            // Optional hold/drag phase if the user keeps holding or slides across the pill
                            val preHoldResult = withTimeoutOrNull(holdTimeoutMs) {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                        ?: event.changes.firstOrNull()
                                    if (change == null || !change.pressed) {
                                        pointerReleased = true
                                        break
                                    }
                                    currentX = change.position.x
                                    if (abs(currentX - startX) > touchSlopPx * 0.45f) {
                                        holdModeActive = true
                                        change.consume()
                                        break
                                    }
                                }
                            }

                            if (preHoldResult == null && !pointerReleased) {
                                holdModeActive = true
                            }

                            if (holdModeActive && !pointerReleased) {
                                isHoldingPill = true
                                dragTabPos = ((currentX / tabWidthPx) - 0.5f)
                                    .coerceIn(0f, (items.size - 1).toFloat())

                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                        ?: event.changes.firstOrNull()
                                    if (change == null || !change.pressed) {
                                        break
                                    }
                                    currentX = change.position.x
                                    change.consume()
                                    dragTabPos = ((currentX / tabWidthPx) - 0.5f)
                                        .coerceIn(0f, (items.size - 1).toFloat())
                                }

                                val landedTab = dragTabPos.roundToInt().coerceIn(0, items.size - 1)
                                isHoldingPill = false
                                if (landedTab != initialTappedTab) {
                                    triggerNavbarHaptic(view, context, currentHapticsEnabled)
                                    currentOnTabSelected(landedTab)
                                }
                            } else {
                                isHoldingPill = false
                            }
                        }
                    }
            ) {
                val density = LocalDensity.current
                val containerWidthPx = with(density) { maxWidth.toPx() }
                val containerHeightPx = with(density) { maxHeight.toPx() }

                Spacer(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            val fluidPaths = FluidDropPathBuilder.buildFluidDropPaths(
                                dropPos = dropTabPos,
                                bodyPos = bodyTabPos,
                                tabCount = items.size,
                                containerWidthPx = size.width.coerceAtLeast(containerWidthPx),
                                containerHeightPx = size.height.coerceAtLeast(containerHeightPx)
                            )

                            val left = fluidPaths.boundingLeftPx
                            val right = (fluidPaths.boundingRightPx).coerceAtLeast(left + 1f)
                            val h = size.height

                            // Outer soft neon purple glow around the main pill and connected droplet
                            drawPath(
                                path = fluidPaths.mainMetaballPath,
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        AuroraRoyalViolet.copy(alpha = 0.36f),
                                        AuroraAmethyst.copy(alpha = 0.48f),
                                        AuroraRoyalViolet.copy(alpha = 0.36f)
                                    ),
                                    startX = left,
                                    endX = right
                                )
                            )

                            // Main Neon Purple Fluid Drop + Connected Pill Fill
                            drawPath(
                                path = fluidPaths.mainMetaballPath,
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

                            // Subtle outer rim highlight along the capsule contour
                            drawPath(
                                path = fluidPaths.mainMetaballPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.38f),
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
                        val isSelected = visualSelectedTab == item.index

                        val contentColor by animateColorAsState(
                            targetValue = if (isSelected) Color.White else unselectedItemColor,
                            animationSpec = tween(durationMillis = 160),
                            label = "tab_content_color"
                        )

                        val iconScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.08f else 1.0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            label = "tab_icon_scale"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(horizontal = 2.dp)
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label,
                                    tint = contentColor,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .scale(iconScale)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = item.label,
                                    color = contentColor,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = contentColor,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                    ),
                                    maxLines = 1,
                                    softWrap = false
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
