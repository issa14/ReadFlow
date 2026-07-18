package com.inktone.ui.screen.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatLineSpacing
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.HorizontalDistribute
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inktone.ui.theme.OpenDyslexicFamily

@Composable
fun ReaderSettingsPanel(
    currentTheme: ReaderTheme,
    currentFont: ReaderFont,
    fontSizeSp: Float,
    lineHeightEm: Float,
    horizontalMarginDp: Int,
    onThemeChange: (ReaderTheme) -> Unit,
    onFontChange: (ReaderFont) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    onHorizontalMarginChange: (Int) -> Unit,
    accentColor: Color,
    panelBg: Color,
    textColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .background(panelBg)
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp)
    ) {
        // En-tête
        Text(
            text = "Options d'affichage",
            color = textColor.copy(alpha = 0.9f),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 12.dp),
            textAlign = TextAlign.Center
        )

        HorizontalDivider(color = textColor.copy(alpha = 0.15f), modifier = Modifier.padding(bottom = 20.dp))

        // ─────────────────────────────────────────────────────
        // 1. CHOIX DU THÈME
        // ─────────────────────────────────────────────────────
        Text(
            text = "Thèmes de lecture",
            color = textColor.copy(alpha = 0.6f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ThemeOptionItem(
                label = "Clair",
                isSelected = currentTheme == ReaderTheme.DAY,
                bgCol = Color(0xFFFAFAFA),
                textCol = Color(0xFF1A1A1A),
                borderColor = if (currentTheme == ReaderTheme.DAY) accentColor else Color.Transparent,
                onClick = { onThemeChange(ReaderTheme.DAY) },
                modifier = Modifier.weight(1f)
            )

            ThemeOptionItem(
                label = "Sépia",
                isSelected = currentTheme == ReaderTheme.SEPIA,
                bgCol = Color(0xFFF4ECD8),
                textCol = Color(0xFF3C2F2F),
                borderColor = if (currentTheme == ReaderTheme.SEPIA) accentColor else Color.Transparent,
                onClick = { onThemeChange(ReaderTheme.SEPIA) },
                modifier = Modifier.weight(1f)
            )

            ThemeOptionItem(
                label = "Sombre",
                isSelected = currentTheme == ReaderTheme.NIGHT,
                bgCol = Color(0xFF000000),
                textCol = Color(0xFFFAFAFA),
                borderColor = if (currentTheme == ReaderTheme.NIGHT) accentColor else Color.Transparent,
                onClick = { onThemeChange(ReaderTheme.NIGHT) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ─────────────────────────────────────────────────────
        // 2. CHOIX DE LA POLICE
        // ─────────────────────────────────────────────────────
        Text(
            text = "Polices d'écriture",
            color = textColor.copy(alpha = 0.6f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FontOptionItem(
                label = "Sérif",
                fontFamily = FontFamily.Serif,
                isSelected = currentFont == ReaderFont.SERIF,
                onClick = { onFontChange(ReaderFont.SERIF) },
                panelBg = panelBg,
                textColor = textColor,
                accentColor = accentColor,
                modifier = Modifier.weight(1f)
            )

            FontOptionItem(
                label = "Sans-Sérif",
                fontFamily = FontFamily.SansSerif,
                isSelected = currentFont == ReaderFont.SANS_SERIF,
                onClick = { onFontChange(ReaderFont.SANS_SERIF) },
                panelBg = panelBg,
                textColor = textColor,
                accentColor = accentColor,
                modifier = Modifier.weight(1f)
            )

            FontOptionItem(
                label = "Dyslexique",
                fontFamily = OpenDyslexicFamily,
                isSelected = currentFont == ReaderFont.OPEN_DYSLEXIC,
                onClick = { onFontChange(ReaderFont.OPEN_DYSLEXIC) },
                panelBg = panelBg,
                textColor = textColor,
                accentColor = accentColor,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // ─────────────────────────────────────────────────────
        // 3. TAILLE DE POLICE
        // ─────────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                Icons.Default.FormatSize,
                contentDescription = null,
                tint = textColor.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Taille de police",
                color = textColor.copy(alpha = 0.8f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${fontSizeSp.toInt()} sp",
                color = accentColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Slider(
            value = fontSizeSp,
            onValueChange = onFontSizeChange,
            valueRange = 12f..32f,
            steps = 9, // 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = textColor.copy(alpha = 0.2f)
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ─────────────────────────────────────────────────────
        // 4. ESPACEMENT DES LIGNES (INTERLIGNE)
        // ─────────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                Icons.Default.FormatLineSpacing,
                contentDescription = null,
                tint = textColor.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Espacement des lignes",
                color = textColor.copy(alpha = 0.8f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${"%.1f".format(lineHeightEm)}x",
                color = accentColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Slider(
            value = lineHeightEm,
            onValueChange = onLineHeightChange,
            valueRange = 1.2f..2.4f,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = textColor.copy(alpha = 0.2f)
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ─────────────────────────────────────────────────────
        // 5. MARGES HORIZONTALES
        // ─────────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                Icons.Default.HorizontalDistribute,
                contentDescription = null,
                tint = textColor.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Marges horizontales",
                color = textColor.copy(alpha = 0.8f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "$horizontalMarginDp dp",
                color = accentColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Slider(
            value = horizontalMarginDp.toFloat(),
            onValueChange = { onHorizontalMarginChange(it.toInt()) },
            valueRange = 8f..48f,
            steps = 4, // 8, 16, 24, 32, 40, 48
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = textColor.copy(alpha = 0.2f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun ThemeOptionItem(
    label: String,
    isSelected: Boolean,
    bgCol: Color,
    textCol: Color,
    borderColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgCol)
            .border(
                border = BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) borderColor else textCol.copy(alpha = 0.15f)
                ),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(borderColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = label,
                color = textCol,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
fun FontOptionItem(
    label: String,
    fontFamily: FontFamily,
    isSelected: Boolean,
    onClick: () -> Unit,
    panelBg: Color,
    textColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val containerBg = if (isSelected) accentColor.copy(alpha = 0.12f) else textColor.copy(alpha = 0.04f)
    val borderCol = if (isSelected) accentColor else textColor.copy(alpha = 0.15f)

    Box(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(containerBg)
            .border(
                border = BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = borderCol
                ),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontFamily = fontFamily,
            color = if (isSelected) accentColor else textColor.copy(alpha = 0.8f),
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
