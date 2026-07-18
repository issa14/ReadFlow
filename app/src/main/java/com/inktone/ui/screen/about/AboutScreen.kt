package com.inktone.ui.screen.about

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val CardBg = Color(0xFF1A1A1A)
private val AccentBlue = Color(0xFF4FC3F7)
private val AccentGreen = Color(0xFF81C784)

@Composable
fun AboutScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(20.dp))

        Icon(Icons.Default.MenuBook, null, tint = AccentBlue, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(12.dp))
        Text("InkTone", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 26.sp)
        Text("Version 0.1.0", color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp)

        Spacer(Modifier.height(20.dp))

        Card(colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Text(
                "InkTone est une solution de lecture intelligente conçue pour transformer " +
                "vos documents et livres numériques en livres audio haute fidélité, directement " +
                "sur votre appareil. Alliant accessibilité, confort visuel et technologies de " +
                "traitement de la voix de pointe, l'application offre une expérience d'écoute " +
                "fluide, immersive et entièrement adaptée à votre rythme.",
                color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, lineHeight = 20.sp,
                modifier = Modifier.padding(20.dp)
            )
        }

        Spacer(Modifier.height(12.dp))

        SectionHeader("🛡️ Confidentialité & Sécurité Absolue")
        PrivacyCard("Architecture 100% Locale", "La synthèse vocale s'exécute intégralement sur le processeur de votre appareil. Aucun texte n'est transféré vers des serveurs tiers.")
        PrivacyCard("Respect de la Vie Privée", "Vos habitudes de lecture et vos fichiers restent strictement confidentiels et ne sortent jamais de votre espace de stockage.")
        PrivacyCard("Autonomie Hors-Ligne", "L'application fonctionne sans aucune connexion internet, garantissant une utilisation ininterrompue, partout et à tout moment.")

        Spacer(Modifier.height(12.dp))

        SectionHeader("⚙️ Innovations Techniques")
        Card(colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Développé avec un objectif d'optimisation matérielle et de performance, InkTone s'appuie sur des moteurs open-source industriels de premier plan :", color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp, lineHeight = 19.sp)
                Spacer(Modifier.height(12.dp))
                TechItem("Moteur d'inférence", "Sherpa-ONNX")
                TechItem("Modèles acoustiques", "Piper VITS (haute définition)")
            }
        }

        Spacer(Modifier.height(12.dp))

        SectionHeader("👥 Crédits & Support")
        CreditCard("Développement", "Issa ADAMOU", Icons.Default.Person)
        CreditCard("Code Source", "github.com/issa14/InkTone", Icons.Default.Code)
        CreditCard("Contact", "issadotnet@gmail.com", Icons.Default.Mail)

        Spacer(Modifier.height(16.dp))
        Text("© 2026 InkTone. Tous droits réservés.", color = Color.White.copy(alpha = 0.25f), fontSize = 11.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
}

@Composable
private fun PrivacyCard(title: String, description: String) {
    Card(colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.Security, null, tint = AccentGreen.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                Spacer(Modifier.height(2.dp))
                Text(description, color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp, lineHeight = 17.sp)
            }
        }
    }
}

@Composable
private fun TechItem(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text("• ", color = AccentBlue, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text("$label : ", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
        Text(value, color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Medium, fontSize = 13.sp)
    }
}

@Composable
private fun CreditCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Card(colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = AccentBlue.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(label, color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
                Text(value, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
            }
        }
    }
}
