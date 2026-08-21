package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.VaultItemEntity
import com.example.data.local.VaultSecurityManager
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.MagentaAccent
import com.example.ui.theme.SunsetGold

@Composable
fun PrivateVaultScreen(
    isUnlocked: Boolean,
    vaultItems: List<VaultItemEntity>,
    securityManager: VaultSecurityManager,
    onUnlock: (String) -> Boolean,
    onLock: () -> Unit,
    onSetupPin: (String, String, String) -> Boolean,
    onAddItem: (String, String, String, MediaType, String) -> Unit,
    onDeleteItem: (VaultItemEntity) -> Unit,
    onPlayMediaItem: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showForgotDialog by remember { mutableStateOf(false) }

    val isVaultSetup = securityManager.isVaultConfigured()

    if (!isUnlocked) {
        // Lock / Setup Screen
        VaultLockKeypadView(
            isConfigured = isVaultSetup,
            pinInput = pinInput,
            isError = pinError,
            securityQuestion = securityManager.getSecurityQuestion(),
            onPinChange = {
                pinInput = it
                pinError = false
            },
            onUnlock = {
                val success = onUnlock(pinInput)
                if (!success) {
                    pinError = true
                    Toast.makeText(context, "Incorrect PIN, please try again", Toast.LENGTH_SHORT).show()
                }
            },
            onSetup = { pin, q, a ->
                val success = onSetupPin(pin, q, a)
                if (success) {
                    Toast.makeText(context, "Vault PIN Secured!", Toast.LENGTH_SHORT).show()
                }
            },
            onForgotPin = { showForgotDialog = true },
            modifier = modifier
        )

        // Forgot PIN recovery dialog
        if (showForgotDialog) {
            var answerInput by remember { mutableStateOf("") }
            var newPinInput by remember { mutableStateOf("") }

            AlertDialog(
                onDismissRequest = { showForgotDialog = false },
                title = { Text("Reset Vault PIN", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            text = "Security Question: ${securityManager.getSecurityQuestion()}",
                            color = CyanPrimary,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = answerInput,
                            onValueChange = { answerInput = it },
                            placeholder = { Text("Your Secret Answer") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newPinInput,
                            onValueChange = { newPinInput = it },
                            placeholder = { Text("Enter New 4-digit PIN") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val resetOk = securityManager.resetPinWithAnswer(answerInput, newPinInput)
                            if (resetOk) {
                                onUnlock(newPinInput)
                                showForgotDialog = false
                                Toast.makeText(context, "PIN Reset Successfully!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Wrong security answer", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary)
                    ) {
                        Text("Reset & Unlock", color = Color.Black)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showForgotDialog = false }) {
                        Text("Cancel", color = Color.White)
                    }
                },
                containerColor = Color(0xFF1F2033)
            )
        }
    } else {
        // Vault Unlocked Content
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(DarkSurface)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Banner
                Surface(
                    color = Color(0xFF161726),
                    shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Private Secret Vault",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.LockOpen,
                                    contentDescription = "Unlocked",
                                    tint = SunsetGold,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = "${vaultItems.size} Secret items securely locked",
                                style = MaterialTheme.typography.bodySmall,
                                color = SunsetGold
                            )
                        }

                        Button(
                            onClick = onLock,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF33354E)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("lock_vault_button")
                        ) {
                            Icon(Icons.Default.Lock, null, modifier = Modifier.size(16.dp), tint = SunsetGold)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Lock", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }

                if (vaultItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = SunsetGold.copy(alpha = 0.5f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Your Private Vault is Empty",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Add secret passwords, private notes, hidden photos, songs, or streaming links.",
                                color = Color.White.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 90.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(vaultItems, key = { it.id }) { item ->
                            VaultItemCard(
                                item = item,
                                onDelete = { onDeleteItem(item) },
                                onPlay = {
                                    val type = try {
                                        MediaType.valueOf(item.mediaType)
                                    } catch (e: Exception) {
                                        MediaType.STREAM_URL
                                    }
                                    val mediaItem = MediaItem(
                                        title = item.title,
                                        subtitle = item.secretNotes.ifEmpty { "Private Vault Item" },
                                        uri = item.contentOrUri,
                                        type = type
                                    )
                                    onPlayMediaItem(mediaItem)
                                }
                            )
                        }
                    }
                }
            }

            // FAB to add new secret item
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = SunsetGold,
                contentColor = Color.Black,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 85.dp)
                    .testTag("add_vault_item_fab")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Secret Item")
            }

            // Add Vault Item Dialog
            if (showAddDialog) {
                AddVaultItemDialog(
                    onDismiss = { showAddDialog = false },
                    onConfirm = { title, content, notes, type, cat ->
                        onAddItem(title, content, notes, type, cat)
                        showAddDialog = false
                    }
                )
            }
        }
    }
}

