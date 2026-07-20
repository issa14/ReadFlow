package com.inktone.ui.screen.sync

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader


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

    // ── Google Sign-In ──
    val googleSignInClient = remember {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope("https://www.googleapis.com/auth/drive.appdata"))
            .build()
        GoogleSignIn.getClient(context, gso)
    }
    val googleSignInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.handleGoogleSignInResult(result.data)
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
                placeholder = { Text("https://nextcloud.example.com/remote.php/dav/files/user/inktone") },
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
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    if (state.isLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onSurface)
                    else Text("Tester connexion")
                }
                if (state.webdavConnected) {
                    AssistChip(
                        onClick = {},
                        label = { Text("Connecté", color = MaterialTheme.colorScheme.tertiary) },
                        leadingIcon = {
                            Icon(
                                com.inktone.ui.theme.AppIcons.SuccessOutlined,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.50f))
            Spacer(Modifier.height(16.dp))

            // ── Section Google Drive ──
            SectionTitle("Google Drive")
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { googleSignInLauncher.launch(googleSignInClient.signInIntent) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading
            ) {
                Icon(
                    if (state.driveConnected) com.inktone.ui.theme.AppIcons.SuccessOutlined else Icons.Default.Cloud,
                    contentDescription = "Synchronisation",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (state.driveConnected) "Connecté à Google Drive"
                    else "Se connecter avec Google Drive"
                )
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.50f))
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
                    onClick = { exportLauncher.launch("inktone_backup.rfbackup") },
                    enabled = state.encryptionPassword.isNotBlank() && !state.isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Upload, "Exporter", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Exporter")
                }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/octet-stream", "*/*")) },
                    enabled = state.encryptionPassword.isNotBlank() && !state.isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Download, "Importer", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Importer")
                }
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.50f))
            Spacer(Modifier.height(16.dp))

            // ── Synchronisation cloud ──
            SectionTitle("Synchronisation cloud")
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { scope.launch { viewModel.syncNow() } },
                enabled = !state.isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(8.dp))
                }
                Icon(Icons.Default.Sync, "Synchroniser", modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Synchroniser maintenant")
            }

            if (state.lastSyncTimestamp > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Dernière synchro: ${formatTimestamp(state.lastSyncTimestamp)}",
                    color = MaterialTheme.colorScheme.outlineVariant,
                    fontSize = 12.sp
                )
            }

            // ── Message status ──
            state.statusMessage?.let { msg ->
                Spacer(Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (state.isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        msg,
                        color = if (state.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
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
        color = MaterialTheme.colorScheme.onSurface,
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
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.outlineVariant,
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 1.00f)
)
