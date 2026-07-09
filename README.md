# 📖 ReadFlow

> Lecteur d'ebooks Android avec synthèse vocale neuronale locale — 100% offline, 100% français.

[![Kotlin](https://img.shields.io/badge/Kotlin-2.x-7F52FF?logo=kotlin)](https://kotlinlang.org/)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Architecture](https://img.shields.io/badge/Architecture-Clean%20%2B%20MVI-00C853)](./architecture.md)
[![License](https://img.shields.io/badge/License-MIT-blue)](./LICENSE)
[![Status](https://img.shields.io/badge/Status-Phase%203%E2%80%934%20%E2%80%94%20UI%20%2B%20Polish-brightgreen)](./PROJECT_STATUS.md)

---

## 🎯 Vision

**ReadFlow** est un lecteur d'ebooks Android inspiré de l'UX de **Moon+ Reader**, couplé à un moteur de **synthèse vocale neuronale** (Text-to-Speech) qui tourne **100% en local** sur l'appareil — aucune connexion internet requise.

Le surlignage dynamique **mot-à-mot** synchronisé avec la voix permet une expérience de lecture immersive unique.

### ✨ Fonctionnalités clés (cibles)

- 📚 **Lecture EPUB2 & EPUB3** — Import local via fichier, parsing robuste
- 🗣️ **TTS neuronal local** — Voix française (Piper VITS via Sherpa-ONNX, 100% offline)
- 🎯 **Surlignage mot-à-mot** — Synchronisation audio ↔ texte en temps réel
- 🎛️ **Contrôles de lecture avancés** — Vitesse, voix, navigation phrase par phrase
- 🌓 **Thèmes multiples** — Clair, sépia, sombre + police OpenDyslexic
- 🔖 **Signets & progression** — Sauvegarde automatique, reprise exacte
- 🔍 **Recherche full-text** — Dans le livre courant
- 🎧 **Background audio** — Notification lockscreen, contrôles Bluetooth
- 📱 **Android 14+** — Material 3, edge-to-edge, per-app language

---

## 🏗️ Architecture

```
UI (Compose)  →  ViewModel (MVI)  →  Domain (UseCases)  →  Data (Room + Files)
                                                              ↓
                                                    Native (ONNX + AudioTrack)
```

Voir le document complet : **[📄 ARCHITECTURE.md](./architecture.md)**

| Composant | Technologie |
|---|---|
| **Langage** | Kotlin 2.0.21 |
| **UI** | Jetpack Compose + Material 3 |
| **Architecture** | Clean Architecture 4 couches + MVI |
| **DI** | Hilt 2.51.1 |
| **Base de données** | Room 2.6.1 |
| **Parser EPUB** | Readium Kotlin Toolkit 3.0.0 |
| **Moteur TTS** | Sherpa-ONNX 1.13.4 / Piper VITS (fr_FR-miro-high) |
| **Audio** | AudioTrack PCM float 22050 Hz, gapless |
| **Background** | MediaSessionService (Media3 1.4.1) |

---

## 🚀 Quickstart

### Prérequis

- **Android Studio** Hedgehog (2024.1+) ou plus récent
- **JDK 17+**
- **Android SDK 34+** (Android 14)
- **Appareil Android 14+ ARM64** (Snapdragon 680 testé)

### Build & Run

```bash
# Cloner le repo
git clone https://github.com/issa14/ReadFlow.git
cd ReadFlow

# Ouvrir dans Android Studio
# → Sync Gradle
# → Run sur device Android 14+ (ARM64)

# Ou en ligne de commande
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Premier lancement

1. Le modèle vocal français (~61 Mo) est inclus dans l'APK — **aucun téléchargement requis**
2. Importer un fichier `.epub` via le bouton "+" ou le partage Android
3. Ouvrir le livre → appuyer sur ▶️ Play
4. La synthèse démarre — le texte défile en temps réel

---

## 📊 Statut du Projet

→ **[📊 PROJECT_STATUS.md](./PROJECT_STATUS.md)** — Suivi détaillé des tâches, bloqueurs, risques et métriques.

**Phase actuelle :** Phase 3 (UI) complétée — Phase 4 (Polish & Release) en cours  
**Progression :** ~65%  
**Modèle TTS :** Piper VITS `fr_FR-miro-high` (61 Mo, RTF ~0.24)

---

## 📂 Structure

```
ReadFlow/
├── 📄 README.md
├── 📄 ARCHITECTURE.md          # Spécifications techniques
├── 📄 PROJECT_STATUS.md        # Suivi d'avancement
├── 📄 CHANGELOG.md
├── 📄 CONTRIBUTING.md
├── 📄 .gitignore
├── 📁 docs/
│   └── 📄 prototype-report.md  # Rapport de prototype
├── 📁 app/                     # Code source Android
│   └── 📁 src/
│       ├── 📁 main/java/com/readflow/
│       │   ├── 📁 ui/          # Compose UI
│       │   ├── 📁 domain/      # UseCases + Models (Kotlin pur)
│       │   ├── 📁 data/        # Room, Repositories
│       │   └── 📁 service/     # ONNX, AudioTrack, Readium
│       └── 📁 test/            # Tests unitaires
└── 📁 gradle/
    └── 📄 libs.versions.toml   # Version Catalog
```

---

## 🤝 Contribuer

Voir **[CONTRIBUTING.md](./CONTRIBUTING.md)**.

Les contributions sont les bienvenues. Voir **[CONTRIBUTING.md](./CONTRIBUTING.md)**.

---

## 📜 Licence

Ce projet est sous licence **MIT**. Voir **[LICENSE](./LICENSE)**.

Les modèles ONNX utilisés (Sherpa-ONNX/VITS) sont sous leurs licences respectives (Apache 2.0 / MIT). Readium Kotlin Toolkit est sous licence BSD-3.

---

## 🙏 Remerciements

- [Sherpa-ONNX](https://github.com/k2-fsa/sherpa-onnx) — Inference TTS with timestamps
- [Readium Kotlin](https://github.com/readium/kotlin-toolkit) — EPUB parser
- [ONNX Runtime](https://onnxruntime.ai/) — Cross-platform ML inference
- [Moon+ Reader](https://www.moondownload.com/) — UX inspiration
