# 📊 InkTone — Suivi d'Avancement Projet

> Dernière mise à jour : 2026-07-20  
> Phase actuelle : **Plan Top-Tier — Phases 0 et 1 ✅ complétées, Phase 2 à venir**  
> Progression globale : **~95%**  
> Moteurs TTS : **Piper VITS `fr_FR-upmc-medium`** — 2 locuteurs Jessica ♀ + Pierre ♂ (local) + **Microsoft Edge TTS** (cloud, Vivienne & Henri)  
> Tests unitaires : **130 tests, 0 échec** (+ 3 tests instrumentés sur appareil réel — migration Room et reprise de lecture)  
> Score audit : **8.0/10** (corrigé depuis 6.2/10)

---

## 🎯 Vision

Lecteur d'ebooks Android (EPUB2/EPUB3) avec synthèse vocale neuronale locale en français, inspiré de Moon+ Reader pour l'UX, avec un moteur TTS Sherpa-ONNX pour le surlignage synchronisé mot-à-mot.

**Documents de référence :**
- [`architecture.md`](./architecture.md) — Architecture technique complète
- [`README.md`](./README.md) — Présentation du projet

---

## 📋 Suivi par Phase

### Phase 0 — Préparation & Prototype Sherpa-ONNX (Objectif : 2026-07-14)

| # | Tâche | Statut | Priorité | Assigné | Notes |
|---|---|---|---|---|---|
| 0.1 | Créer repo GitHub + README | ✅ Fait | 🔴 | — | — |
| 0.2 | Rédiger `PROJECT_STATUS.md` | ✅ Fait | 🔴 | — | Ce document |
| 0.3 | Rédiger `architecture.md` | ✅ Fait | 🔴 | — | Auditée et corrigée |
| 0.4 | Scaffold projet Android (Gradle, Hilt, Room, Compose) | ✅ Fait | 🔴 | — | Build OK — app-debug.apk (38 Mo) |
| 0.5 | Configurer Version Catalog (`libs.versions.toml`) | ✅ Fait | 🔴 | — | Compose, Hilt, Room, Media3, ONNX Runtime |
| 0.6 | Prototyper Sherpa-ONNX sur 1 device Android | ✅ Fait | 🔴 | — | Synthèse OK — RTF ~0.8, 2 voix FR |
| 0.7 | Valider phonémisation FR (liaisons, muets) | ✅ Fait | 🔴 | — | 7/10 OK, 3 imperfections mineures (eSpeak) |
| 0.8 | Mesurer RTF sur Snapdragon / MediaTek / Tensor | ✅ Fait | 🟡 | — | Snapdragon 680 < 1.0 toutes longueurs ; MediaTek/Tensor à tester |
| 0.9 | Décision finale : Sherpa-ONNX vs Piper | ✅ Fait | 🔴 | — | GO Sherpa-ONNX (VITS Piper) |

### Phase 1 — Fondations & Validation ONNX (Objectif : Semaines 1-4)

| # | Tâche | Statut | Priorité | Notes |
|---|---|---|---|---|
| 1.1 | Projet Android : Hilt, Room, Compose Navigation | ✅ Fait | 🔴 | Fait en Phase 0.4 |
| 1.2 | Intégration ONNX Runtime Android | ✅ Fait | 🔴 | Inclus dans sherpa-onnx AAR |
| 1.3 | Intégration Sherpa-ONNX modèle VITS français | ✅ Fait | 🔴 | Modèle fr_FR-upmc-medium intégré |
| 1.4 | Compilation `piper-phonemize` pour NDK (fallback) | ⏭️ Sautée | 🟡 | eSpeak-NG intégré suffisant (7/10 phono OK) |
| 1.5 | Test RTF sur 3 chipsets | 🔄 Partiel | 🔴 | Snapdragon 680 ✅ ; MediaTek/Tensor à tester |
| 1.6 | Test de bout en bout : texte → phonèmes → audio + timestamps | ✅ Fait | 🔴 | Alignement phrase par phrase fonctionnel |
| 1.7 | Rédiger `docs/prototype-report.md` | ✅ Fait | 🟡 | Rapport complet (RTF, phono, timestamps) |

