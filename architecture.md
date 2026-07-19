# Architecture Technique & Spécifications — InkTone

Ce document définit l'architecture technique, la structure des données et la feuille de route pour le développement de **InkTone**, un lecteur d'ebooks Android (EPUB2/EPUB3) avec synthèse vocale neuronale locale en français.

---

## 1. Vue d'ensemble de l'Architecture

**Stack :** Kotlin natif + Jetpack Compose + Clean Architecture (4 couches) + MVI

Pour garantir une UI fluide à 60 FPS sans saccades pendant la génération audio, InkTone repose sur une **Clean Architecture à 4 couches** avec isolation via coroutines Kotlin et threads NDK pour l'inférence ONNX.

### Schéma Global — Clean Architecture (4 couches)

```
┌─────────────────────────────────────────────────────────────────┐
│  🎨 UI LAYER — Jetpack Compose + Material 3                     │
│  ReaderScreen · LibraryScreen · MediaControls · Settings        │
│  Surlignage dynamique via AnnotatedString + SpanStyle           │
└───────────────────────────┬─────────────────────────────────────┘
                            ▼ Intent / ▲ StateFlow<ScreenState>
┌─────────────────────────────────────────────────────────────────┐
│  🧠 VIEWMODEL LAYER — MVI (Model-View-Intent)                   │
│  ReaderViewModel · LibraryViewModel · PlaybackViewModel         │
│  Transforme les Intents en appels UseCase, expose des States    │
└───────────────────────────┬─────────────────────────────────────┘
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│  💼 DOMAIN LAYER — Use Cases (Kotlin pur, sans Android)         │
│  ParseEpubUseCase · ChunkTextUseCase · SynthesizeUseCase        │
│  PlaybackOrchestrator · SyncHighlightUseCase · SearchBookUC     │
└───────────────────────────┬─────────────────────────────────────┘
                            ▼ (interfaces)
┌─────────────────────────────────────────────────────────────────┐
│  💾 DATA LAYER — Repositories + Sources                         │
│  BookRepository · TtsRepository · ProgressRepository            │
│  Room DB (SQLite) · FileSystem Source · ONNX Inference Source   │
└───────────────────────────┬─────────────────────────────────────┘
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│  🔧 NATIVE SERVICES — Android-specific                          │
│  OnnxInferenceService (JNI/NDK) · AudioPlaybackService          │
│  MediaSessionService · EpubParserEngine (Readium Kotlin)        │
│  ForegroundService (background audio obligatoire Android)       │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. Spécifications Détaillées des Couches

### A. UI Layer — Jetpack Compose + Material 3
* **Technologie :** Kotlin + Jetpack Compose (Material 3), navigation type-safe via Compose Navigation.
* **Rôle :** Rendu déclaratif. L'UI observe des `StateFlow<ScreenState>` émis par les ViewModels.
* **Surlignage dynamique :** Utilisation de `AnnotatedString` + `SpanStyle` pour colorer dynamiquement le mot/la phrase en cours de lecture. Recomposition fine sans `invalidate()` global.
* **Écrans principaux :**
    * `LibraryScreen` — Grid/List des livres importés, cover, titre, progression (%)
    * `ReaderScreen` — Texte paginé ou scrollable, contrôles de lecture superposés
    * `SettingsScreen` — Voix, vitesse, typographie (police, taille, interlignage, thème)

### B. ViewModel Layer — MVI
* **Pattern :** Model-View-Intent. Chaque action utilisateur est une `Intent` scellée, traitée par le ViewModel, qui émet un nouvel `State` immuable.
* **Exemple :**
```kotlin
// Intent
sealed interface ReaderIntent {
    data class Play(val chapterIndex: Int) : ReaderIntent
    data class SeekTo(val positionMs: Long) : ReaderIntent
    data object Pause : ReaderIntent
    data object NextSentence : ReaderIntent
    data object PreviousSentence : ReaderIntent
}

