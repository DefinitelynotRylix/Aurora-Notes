package com.aurora.notes

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.aurora.notes.model.Note
import com.aurora.notes.ui.components.AnimatedBottomPillBar
import com.aurora.notes.ui.components.SelectionActionBarState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import com.aurora.notes.ui.screens.NoteEditorScreen
import com.aurora.notes.ui.screens.NotesScreen
import com.aurora.notes.ui.screens.SettingsScreen
import com.aurora.notes.ui.screens.TrashScreen
import com.aurora.notes.ui.theme.AuroraRoyalViolet
import com.aurora.notes.ui.theme.AuroraTheme
import com.aurora.notes.util.NoteShareHelper
import com.aurora.notes.viewmodel.NotesViewModel

class MainActivity : FragmentActivity() {

    private val viewModel: NotesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.decorView.isForceDarkAllowed = false
        }
        if (intent?.getBooleanExtra("extra_updated_restarted", false) == true) {
            intent?.removeExtra("extra_updated_restarted")
            viewModel.onAppUpdatedRestart()
            Toast.makeText(
                this,
                "Aurora Notes updated & restarted (v${BuildConfig.VERSION_NAME})",
                Toast.LENGTH_LONG
            ).show()
        }
        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val useDarkTheme = when (themeMode.lowercase()) {
                "dark" -> true
                "light" -> false
                else -> systemDark
            }

            AuroraTheme(darkTheme = useDarkTheme) {
                AuroraApp(
                    viewModel = viewModel,
                    onTriggerBiometricAuth = { onSuccess, onError ->
                        showBiometricPrompt(this, onSuccess, onError)
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onAppResumed()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == NoteShareHelper.REQUEST_CODE_SAVE_TO_FOLDER && resultCode == Activity.RESULT_OK) {
            NoteShareHelper.handlePendingSafResult(this, data?.data)
        }
    }

    private fun showBiometricPrompt(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errString.toString())
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onError("Authentication failed")
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Aurora Notes Security")
            .setSubtitle("Verify your identity with biometric or device PIN")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        try {
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            onError(e.localizedMessage ?: "Biometric prompt error")
        }
    }
}

