package com.aurora.notes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
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
        setContent {
            AuroraTheme {
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
            .setTitle("Aurora Notes Lock")
            .setSubtitle("Verify identity to access your notes")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()

        try {
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            onError(e.localizedMessage ?: "Biometric prompt error")
        }
    }
}

import com.aurora.notes.ui.components.FloatingPillNavigationBar
import androidx.compose.material.icons.filled.Fingerprint

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

    val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsState()
    val isAppLocked by viewModel.isAppLocked.collectAsState()

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
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Column(
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = AuroraRoyalViolet.copy(alpha = 0.15f),
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
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
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    )

                    Text(
                        text = "Authenticate with biometric fingerprint, face unlock, or device PIN to continue",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
                        Text("Unlock App")
                    }
                }
            }
        }
    } else if (activeEditingNote != null) {
        NoteEditorScreen(
            note = activeEditingNote!!,
            onSaveAndClose = { updatedNote ->
                viewModel.saveNote(updatedNote)
                viewModel.closeEditor()
            }
        )
    } else {
        Scaffold(
            floatingActionButton = {
                if (selectedTab == 0 || selectedTab == 1) {
                    FloatingActionButton(
                        onClick = { viewModel.openNewNote() },
                        containerColor = AuroraRoyalViolet,
                        contentColor = MaterialTheme.colorScheme.surface,
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "New Note")
                    }
                }
            },
            bottomBar = {
                FloatingPillNavigationBar(
                    selectedTab = selectedTab,
                    onTabSelected = { viewModel.setSelectedTab(it) }
                )
            }
        ) { paddingValues ->
            when (selectedTab) {
                0, 1 -> {
                    NotesScreen(
                        notes = filteredNotes,
                        searchQuery = searchQuery,
                        onSearchChange = { viewModel.setSearchQuery(it) },
                        selectedCategory = selectedCategory,
                        onSelectCategory = { viewModel.setSelectedCategory(it) },
                        isGridView = isGridView,
                        onToggleGridView = { viewModel.toggleGridView() },
                        onSelectNote = { viewModel.openEditNote(it) },
                        onTogglePin = { viewModel.togglePin(it) },
                        onDeleteNote = { viewModel.moveToTrash(it) },
                        onToggleCheckItem = { noteId, itemId -> viewModel.toggleChecklistItem(noteId, itemId) },
                        onAddNote = { viewModel.openNewNote() },
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
                        isBiometricEnabled = isBiometricEnabled,
                        onSetBiometricLock = { enabled -> viewModel.setBiometricLockEnabled(enabled) },
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