// State
data class ReaderState(
    val book: Book,
    val chapter: Chapter,
    val sentences: List<Sentence>,
    val currentSentenceIndex: Int,
    val currentWordIndex: Int,
    val isPlaying: Boolean,
    val playbackProgress: Float  // 0.0 à 1.0
)
```

### C. Domain Layer — Use Cases (Kotlin pur)
* **`ParseEpubUseCase` :** Dézippe l'archive `.epub`, extrait le manifeste (`content.opf`), trie le `spine` (ordre de lecture), convertit le HTML/XHTML en texte brut structuré en conservant les métadonnées de position (paragraphe, offset caractère). **Utilise Readium Kotlin Toolkit.**
* **`ChunkTextUseCase` :** Découpe le texte en phrases via un segmenteur NLP (OpenNLP ou règles custom pour le français). Gère les cas complexes : dialogues (`« »`), abréviations (`M.`, `Dr.`, `etc.`), ellipses (`...`), points de suspension. Une phrase > 500 caractères est sous-découpée aux virgules ou conjonctions.
* **`SynthesizeUseCase` :** Ordonnance l'inférence ONNX pour une phrase donnée. Retourne un `Flow<SynthesisResult>` contenant le fichier WAV + les timestamps mot/milliseconde.
* **`PlaybackOrchestrator` :** Le chef d'orchestre. Maintient un buffer de **3 phrases d'avance** (Channel-based). Pipeline consommateur/producteur avec `consumeAndPlay` qui **attend sur pause** (ne break pas) pour une reprise instantanée. Protégé par `ReentrantLock` contre les race conditions UI/AudioFocus/notification. Gestion erreurs réseau avec classification par type d'exception (`isNetworkError`).
* **`SyncHighlightUseCase` :** Corrèle les timestamps audio avec la position dans le texte pour le surlignage.
* **`SearchBookUseCase` :** Recherche full-text dans un livre (FTS5 via Room).

### D. Data Layer — Repositories
* **`BookRepository` :** Interface dans le domain, implémentation dans `data/`. Gère l'import (copie du fichier `.epub` vers le sandbox), le parsing, la persistence dans Room.
* **`TtsRepository` :** Interface de synthèse vocale. L'implémentation (`TtsRepositoryImpl`) agit comme un **routeur** entre les providers TTS disponibles (pattern Strategy multi-moteurs).
* **`TtsProvider` :** Interface de provider TTS (pattern Strategy). Chaque moteur est un provider indépendant :
    * **`PiperTtsProvider`** — moteur ONNX local Sherpa-ONNX / Piper VITS
    * **`EdgeTtsProvider`** — moteur cloud Microsoft Edge TTS (gratuit, via WebSocket)
* **`ProgressRepository` :** Sauvegarde/Restauration de la progression de lecture (chapitre, phrase, offset) dans Room.
* **`ModelRepository` :** Gère le téléchargement, la vérification (SHA256) et le stockage du modèle ONNX français.

### E. Native Services — Android
* **`OnnxInferenceService` :** Moteur d'inférence ONNX Runtime Mobile exécuté via JNI/NDK. Utilise **Sherpa-ONNX** (modèle VITS français UPMC, ~73 Mo, 2 locuteurs : Jessica ♀ + Pierre ♂) avec RTF ~0.33.
* **`EdgeTtsClient` :** Client WebSocket pour Microsoft Edge TTS (cloud, gratuit). Pipeline complet : authentification DRM (`Sec-MS-GEC`), SSML, réception MP3 binaire, décodage PCM. Retry exponentiel 3 tentatives + fallback automatique vers Piper en cas de perte réseau.
* **`Mp3Decoder` :** Décodeur MP3→PCM via `MediaCodec` Android. Conversion `ShortArray`→`FloatArray` normalisée pour compatibilité avec le pipeline `GaplessAudioPlayer`.
* **`AudioPlaybackService` :** `MediaSessionService` (Media3). Lecture audio gapless via **`AudioTrack`** (PCM 16-bit) avec `ReentrantLock` anti-use-after-free. Gestion pause/reprise instantanée (<50ms) avec pipeline de synthèse maintenue en vie.
* **`AudioFocusManager` :** Respecte `AudioManager` — ducking lors des notifications, pause lors d'un appel, reprise après. Intégré au `MediaSession.Callback`.

---

## 3. Architecture du Stockage Interne

Fonctionnement 100% offline. L'application utilise le sandbox Android (`app-specific storage`).

```
📁 files/                          # Context.getFilesDir()
│
├── 📁 models/                     # Modèles IA
│   ├── 📄 manifest.json           # { "fr": { "name": "vits-fr", "sha256": "...", "url": "..." } }
│   └── 📄 vits_fr_sherpa.onnx     # Modèle Sherpa-ONNX VITS français (~60 Mo)
│
├── 📁 epubs/                      # Ebooks importés par l'utilisateur
│   ├── 📄 <uuid_1>.epub
│   └── 📄 <uuid_2>.epub
│
├── 📁 covers/                     # Couvertures extraites des EPUB
│   ├── 📄 <uuid_1>.jpg
│   └── 📄 <uuid_2>.jpg
│
├── 📁 cache_audio/               # Fichiers WAV temporaires (nettoyés au changement de chapitre)
│   ├── 📄 <bookId>_ch3_s12.wav
│   ├── 📄 <bookId>_ch3_s12.json  # Timestamps de la phrase 12
│   └── 📄 <bookId>_ch3_s13.wav
│
└── 📁 databases/                  # Room (SQLite)
    └── 📄 inktone.db
```

### Modèle de Données Room (Complet)

```kotlin
@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,          // UUID
    val title: String,
    val author: String,
    val description: String?,
    val filePath: String,                // Chemin relatif dans epubs/
    val coverPath: String?,              // Chemin relatif dans covers/
    val totalChapters: Int,
    val language: String,                // "fr"
    val addedAt: Long                    // timestamp ms
)

@Entity(
    tableName = "progress",
    foreignKeys = [ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("bookId")]
)
data class ProgressEntity(
    @PrimaryKey val bookId: String,      // FK → books.id
    val currentChapterIndex: Int,
    val currentSentenceIndex: Int,
    val currentWordOffset: Int,
    val totalProgressFraction: Float,    // 0.0 à 1.0
    val updatedAt: Long
)

