# 📝 Changelog — InkTone

Toutes les modifications notables de ce projet sont documentées dans ce fichier.

Le format est basé sur [Keep a Changelog](https://keepachangelog.com/fr/1.0.0/),
et ce projet adhère au [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### 2026-07-20 — Plan Top-Tier, Phase 2.1 : Import EPUB — une seule ouverture ZIP (branche `main`)

Tâche 2.1 de [`PLAN_ACTION_TOP_TIER_CLAUDECODE.md`](./PLAN_ACTION_TOP_TIER_CLAUDECODE.md).

#### Changed — 🔴 Performance de l'import

- **`extractRawHtml()`, `extractAndSaveImage()`, `extractCoverHeuristic()`, `extractCalibreSeriesFallback()`** ouvraient chacune un `ZipFile(epubFile)` séparé et rescannaient linéairement toutes les entrées de l'archive à chaque appel — potentiellement 70-100+ ouvertures/scans redondants par import (une fois par chapitre, une fois par image). Remplacé par `EpubZipIndex` : un seul `ZipFile` ouvert en tête d'import, une `Map<String, ZipEntry>` indexée une fois, recherche O(1) par chemin exact avec repli par suffixe (préserve le comportement existant pour les chemins relatifs/préfixés différemment). `getChapter()` (chargement à froid d'un chapitre) et `regenerateCover()` bénéficient du même changement.
- Fermeture garantie via `try/finally` englobant tout le corps de `importEpub()` après l'ouverture du ZIP.

**Validation** : `./gradlew assembleDebug` ✅, `testDebugUnitTest` ✅. Mesure réelle sur appareil physique (pas de comparaison avant/après contrôlée — l'optimisation remplace un pattern connu par un autre strictement moins coûteux, sans changement de comportement) : trois imports capturés via logcat, aucun pic de latence par chapitre malgré des dizaines d'appels `extractRawHtml`/`extractRichBlocks` contre la même archive —
  - *Daisy Fortune Tome 2* (27 chapitres) : 4,55s
  - *Avoir le courage de ne pas être aimé* (27 chapitres) : 2,50s
  - *Persuasion* (Austen) : chapitres traités en ~100-150ms chacun malgré des dizaines de blocs riches par chapitre

### 2026-07-20 — Plan Top-Tier, Phase 1 : Progression de lecture (branche `main`)

Phase 1 exécutée dans l'ordre 1.1 → 1.8 selon [`PLAN_ACTION_TOP_TIER_CLAUDECODE.md`](./PLAN_ACTION_TOP_TIER_CLAUDECODE.md). Conception préalable dans [`architecture.md` §11](./architecture.md).

#### Changed — 🔴 Source de vérité unique pour la position de lecture

- **Fusion des tables `progress` et `reading_progress`** en une seule table `reading_progress` (Room v15→16, `MIGRATION_15_16`) : `bookId, chapterIndex, sentenceIndex, characterOffset, totalProgressFraction, updatedAt, source`. Les deux anciennes tables ne se recouvraient pas forcément (l'une alimentait le badge `%` de la bibliothèque, l'autre la reprise exacte du Reader) — la migration préserve le champ propre à chacune plutôt que de garder une seule ligne "gagnante" qui aurait perdu l'autre moitié de l'information. `ProgressEntity.kt`, `ProgressDao.kt` et le modèle domaine `Progress.kt` supprimés ; `BookRepository`/`CalculateReadingProgressUseCase` retypés directement sur l'entité `ReadingProgress`.
- **Pondération réelle de la progression par longueur de chapitre** — `TocEntry.charCount` (nouveau champ, peuplé gratuitement à l'import depuis `combinedHtml.length`, déjà en mémoire) remplace la formule naïve `(chapterIndex + sentenceIndex/totalSentences) / totalChapters` qui traitait tous les chapitres comme équivalents. Dégrade vers l'ancienne formule pour les livres importés avant ce changement (`charCount = 0`), sans blocage ni ré-import forcé.
- **Course d'écriture éliminée** entre le chemin TTS (`PlaybackOrchestrator.saveProgressAsync`) et le chemin manuel (`CalculateReadingProgressUseCase` via `ReaderViewModel`) : les deux écrivaient indépendamment sur la même ligne (`INSERT OR REPLACE`) sans coordination une fois les tables fusionnées. `PlaybackOrchestrator` calcule et persiste désormais lui-même `totalProgressFraction` (formule partagée `computeReadingProgressFraction`, `source=TTS`) ; `CalculateReadingProgressUseCase` ne sert plus que pour le scroll manuel (`source=MANUAL_SCROLL`), qui ne s'exécute jamais pendant une lecture TTS active.
- **`characterOffset` réutilisé** pour la pondération (offset caractère de la phrase courante dans son chapitre) au lieu d'être écrit sans jamais être lu.

#### Fixed — 🔴 Reprise de lecture

- **Scroll/tap manuel désormais persisté** — auparavant, seule une lecture TTS active déclenchait une écriture Room ; naviguer manuellement sans lancer l'audio ne survivait pas à un `kill` de process. `ReaderContent` débounce (500 ms) le scroll (`snapshotFlow` sur `firstVisibleItemIndex`/`pagerState.currentPage`), ignoré pendant l'auto-scroll programmatique du TTS (flag `isProgrammaticScroll`) ou une lecture active, et remonte vers `ReaderViewModel.onManualPositionChanged()`.
- **`activeIdx` unifié** dans `ReaderContent` : `if (isSpeaking) playbackState.activeSentenceIndex else currentSentenceIndex` — la position restaurée s'affiche désormais dès le premier rendu, sans attendre une interaction (avant : toujours 0 par défaut hors lecture).
- **`startFrom = 0` codé en dur** dans `ReaderViewModel.play()` remplacé par `startFrom = currentSentenceIndex` — presser Play redémarrait toujours en début de chapitre au lieu de la position affichée. `stop()` ne réinitialise plus l'affichage à 0 (la DB gardait déjà la bonne valeur, seul l'affichage était incohérent).

#### Fixed — 🟠 Sous-problème découvert (hors périmètre initial, ajouté au plan en 1.2bis)

- **Trou de migration Room `6→13`** — aucune `Migration` explicite pour ce chemin (versions consommées pendant une période de churn pré-beta, dont deux commits divergents ayant chacun bumpé vers la version 12 indépendamment), couvert silencieusement par `fallbackToDestructiveMigration()`. Remplacé par `fallbackToDestructiveMigrationFrom(6..12)` (Room refuse qu'une version soit à la fois couverte par une `Migration` explicite et listée en fallback — 1 à 5 ont déjà leur propre migration) : seul ce trou historique (sans base installée réelle à reconstituer) tolère encore un fallback destructif — tout futur trou de migration non couvert fera désormais planter l'app au lieu d'effacer silencieusement la base d'un testeur.

#### Added — Tests

- `CalculateReadingProgressUseCaseTest` — pondération sur chapitres très inégaux (préface 200 caractères vs chapitre 50 000), dégradation gracieuse sans `charCount`.
- `InkToneDatabaseMigrationTest` (instrumenté) — migration 15→16 contre une vraie base SQLite, fusion sans perte des deux anciennes tables.
- `ReadingProgressIntegrationTest` (instrumenté) — scénario complet ouverture → scroll manuel sans audio → simulation de fermeture de process → réouverture → chapitre/phrase restaurés depuis Room (pas `SavedStateHandle`).
- Garde-fous régression sur `startFrom`/`stop()` dans `ReaderViewModelTest`, vérifiés capables de détecter la régression (bug réintroduit temporairement en local, suite rouge confirmée, puis reverti).

**Validation** : `./gradlew assembleDebug` ✅, `testDebugUnitTest` ✅ (130 tests, 0 échec), `connectedDebugAndroidTest` ✅ (3 tests instrumentés sur appareil physique, 0 échec).

### 2026-07-20 — Plan Top-Tier, Phase 0 : Conformité store & exploitation (branche `main`)

Phase 0 exécutée selon [`PLAN_ACTION_TOP_TIER_CLAUDECODE.md`](./PLAN_ACTION_TOP_TIER_CLAUDECODE.md), basé sur [`AUDIT_INDEPENDANT_UX_PERFORMANCE_2026-07-20.md`](./AUDIT_INDEPENDANT_UX_PERFORMANCE_2026-07-20.md) (audit indépendant, commit `567d836`).

#### Removed — 🔴 Risque de rejet Play Store

- **`MANAGE_EXTERNAL_STORAGE`** retirée du manifeste et **`FilesScreen.kt`** supprimé entièrement — cet explorateur de fichiers interne était redondant avec l'import SAF (`OpenMultipleDocuments`) déjà en place, et cette permission "accès à tous les fichiers" expose à un rejet/suspension Play Store. `NavigationDestination.FILES`, le bouton "Parcourir mes fichiers" et `LibraryViewModel.importFile()` (devenu mort) retirés en cohérence.

#### Added — 🔴 Observabilité

- **Firebase Crashlytics** intégré (`CrashReporter.kt`, style aligné sur `PerfLogger`) — no-op silencieux tant qu'aucun `google-services.json` n'est présent (`FirebaseApp.initializeApp` renvoie `null`), donc le build reste fonctionnel sans les identifiants Firebase du mainteneur. Plugins `google-services`/`firebase-crashlytics` appliqués conditionnellement dans `app/build.gradle.kts`, sur le même modèle que `keystore.properties`.
- Les `Log.e()` des zones critiques (`PlaybackOrchestrator`, `BookRepositoryImpl.importEpub`, `OnnxInferenceService`) remontent désormais aussi vers `CrashReporter.recordException()`, en plus du log local existant.
- `BuildConfig.GIT_COMMIT` (hash court, résolu via `providers.exec` — compatible configuration cache) exposé comme clé custom Crashlytics aux côtés de `VERSION_NAME`, pour recouper un crash avec l'historique git.
- Pipeline validé bout en bout sur appareil physique : crash de test volontaire déclenché et confirmé visible dans le dashboard Firebase Crashlytics (projet `ink-tone`).

**Validation** : `./gradlew assembleDebug` ✅, `testDebugUnitTest` ✅, permission absente du manifeste fusionné (vérifié), crash de test remonté dans Crashlytics (vérifié manuellement sur appareil).

### 2026-07-19 — Audit fonctionnel (branche `feature/feature-polish`)

Audit fonctionnel complet exécuté selon [`PLAN_FEATURE_AUDIT_CLAUDECODE.md`](./PLAN_FEATURE_AUDIT_CLAUDECODE.md), 8 tâches. Cible : fonctionnalités identifiées **CASSÉES** (Recherche FTS, Notes, navigation depuis Signets) ou en **CODE MORT** (Onboarding orphelin, `RecentBooksRepository`, `BookProgressDao`).

#### Fixed — 🔴 Fonctionnalités cassées

- **Recherche FTS** — `sentence_fts` n'était jamais peuplée à l'import ; `BookRepositoryImpl.importEpub()` insère désormais chaque phrase segmentée dans `SearchDao` au fil de l'import. Taper un résultat de recherche navigue maintenant vers la bonne position (chapitre + phrase) dans le Reader via `SavedStateHandle` (retour d'écran) ou des arguments de route optionnels (navigation directe depuis la bibliothèque).
- **Navigation depuis les Signets** — même mécanisme `jumpChapter`/`jumpSentence` appliqué à `BookmarkScreen` et au panneau "tous les livres" (`AllBookmarksPanel`), qui transmet désormais aussi la position (chapitre, phrase) et non plus seulement l'identifiant du livre.
- **Notes** — un dialog de saisie de texte remplace l'ajout silencieux d'une annotation vide ; le texte est stocké dans `AnnotationEntity.notes` et un indicateur 📝 discret est affiché dans le texte du Reader pour les phrases annotées.

#### Added — 🟠 Fonctionnalités partielles complétées

- **Surlignages** — mini color picker (5 couleurs) au lieu d'une couleur unique imposée ; nouvel onglet "Surlignages" dans `BookmarkScreen` (liste + suppression), en plus de l'onglet Signets existant.
- **Table des matières** — affiche les vrais titres de chapitres (`SentenceCacheDao.getChapterTitles()`) au lieu de "Chapitre 1, 2, 3…".
- **Overlays bas d'écran** — suppression des collisions visuelles entre `UnifiedControlPanel`, tooltips et captions TTS : le tooltip d'accueil du Reader ne s'affiche plus que HUD masqué (il pointe vers le FAB ▶, pas le panneau), et le tooltip post-lecture ainsi que les captions TTS sont masqués pendant une sélection de texte.

#### Removed — 🟡 Code mort

- `BookProgressDao` / `BookProgressEntity` et `RecentBooksRepository` / `RecentBookDao` / `RecentBookEntity` supprimés (aucune référence productive) — migration Room `5→6` ajoutée pour `DROP TABLE` les tables `book_progress` et `recent_books` obsolètes.
- `TtsTestScreen` et le bouton "Debug" du tiroir de navigation conditionnés à `BuildConfig.DEBUG`.
- Onboarding vérifié déjà connecté (`MainActivity` + flag `isFirstLaunch` DataStore) — aucun changement nécessaire.

#### Fixed — 🐛 Bugs découverts pendant l'audit (hors périmètre initial)

- **Course critique dans `PlaybackOrchestrator`** — `consumeAndPlay()` pouvait écraser un état `Error` (posé par le pipeline de synthèse après le seuil d'erreurs consécutives) avec `Playing`, les deux coroutines producteur/consommateur tournant en parallèle sur le même scope. Corrigé via `MutableStateFlow.update{}` (compare-and-swap) au lieu d'une affectation directe non atomique.
- `ReaderViewModelTest` ne compilait plus (mocks désynchronisés du constructeur du ViewModel) — réaligné.
- `PlaybackOrchestratorTest` stabilisé (attente réelle par polling au lieu d'un `Thread.sleep` fixe, racy sous charge machine) et une annotation `@Test` manquante restaurée sur un test qui ne s'exécutait jamais silencieusement.

**Validation** : `./gradlew assembleDebug` ✅, `kspDebugKotlin` ✅ après chaque tâche ; `testDebugUnitTest` ✅ (111 tests, 0 échec, stable sur 15 exécutions répétées après correction de la course).

### 2026-07-19 — Refonte UI/UX (branche `ui-ux-audit-phase1`)

Audit UI/UX complet exécuté selon [`PLAN_ACTION_UXUI_CLAUDECODE.md`](./PLAN_ACTION_UXUI_CLAUDECODE.md), 10 tâches.

#### Changed — 🟢 Cohérence thème & Material 3

- **Suppression des couleurs hardcodées** dans `BookmarkScreen`, `AllBookmarksPanel`, `SearchScreen`, `ReaderScreen`, `ReaderTtsPanel`, `LibraryScreen` — remplacées par `MaterialTheme.colorScheme.*`, désormais cohérentes sur les 3 thèmes (Papier d'Art, Obsidian, Nordic Fog). Les couleurs calculées depuis `ReaderTheme` (Jour/Nuit/Sépia) restent volontairement indépendantes du thème app.
- **`LibraryScreen` migré vers `ModalNavigationDrawer`** — remplace le drawer manuel (`Surface` + `AnimatedVisibility`) ; gère nativement le back gesture Android, le predictive back (Android 14+) et l'annonce d'état TalkBack.
- **Empty state actionnable** (bibliothèque et récents vides) — icône dans cercle `primaryContainer`, titre + description, CTA "Importer un livre" et "Parcourir mes fichiers".
- **`ErrorBanner` aligné sur le pattern Material 3 error** — `errorContainer` plein, icône `ErrorOutline`, préfixes emoji retirés du message.
- **Couvertures de livres via Coil `AsyncImage`** — remplace un `BitmapFactory.decodeFile()` synchrone dans `remember()` qui bloquait le thread de composition ; cache LRU et chargement async gérés nativement par Coil.
- **`LibraryNavigationPopup` connecté aux données réelles** — le groupement par auteur (`NavSubItem`) vient désormais de `allBooks` en mémoire au lieu de données mockées ("Black Wings", etc.) ; cliquer un auteur filtre la bibliothèque via la recherche existante.

#### Added — 🟡 Accessibilité

- `strings.xml` peuplé (navigation, bibliothèque, lecteur, stats) et branché sur les icônes interactives (`IconButton` sans texte).
- `semantics { contentDescription = ... }` ajouté sur les 2 `Canvas` de `StatsScreen` (jauge objectif quotidien, graphique WPM hebdomadaire) et sur le badge de progression des couvertures.
- Correction incidente : labels du graphique WPM dessinés en blanc pur via `nativeCanvas` (invisibles en thème clair) → couleur dérivée de `MaterialTheme.colorScheme.onSurfaceVariant`.

#### Changed — 🟡 Lecteur (Reader)

- **`UnifiedControlPanel` restructuré** en deux rangées : contrôles primaires (chapitre précédent/suivant + play/pause central agrandi avec haptique) et actions secondaires avec icône + label (Voix, Police, Thème, Veille). La navigation phrase par phrase reste accessible depuis le panneau TTS.
- **Barre de progression + ETA** — `LinearProgressIndicator` fine (2dp) toujours visible en haut de l'écran de lecture, plus une estimation du temps restant du chapitre (`etaMinutes`) calculée depuis le WPM moyen de l'utilisateur (`ReadingSessionDao`).
- **Dictionnaire de prononciation déplacé** de `ReaderTtsPanel` (panneau de lecture) vers une nouvelle section "🗣️ Prononciation" dans `SettingsScreen` — CRUD porté par `SettingsViewModel`.

Validation : `./gradlew assembleDebug` ✅ après chaque tâche.

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