### Phase 2 — Parsing EPUB & Pipeline Audio (Objectif : Semaines 5-10)

| # | Tâche | Statut | Priorité | Notes |
|---|---|---|---|---|
| 2.1 | Intégration Readium Kotlin Toolkit | ✅ Fait | 🔴 | Readium 3.0.0 (PublicationOpener, AssetRetriever) |
| 2.2 | `ParseEpubUseCase` + extraction metadata | ✅ Fait | 🔴 | Titre, auteur, langue, chapters via Readium |
| 2.3 | `ChunkTextUseCase` — segmenteur phrases FR | ✅ Fait | 🔴 | FrenchSentenceSplitter avec règles FR |
| 2.4 | `FrenchSentenceSplitter` + tests unitaires | ✅ Fait | 🔴 | 10 tests (0 échec) — abrév., initiales, etc. |
| 2.5 | `PhonemizationPipeline` (texte → phonèmes) | ✅ Fait | 🔴 | eSpeak-NG intégré dans sherpa-onnx (voix fr) |
| 2.6 | `OnnxInferenceService` (bridge JNI) | ✅ Fait | 🔴 | Synthèse ONNX opérationnelle |
| 2.7 | `PlaybackOrchestrator` (buffer +3, async) | ✅ Fait | 🔴 | 321 phrases, channel-based, fill coroutine |
| 2.8 | `GaplessAudioPlayer` (AudioTrack) | ✅ Fait | 🔴 | PCM float, ConcurrentLinkedQueue, gapless |
| 2.9 | `AudioPlaybackService` (MediaSessionService) | ✅ Fait | 🔴 | InkTonePlayer + MediaSession + notif foreground |
| 2.10 | `MediaSessionConnector` + `AudioFocusManager` | ✅ Fait | 🔴 | AudioFocus: appels → pause, notifs → ducking |
| 2.11 | `AudioCacheManager` (LRU, eviction, purge) | ✅ Fait | 🟡 | LRU 30 Mo, TTL 10 min, intégré TtsRepository |
| 2.12 | `SynthesisResult` sealed class + error handling | ✅ Fait | 🟡 | Fait avec OnnxInferenceService |
| 2.13 | Test intégration : lecture TTS chapitre complet | ✅ Fait | 🔴 | Validé — Piper UPMC, RTF ~0.33, 2 locuteurs |

### Phase 3 — UI & Intégration ✅ COMPLÉTÉE (Objectif : Semaines 11-16)

| # | Tâche | Statut | Priorité | Notes |
|---|---|---|---|---|
| 3.1 | `LibraryScreen` — Import EPUB via SAF + grille | ✅ Fait | 🔴 | Grid 3 colonnes, covers, filtres, tri |
| 3.2 | Table des matières navigable (NCX/NAV) | ✅ Fait | 🔴 | Drawer TOC dans ReaderScreen |
| 3.3 | `ReaderScreen` — Rendu texte immersif | ✅ Fait | 🔴 | Overlay, center-third tap, thèmes |
| 3.4 | Surlignage synchronisé phrase par phrase | ✅ Fait | 🔴 | Scroll automatique sur lecture |
| 3.5 | `UnifiedControlPanel` — Play, Pause, Next/Prev | ✅ Fait | 🔴 | Restructuré : rangée primaire (skip chapitre + play/pause central) + rangée secondaire icône+label (Voix, Police, Thème, Veille) |
| 3.6 | Indicateur de progression | ✅ Fait | 🟡 | Barre fine 2dp en haut + micro-indicateur chapitre + ETA (WPM moyen via `ReadingSessionDao`) |
| 3.7 | Réglages TTS dans le panneau (vitesse, voix) | ✅ Fait | 🟡 | Intégré au UnifiedControlPanel |
| 3.8 | `BookmarkScreen` — Gestion signets | ✅ Fait | 🟡 | BookmarkScreen + BookmarkViewModel + BookmarkDao |
| 3.9 | Thèmes : Nuit, Jour, Sépia | ✅ Fait | 🟢 | cycleTheme() + Material 3 |
| 3.10 | Police OpenDyslexic | ✅ Fait | 🟢 | OTF téléchargé (171 Ko), intégré via Font(R.font.opendyslexic_regular) |
| 3.11 | Tests UI + capture screenshots | ⬜ À faire | 🟡 | — |