@Entity(
    tableName = "bookmarks",
    foreignKeys = [ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE)]
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val bookId: String,
    val chapterIndex: Int,
    val sentenceIndex: Int,
    val label: String?,
    val createdAt: Long
)

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,         // Singleton (une seule ligne)
    val voiceModelPath: String,
    val speechRate: Float,               // 0.5 à 2.0
    val fontSize: Int,                   // sp
    val lineSpacing: Float,              // 1.0 à 2.5
    val fontFamily: String,              // "System", "OpenDyslexic", "Serif"
    val theme: String                    // "light", "dark", "sepia"
)
```

### Indexation Full-Text Search (FTS5)

```sql
-- FTS pour la recherche dans les métadonnées de livres
CREATE VIRTUAL TABLE books_fts USING fts5(
    title, author, description,
    content='books',
    content_rowid='rowid'
);

-- FTS pour la recherche DANS le contenu des livres
CREATE VIRTUAL TABLE chapter_content_fts USING fts5(
    book_id UNINDEXED,
    chapter_index,
    sentence_index,
    content
);
```

> **Note :** `chapter_content_fts` est peuplée après le parsing EPUB. Prévoir ~1-2 Mo par livre. Les recherches sont limitées au livre courant (filtrage par `book_id`).

---

## 4. Pipeline de Lecture — Flux Asynchrone Complet

```
[Utilisateur appuie sur PLAY]
             │
             ▼
[PlaybackOrchestrator] ── Vérifie cache_audio/ pour phrase N, N+1, N+2, N+3
             │
             ├── Présent → Envoie WAV + JSON timestamps → ExoPlayer
             │
             └── Absent → Demande SYNTHÈSE PRIORITAIRE → OnnxInferenceService
                              │
                              ▼
                        [ONNX Runtime: Piper fr_FR-siwis]
                              │
                              ▼
                        [SynthesisResult: WAV + Timestamps]
                              │
                              ▼
                        [Stocke dans cache_audio/ + notifie Orchestrator]
                              │
                              ▼
