package com.aurora.notes

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.aurora.notes.ui.components.FloatingPillNavigationBar
import com.aurora.notes.ui.screens.NoteEditorScreen
import com.aurora.notes.ui.screens.NotesScreen
import com.aurora.notes.ui.screens.SettingsScreen
import com.aurora.notes.ui.screens.TrashScreen
import com.aurora.notes.ui.theme.AuroraRoyalViolet
import com.aurora.notes.ui.theme.AuroraTheme
import com.aurora.notes.viewmodel.NotesViewModel

class MainActivity : FragmentActivity() {

    private val viewModel: NotesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.getBooleanExtra("extra_updated_restarted", false) == true) {
            intent?.removeExtra("extra_updated_restarted")
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
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val isGridView by viewModel.isGridView.collectAsState()
    val filteredNotes by viewModel.filteredActiveNotes.collectAsState()
    val filteredTrashedNotes by viewModel.filteredTrashedNotes.collectAsState()
    val allActiveNotes by viewModel.allActiveNotes.collectAsState()
    val userTags by viewModel.userTags.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()

    val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsState()
    val isAppLocked by viewModel.isAppLocked.collectAsState()
    val isWelcomeOpen by viewModel.isWelcomeOpen.collectAsState()

    val pinnedCount = remember(allActiveNotes) { allActiveNotes.count { it.isPinned } }
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
    } else if (activeEditingNote != null) {
        NoteEditorScreen(
            note = activeEditingNote!!,
            userTags = userTags,
            onSaveAndClose = { updatedNote ->
                viewModel.saveNote(updatedNote)
                viewModel.closeEditor()
            },
            onDiscardAndClose = {
                viewModel.closeEditor()
            },
            onDeleteTagGlobal = { tag ->
                viewModel.deleteTagAndDetachFromNotes(tag)
            }
        )
    } else {
        if (isWelcomeOpen) {
            WelcomeTourDialog(
                onDismiss = { viewModel.dismissWelcome() }
            )
        }

        Scaffold(
            floatingActionButton = {
                if (selectedTab == 0 || selectedTab == 1) {
                    FloatingActionButton(
                        onClick = { viewModel.openNewNote() },
                        containerColor = AuroraRoyalViolet,
                        contentColor = Color.White,
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "New Note")
                    }
                }
            },
            bottomBar = {
                FloatingPillNavigationBar(
                    selectedTab = selectedTab,
                    onTabSelected = { viewModel.setSelectedTab(it) },
                    pinnedCount = pinnedCount,
                    trashCount = trashCount
                )
            }
        ) { paddingValues ->
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
                },
                label = "tab_content_transition"
            ) { targetTab ->
                when (targetTab) {
                    0, 1 -> {
                        NotesScreen(
                            notes = filteredNotes,
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
                            onToggleCheckItem = { noteId, itemId ->
                                viewModel.toggleChecklistItem(noteId, itemId)
                            },
                            onAddNote = { viewModel.openNewNote() },
                            onCreateTag = { newTag -> viewModel.addUserTag(newTag) },
                            onDeleteTag = { tag -> viewModel.deleteTagAndDetachFromNotes(tag) },
                            modifier = Modifier.padding(paddingValues)
                        )
                    }
                    2 -> {
                        TrashScreen(
                            trashedNotes = filteredTrashedNotes,
                            searchQuery = searchQuery,
                            onSearchChange = { viewModel.setSearchQuery(it) },
                            isGridView = isGridView,
                            onToggleGridView = { viewModel.toggleGridView() },
                            onRestoreNote = { viewModel.restoreFromTrash(it) },
                            onDeletePermanently = { viewModel.deletePermanently(it) },
                            onEmptyTrash = { viewModel.emptyTrash() },
                            modifier = Modifier.padding(paddingValues)
                        )
                    }
                    3 -> {
                        SettingsScreen(
                            themeMode = themeMode,
                            onSelectThemeMode = { mode -> viewModel.setThemeMode(mode) },
                            userTags = userTags,
                            onAddTag = { newTag -> viewModel.addUserTag(newTag) },
                            onRemoveTag = { tag -> viewModel.deleteTagAndDetachFromNotes(tag) },
                            onWipeAllData = { viewModel.wipeAllData() },
                            isBiometricEnabled = isBiometricEnabled,
                            onSetBiometricLock = { enabled ->
                                viewModel.setBiometricLockEnabled(enabled)
                            },
                            onOpenWelcomeTour = { viewModel.reopenWelcome() },
                            onClearLogs = { viewModel.clearDiagnosticLogs() },
                            onExportLogs = { viewModel.exportDiagnosticLogs() },
                            onTriggerBiometricAuth = onTriggerBiometricAuth,
                            modifier = Modifier.padding(paddingValues)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeTourDialog(
    onDismiss: () -> Unit
) {
    var currentPage by remember { mutableStateOf(0) }

    val titles = listOf(
        "Welcome to Aurora Notes",
        "Local-First & Minimal Internet",
        "Custom Tags & Fast Editor",
        "Native Biometric Lock"
    )
    val subtitles = listOf(
        "Atmospheric Minimalism Meets Speed",
        "Notes Stay Offline • Internet Only for Updates",
        "Create Tags & Style Text On The Fly",
        "Protected by Android Device Security"
    )
    val descriptions = listOf(
        "Aurora Notes is crafted for distraction-free writing with fluid liquid navigation, instant search, and zero cloud bloat.",
        "All notes, checklists, and tags are stored 100% locally in your phone's SQLite database. Internet permission is kept strictly minimal and is only used when you manually scan for GitHub Release updates in Settings.",
        "Tap '+ Create Tag' next to 'All' to organize notes your way, long-press any tag to delete it cleanly, and toggle Medium or Large text sizing as you type.",
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
