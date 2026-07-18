package com.inktone.ui.screen.library

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inktone.ui.theme.AccentBlue
import com.inktone.ui.theme.AccentTts
import com.inktone.ui.theme.TextMain
import com.inktone.ui.theme.TextMuted
import java.io.File
import java.io.FileFilter

private val SUPPORTED_EXTENSIONS = setOf("epub", "pdf", "mobi")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    onFileSelected: (File) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // Permission MANAGE_EXTERNAL_STORAGE (API 30+)
    var hasPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
            else true
        )
    }

    // Relancer la vérif au retour des paramètres
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        hasPermission = if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else true
    }

    // Recheck permission quand l'écran reprend le focus
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 30) {
            hasPermission = Environment.isExternalStorageManager()
        }
    }

    // ── ÉCRAN PERMISSION ─────────────────────────────
    if (!hasPermission) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Lock, null, modifier = Modifier.size(56.dp),
                    tint = TextMuted.copy(alpha = 0.3f))
                Spacer(Modifier.height(16.dp))
                Text("Accès au stockage requis", fontSize = 17.sp,
                    fontWeight = FontWeight.Medium, color = TextMain)
                Spacer(Modifier.height(6.dp))
                Text("Pour parcourir vos fichiers EPUB,",
                    fontSize = 13.sp, color = TextMuted)
                Text("Android nécessite une autorisation.",
                    fontSize = 13.sp, color = TextMuted)
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= 30) {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            permissionLauncher.launch(intent)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Settings, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Accorder l'accès dans les paramètres")
                }
            }
        }
        return
    }

    // ── EXPLORATEUR NORMAL ────────────────────────────
    var currentDir by remember {
        mutableStateOf(Environment.getExternalStorageDirectory())
    }

    val (dirs, files) = remember(currentDir, hasPermission) {
        val all = currentDir?.listFiles(
            FileFilter { it.isDirectory || it.extension.lowercase() in SUPPORTED_EXTENSIONS }
        )
            ?.sortedWith(compareBy<File> { if (it.isDirectory) 0 else 1 }.thenBy { it.name.lowercase() })
            ?: emptyList()
        all.partition { it.isDirectory }
    }

    if (currentDir == null || (dirs.isEmpty() && files.isEmpty())) {
        EmptyDir()
    } else {
        LazyColumn(
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(dirs, key = { it.absolutePath }) { dir ->
                FileRow(dir.name, isDirectory = true) { currentDir = dir }
            }
            items(files, key = { it.absolutePath }) { file ->
                FileRow(file.name, isDirectory = false) { onFileSelected(file) }
            }
        }
    }
}

@Composable
private fun EmptyDir() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Folder, null, modifier = Modifier.size(56.dp),
                tint = TextMuted.copy(alpha = 0.25f))
            Spacer(Modifier.height(12.dp))
            Text("Dossier vide ou inaccessible", fontSize = 15.sp, color = TextMuted)
        }
    }
}

@Composable
private fun FileRow(name: String, isDirectory: Boolean, onClick: () -> Unit) {
    Surface(color = Color.Transparent, onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isDirectory) Icons.Default.Folder else Icons.Default.Description,
                null, tint = if (isDirectory) AccentBlue else AccentTts,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(16.dp))
            Text(name, fontSize = 14.sp, color = TextMain,
                fontWeight = if (isDirectory) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