┌───────────────────────────────────────────────────────────────┐
│  🔄 BOUCLE DE LECTURE PRINCIPALE                             │
│                                                               │
│  ExoPlayer joue phrase N                                      │
│      │                                                        │
│      ├─► Émet callback de position toutes les ~50ms           │
│      │                                                        │
│      ├─► SyncHighlightUseCase compare position ms ↔ JSON      │
│      │   → Trouve le mot courant → Émet (wordIndex)           │
│      │                                                        │
│      └─► UI recoit StateFlow update → AnnotatedString         │
│          avec SpanStyle(surlignage) sur le mot courant        │
│                                                               │
│  Pendant ce temps (coroutines parallèles) :                   │
│      ├─► Coroutine A : Précharge N+1, N+2, N+3               │
│      └─► Coroutine B : Nettoie les phrases déjà lues (N-5+)  │
└───────────────────────────────────────────────────────────────┘
```

### Gestion des cas complexes

| Scénario | Comportement |
|---|---|
| **Phrase non prête à temps** | Silence 50ms → Émission d'un `PlaybackEvent.BufferUnderrun` → Skip à la phrase suivante |
| **Seek utilisateur** | Purge immédiate du cache audio → Régénération prioritaire de la nouvelle position → Reprise |
| **Changement de chapitre** | Purge du cache → Régénération complète du nouveau chapitre |
| **Appel téléphonique** | Audio Focus LOSS → Pause automatique → Sauvegarde progression |
| **Phrase > 500 caractères** | Découpage automatique aux conjonctions/virgules → Syntheses séquentielles |

---

## 5. Stack Technique — Récapitulatif

| Composant | Choix | Version |
|---|---|---|
| **Langage** | Kotlin | 2.x |
| **UI** | Jetpack Compose + Material 3 | BOM 2024+ |
| **Architecture** | Clean Architecture + MVI | — |
| **Navigation** | Compose Navigation (type-safe) | 2.8+ |
| **State Management** | `StateFlow` + `MutableStateFlow` | Kotlin Coroutines 1.8+ |
| **DI** | Hilt | 2.51+ |
| **Base de données** | Room + FTS5 | 2.6+ |
| **Parser EPUB** | Readium Kotlin Toolkit | 3.0+ |
| **Audio Player** | AudioTrack (PCM gapless) | 0 gap entre phrases, latence frame-level |
| **TTS Inference** | ONNX Runtime Android + **Sherpa-ONNX** (Piper VITS) | Sherpa fournit les timestamps phrase **nativement** |
| **Phonémisation** | eSpeak-NG intégré dans Sherpa-ONNX | Texte brut → phonèmes avant inférence ONNX |
| **Segmenteur phrases** | Règles custom Kotlin (français) | — |
| **Background** | `MediaSessionService` (Media3) + `AudioTrack` | Notification, lockscreen, Bluetooth, gapless |
| **Modèle vocal** | Sherpa-ONNX modèle Piper VITS UPMC | 73 Mo, 2 locuteurs (Jessica ♀ + Pierre ♂), RTF ~0.33 |

---

## 6. Feuille de Route — InkTone (Android / Français)

### Phase 1 : Fondations & Validation Sherpa-ONNX (Semaines 1-4)
* Mise en place du projet Android avec Hilt, Room, Compose Navigation.
* **PROTOTYPE CRITIQUE :** Test Sherpa-ONNX vs Piper sur 3 devices Android (Snapdragon, MediaTek, Tensor) — validation des timestamps natifs.
* Compilation de `piper-phonemize` pour Android NDK (ou validation du tokenizer intégré Sherpa).
* Intégration de ONNX Runtime Android + modèle VITS français (~60 Mo).
* Test de la vitesse d'inférence (RTF) sur CPU ARM.
* **Livrable :** Un binaire de test qui synthétise une phrase FR et retourne audio + timestamps.

### Phase 2 : Parsing EPUB & Pipeline Audio (Semaines 5-10)
* Intégration de **Readium Kotlin Toolkit** pour le parsing EPUB2/EPUB3.
* Développement du `ChunkTextUseCase` (segmenteur de phrases français avec règles NLP).
* Développement du `PhonemizationPipeline` (texte brut → phonèmes → tenseur ONNX).
* Développement du `PlaybackOrchestrator` (buffer +3 phrases, 100% async).
* Implémentation du bridge JNI ONNX (`OnnxInferenceService`).
* Implémentation de `AudioTrack` gapless avec buffer circulaire.
* `MediaSessionService` (Media3) : notification lockscreen, contrôles Bluetooth.
* `AudioFocusManager` : ducking notifs, pause appel, reprise.
* **Livrable :** Lecture TTS d'un chapitre EPUB complet, phrase par phrase, avec surlignage basique.

### Phase 3 : UI & Intégration (Semaines 11-16)
* `LibraryScreen` : Import EPUB via SAF, affichage grid/list, couvertures, progression.
* Table des matières navigable (NCX/NAV EPUB).
* `ReaderScreen` : Rendu texte paginé, contrôles overlay, typographie (police, taille, interligne, thèmes clair/sépia/sombre, OpenDyslexic).
* Surlignage dynamique synchronisé (`AnnotatedString` + `SpanStyle`) avec isolation de recomposition.
* `SettingsScreen` : Vitesse de lecture, choix de voix, typographie.
* `BookmarkScreen` : Gestion des signets.
* **Livrable :** Application utilisable avec UI complète, lecture fluide.

### Phase 4 : Optimisation, Robustesse & Polissage (Semaines 17-20)
* **Profilage CPU/Batterie :** Ajustement du buffer et de la fréquence d'inférence.
* Recherche full-text dans le contenu des livres (FTS5 `chapter_content_fts`).
* Process Death : `SavedStateHandle`, sauvegarde progression dans `onStop()`, restauration automatique.
* Gestion des cas extrêmes : EPUB corrompus, images, tableaux, notes de bas de page.
* Gestion des erreurs ONNX & dégradation gracieuse (skip phrase, pause après 3 échecs).
* Import depuis SAF avec persistence des permissions URI.
* ProGuard/R8 rules pour ONNX Runtime (sinon crash en release).
* Sauvegarde/Restauration des données utilisateur (bookmarks, progrès, settings).
* Accessibilité : TalkBack, tailles de police min/max.
* Vérification des licences (Sherpa-ONNX, Readium, ONNX Runtime) pour conformité Play Store.
* Beta fermée auprès de lecteurs francophones.
* **Livrable :** APK release candidate, prêt pour Play Store (internal testing).

---

## 7. Structure du Projet Android

```
InkTone/
├── 📁 app/
│   ├── 📄 build.gradle.kts
│   └── 📁 src/
│       ├── 📁 main/
│       │   ├── 📄 AndroidManifest.xml
│       │   ├── 📁 java/com/inktone/
│       │   │   ├── 📄 InkToneApplication.kt          # @HiltAndroidApp
│       │   │   │
│       │   │   ├── 📁 ui/                               # 🎨 UI Layer
│       │   │   │   ├── 📁 theme/
│       │   │   │   │   ├── 📄 Color.kt
│       │   │   │   │   ├── 📄 Theme.kt
│       │   │   │   │   └── 📄 Type.kt
│       │   │   │   ├── 📁 navigation/
│       │   │   │   │   └── 📄 InkToneNavGraph.kt       # Routes type-safe
│       │   │   │   ├── 📁 library/
│       │   │   │   │   ├── 📄 LibraryScreen.kt
│       │   │   │   │   └── 📄 LibraryViewModel.kt
│       │   │   │   ├── 📁 reader/
│       │   │   │   │   ├── 📄 ReaderScreen.kt
│       │   │   │   │   ├── 📄 ReaderViewModel.kt
│       │   │   │   │   └── 📁 components/
│       │   │   │   │       ├── 📄 HighlightedText.kt    # AnnotatedString + SpanStyle
│       │   │   │   │       ├── 📄 MediaControlBar.kt
│       │   │   │   │       └── 📄 ChapterProgressBar.kt
│       │   │   │   ├── 📁 settings/
│       │   │   │   │   ├── 📄 SettingsScreen.kt
│       │   │   │   │   └── 📄 SettingsViewModel.kt
│       │   │   │   └── 📁 bookmarks/
│       │   │   │       ├── 📄 BookmarksScreen.kt
│       │   │   │       └── 📄 BookmarksViewModel.kt
│       │   │   │
│       │   │   ├── 📁 domain/                           # 💼 Domain Layer (Kotlin pur)
│       │   │   │   ├── 📁 model/
│       │   │   │   │   ├── 📄 Book.kt
│       │   │   │   │   ├── 📄 Chapter.kt
│       │   │   │   │   ├── 📄 Sentence.kt
│       │   │   │   │   ├── 📄 SynthesisResult.kt        # WAV path + List<WordTimestamp>
│       │   │   │   │   ├── 📄 WordTimestamp.kt          # word, startMs, endMs
│       │   │   │   │   └── 📄 ReaderSettings.kt
│       │   │   │   ├── 📁 usecase/
│       │   │   │   │   ├── 📄 ParseEpubUseCase.kt
│       │   │   │   │   ├── 📄 ChunkTextUseCase.kt
│       │   │   │   │   ├── 📄 SynthesizeUseCase.kt
│       │   │   │   │   ├── 📄 PlaybackOrchestrator.kt   # Buffer +3, async flow
│       │   │   │   │   ├── 📄 SyncHighlightUseCase.kt
│       │   │   │   │   └── 📄 SearchBookUseCase.kt
│       │   │   │   └── 📁 repository/
│       │   │   │       ├── 📄 BookRepository.kt         # Interface
│       │   │   │       ├── 📄 TtsRepository.kt          # Interface
│       │   │   │       ├── 📄 ProgressRepository.kt     # Interface
│       │   │   │       └── 📄 ModelRepository.kt        # Interface
│       │   │   │
│       │   │   ├── 📁 data/                             # 💾 Data Layer
│       │   │   │   ├── 📁 local/
│       │   │   │   │   ├── 📁 dao/
│       │   │   │   │   │   ├── 📄 BookDao.kt
│       │   │   │   │   ├── 📄 ProgressDao.kt
│       │   │   │   │   ├── 📄 BookmarkDao.kt
│       │   │   │   │   ├── 📄 SettingsDao.kt
│       │   │   │   │   └── 📄 ChapterContentFtsDao.kt   # FTS5 in-book search
│       │   │   │   ├── 📁 entity/
│       │   │   │   │   ├── 📄 BookEntity.kt
│       │   │   │   │   ├── 📄 ProgressEntity.kt
│       │   │   │   │   ├── 📄 BookmarkEntity.kt
│       │   │   │   │   ├── 📄 SettingsEntity.kt
│       │   │   │   │   └── 📄 ChapterContentFtsEntity.kt # FTS5 content
│       │   │   │   │   └── 📄 InkToneDatabase.kt       # Room DB
│       │   │   │   ├── 📁 repository/
│       │   │   │   │   ├── 📄 BookRepositoryImpl.kt
│       │   │   │   │   ├── 📄 TtsRepositoryImpl.kt
│       │   │   │   │   ├── 📄 ProgressRepositoryImpl.kt
│       │   │   │   │   └── 📄 ModelRepositoryImpl.kt
│       │   │   │   └── 📁 mapper/
│       │   │   │       ├── 📄 BookMapper.kt              # Entity ↔ Domain
│       │   │   │       └── 📄 ProgressMapper.kt
│       │   │   │
│       │   │   ├── 📁 service/                           # 🔧 Native Services
│       │   │   │   ├── 📁 onnx/
│       │   │   │   │   ├── 📄 OnnxInferenceService.kt    # JNI bridge Sherpa-ONNX
│       │   │   │   │   └── 📄 SherpaTtsEngine.kt         # Wrapper Sherpa-ONNX
│       │   │   │   ├── 📁 phonemizer/
│       │   │   │   │   └── 📄 FrenchPhonemizer.kt        # Texte → phonèmes (piper-phonemize NDK)
│       │   │   │   ├── 📁 audio/
│       │   │   │   │   ├── 📄 AudioPlaybackService.kt    # MediaSessionService (Media3)
│       │   │   │   │   ├── 📄 GaplessAudioPlayer.kt      # AudioTrack buffer circulaire
│       │   │   │   │   ├── 📄 MediaSessionConnector.kt   # Lie MediaSession ↔ AudioTrack
│       │   │   │   │   └── 📄 AudioFocusManager.kt       # Interruptions (appels, notifs)
│       │   │   │   ├── 📁 parser/
│       │   │   │   │   └── 📄 EpubParserService.kt       # Readium wrapper
│       │   │   │   ├── 📁 chunker/
│       │   │   │   │   └── 📄 FrenchSentenceSplitter.kt  # Segmenteur FR
│       │   │   │   └── 📁 cache/
│       │   │   │       └── 📄 AudioCacheManager.kt       # LRU, purge, eviction
│       │   │   │
│       │   │   └── 📁 di/                                # Hilt Modules
│       │   │       ├── 📄 AppModule.kt
│       │   │       ├── 📄 DatabaseModule.kt
│       │   │       ├── 📄 RepositoryModule.kt
│       │   │       └── 📄 ServiceModule.kt
│       │   │
│       │   └── 📁 res/
│       │       ├── 📁 values/
│       │       │   ├── 📄 strings.xml                    # Français uniquement
│       │       │   └── 📄 themes.xml
│       │       └── 📁 drawable/
│       │
│       └── 📁 test/                                      # Tests unitaires
│           └── 📁 java/com/inktone/
│               ├── 📁 domain/usecase/
│               │   ├── 📄 ChunkTextUseCaseTest.kt
│               │   └── 📄 PlaybackOrchestratorTest.kt
│               └── 📁 data/repository/
│                   └── 📄 TtsRepositoryImplTest.kt
│
├── 📁 gradle/
│   └── 📄 libs.versions.toml                             # Version Catalog
├── 📄 build.gradle.kts                                   # Root
└── 📄 settings.gradle.kts
```

---

## 8. Points Clés de Décision

| Décision | Choix retenu | Justification |
|---|---|---|
| **Framework UI** | Kotlin + Compose natif | Pas d'iOS prévu, accès direct API Android |
| **Architecture** | Clean Architecture 4 couches + MVI | Testabilité, séparation des responsabilités |
| **Parser EPUB** | Readium Kotlin Toolkit | Seul parser robuste pour EPUB2 + EPUB3 |
| **Moteur TTS** | **Sherpa-ONNX** (VITS) via ONNX Runtime | **Timestamps mot/milliseconde natifs** — pas de post-traitement |
| **Phonémisation FR** | `piper-phonemize` NDK ou tokenizer Sherpa intégré | Obligatoire pour texte → phonèmes |
| **Audio Playback** | **AudioTrack** PCM gapless (pas ExoPlayer) | 0 gap entre phrases, latence frame-level |
| **Background** | `MediaSessionService` (Media3) + `AudioTrack` | Obligatoire Android, notif, lockscreen, BT |
| **Pipeline** | 100% asynchrone (Coroutines Flow) | Pas de blocage UI, pas de silence gênant |
| **Modèle vocal** | Modèle VITS français (Sherpa-ONNX) | ~50-80 Mo, timestamps natifs, qualité naturelle |
| **Langue app** | Français uniquement | Scope clarifié |
| **État / Process Death** | `SavedStateHandle` + sauvegarde `onStop()` | Survie aux kills système Android |

---

## 9. Points de Vigilance pour l'Implémentation

### 9.1 Recomposition Compose & Surlignage (Performance UI)

**Problème :** Modifier un `SpanStyle` à chaque mot prononcé (toutes les ~200-400ms) peut déclencher une recomposition de tout l'écran `ReaderScreen`.

**Solution :**
- Isoler le surlignage dans un composable `HighlightedText` dédié qui observe **uniquement** le `StateFlow<Int>` de l'index du mot courant.
- Le reste de l'écran (`Scaffold`, `TopAppBar`, `MediaControlBar`) observe un `StateFlow<ReaderUiState>` distinct qui change rarement.
- Utiliser `derivedStateOf` pour éviter les recompositions inutiles.
- Synchroniser les mises à jour avec `withFrameMillis` pour caler le surlignage sur le vsync plutôt que sur les callbacks audio asynchrones.

```kotlin
// ✅ Architecture de state granulaire
@Composable
fun ReaderScreen(viewModel: ReaderViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()     // Rare
    val wordIndex by viewModel.currentWordIndex.collectAsStateWithLifecycle() // Fréquent

    Scaffold(
        topBar = { ReaderTopBar(uiState.chapterTitle) },
        bottomBar = { MediaControlBar(uiState.isPlaying, uiState.progress) }
    ) { padding ->
        // Seul HighlightedText recompose à chaque mot
        HighlightedText(
            sentences = uiState.sentences,
            currentWordIndex = wordIndex,
            modifier = Modifier.padding(padding)
        )
    }
}
```

### 9.2 Mapping Readium ↔ Texte Brut Structuré (Navigation & Tap)

**Problème :** Readium expose des documents XHTML. Quand l'utilisateur tape sur un mot dans la page, il faut instantanément retrouver l'index de la phrase correspondante pour purger le cache et relancer l'inférence ONNX.

**Solution :** Construire un mapping bidirectionnel robuste dans `ParseEpubUseCase` :

```kotlin
data class TextPosition(
    val chapterIndex: Int,
    val paragraphIndex: Int,
    val sentenceIndex: Int,
    val characterOffset: Int,      // Offset absolu dans le chapitre
    val characterRange: IntRange   // Plage de la phrase dans le texte brut
)