### Phase 4 — Edge TTS & Robustesse ✅ (2026-07-17)

| # | Tâche | Statut | Priorité | Notes |
|---|---|---|---|---|
| 4.1 | Architecture Provider TTS (pattern Strategy) | ✅ Fait | 🔴 | `TtsProvider` + `PiperTtsProvider` + `EdgeTtsProvider` |
| 4.2 | Client WebSocket Microsoft Edge TTS | ✅ Fait | 🔴 | DRM `Sec-MS-GEC`, SSML, MP3→PCM |
| 4.3 | Décodeur MP3 via `MediaCodec` | ✅ Fait | 🔴 | `Mp3Decoder`, conversion ShortArray→FloatArray |
| 4.4 | Sélecteur moteur TTS dans Settings | ✅ Fait | 🔴 | Piper / Edge + voix Vivienne / Henri |
| 4.5 | Retry exponentiel + fallback Edge→Piper | ✅ Fait | 🔴 | 3 tentatives, backoff 500ms→1s→2s |
| 4.6 | Gestion erreurs réseau par type d'exception | ✅ Fait | 🔴 | `isNetworkError()` — UnknownHost, SocketTimeout, etc. |
| 4.7 | Refactoring pause/resume (ReentrantLock) | ✅ Fait | 🔴 | Pipeline wait-on-pause, reprise <50ms |
| 4.8 | Correction des 7 failles d'audit pause/resume | ✅ Fait | 🔴 | Zombie, double AudioTrack, use-after-free, latence |
| 4.9 | Logging TtsDebug de bout en bout | ✅ Fait | 🟡 | WebSocket→MediaCodec→AudioTrack |

### Phase 5 — Optimisation, Robustesse & Release (Objectif : Semaines 17-20)

| # | Tâche | Statut | Priorité | Notes |
|---|---|---|---|---|
| 5.1 | Profilage CPU/Batterie (Android Profiler) | ✅ Fait | 🔴 | RTF ~0.33, RAM ~150 Mo, APK 120 Mo debug / 106 Mo release |
| 5.2 | FTS5 `sentence_fts` — recherche in-book | ✅ Fait | 🔴 | FTS4 virtual table, SearchScreen avec wildcard MATCH |
| 5.3 | Process Death : `SavedStateHandle` + restoration | ✅ Fait | 🔴 | Sauvegarde chapter/sentence/voice/theme/font |
| 5.4 | Gestion EPUB corrompus / erreurs parsing | ✅ Fait | 🟡 | try/catch avec messages explicites + nettoyage fichiers |
| 5.5 | SAF : persistence permissions + réimport | ✅ Fait | 🟡 | takePersistableUriPermission() dans importEpub() |
| 5.6 | ProGuard/R8 config (ONNX + Sherpa + Readium) | ✅ Fait | 🔴 | Règles complètes — build release OK (106 Mo) |
| 5.7 | Backup/Restore données (bookmarks, progrès) | ✅ Fait | 🟡 | Export JSON via SAF, import avec restauration complète |
| 5.8 | Accessibilité : TalkBack, tailles min/max | ✅ Fait | 🟡 | contentDescription sur contrôles critiques + OpenDyslexic + strings.xml complet + semantics sur Canvas Stats |
| 5.9 | Vérification licences (Sherpa, Readium, ONNX) | ✅ Fait | 🟡 | THIRD_PARTY_NOTICES.md (Apache 2.0, MIT, BSD, SIL OFL, EPL) |
| 5.10 | Build signed APK / AAB release | ✅ Fait | 🔴 | Keystore généré, release 106 Mo signé |
| 5.11 | Beta fermée — 10-20 lecteurs francophones | ⬜ À faire | 🔴 | — |
| 5.12 | Publication Play Store (internal testing) | ⬜ À faire | 🔴 | — |

### Phase 5b — Corrections critiques pré-release ✅ COMPLÉTÉE (2026-07-18)

