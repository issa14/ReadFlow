package com.inktone.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.ImportContacts
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.ViewDay
import androidx.compose.material.icons.outlined.Warning

/**
 * Point d'entrée unique pour les icônes de l'app (Material Symbols).
 * Convention : `Outlined` par défaut, `Filled` pour les marqueurs d'état
 * qui doivent rester lisibles à petite taille (marque-page, statut).
 */
object AppIcons {
    val Bookmark = Icons.Filled.Bookmark
    val BookmarkAdd = Icons.Outlined.BookmarkAdd
    val Note = Icons.Outlined.EditNote
    val Copy = Icons.Outlined.ContentCopy
    val Highlight = Icons.Filled.Highlight
    val Hint = Icons.Outlined.Lightbulb

    val Success = Icons.Filled.CheckCircle
    val SuccessOutlined = Icons.Outlined.CheckCircle
    val Error = Icons.Filled.Error
    val ErrorOutlined = Icons.Outlined.Error
    val Warning = Icons.Filled.Warning
    val WarningOutlined = Icons.Outlined.Warning

    val Presets = Icons.Outlined.Bolt
    val Reading = Icons.AutoMirrored.Outlined.MenuBook
    val Device = Icons.Outlined.Smartphone
    val Appearance = Icons.Outlined.Palette
    val Accessibility = Icons.Outlined.Accessibility
    val Data = Icons.Outlined.Save
    val Pronunciation = Icons.Outlined.RecordVoiceOver

    val Mic = Icons.Outlined.Mic
    val Speaking = Icons.AutoMirrored.Outlined.VolumeUp
    val Loading = Icons.Outlined.HourglassEmpty
    val Stats = Icons.Outlined.BarChart

    val Search = Icons.Outlined.Search
    val Toc = Icons.AutoMirrored.Outlined.List
    val ReadingModePaged = Icons.Outlined.ViewDay
    val ReadingModeScroll = Icons.Outlined.ImportContacts
}