data class ChapterLayout(
    val sentences: List<Sentence>,
    val positionMap: Map<IntRange, Int>,  // characterRange → sentenceIndex (O(log n))
    val lineMetrics: List<LineMetric>     // Pour le mapping pixel → offset
)
```

- **Tap → Position :** Utiliser `TextLayoutResult` (callback `onTextLayout` de Compose) pour récupérer les bounding boxes de chaque ligne, puis `getOffsetForPosition(x, y)` pour mapper un tap vers un offset caractère.
- **Offset → Phrase :** Recherche dichotomique dans `positionMap` pour trouver la phrase contenant l'offset.
- Chaque `Sentence` doit stocker son `characterRange` pour permettre la navigation inverse (phrase → position dans le texte).

### 9.3 Cycle de Vie du ForegroundService (Android 14+)

**Problème :** Android 14+ (et les versions 2026) est extrêmement strict sur les `ForegroundService`. Sans déclaration explicite du type, le service est tué instantanément.

**Solution :**

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<service
    android:name=".service.audio.AudioPlaybackService"
    android:foregroundServiceType="mediaPlayback"
    android:exported="false" />
```

```kotlin
// AudioPlaybackService.kt — startForeground adapté à l'API level
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val notification = mediaSessionManager.buildNotification()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        startForeground(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
    } else {
        startForeground(NOTIFICATION_ID, notification)
    }

    return START_STICKY // Redémarrage automatique si tué par le système
}
```