**Session de refactoring** — 11 commits sur `fix/corrections-pre-release`.  
Voir [`CHANGELOG.md`](./CHANGELOG.md) et [`files/EXECUTIVE_SUMMARY_CORRECTIONS.md`](./files/EXECUTIVE_SUMMARY_CORRECTIONS.md) pour le détail complet.

| # | Correctif | Commit | Impact |
|---|---|---|---|
| 🔴 C1 | Race condition AudioTrack use-after-free | `0b53f42` | Crash SIGSEGV éliminé |
| 🔴 C2 | Allocation ShortArray(n) par phrase | `a05398d` | GC pressure ÷ ∞ |
| 🔴 C3 | Timeout synthèse ONNX + consecutiveErrors | `5599a1e` | ANR 5-10s éliminé |
| 🟠 H1 | AudioCacheManager sizeOf() +20% alignement | `a060b3c` | Mémoire réelle estimée correctement |
| 🟠 H4 | ReaderViewModel → 5 UseCases extraits | `559e5f4`, `7612302` | ViewModel 520→470 L, logique métier dans domain |
| 🟠 H5 | Baseline Profile 72 entrées (+260%) | `3a7709b` | Cold start cible <300ms |
| 🟠 H6 | Tests unitaires : 110 tests, 12 fichiers | `57dd4fd` | Couverture ~50% (était ~12%) |
| 🔧 FIX | Timeout adaptatif ONNX/Edge + sampleRate dynamique | `389c24c`, `00d8b98` | Edge ne coupe plus après 3 phrases lentes, pitch corrigé |

**Validation** : `./gradlew clean test` ✅, `lint` ✅, `assembleDebug` ✅, `installDebug` ✅

### Phase 5c — Audit UI/UX Phase 1 ✅ COMPLÉTÉE (2026-07-19)

**Branche** `ui-ux-audit-phase1` — 10 tâches exécutées depuis [`PLAN_ACTION_UXUI_CLAUDECODE.md`](./PLAN_ACTION_UXUI_CLAUDECODE.md).
Voir [`CHANGELOG.md`](./CHANGELOG.md) pour le détail complet.

| # | Tâche | Statut | Priorité |
|---|---|---|---|
| 1 | Suppression des couleurs hardcodées (cohérence 3 thèmes) | ✅ Fait | 🔴 |
| 2 | `LibraryScreen` → `ModalNavigationDrawer` (back gesture, TalkBack) | ✅ Fait | 🔴 |
| 3 | Empty state actionnable (CTA import) | ✅ Fait | 🔴 |
| 4 | `ErrorBanner` aligné Material 3 | ✅ Fait | 🟠 |
| 5 | Couvertures via Coil `AsyncImage` (décodage async) | ✅ Fait | 🟠 |
| 6 | Accessibilité : `strings.xml` + `contentDescription` + semantics Canvas | ✅ Fait | 🔴 |
| 7 | `UnifiedControlPanel` restructuré (primaire/secondaire) | ✅ Fait | 🟠 |
| 8 | Barre de progression + ETA (WPM moyen) | ✅ Fait | 🟡 |
| 9 | `LibraryNavigationPopup` connecté aux données réelles | ✅ Fait | 🟡 |
| 10 | Dictionnaire phonétique déplacé vers `SettingsScreen` | ✅ Fait | 🟢 |

**Validation** : `./gradlew assembleDebug` ✅ après chaque tâche.

### Phase 5d — Audit fonctionnel ✅ COMPLÉTÉE (2026-07-19)

**Branche** `feature/feature-polish` — 8 tâches exécutées depuis [`PLAN_FEATURE_AUDIT_CLAUDECODE.md`](./PLAN_FEATURE_AUDIT_CLAUDECODE.md), qui ciblaient les fonctionnalités identifiées CASSÉES ou en CODE MORT (recherche, notes, navigation depuis signets, onboarding orphelin).
Voir [`CHANGELOG.md`](./CHANGELOG.md) pour le détail complet.

