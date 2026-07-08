package com.readflow.ui.screen.library

import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.readflow.ui.theme.AccentBlue
import com.readflow.ui.theme.AccentTts
import com.readflow.ui.theme.SurfaceRaised
import com.readflow.ui.theme.TextMain
import com.readflow.ui.theme.TextMuted
import java.io.File
import java.io.FileFilter

private val SUPPORTED_EXTENSIONS = setOf("epub", "pdf", "mobi")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    onFileSelected: (File) -> Unit,
    onBack: () -> Unit
) {
    var currentDir by remember { mutableStateOf(Environment.getExternalStorageDirectory()) }
    val (dirs, files) = remember(currentDir) {
        val all = currentDir?.listFiles(FileFilter { it.isDirectory || it.extension.lowercase() in SUPPORTED_EXTENSIONS })
            ?.sortedWith(compareBy<File> { if (it.isDirectory) 0 else 1 }.thenBy { it.name.lowercase() })
            ?: emptyList()
        all.partition { it.isDirectory }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        currentDir?.name ?: "Fichiers",
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    if (currentDir?.parentFile != null && currentDir?.path != Environment.getExternalStorageDirectory()?.path) {
                        IconButton(onClick = {
                            currentDir = currentDir?.parentFile
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Remonter",
                                tint = Color.White)
                        }
                    }
                },
                actions = {
                    TextButton(onClick = onBack) {
                        Text(" Biblio", color = Color.White.copy(alpha = 0.8f))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AccentBlue
                )
            )
        },
        containerColor = Color(0xFF0D0E15)
    ) { padding ->
        if (currentDir == null || (dirs.isEmpty() && files.isEmpty())) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Folder, null,
                        modifier = Modifier.size(56.dp),
                        tint = TextMuted.copy(alpha = 0.25f)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Dossier vide ou inaccessible",
                        fontSize = 15.sp,
                        color = TextMuted
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(dirs, key = { it.absolutePath }) { dir ->
                    FileRow(
                        name = dir.name,
                        isDirectory = true,
                        onClick = { currentDir = dir }
                    )
                }
                items(files, key = { it.absolutePath }) { file ->
                    FileRow(
                        name = file.name,
                        isDirectory = false,
                        onClick = { onFileSelected(file) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FileRow(
    name: String,
    isDirectory: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = Color.Transparent,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isDirectory) Icons.Default.Folder else Icons.Default.Description,
                contentDescription = null,
                tint = if (isDirectory) AccentBlue else AccentTts,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(16.dp))
            Text(
                name,
                fontSize = 14.sp,
                color = TextMain,
                fontWeight = if (isDirectory) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