- `START_STICKY` garantit que le service redémarre si Android le tue (low memory).
- La notification doit être **impossible à dismiss** (`.setOngoing(true)`).
- `MediaSessionService` doit être correctement connecté pour les contrôles lockscreen et Bluetooth.

### 9.4 Gestion de la Mémoire du Cache Audio

**Problème :** Les WAV PCM non compressés consomment ~1.4 Mo par minute de parole. Un chapitre de 30 minutes génère ~40 Mo de cache.

**Solution :**
- Utiliser `context.cacheDir` (et non `filesDir`) pour le cache audio — le système Android peut nettoyer automatiquement si l'espace est faible.
- Politique d'éviction LRU : max **30 Mo** ou **10 minutes d'audio** (le plus petit des deux).
- Nettoyage immédiat et complet au changement de chapitre.
- Format de sortie : WAV 16-bit 22.05 kHz mono (suffisant pour la voix, divise la taille par 2 par rapport au 44.1 kHz).

```kotlin
object AudioCacheManager {
    private const val MAX_CACHE_SIZE_BYTES = 30 * 1024 * 1024  // 30 Mo
    private const val MAX_CACHE_DURATION_MS = 10 * 60 * 1000   // 10 min

    suspend fun evictIfNeeded(cacheDir: File) = withContext(Dispatchers.IO) {
        val wavFiles = cacheDir.listFiles()?.filter { it.extension == "wav" }
            ?.sortedBy { it.lastModified() } ?: return@withContext

        var totalSize = wavFiles.sumOf { it.length() }
        var oldest = wavFiles.firstOrNull()

        while (oldest != null && totalSize > MAX_CACHE_SIZE_BYTES) {
            totalSize -= oldest.length()
            oldest.delete()
            oldest.delete()  // Supprime aussi le .json de timestamps associé
            oldest = wavFiles.getOrNull(wavFiles.indexOf(oldest) + 1)
        }
    }

    suspend fun purgeChapter(chapterId: String, cacheDir: File) = withContext(Dispatchers.IO) {
        cacheDir.listFiles()
            ?.filter { it.name.startsWith(chapterId) }
            ?.forEach { it.delete() }
    }
}
```