@Composable
private fun VaultLockKeypadView(
    isConfigured: Boolean,
    pinInput: String,
    isError: Boolean,
    securityQuestion: String,
    onPinChange: (String) -> Unit,
    onUnlock: () -> Unit,
    onSetup: (String, String, String) -> Unit,
    onForgotPin: () -> Unit,
    modifier: Modifier = Modifier
) {
    var setupAnswer by remember { mutableStateOf("Cyan") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkSurface)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Vault Lock Icon
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(SunsetGold.copy(alpha = 0.3f), Color(0xFF1D1B28))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Vault Safe",
                tint = SunsetGold,
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = if (isConfigured) "Private Vault Locked" else "Set Up Private Vault",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            color = Color.White
        )
        Text(
            text = if (isConfigured) "Enter your 4-digit Secret PIN to enter" else "Create a 4-digit PIN to secure your private files",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // PIN Input Dots / Field
        OutlinedTextField(
            value = pinInput,
            onValueChange = {
                if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                    onPinChange(it)
                    if (isConfigured && it.length == 4) {
                        onUnlock()
                    }
                }
            },
            placeholder = { Text("••••", fontSize = 24.sp, textAlign = TextAlign.Center) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            isError = isError,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SunsetGold,
                unfocusedBorderColor = Color(0xFF3B3D5A),
                errorBorderColor = MagentaAccent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .width(200.dp)
                .testTag("vault_pin_input_field")
        )

        if (!isConfigured) {
            Spacer(modifier = Modifier.height(14.dp))
            OutlinedTextField(
                value = setupAnswer,
                onValueChange = { setupAnswer = it },
                label = { Text("Security Answer: (Favorite Color)") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyanPrimary,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(0.85f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (isConfigured) {
                    onUnlock()
                } else {
                    if (pinInput.length >= 4) {
                        onSetup(pinInput, "Favorite Color", setupAnswer)
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = SunsetGold),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(48.dp)
                .testTag("vault_unlock_submit_button")
        ) {
            Text(
                text = if (isConfigured) "UNLOCK VAULT" else "SAVE & ACTIVATE VAULT",
                color = Color.Black,
                fontWeight = FontWeight.Black
            )
        }

        if (isConfigured) {
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onForgotPin) {
                Text(
                    text = "Forgot PIN / Reset Password",
                    color = SunsetGold.copy(alpha = 0.8f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun VaultItemCard(
    item: VaultItemEntity,
    onDelete: () -> Unit,
    onPlay: () -> Unit
) {
    val context = LocalContext.current

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1C2E)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("vault_item_${item.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                when (item.mediaType) {
                                    "PHOTO" -> SunsetGold.copy(alpha = 0.2f)
                                    "VIDEO" -> MagentaAccent.copy(alpha = 0.2f)
                                    "MUSIC" -> Color(0xFFB588FF).copy(alpha = 0.2f)
                                    else -> CyanPrimary.copy(alpha = 0.2f)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (item.mediaType) {
                                "PHOTO" -> Icons.Default.Image
                                "VIDEO" -> Icons.Default.Videocam
                                "MUSIC" -> Icons.Default.MusicNote
                                "STREAM_URL" -> Icons.Default.Link
                                else -> Icons.Default.Description
                            },
                            contentDescription = null,
                            tint = when (item.mediaType) {
                                "PHOTO" -> SunsetGold
                                "VIDEO" -> MagentaAccent
                                "MUSIC" -> Color(0xFFB588FF)
                                else -> CyanPrimary
                            },
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = item.category,
                            style = MaterialTheme.typography.bodySmall,
                            color = SunsetGold
                        )
                    }
                }

                Row {
                    IconButton(
                        onClick = {
                            val clip = ClipData.newPlainText("Vault Content", item.contentOrUri)
                            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                            Toast.makeText(context, "Copied secretly to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, "Copy", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, "Delete", tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF131422),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = item.contentOrUri,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(10.dp)
                )
            }

            if (item.secretNotes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Note: ${item.secretNotes}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }

            if (item.mediaType != "PRIVATE_NOTE") {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onPlay,
                    colors = ButtonDefaults.buttonColors(containerColor = SunsetGold),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Open & Play in GS Player", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun AddVaultItemDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, MediaType, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(MediaType.PRIVATE_NOTE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Secret Vault Item", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Secret Title") },
                    placeholder = { Text("e.g. My Password, Private Stream...") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Secret Content / Link / URL") },
                    placeholder = { Text("Paste secret code, link, or note...") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Secret Remarks (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && content.isNotBlank()) {
                        val type = when {
                            content.startsWith("http") && (content.endsWith(".mp4") || content.contains("video")) -> MediaType.VIDEO
                            content.startsWith("http") && (content.endsWith(".mp3") || content.contains("audio")) -> MediaType.MUSIC
                            content.startsWith("http") -> MediaType.STREAM_URL
                            else -> MediaType.PRIVATE_NOTE
                        }
                        onConfirm(title, content, notes, type, "Private")
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = SunsetGold)
            ) {
                Text("Lock in Vault", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White)
            }
        },
        containerColor = Color(0xFF1E1F33)
    )
}
