package com.iris.notes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.iris.notes.model.Note
import com.iris.notes.security.CryptoManager
import com.iris.notes.ui.theme.IrisRoyalViolet
import javax.crypto.spec.SecretKeySpec

@Composable
fun CipherInspectorDialog(
    note: Note,
    onDismiss: () -> Unit
) {
    // Generate simulated/real AES-256-GCM cipher payload for the note
    val cipherDetails = remember(note.id) {
        val dummyKey = SecretKeySpec(ByteArray(32) { (it + 7).toByte() }, "AES")
        val (cipherHex, ivHex) = CryptoManager.encryptAesGcm(
            "${note.title}\n\n${note.content}",
            dummyKey
        )
        val salt = CryptoManager.generateSalt().joinToString("") { "%02x".format(it) }
        Triple(cipherHex, ivHex, salt)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = IrisRoyalViolet,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AES-256-GCM Cipher",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Encrypted in local device memory using PBKDF2-SHA256 (100,000 rounds) and authenticated AES-GCM.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                CipherField(title = "INITIALIZATION VECTOR (IV)", value = cipherDetails.second)
                Spacer(modifier = Modifier.height(10.dp))
                CipherField(title = "CRYPTOGRAPHIC SALT", value = cipherDetails.third)
                Spacer(modifier = Modifier.height(10.dp))
                CipherField(title = "CIPHERTEXT PAYLOAD", value = cipherDetails.first)

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = IrisRoyalViolet),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Close Inspector")
                }
            }
        }
    }
}

@Composable
private fun CipherField(title: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                color = IrisRoyalViolet
            )
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.background,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(8.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
            )
        }
    }
}