### 9.5 Gestion des Erreurs ONNX & Dégradation Gracieuse

**Problème :** L'inférence ONNX peut échouer (modèle corrompu, mémoire insuffisante, phrase trop longue).

**Solution :**
- `SynthesisResult` doit être un `sealed class` avec branches `Success` et `Error`.
- En cas d'erreur, le `PlaybackOrchestrator` skip la phrase et log l'erreur, sans interrompre la lecture.
- Après 3 échecs consécutifs → pause automatique + notification à l'utilisateur.

```kotlin
sealed class SynthesisResult {
    data class Success(
        val wavFilePath: String,
        val timestamps: List<WordTimestamp>,
        val durationMs: Long
    ) : SynthesisResult()

    data class Error(
        val sentence: Sentence,
        val exception: Exception,
        val retryable: Boolean
    ) : SynthesisResult()
}
```

### 9.6 Process Death & Restauration d'État

**Problème :** Android tue les processus applicatifs en arrière-plan de manière agressive. Si l'utilisateur lit, met l'app en arrière-plan et revient 2 minutes plus tard, le processus peut être mort et l'état de lecture perdu.

**Solution :**
- Utiliser `SavedStateHandle` dans tous les ViewModels pour les données critiques.
- Sauvegarder la progression dans Room à chaque `onStop()` du `AudioPlaybackService`.
- Restaurer automatiquement la position de lecture au retour de l'utilisateur.