| # | Tâche | Statut | Priorité |
|---|---|---|---|
| 1 | Recherche FTS réparée (peuplement à l'import + navigation vers résultat) | ✅ Fait | 🔴 |
| 2 | Navigation depuis Signets (écran livre + panneau tous-livres) réparée | ✅ Fait | 🔴 |
| 3 | Notes : dialog de saisie + indicateur visuel dans le texte | ✅ Fait | 🔴 |
| 4 | Surlignages : color picker (5 couleurs) + onglet gestion/suppression | ✅ Fait | 🟠 |
| 5 | Table des matières : vrais titres de chapitres (au lieu de "Chapitre N") | ✅ Fait | 🟠 |
| 6 | Overlays bas d'écran unifiés (suppression des collisions visuelles) | ✅ Fait | 🟠 |
| 7 | Purge code mort (`BookProgressDao`, `RecentBooksRepository`) + migration Room 5→6 | ✅ Fait | 🟡 |
| 8 | Onboarding — déjà connecté (`MainActivity` + DataStore), aucun changement nécessaire | ✅ Vérifié | 🟡 |

**Corrections complémentaires** (tests découverts cassés pendant l'audit) :
- `ReaderViewModelTest` ne compilait plus (mocks désynchronisés du constructeur du ViewModel) — réaligné.
- **Course critique** dans `PlaybackOrchestrator.consumeAndPlay()` : l'état pouvait être écrasé de `Error` vers `Playing` par une coroutine concurrente (producteur/consommateur du pipeline de synthèse tournant en parallèle) — corrigée via `MutableStateFlow.update{}` (CAS) au lieu d'une affectation directe.
- `PlaybackOrchestratorTest` stabilisé (attente réelle au lieu de `Thread.sleep` fixe, racy sous charge) + une annotation `@Test` manquante restaurée (test silencieusement jamais exécuté).

**Validation** : `./gradlew assembleDebug` ✅, `kspDebugKotlin` ✅, `testDebugUnitTest` ✅ (111 tests, 0 échec, stable sur 15 exécutions répétées).

### Phase 5e — Audit Top-Tier, Phase 0 ✅ COMPLÉTÉE (2026-07-20)

**Branche** `main` — Phase 0 (Conformité store & exploitation) exécutée depuis [`PLAN_ACTION_TOP_TIER_CLAUDECODE.md`](./PLAN_ACTION_TOP_TIER_CLAUDECODE.md), lui-même basé sur [`AUDIT_INDEPENDANT_UX_PERFORMANCE_2026-07-20.md`](./AUDIT_INDEPENDANT_UX_PERFORMANCE_2026-07-20.md) (audit indépendant, commit `567d836`).

| # | Tâche | Statut | Priorité |
|---|---|---|---|
| 0.1 | Suppression de `MANAGE_EXTERNAL_STORAGE` + `FilesScreen.kt` (risque de rejet Play Store, redondant avec le SAF) | ✅ Fait | 🔴 |
| 0.2 | Intégration Firebase Crashlytics + routage des `Log.e()` critiques (`PlaybackOrchestrator`, `BookRepositoryImpl.importEpub`, `OnnxInferenceService`) | ✅ Fait | 🔴 |

**Détail** :
- `FilesScreen.kt` supprimé entièrement (explorateur de fichiers interne redondant avec l'import SAF `OpenMultipleDocuments` déjà en place) ; `NavigationDestination.FILES` et le bouton "Parcourir mes fichiers" retirés de `LibraryScreen`/`LibraryViewModel`.
- `CrashReporter.kt` (nouveau, style aligné sur `PerfLogger`) : no-op silencieux si aucun projet Firebase n'est configuré (`FirebaseApp.initializeApp` renvoie `null` sans `google-services.json`), donc le build reste fonctionnel sans les identifiants Firebase du mainteneur.
- Plugins `google-services`/`firebase-crashlytics` appliqués conditionnellement dans `app/build.gradle.kts` (même pattern que `keystore.properties` déjà utilisé pour la signature release).
- `BuildConfig.GIT_COMMIT` (hash court, via `providers.exec`, compatible configuration cache) exposé comme clé custom Crashlytics aux côtés de `VERSION_NAME`.
- Pipeline validé bout en bout : crash de test volontaire déclenché sur appareil physique, confirmé visible dans le dashboard Firebase Crashlytics du projet `ink-tone`.

**Validation** : `./gradlew assembleDebug` ✅, `testDebugUnitTest` ✅, permission absente du manifeste fusionné (vérifié), crash de test remonté dans Crashlytics (vérifié manuellement).

### Phase 5f — Plan Top-Tier, Phase 1 : Progression de lecture ✅ COMPLÉTÉE (2026-07-20)

**Branche** `main` — Phase 1 (le chantier le plus long et couplé du plan) exécutée dans l'ordre 1.1 → 1.8 depuis [`PLAN_ACTION_TOP_TIER_CLAUDECODE.md`](./PLAN_ACTION_TOP_TIER_CLAUDECODE.md). Conception préalable documentée dans [`architecture.md` §11](./architecture.md).

| # | Tâche | Statut | Priorité |
|---|---|---|---|
| 1.1 | Conception du schéma unifié de position de lecture (`architecture.md` §11) | ✅ Fait | 🔴 |
| 1.2 | Migration Room fusionnant `progress` + `reading_progress` (v15→16) | ✅ Fait | 🔴 |
| 1.2bis | Sous-problème découvert : trou de migration `6→13`, `fallbackToDestructiveMigrationFrom` restreint | ✅ Fait | 🟠 |
| 1.3 | Pondération réelle de la progression par longueur de chapitre (`TocEntry.charCount`) | ✅ Fait | 🔴 |
| 1.4 | Découplage de la sauvegarde de position du TTS (scroll manuel persisté) | ✅ Fait | 🔴 |
| 1.5 | Unification de `activeIdx` (TTS actif vs position restaurée) | ✅ Fait | 🔴 |
| 1.6 | Correction `startFrom = 0` codé en dur + `stop()` qui réinitialisait l'affichage | ✅ Fait | 🔴 |
| 1.7 | Décision `characterOffset` : réutilisé pour la pondération, pas un champ mort | ✅ Fait | 🟡 |
| 1.8 | Tests de non-régression (unitaires + intégration sur appareil réel) | ✅ Fait | 🔴 |

**Détail** :
- **Table unique `reading_progress` (v16)** : `bookId, chapterIndex, sentenceIndex, characterOffset, totalProgressFraction, updatedAt, source`. `progress`/`ProgressEntity`/`ProgressDao`/`Progress.kt` (domaine) supprimés — `BookRepository.saveProgress`/`getProgress` et `CalculateReadingProgressUseCase` retypés sur l'entité `ReadingProgress` directement (pas de nouveau wrapper domaine).
- **Migration `MIGRATION_15_16`** : fusionne les deux anciennes tables sans perte — position depuis l'ancienne `reading_progress` si présente, `totalProgressFraction` depuis l'ancienne `progress` si présente (les deux tables ne se recouvraient pas forcément). Testée contre une vraie base SQLite (pas de mock) sur appareil physique.
- **Course d'écriture éliminée** : avant la fusion, deux chemins indépendants (`PlaybackOrchestrator.saveProgressAsync` et `CalculateReadingProgressUseCase` via `ReaderViewModel`) auraient écrit sur la même ligne en `INSERT OR REPLACE` sans coordination. `PlaybackOrchestrator` calcule et persiste désormais lui-même `totalProgressFraction` (formule partagée `computeReadingProgressFraction`, `source=TTS`) ; `CalculateReadingProgressUseCase` ne sert plus que pour le chemin manuel (`source=MANUAL_SCROLL`), qui ne tourne jamais en même temps que le TTS.
- **Pondération réelle** : `TocEntry.charCount` peuplé gratuitement à l'import (`combinedHtml.length`, déjà en mémoire). Dégrade vers l'ancienne formule non pondérée pour les livres importés avant ce changement (`charCount` à 0), pas de blocage ni de ré-import forcé.
- **Scroll manuel persisté** : `ReaderContent` — flag `isProgrammaticScroll` autour de l'auto-scroll TTS, `snapshotFlow` débouncé (500 ms) sur `firstVisibleItemIndex`/`pagerState.currentPage` → `ReaderViewModel.onManualPositionChanged()`, ignoré pendant le scroll programmatique ou la lecture TTS.
- **`activeIdx` unifié** : `if (isSpeaking) playbackState.activeSentenceIndex else currentSentenceIndex` — la position restaurée s'affiche désormais immédiatement à l'ouverture, sans attendre une interaction.
- **`startFrom`/`stop()`** : `play()` démarre à `currentSentenceIndex` (plus jamais 0 en dur) ; `stop()` ne réinitialise plus l'affichage.
- **Sous-problème découvert (1.2bis)** : trou de migration Room `6→13` (versions consommées pendant une période de churn pré-beta, dont deux commits divergents ayant tous deux bumpé vers la version 12 — pas de base installée réelle à cette période à reconstituer fidèlement). `fallbackToDestructiveMigration()` remplacé par `fallbackToDestructiveMigrationFrom(6..12)` (Room refuse qu'une version soit à la fois couverte par une `Migration` explicite et listée en fallback — 1 à 5 ont déjà leur propre migration) : seul ce trou historique tolère un fallback destructif, tout futur trou de migration fera planter l'app au lieu d'effacer silencieusement la base d'un testeur.

**Tests ajoutés** : `CalculateReadingProgressUseCaseTest` (pondération sur chapitres inégaux, dégradation gracieuse), `InkToneDatabaseMigrationTest` (migration 15→16 sur SQLite réel, instrumenté), `ReadingProgressIntegrationTest` (scénario complet ouverture → scroll manuel sans audio → simulation de fermeture de process → réouverture → position restaurée, instrumenté), garde-fous régression sur `startFrom`/`stop()` dans `ReaderViewModelTest` — vérifiés capables de détecter la régression (bug réintroduit temporairement, suite rouge, puis reverti).

**Validation** : `./gradlew assembleDebug` ✅, `testDebugUnitTest` ✅ (130 tests, 0 échec), `connectedDebugAndroidTest` ✅ (3 tests instrumentés sur appareil physique V2206, 0 échec).

### 🔄 Historique TTS : Kokoro → Piper

Le modèle **Kokoro int8 multi-langue** (150 Mo, 53 locuteurs) a été testé puis abandonné :
- Crashs natifs OOM/SIGSEGV sur Snapdragon 680 (4 Go RAM)
- RTF ~3-4, instable
- Voix `ff_siwis` (sid=30) causait des crashs

**Retour à Piper VITS** avec `fr_FR-upmc-medium` (73 Mo, officiel sherpa-onnx model zoo 2023) :
- RTF ~0.33, stable, pas de crash
- 2 locuteurs : Jessica ♀ (sid=0, défaut) + Pierre ♂ (sid=1)
- Miro supprimé définitivement (25c66dc)
- Log SID dans synthesize() pour diagnostic

---

## 🚧 Bloqueurs Actifs

| # | Bloqueur | Impact | Date identifié | Résolution |
|---|---|---|---|---|
| — | Aucun bloquant actif | — | — | — |

---

## 🔮 Risques

| # | Risque | Probabilité | Impact | Mitigation |
|---|---|---|---|---|
| R1 | Sherpa-ONNX timestamps non fiables en français | Faible | Critique | ✅ Résolu — alignement phrase par phrase OK |
| R2 | RTF > 1.0 sur chipsets milieu de gamme | Faible | Majeur | ✅ Résolu — Piper UPMC RTF ~0.33 sur SD680 |
| R3 | Readium échoue sur certains EPUB3 complexes | Moyenne | Majeur | À tester avec plus d'EPUBs |
| R4 | Phonémisation FR de mauvaise qualité | Faible | Majeur | ✅ Résolu — eSpeak-NG + Piper OK |
| R5 | Kokoro OOM/SIGSEGV sur 4 Go RAM | — | — | ✅ Résolu — Retour à Piper VITS UPMC (73 Mo) |

---

## 📊 Métriques

| Métrique | Cible | Actuel |
|---|---|---|
| RTF (Real-Time Factor) | < 1.0 | **~0.33** (Piper UPMC) |
| Temps synthèse par phrase | < 500ms | ~100-300ms |
| Gap inter-phrases | 0ms | 0ms (gapless) |
| Taille APK (debug) | < 200 Mo | **120 Mo** |
| Taille modèle ONNX | < 80 Mo | **73 Mo** (Piper UPMC) |
| Tests unitaires | > 70% (domain) | **111 tests** (0 échec) |

---

## 📝 Notes de Session

### 2026-07-19 (branche `ui-ux-audit-phase1`)
- **Audit UI/UX Phase 1** : 10 tâches exécutées depuis `PLAN_ACTION_UXUI_CLAUDECODE.md` (voir Phase 5c ci-dessus et CHANGELOG.md)
- **Cohérence thème** : plus aucune couleur hardcodée dans les composables `ui/**` (vérifié par grep) — tout passe par `MaterialTheme.colorScheme.*`
- **`ModalNavigationDrawer`** : le drawer de `LibraryScreen` gère désormais back gesture, predictive back et TalkBack nativement
- **Coil** : couvertures de livres chargées de façon asynchrone (remplace un `BitmapFactory.decodeFile()` bloquant)
- **Accessibilité** : `strings.xml` complété, semantics ajoutées sur les Canvas de `StatsScreen`
- **Reader** : `UnifiedControlPanel` restructuré + barre de progression avec ETA (temps de lecture restant estimé)
- **Build validé** : `./gradlew assembleDebug` ✅ après chaque tâche
- **Prochaine étape :** revue de la branche → merge → Beta fermée → Play Store

### 2026-07-19
- **Suppression Miro** : modèle 61MB retiré, fallbacks nettoyés (25c66dc)
- **UPMC officiel** : modèle sherpa-onnx model zoo (73MB, PyTorch 1.13, IR v9) compatible ONNX RT 1.27
- **2 locuteurs** : Jessica ♀ (sid=0) + Pierre ♂ (sid=1)
- **Fix voix Pierre** : `JESSICA` avant `MIRO` dans l'enum → `resolveVoiceId(0)` trouve bien `"jessica"`
- **Log SID** : `synthesize()` trace `sid=$safeSid` pour diagnostic
- **Sync Settings→Reader** : la voix sélectionnée dans Settings est propagée au `ReaderViewModel.loadBook()`
- **OnnxInferenceService** : -91 lignes nettes, plus de `loadedModelDir`, crash guard, ni fallback
- **Prochaine étape :** Documentation → Beta fermée → Play Store

### 2026-07-18
- **Audit professionnel complété** : 17 bugs/optimisations corrigés (3 critiques, 6 hautes, 3 moyennes, 5 optimisations)
- **110 tests unitaires** : 0 échec, couverture des classes critiques (PlaybackOrchestrator, OnnxInferenceService, ReaderViewModel)
- **FrenchSentenceSplitter** : 10/10 tests corrigés (abréviations M., Dr., etc., initiales, guillemets)
- **Cold Start** : corrections confirmées (Dispatchers.IO déjà en place) + baseline-prof.txt
- **CI/CD** : workflow GitHub Actions (lint + test + assemble)
- **Documentation** : CHANGELOG, PROJECT_STATUS, Plan_d_action, COLD_START_AUDIT mis à jour

### 2026-07-07
- Architecture finalisée et auditée
- Corrections critiques : Sherpa-ONNX, AudioTrack, MediaSessionService, phonémisation FR
- Documents créés : `architecture.md`, `PROJECT_STATUS.md`, `README.md`
- **Prochaine étape :** Scaffold projet Android + prototype Sherpa-ONNX

---

## 📂 Structure des Documents

```
InkTone/
├── 📄 README.md                 # Présentation, badges, quickstart
├── 📄 ARCHITECTURE.md           # Spécifications techniques (→ architecture.md)
├── 📄 PROJECT_STATUS.md         # Ce fichier — suivi d'avancement
├── 📄 CHANGELOG.md              # Versions et changements
├── 📄 CONTRIBUTING.md           # Guide de contribution
├── 📄 .gitignore                # Règles Git
├── 📁 docs/
│   ├── 📄 prototype-report.md   # Rapport de prototype Sherpa-ONNX
│   └── 📄 screenshots/          # Captures d'écran
└── 📁 app/                      # Code source Android
```

---

> **Légende :** 🔴 Critique & Bloquant · 🟡 Important · 🟢 Nice-to-have · ✅ Fait · ⬜ À faire · 🔄 En cours · ❌ Bloqué