@Composable
fun AuroraApp(
    viewModel: NotesViewModel,
    onTriggerBiometricAuth: (onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit = { _, _ -> }
) {
    val activeEditingNote by viewModel.activeEditingNote.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val pendingNavTab by viewModel.pendingNavTab.collectAsState()
    val isNavTransitionPending by viewModel.isNavTransitionPending.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val isGridView by viewModel.isGridView.collectAsState()
    val filteredTrashedNotes by viewModel.filteredTrashedNotes.collectAsState()
    val allActiveNotes by viewModel.allActiveNotes.collectAsState()
    val allArchivedNotes by viewModel.allArchivedNotes.collectAsState()
    val userTags by viewModel.userTags.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()

    val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsState()
    val isAppLocked by viewModel.isAppLocked.collectAsState()
    val isWelcomeOpen by viewModel.isWelcomeOpen.collectAsState()
    val isWhatsNewOpen by viewModel.isWhatsNewOpen.collectAsState()
    val isHapticsEnabled by viewModel.isHapticsEnabled.collectAsState()
    val isDbEncryptionEnabled by viewModel.isDbEncryptionEnabled.collectAsState()
    val storageStats by viewModel.storageStats.collectAsState()
    val lastCleanupSummary by viewModel.lastCleanupSummary.collectAsState()

    // Synchronously compute Active Notes (Tab 0) and Archived Notes (Tab 1)
    val allFilteredNotes = remember(allActiveNotes, searchQuery, selectedCategory) {
        var list = allActiveNotes
        if (selectedCategory != "All") {
            list = list.filter { note ->
                note.tags.any { it.equals(selectedCategory, ignoreCase = true) } ||
                    note.category.equals(selectedCategory, ignoreCase = true)
            }
        }
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            list = list.filter { note ->
                note.title.lowercase().contains(q) ||
                    note.content.lowercase().contains(q) ||
                    note.tags.any { it.lowercase().contains(q) } ||
                    note.checklist.any { it.text.lowercase().contains(q) }
            }
        }
        list
    }

    val archivedFilteredNotes = remember(allArchivedNotes, searchQuery, selectedCategory) {
        var list = allArchivedNotes
        if (selectedCategory != "All") {
            list = list.filter { note ->
                note.tags.any { it.equals(selectedCategory, ignoreCase = true) } ||
                    note.category.equals(selectedCategory, ignoreCase = true)
            }
        }
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            list = list.filter { note ->
                note.title.lowercase().contains(q) ||
                    note.content.lowercase().contains(q) ||
                    note.tags.any { it.lowercase().contains(q) } ||
                    note.checklist.any { it.text.lowercase().contains(q) }
            }
        }
        list
    }

    val archivedCount = remember(allArchivedNotes) { allArchivedNotes.size }
    val trashCount = remember(filteredTrashedNotes) { filteredTrashedNotes.size }

    LaunchedEffect(isAppLocked) {
        if (isAppLocked) {
            onTriggerBiometricAuth(
                { viewModel.unlockApp() },
                { /* keep locked on failure */ }
            )
        }
    }

    if (isAppLocked) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = AuroraRoyalViolet.copy(alpha = 0.15f),
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Fingerprint,
                                contentDescription = null,
                                tint = AuroraRoyalViolet,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    Text(
                        text = "Aurora Notes Locked",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )

                    Text(
                        text = "Authenticate with biometric fingerprint, face unlock, or device PIN to continue",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        ),
                        textAlign = TextAlign.Center
                    )

                    Button(
                        onClick = {
                            onTriggerBiometricAuth(
                                { viewModel.unlockApp() },
                                { /* keep locked */ }
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AuroraRoyalViolet),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Unlock App", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    } else {
        val coroutineScope = rememberCoroutineScope()
        var displayedEditorNote by remember { mutableStateOf<Note?>(activeEditingNote) }
        val editorRevealProgress = remember { Animatable(if (activeEditingNote != null) 1f else 0f) }
        var isClosingEditor by remember { mutableStateOf(false) }

        // Bottom selection action navbar state when holding down a note
        var selectionBarState by remember { mutableStateOf(SelectionActionBarState()) }

        LaunchedEffect(selectedTab) {
            if (selectedTab != 0 && selectedTab != 1) {
                selectionBarState = SelectionActionBarState()
            }
        }

        // Coordinates of the circular + FloatingActionButton in root space
        var fabCenterX by remember { mutableFloatStateOf(0f) }
        var fabCenterY by remember { mutableFloatStateOf(0f) }
        var fabRadiusPx by remember { mutableFloatStateOf(0f) }

        // Predictive back state for NoteEditorScreen and non-home tabs
        var editorBackProgress by remember { mutableFloatStateOf(0f) }
        var editorBackSwipeEdge by remember { mutableIntStateOf(0) }
        var tabBackProgress by remember { mutableFloatStateOf(0f) }

        LaunchedEffect(activeEditingNote) {
            if (activeEditingNote != null) {
                isClosingEditor = false
                displayedEditorNote = activeEditingNote
                editorBackProgress = 0f
                editorRevealProgress.snapTo(0f)
                editorRevealProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing)
                )
            } else if (displayedEditorNote != null && !isClosingEditor) {
                isClosingEditor = true
                editorRevealProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)
                )
                displayedEditorNote = null
                editorBackProgress = 0f
                isClosingEditor = false
            }
        }

        fun animateCloseEditor(onBeforeClose: () -> Unit = {}) {
            if (isClosingEditor) return
            isClosingEditor = true
            onBeforeClose()
            coroutineScope.launch {
                editorRevealProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)
                )
                displayedEditorNote = null
                editorBackProgress = 0f
                isClosingEditor = false
                viewModel.closeEditor()
            }
        }

        // Predictive Back for non-Notes tabs (Archive, Trash, Settings -> smoothly returns to Notes tab)
        PredictiveBackHandler(enabled = displayedEditorNote == null && selectedTab != 0 && !selectionBarState.isVisible) { progress ->
            try {
                progress.collect { backEvent ->
                    tabBackProgress = backEvent.progress
                }
                tabBackProgress = 0f
                viewModel.setSelectedTab(0)
            } catch (_: CancellationException) {
                tabBackProgress = 0f
            }
        }

        if (displayedEditorNote == null) {
            if (isWelcomeOpen) {
                WelcomeTourDialog(
                    onDismiss = { viewModel.dismissWelcome() }
                )
            } else if (isWhatsNewOpen) {
                WhatsNewDialog(
                    onDismiss = { viewModel.dismissWhatsNew() }
                )
            }
        }

        // Pre-warm all 4 menus shortly after initial launch so jumping to Settings/Archive/Trash has 0ms cold-composition lag
        var areTabsPrewarmed by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(180L)
            areTabsPrewarmed = true
        }

        // Direct pair slide transition between fromTab and toTab (never mounts or animates skipped middle tabs)
        var fromTab by remember { mutableIntStateOf(selectedTab) }
        var toTab by remember { mutableIntStateOf(selectedTab) }
        val tabSlideProgress = remember { Animatable(1f) }

        LaunchedEffect(selectedTab) {
            if (selectedTab != toTab) {
                fromTab = toTab
                toTab = selectedTab
                tabSlideProgress.snapTo(0f)
                tabSlideProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 210,
                        easing = FastOutSlowInEasing
                    )
                )
                fromTab = toTab
            }
        }

        val slideDir = if (toTab >= fromTab) 1f else -1f
        val progressVal = tabSlideProgress.value

        fun computeTabOffsetPx(tabIndex: Int, widthPx: Float): Int {
            val slideSpan = widthPx * 0.24f
            return when {
                tabIndex == toTab -> {
                    if (fromTab == toTab || progressVal >= 0.999f) {
                        0
                    } else {
                        ((1f - progressVal) * slideDir * slideSpan).toInt()
                    }
                }
                tabIndex == fromTab && progressVal < 0.999f -> {
                    (-progressVal * slideDir * slideSpan).toInt()
                }
                else -> {
                    (widthPx * 2.5f).toInt()
                }
            }
        }

        fun computeTabAlpha(tabIndex: Int): Float {
            return when {
                tabIndex == toTab -> {
                    if (fromTab == toTab || progressVal >= 0.999f) 1f else progressVal.coerceIn(0f, 1f)
                }
                tabIndex == fromTab && progressVal < 0.999f -> {
                    (1f - progressVal).coerceIn(0f, 1f)
                }
                else -> 0f
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                floatingActionButton = {
                    if (selectedTab == 0 && !selectionBarState.isVisible) {
                        FloatingActionButton(
                            onClick = { viewModel.openNewNote() },
                            containerColor = AuroraRoyalViolet,
                            contentColor = Color.White,
                            shape = CircleShape,
                            modifier = Modifier.onGloballyPositioned { coords ->
                                val pos = coords.positionInRoot()
                                fabCenterX = pos.x + coords.size.width / 2f
                                fabCenterY = pos.y + coords.size.height / 2f
                                fabRadiusPx = coords.size.width / 2f
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "New Note")
                        }
                    }
                },
                bottomBar = {
                    AnimatedBottomPillBar(
                        selectedTab = selectedTab,
                        pendingTab = pendingNavTab,
                        isNavTransitionPending = isNavTransitionPending,
                        onTabSelected = { viewModel.setSelectedTab(it) },
                        archivedCount = archivedCount,
                        trashCount = trashCount,
                        isHapticsEnabled = isHapticsEnabled,
                        selectionState = selectionBarState
                    )
                }
            ) { paddingValues ->
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .graphicsLayer {
                            if (tabBackProgress > 0f) {
                                val s = 1f - (tabBackProgress * 0.08f)
                                scaleX = s
                                scaleY = s
                                alpha = (1f - tabBackProgress * 0.25f).coerceIn(0.6f, 1f)
                            }
                        }
                ) {
                    val widthPx = with(LocalDensity.current) { maxWidth.toPx().coerceAtLeast(1f) }

                    // Tab 0: Notes (kept pre-warmed in memory)
                    if (areTabsPrewarmed || toTab == 0 || fromTab == 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .offset { IntOffset(x = computeTabOffsetPx(0, widthPx), y = 0) }
                                .graphicsLayer {
                                    alpha = computeTabAlpha(0)
                                }
                        ) {
                            NotesScreen(
                                notes = allFilteredNotes,
                                userTags = userTags,
                                searchQuery = searchQuery,
                                onSearchChange = { viewModel.setSearchQuery(it) },
                                selectedCategory = selectedCategory,
                                onSelectCategory = { viewModel.setSelectedCategory(it) },
                                isGridView = isGridView,
                                onToggleGridView = { viewModel.toggleGridView() },
                                onSelectNote = { viewModel.openEditNote(it) },
                                onTogglePin = { viewModel.togglePin(it) },
                                onDeleteNote = { viewModel.moveToTrash(it) },
                                onDeleteNotesBatch = { ids -> viewModel.moveToTrashBatch(ids) },
                                onArchiveNotesBatch = { ids -> viewModel.archiveNotesBatch(ids) },
                                onToggleCheckItem = { noteId, itemId ->
                                    viewModel.toggleChecklistItem(noteId, itemId)
                                },
                                onAddNote = { viewModel.openNewNote() },
                                onSaveNoteDetails = { updatedNote -> viewModel.saveNote(updatedNote) },
                                onReorderNotes = { orderedIds -> viewModel.reorderNotes(orderedIds, isArchive = false) },
                                onCreateTag = { newTag -> viewModel.addUserTag(newTag) },
                                onDeleteTag = { tag -> viewModel.deleteTagAndDetachFromNotes(tag) },
                                isArchiveTab = false,
                                isActiveTab = selectedTab == 0,
                                onSelectionBarStateChange = { state ->
                                    if (selectedTab == 0) selectionBarState = state
                                }
                            )
                        }
                    }

                    // Tab 1: Archive (kept pre-warmed in memory)
                    if (areTabsPrewarmed || toTab == 1 || fromTab == 1) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .offset { IntOffset(x = computeTabOffsetPx(1, widthPx), y = 0) }
                                .graphicsLayer {
                                    alpha = computeTabAlpha(1)
                                }
                        ) {
                            NotesScreen(
                                notes = archivedFilteredNotes,
                                userTags = userTags,
                                searchQuery = searchQuery,
                                onSearchChange = { viewModel.setSearchQuery(it) },
                                selectedCategory = selectedCategory,
                                onSelectCategory = { viewModel.setSelectedCategory(it) },
                                isGridView = isGridView,
                                onToggleGridView = { viewModel.toggleGridView() },
                                onSelectNote = { viewModel.openEditNote(it) },
                                onTogglePin = { viewModel.togglePin(it) },
                                onDeleteNote = { viewModel.moveToTrash(it) },
                                onDeleteNotesBatch = { ids -> viewModel.moveToTrashBatch(ids) },
                                onUnarchiveNote = { id -> viewModel.unarchiveNote(id) },
                                onUnarchiveNotesBatch = { ids -> viewModel.unarchiveNotesBatch(ids) },
                                onToggleCheckItem = { noteId, itemId ->
                                    viewModel.toggleChecklistItem(noteId, itemId)
                                },
                                onAddNote = { viewModel.openNewNote() },
                                onReorderNotes = { orderedIds -> viewModel.reorderNotes(orderedIds, isArchive = true) },
                                onCreateTag = { newTag -> viewModel.addUserTag(newTag) },
                                onDeleteTag = { tag -> viewModel.deleteTagAndDetachFromNotes(tag) },
                                isArchiveTab = true,
                                isActiveTab = selectedTab == 1,
                                onSelectionBarStateChange = { state ->
                                    if (selectedTab == 1) selectionBarState = state
                                }
                            )
                        }
                    }

                    // Tab 2: Trash (kept pre-warmed in memory)
                    if (areTabsPrewarmed || toTab == 2 || fromTab == 2) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .offset { IntOffset(x = computeTabOffsetPx(2, widthPx), y = 0) }
                                .graphicsLayer {
                                    alpha = computeTabAlpha(2)
                                }
                        ) {
                            TrashScreen(
                                trashedNotes = filteredTrashedNotes,
                                searchQuery = searchQuery,
                                onSearchChange = { viewModel.setSearchQuery(it) },
                                isGridView = isGridView,
                                onToggleGridView = { viewModel.toggleGridView() },
                                onRestoreNote = { viewModel.restoreFromTrash(it) },
                                onDeletePermanently = { viewModel.deletePermanently(it) },
                                onEmptyTrash = { viewModel.emptyTrash() }
                            )
                        }
                    }

                    // Tab 3: Settings (kept pre-warmed in memory with LazyColumn)
                    if (areTabsPrewarmed || toTab == 3 || fromTab == 3) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .offset { IntOffset(x = computeTabOffsetPx(3, widthPx), y = 0) }
                                .graphicsLayer {
                                    alpha = computeTabAlpha(3)
                                }
                        ) {
                            SettingsScreen(
                                themeMode = themeMode,
                                onSelectThemeMode = { mode -> viewModel.setThemeMode(mode) },
                                isHapticsEnabled = isHapticsEnabled,
                                onSetHapticsEnabled = { enabled -> viewModel.setHapticsEnabled(enabled) },
                                isDbEncryptionEnabled = isDbEncryptionEnabled,
                                onSetDatabaseEncryption = { enabled ->
                                    viewModel.setDatabaseEncryptionEnabled(enabled)
                                },
                                storageStats = storageStats,
                                lastCleanupSummary = lastCleanupSummary,
                                onCleanUpOrphanedFiles = { onComplete ->
                                    viewModel.cleanUpOrphanedFiles(onComplete)
                                },
                                userTags = userTags,
                                onAddTag = { newTag -> viewModel.addUserTag(newTag) },
                                onRemoveTag = { tag -> viewModel.deleteTagAndDetachFromNotes(tag) },
                                onWipeAllData = { viewModel.wipeAllData() },
                                isBiometricEnabled = isBiometricEnabled,
                                onSetBiometricLock = { enabled ->
                                    viewModel.setBiometricLockEnabled(enabled)
                                },
                                onOpenWelcomeTour = { viewModel.reopenWelcome() },
                                onOpenWhatsNew = { viewModel.reopenWhatsNew() },
                                onClearLogs = { viewModel.clearDiagnosticLogs() },
                                onExportLogs = { viewModel.exportDiagnosticLogs() },
                                onTriggerBiometricAuth = onTriggerBiometricAuth
                            )
                        }
                    }
                }
            }

            // Full-Screen Circular Reveal Note Editor Overlay with Predictive Back Gesture Support
            val currentEditorNote = displayedEditorNote
            if (currentEditorNote != null) {
                val effectiveReveal = (editorRevealProgress.value * (1f - editorBackProgress * 0.22f)).coerceIn(0f, 1f)
                val purpleCircleTintAlpha = ((1f - (effectiveReveal / 0.28f)).coerceIn(0f, 1f)) * 0.85f

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            if (editorBackProgress > 0f) {
                                val backScale = 1f - (editorBackProgress * 0.12f)
                                scaleX = backScale
                                scaleY = backScale
                                val edgeDir = if (editorBackSwipeEdge == 0) 1f else -1f
                                translationX = edgeDir * editorBackProgress * 36f
                            }
                        }
                        .clip(
                            if (effectiveReveal < 0.999f) {
                                CircularRevealShape(
                                    progress = effectiveReveal,
                                    originX = fabCenterX,
                                    originY = fabCenterY,
                                    minRadiusPx = fabRadiusPx
                                )
                            } else if (editorBackProgress > 0f) {
                                RoundedCornerShape((editorBackProgress * 28f).dp)
                            } else {
                                RoundedCornerShape(0.dp)
                            }
                        )
                ) {
                    NoteEditorScreen(
                        note = currentEditorNote,
                        userTags = userTags,
                        onSaveAndClose = { updatedNote ->
                            animateCloseEditor {
                                viewModel.saveNote(updatedNote)
                            }
                        },
                        onSaveInPlace = { updatedNote ->
                            viewModel.saveNote(updatedNote)
                        },
                        onDiscardAndClose = {
                            animateCloseEditor()
                        },
                        onUnarchiveNote = { noteId ->
                            viewModel.unarchiveNote(noteId)
                        },
                        onDeleteNote = { noteId ->
                            viewModel.moveToTrash(noteId)
                        },
                        onDeleteTagGlobal = { tag ->
                            viewModel.deleteTagAndDetachFromNotes(tag)
                        },
                        onPredictiveBackProgress = { progress, swipeEdge ->
                            editorBackProgress = progress
                            editorBackSwipeEdge = swipeEdge
                        }
                    )

                    if (purpleCircleTintAlpha > 0.01f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(AuroraRoyalViolet.copy(alpha = purpleCircleTintAlpha))
                        )
                    }
                }
            }
        }
    }
}