```kotlin
@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val progressRepository: ProgressRepository,
    ...
) : ViewModel() {
    // Survit au process death — restauré automatiquement par Android
    val currentChapterIndex: MutableStateFlow<Int> =
        savedStateHandle.getStateFlow(scope = viewModelScope, key = "chapterIndex", initialValue = 0)

    val currentSentenceIndex: MutableStateFlow<Int> =
        savedStateHandle.getStateFlow(scope = viewModelScope, key = "sentenceIndex", initialValue = 0)

    // Sauvegarde additionnelle dans Room pour la reprise à froid
    fun onStop() {
        viewModelScope.launch {
            progressRepository.save(bookId, currentChapterIndex.value, currentSentenceIndex.value)
        }
    }
}
```

### 9.7 SAF (Storage Access Framework) & Persistence des Permissions URI

**Problème :** Sur Android 10+, l'import d'EPUB passe par SAF. Les URIs `content://` retournées ont une durée de vie limitée si la permission n'est pas persistée.

**Solution :**
- Prendre une permission persistante avec `contentResolver.takePersistableUriPermission()`.
- Copier immédiatement le fichier dans le sandbox applicatif (`epubs/`).
- Stocker l'URI persistante dans Room pour permettre la réimportation/réouverture.

```kotlin
// Dans BookRepositoryImpl.importEpub()
context.contentResolver.takePersistableUriPermission(
    uri,
    Intent.FLAG_GRANT_READ_URI_PERMISSION
)
val destFile = File(epubsDir, "${UUID.randomUUID()}.epub")
context.contentResolver.openInputStream(uri)?.use { input ->
    FileOutputStream(destFile).use { output ->
        input.copyTo(output)
    }
}
```

### 9.8 ProGuard/R8 — Règles pour ONNX Runtime

**Problème :** ONNX Runtime utilise JNI et de la réflexion. Sans règles ProGuard/R8, l'application crashe en release (classes JNI obfusquées).

**Solution :** Ajouter dans `proguard-rules.pro` :

```proguard
# ONNX Runtime
-keep class ai.onnxruntime.** { *; }
-keep class com.microsoft.onnxruntime.** { *; }

# Sherpa-ONNX
-keep class com.k2fsa.sherpa.onnx.** { *; }

# Modèles chargés depuis fichiers
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
```

---

## 10. Décision Critique & Résumé des Corrections

### 🔴 Décision à prendre AVANT de coder

> **Sherpa-ONNX (VITS) vs Piper TTS — Le choix du moteur TTS détermine tout le reste.**

| Critère | Piper TTS | Sherpa-ONNX |
|---|---|---|
| **Timestamps mot/milliseconde** | ❌ Pas natif — nécessite patch C++ | ✅ API native `timestamps` |
| **Qualité voix FR** | Bonne (siwis) | Bonne à très bonne (VITS) |
| **Taille modèle** | ~50 Mo | ~50-80 Mo |
| **Intégration Android** | ONNX Runtime + JNI manuel | Binding Android natif + ONNX |
| **Phonémisation** | `piper-phonemize` (C++) | Intégrée ou `piper-phonemize` |
| **Risque technique** | ÉLEVÉ — timestamps non garantis | FAIBLE — API stable et documentée |

**🔥 Recommandation :** Prototyper Sherpa-ONNX en priorité (3 jours). Si le modèle VITS français est satisfaisant, c'est le choix le plus sûr. Piper reste un plan B si Sherpa ne fonctionne pas, mais prévoir +3 semaines pour patcher les timestamps.

### ✅ Corrections apportées suite à l'audit

| Problème identifié | Correction |
|---|---|
| ExoPlayer → gaps entre phrases | Remplacé par **AudioTrack** gapless avec buffer circulaire |
| Piper → pas de timestamps natifs | Migré vers **Sherpa-ONNX** (VITS) avec API timestamps |
| MediaSessionService mal modélisé | Corrigé : extends `MediaSessionService` (Media3), pas `Service` |
| Phonémisation FR absente | Ajoutée : `PhonemizationPipeline` + `piper-phonemize` NDK |
| FTS5 sur mauvaise table | Ajouté : `chapter_content_fts` pour la recherche in-book |
| Process Death non traité | Ajouté : `SavedStateHandle` + sauvegarde `onStop()` |
| SAF permissions non persistées | Ajouté : `takePersistableUriPermission()` + copie sandbox |
| ProGuard/R8 manquant | Ajouté : règles ONNX Runtime + Sherpa-ONNX |
| Roadmap trop optimiste | Ajustée : 16 → **20 semaines** (réaliste pour 1 dev) |

### 📋 Checklist avant de commencer à coder

- [ ] Prototyper Sherpa-ONNX sur 3 devices Android (Snapdragon, MediaTek, Tensor)
- [ ] Valider les timestamps mot/milliseconde sur 10 phrases françaises
- [ ] Mesurer le RTF (Real-Time Factor) — doit être < 1.0 pour du temps réel
- [ ] Valider la phonémisation française (liaisons, « les amis » → [le.z‿a.mi])
- [ ] Tester Readium Kotlin sur 5 EPUBs variés (EPUB2 simple, EPUB3 complexe, avec images, corrompu)
- [ ] Configurer le Version Catalog (`libs.versions.toml`) avec toutes les dépendances
- [ ] Mettre en place la CI (GitHub Actions) pour les tests unitaires