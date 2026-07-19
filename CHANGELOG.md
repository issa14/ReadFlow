# 📝 Changelog — InkTone

Toutes les modifications notables de ce projet sont documentées dans ce fichier.

Le format est basé sur [Keep a Changelog](https://keepachangelog.com/fr/1.0.0/),
et ce projet adhère au [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### 2026-07-19 — Migration UPMC & Suppression Miro

#### Changed — 🟢 Améliorations

- **Modèle UPMC officiel** (`fr_FR-upmc-medium`, 73MB) remplace Miro (`fr_FR-miro-high`, 61MB)
  - Source : [sherpa-onnx model zoo](https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models) (2023, PyTorch 1.13, IR v9)
  - Compatible ONNX Runtime 1.27.0 — pas de SIGSEGV
  - 2 locuteurs : **Jessica ♀** (sid=0, défaut) + **Pierre ♂** (sid=1)
  - RTF ~0.33 sur Snapdragon 680

- **Suppression totale de Miro**
  - Modèle 61MB supprimé des assets
  - `Voice.MIRO` retiré de l'enum
  - Crash guard (`upmc_init_failed`) supprimé — plus de fallback
  - `OnnxInferenceService` : -91 lignes nettes

- **Fix voix Pierre**
  - `JESSICA` placé avant `MIRO` dans l'enum → `resolveVoiceId(0)` trouve `"jessica"` et non `"miro"`
  - Log `sid=$safeSid` dans `synthesize()` pour diagnostic

- **Sync Settings → Reader**
  - La voix sélectionnée dans les Settings est propagée au `ReaderViewModel.loadBook()`

### 2026-07-18 — Corrections critiques pré-release (session de refactoring)

#### Fixed — 🔴 Critiques

- **Race condition use-after-free dans `GaplessAudioPlayer.stop()`**
  - Ajout d'un flag atomique `willStop` vérifié sous `writeLock` avant chaque `AudioTrack.write()`
  - Déplacement de la vérification d'état `_state == Playing` à l'intérieur du verrou
  - Réinitialisation de `willStop` dans `play()`
  - Tests : `GaplessAudioPlayerTest` avec 2 stress tests (100+ cycles stop/play rapides, arrêt concurrent pendant écriture)

- **Allocation `ShortArray(n)` par phrase dans `GaplessAudioPlayer.writeBlocking()`**
  - Remplacement par un `chunkBuffer` `ShortArray(4096)` alloué une seule fois par instance
  - Conversion Float→Short par chunks de 4096 échantillons, zéro allocation par phrase
  - Compteur `allocationsSaved` pour le benchmarking
  - Résultat : ~720 allocations/heure → 0, GC pressure éliminé

- **Deadlock + ANR dans `PlaybackOrchestrator.launchSynthesisPipeline()`**
  - `withTimeoutOrNull(2000)` autour de chaque `ttsRepository.synthesize()`
  - Nouvelle `SynthesisTimeoutException` levée en cas de dépassement
  - Compteur `consecutiveErrors` : après 3 erreurs consécutives → pause automatique + notification UI
  - Silence court (50ms) injecté après chaque timeout pour maintenir le flux audio
  - Tests : `SynthesizeUseCaseTest` (8 tests), `PlaybackOrchestratorTest` (+5 tests timeout)

#### Fixed — 🟠 Priorités hautes

- **`AudioCacheManager.sizeOf()` sous-estimait la mémoire réelle**
  - Ajout des overheads manquants : `voiceLabel`, `engineId`, primitives (`Int`+`Long`×2), `LinkedHashMap.Node`
  - Facteur d'alignement heap de +20% pour refléter la fragmentation réelle
  - Réduction de `MAX_SIZE_BYTES` de 20 Mo à 15 Mo
  - Tests : 20 phrases réalistes, coût des chaînes longues, validation bornes 15 Mo

- **Logique métier extraite de `ReaderViewModel` vers des UseCases**
  - `CalculateReadingProgressUseCase` : calcul + persistance progression (7 tests)
  - `LoadChapterUseCase` : chargement chapitre + annotations (3 tests)
  - `ManageReaderAnnotationsUseCase` : CRUD bookmarks/highlights/annotations (7 tests)
  - `PreWarmNextChapterUseCase` : pré-synthèse chapitre N+1
  - `ResolveReadingPositionUseCase` : résolution position DB vs SavedState (5 tests)
  - ViewModel réduit de 520 à 470 lignes (−10%)

- **Baseline Profile étendu à 72 entrées (+260% de couverture)**
  - Couvre tous les écrans (10), le pipeline audio (4 classes), la synthèse TTS (3 classes)
  - Tous les DAOs Room (11), DataStore, 9 UseCases, repositories, DI Hilt
  - Fondations Compose/Material3 (LazyList, Column, Row, Box), DataStore, Lifecycle

- **Tests unitaires : 110 tests dans 12 fichiers (+96%)**
  - `TtsRepositoryImplTest` : 8 tests (synthèse Piper/Edge, fallback réseau, cache, indisponibilité)
  - `ReaderViewModelTest` : +5 tests (Process Death, SavedStateHandle, rapid intents, ONNX error)
  - `GaplessAudioPlayerTest` : 17 tests (race conditions, stress, mémoire)
  - `PlaybackOrchestratorTest` : 10 tests (timeout, erreurs consécutives, pipeline)

- **Pipeline TTS : timeouts et seuils d'erreurs adaptatifs par moteur**
  - Timeout différencié : ONNX/Piper 2s, Edge cloud 8s, défaut 5s
  - Seuil d'erreurs consécutives adaptatif : 3 (ONNX) vs 8 (Edge)
  - SampleRate du player ajusté dynamiquement au résultat de synthèse (Edge=24000 Hz, ONNX=22050 Hz)
  - Le sampleRate est appliqué avant `player.play()` → `ensureTrack()` crée l'AudioTrack au bon taux

#### Confirmed — Déjà conformes

- **SILENCE_BUFFER / ShortArray** : `ShortArray(n)` par phrase déjà éliminé via CRITIQUE 2 ✅
- **SharedPreferences → DataStore** : migration complète, zéro référence restante ✅
- **SettingsRepository** : 100% des lectures exposées en `Flow<*>` ✅

### 2026-07-18 — Audit initial (recommandations)

#### Added
- **Baseline Profile** (`baseline-prof.txt`) pour réduire le cold start de ~300ms (compilation AOT)
- **Workflow CI/CD** (`.github/workflows/ci.yml`) : lint, tests unitaires, assemble debug sur PR
- **Tests unitaires** :
  - `PlaybackOrchestratorTest` — 5 tests : fillJob cancellation, stop pendant synthèse, erreur de synthèse, substitution de play, pause/resume
  - `OnnxInferenceServiceTest` — 6 tests : initialisation, modèle absent, tokens vide, idempotence, paramètres prosodiques
  - `ReaderViewModelTest` — 12 tests : UI state, toggle, setSpeed/setVoice, cycleTheme, play/pause/stop, SavedStateHandle restoration, erreurs

#### Fixed
- **fillJob orphelin** : ajout de `ensureActive()` avant et après `ttsRepository.synthesize()` dans `launchSynthesisPipeline` (🔴 CRITIQUE 3)
- **Allocations FloatArray silence** : réutilisation du `SILENCE_BUFFER` statique sans copie quand la taille correspond (🟠 HAUTE 2)
- **FrenchSentenceSplitter** : 5 tests corrigés (M., Dr., etc., J. K., guillemets). Bug racine : `punctIndex` pointait sur l'espace après le point (BreakIterator inclut les blancs). Ajout de `findLastPunctuationBefore()` et `isInsideGuillemets()`
- **`SystemClock.elapsedRealtime()`** : timestamps de lecture monotonic (P05)
- **Code mort** : suppression de `ProgressEntity.currentWordOffset` (A06)
- **Cold Start** : `LibraryViewModel.loadBooks()` déjà sur `Dispatchers.IO` ✅, `SettingsViewModel` DataStore sur `Dispatchers.IO` ✅, `InkToneApplication` minimal ✅
- **Double ouverture EPUB** : `getChapter()` retourne sur cache hit sans `openPublication()` (P01) ✅
- **`rememberTextMeasurer()`** : déjà sur `Dispatchers.Default` via `produceState` (C04) ✅
- **`PlaybackOrchestrator.play()`** : déjà refactoré en sous-méthodes (A01) ✅

#### Confirmed
- **SharedPreferences → DataStore** : migration complète, plus aucune référence à SharedPreferences (🟠 HAUTE 3) ✅
- **AudioServiceLauncher** : séparation propre ViewModel/Android via interface domaine (🟠 HAUTE 4) ✅
- **AudioCacheManager** : LruCache AndroidX avec taille réelle mesurée, capacité 20 Mo (🟠 HAUTE 1) ✅

### 2026-07-17 — Feature Edge TTS + Refactoring Pause/Resume

#### Added
- **Moteur TTS Microsoft Edge** (cloud, gratuit) :
  - `EdgeTtsClient` — WebSocket vers `speech.platform.bing.com` avec authentification DRM (`Sec-MS-GEC`)
  - `Mp3Decoder` — décodage MP3→PCM via `MediaCodec` Android
  - Voix françaises : `fr-FR-VivienneNeural` (défaut), `fr-FR-HenriNeural`
- **Architecture Provider TTS** (pattern Strategy) :
  - Interface `TtsProvider` avec `PiperTtsProvider` (ONNX local) et `EdgeTtsProvider` (cloud)
  - `TtsRepositoryImpl` routeur avec fallback automatique Edge→Piper sur erreur réseau
- **Sélecteur de moteur TTS** dans `SettingsScreen` (Piper ONNX / Microsoft Edge)
- **Retry exponentiel** (3 tentatives : 500ms→1s→2s) sur erreurs réseau transitoires
- **Classification d'erreurs par type** (`isNetworkError`) : `UnknownHostException`, `SocketTimeoutException`, `ConnectException`, `SocketException`
- **Logging TtsDebug** de bout en bout : WebSocket chunks → MediaCodec → AudioTrack.write

#### Changed
- **Refactoring pause/resume** dans `PlaybackOrchestrator` :
  - `ReentrantLock` pour atomicité des transitions pause/resume/stop
  - `consumeAndPlay` attend sur pause (ne break pas) → reprise <50ms
  - `ReaderViewModel.play()` après pause appelle `orchestrator.resume()` (plus de destruction/reconstruction)
  - Boucle de tracking gère l'état Paused via `delay(200); continue`
- `SynthesisResult` : ajout du champ `engineId` pour tracer l'origine
- `TtsRepository` : nouvelles méthodes `getAvailableEngines()` et `getEngine()`

#### Fixed
- Boucle infinie dans `Mp3Decoder.decodeToShortArray` (condition `outputBuffers.isNotEmpty()`)
- 403 Forbidden WebSocket : ajout des paramètres `Sec-MS-GEC` + `Sec-MS-GEC-Version` + headers complets
- Chunks binaires ignorés (collecte inconditionnelle, suppression du flag `audioStarted`)
- Pipeline zombie sur pause (le job de synthèse continuait sans consommateur)

### 2026-07-08 — Scaffold & Build initial
- **Scaffold projet Android** : Gradle, Hilt, Room, Compose Navigation, Media3, ONNX Runtime
- **Version Catalog** (`libs.versions.toml`) avec toutes les dépendances
- **Gradle Wrapper** généré (Gradle 8.9)
- **Android SDK** configuré (API 34 + 35, Build-Tools 35.0.0)
- **Premier build réussi** : `app-debug.apk` (38 Mo)
- Corrections : downgrade `compileSdk` 35 → 34 (compatibilité AGP 8.5.2), ONNX Runtime 1.18.1 → 1.19.2
- `local.properties` créé avec chemin SDK

### 2026-07-08 — Phase 0 terminée ✅
- **Prototype Sherpa-ONNX validé** sur Snapdragon 680 : synthèse FR fonctionnelle
- **Modèle VITS Piper** `fr_FR-upmc-medium` intégré (2 voix : Jessica ♀, Pierre ♂)
- **RTF ~0.8** sur Snapdragon 680 (toutes longueurs de texte < 1.0)
- **Phonémisation FR** : 7/10 phrases OK, 3 imperfections mineures (eSpeak)
- **Écran de test** avec synthèse + lecture AudioTrack
- **Décision finale** : GO Sherpa-ONNX (pas de fallback Piper nécessaire)

### 2026-07-08 — Phase 1 complétée ✅
- **Test timestamps** : alignement phrase par phrase validé (cumul échantillons)
- **Rapport prototype** : `docs/prototype-report.md` complet
- **Pipeline texte → audio + timestamps** fonctionnel de bout en bout

---

## Versions futures

### [0.1.0] — ~Semaine 4 (cible)
- Prototype Sherpa-ONNX fonctionnel
- Validation timestamps mot/milliseconde
- Mesure RTF sur 3 chipsets

### [0.2.0] — ~Semaine 10 (cible)
- Parsing EPUB2/EPUB3 via Readium
- Pipeline audio gapless (AudioTrack)
- Lecture TTS d'un chapitre complet

### [0.3.0] — ~Semaine 16 (cible)
- UI complète (Library, Reader, Settings, Bookmarks)
- Surlignage synchronisé fluide
- Thèmes clair/sépia/sombre

### [1.0.0] — ~Semaine 20 (cible)
- Release candidate Play Store
- Optimisation batterie
- Beta fermée

---

> **Légende :** `Added` · `Changed` · `Deprecated` · `Removed` · `Fixed` · `Security`
