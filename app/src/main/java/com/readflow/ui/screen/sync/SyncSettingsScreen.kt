package com.readflow.ui.screen.sync

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

private val DarkBg = Color(0xFF0D0D0D)
private val CardBg = Color(0xFF1A1A1A)
private val AccentBlue = Color(0xFF4FC3F7)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSettingsScreen(
    onBack: () -> Unit,
    viewModel: SyncSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ── Export fichier ──
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let { viewModel.exportToUri(context, it) }
    }

    // ── Import fichier ──
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importFromUri(context, it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // ── Section WebDAV ──
            SectionTitle("WebDAV (Nextcloud / Owncloud)")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.webdavUrl,
                onValueChange = { viewModel.updateWebdavUrl(it) },
                label = { Text("URL du serveur") },
                placeholder = { Text("https://nextcloud.example.com/remote.php/dav/files/user/readflow") },
                singleLine = true,
                colors = darkTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.webdavUsername,
                    onValueChange = { viewModel.updateWebdavUser(it) },
                    label = { Text("Identifiant") },
                    singleLine = true,
                    colors = darkTextFieldColors(),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = state.webdavPassword,
                    onValueChange = { viewModel.updateWebdavPass(it) },
                    label = { Text("Mot de passe") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    colors = darkTextFieldColors(),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { scope.launch { viewModel.testWebdav() } },
                    enabled = !state.isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                ) {
                    if (state.isLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                    else Text("Tester connexion")
                }
                if (state.webdavConnected) {
                    AssistChip(
                        onClick = {},
                        label = { Text("✓ Connecté", color = Color(0xFF81C784)) }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            Spacer(Modifier.height(16.dp))

            // ── Section Google Drive ──
            SectionTitle("Google Drive")
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { viewModel.connectGoogleDrive() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Cloud, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (state.driveConnected) "✓ Connecté à Google Drive"
                    else "Se connecter avec Google Drive"
                )
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            Spacer(Modifier.height(16.dp))

            // ── Section Fichier Local ──
            SectionTitle("Fichier local (.rfbackup)")
            Spacer(Modifier.height(8.dp))

            // Mot de passe de chiffrement
            OutlinedTextField(
                value = state.encryptionPassword,
                onValueChange = { viewModel.updatePassword(it) },
                label = { Text("Mot de passe de chiffrement") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                colors = darkTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { exportLauncher.launch("readflow_backup.rfbackup") },
                    enabled = state.encryptionPassword.isNotBlank() && !state.isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Upload, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Exporter")
                }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/octet-stream", "*/*")) },
                    enabled = state.encryptionPassword.isNotBlank() && !state.isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Importer")
                }
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            Spacer(Modifier.height(16.dp))

            // ── Synchronisation cloud ──
            SectionTitle("Synchronisation cloud")
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { scope.launch { viewModel.syncNow() } },
                enabled = !state.isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF)),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                    Spacer(Modifier.width(8.dp))
                }
                Icon(Icons.Default.Sync, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Synchroniser maintenant")
            }

            if (state.lastSyncTimestamp > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Dernière synchro: ${formatTimestamp(state.lastSyncTimestamp)}",
                    color = Color.White.copy(alpha = 0.35f),
                    fontSize = 12.sp
                )
            }

            // ── Message status ──
            state.statusMessage?.let { msg ->
                Spacer(Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (state.isError) Color(0xFF4E1A1A) else Color(0xFF1A3A1A)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        msg,
                        color = if (state.isError) Color(0xFFFF6B6B) else Color(0xFF81C784),
                        modifier = Modifier.padding(12.dp),
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = Color.White.copy(alpha = 0.7f),
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp
    )
}

private fun formatTimestamp(ts: Long): String {
    val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(ts))
}

@Composable
private fun darkTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White.copy(alpha = 0.7f),
    focusedLabelColor = AccentBlue,
    unfocusedLabelColor = Color.White.copy(alpha = 0.4f),
    cursorColor = AccentBlue,
    focusedBorderColor = AccentBlue,
    unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
)