private class CircularRevealShape(
    private val progress: Float,
    private val originX: Float,
    private val originY: Float,
    private val minRadiusPx: Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val p = progress.coerceIn(0f, 1f)
        val cx = if (originX > 0f) originX else size.width - with(density) { 44.dp.toPx() }
        val cy = if (originY > 0f) originY else size.height - with(density) { 116.dp.toPx() }
        val maxDx = max(cx, size.width - cx)
        val maxDy = max(cy, size.height - cy)
        val maxRadius = hypot(maxDx, maxDy) * 1.04f
        val startRadius = minRadiusPx.coerceAtLeast(with(density) { 28.dp.toPx() })
        val currentRadius = startRadius + (maxRadius - startRadius) * p

        val path = Path().apply {
            addOval(
                Rect(
                    left = cx - currentRadius,
                    top = cy - currentRadius,
                    right = cx + currentRadius,
                    bottom = cy + currentRadius
                )
            )
        }
        return Outline.Generic(path)
    }
}

@Composable
private fun WelcomeTourDialog(
    onDismiss: () -> Unit
) {
    var currentPage by remember { mutableStateOf(0) }

    val titles = listOf(
        "Welcome to Aurora Notes",
        "AES-256 Local Storage & Auto-Cleanup",
        "Custom Tags & Fast Editor",
        "Native Biometric Lock"
    )
    val subtitles = listOf(
        "Tactile Minimalism Meets AES-256 Cryptography",
        "Encrypted Offline Vault • Internet Only for Updates",
        "Live Word Counter, Exact Timestamps & Tags",
        "Protected by Android Device Security"
    )
    val descriptions = listOf(
        "Aurora Notes is crafted for distraction-free writing protected by mandatory hardware-backed AndroidKeyStore AES-256-GCM cryptography, fluid liquid navigation, and zero cloud bloat.",
        "All notes, checklists, and tags are permanently encrypted at rest with AES-256-GCM in your Room SQLite database and private device storage, with automatic orphaned and .tmp file cleanup.",
        "Track live word and letter counts on top of the editor, view exact hour and minute last-edited timestamps, and organize notes with custom tags.",
        "Enable Biometric & App Lock in Settings to require your phone's native fingerprint, face unlock, or PIN whenever Aurora Notes opens."
    )
    val icons = listOf(
        Icons.Default.AutoAwesome,
        Icons.Default.CheckCircle,
        Icons.Default.Tag,
        Icons.Default.Lock
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.5.dp, AuroraRoyalViolet.copy(alpha = 0.45f)),
            tonalElevation = 12.dp,
            shadowElevation = 24.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 420.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = AuroraRoyalViolet.copy(alpha = 0.14f),
                    modifier = Modifier.size(60.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icons[currentPage],
                            contentDescription = null,
                            tint = AuroraRoyalViolet,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "STEP ${currentPage + 1} OF ${titles.size}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = AuroraRoyalViolet
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = titles[currentPage],
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = subtitles[currentPage],
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        fontWeight = FontWeight.SemiBold
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.background,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = descriptions[currentPage],
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Page Indicator Dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in titles.indices) {
                        Box(
                            modifier = Modifier
                                .height(7.dp)
                                .width(if (i == currentPage) 22.dp else 7.dp)
                                .background(
                                    color = if (i == currentPage) AuroraRoyalViolet else AuroraRoyalViolet.copy(alpha = 0.25f),
                                    shape = CircleShape
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentPage > 0) {
                        OutlinedButton(
                            onClick = { currentPage-- },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Previous", fontSize = 12.sp)
                        }
                    } else {
                        TextButton(onClick = onDismiss) {
                            Text("Skip", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }

                    Button(
                        onClick = {
                            if (currentPage < titles.lastIndex) {
                                currentPage++
                            } else {
                                onDismiss()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AuroraRoyalViolet)
                    ) {
                        Text(
                            text = if (currentPage < titles.lastIndex) "Next" else "Get Started",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

private data class WhatsNewFeatureItem(
    val title: String,
    val badge: String,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@Composable
private fun WhatsNewDialog(
    onDismiss: () -> Unit
) {
    val features = remember {
        listOf(
            WhatsNewFeatureItem(
                title = "Drag-to-Reorder Notes & Hold Action Navbar",
                badge = "NEW FEATURE",
                description = "Hold down any note to elevate it with a subtle wobbly physics effect and drag it over other notes to reorder in both Masonry and Single-Column views, or use the pop-up Selection Action Navbar (Share, Archive, Edit, Delete).",
                icon = Icons.Default.Edit
            ),
            WhatsNewFeatureItem(
                title = "Gallery Note Image Export & Decrypted Markdown",
                badge = "EXPORT & MEDIA",
                description = "Insert lossless images in dedicated vertical blocks, save any note as a full-length visual image directly into your Android Gallery, or export decrypted Markdown (.md) notes with companion .png image files.",
                icon = Icons.Default.Image
            ),
            WhatsNewFeatureItem(
                title = "Persistent Lists, Checkboxes & Checkmark Save",
                badge = "EDITOR",
                description = "Bullet lists and checkboxes stay active across unlimited Enter presses without cutting off, coexist cleanly on the same line, and pair with compact H2/H1 buttons and the top-right Checkmark quick-save indicator.",
                icon = Icons.Default.CheckCircle
            ),
            WhatsNewFeatureItem(
                title = "AES-256-GCM Cryptography & Codebase Optimization",
                badge = "SECURITY",
                description = "Every note and attached image in your Room SQLite database and private storage is encrypted at rest with AndroidKeyStore AES-256-GCM cryptography, automatic orphaned file cleanup, and streamlined rendering.",
                icon = Icons.Default.Lock
            )
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val configuration = LocalConfiguration.current
        val screenHeight = configuration.screenHeightDp.dp
        val maxDialogHeight = (screenHeight * 0.88f).coerceAtLeast(320.dp)

        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.5.dp, AuroraRoyalViolet.copy(alpha = 0.45f)),
            tonalElevation = 12.dp,
            shadowElevation = 24.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 430.dp)
                .heightIn(max = maxDialogHeight)
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Row with App Icon, Version Badge, Title, and Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = AuroraRoyalViolet,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "MAJOR UPDATE • v${NotesViewModel.CURRENT_MAJOR_UPDATE_ID}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = AuroraRoyalViolet,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "What's New in Aurora Notes",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            maxLines = 1
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    thickness = 0.8.dp
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable Feature Items Column (Dynamically shrinks & scrolls on compact or landscape phones)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    features.forEach { item ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.background,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = AuroraRoyalViolet.copy(alpha = 0.14f),
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = item.icon,
                                            contentDescription = null,
                                            tint = AuroraRoyalViolet,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.5.sp
                                            )
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = item.description,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 11.5.sp,
                                            lineHeight = 16.sp,
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Anchored Bottom Action Button (ALWAYS 100% visible and clickable on every phone screen)
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AuroraRoyalViolet),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Got It, Let's Go",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

