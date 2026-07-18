# 📊 InkTone — Suivi d'Avancement Projet

> Dernière mise à jour : 2026-07-18  
> Phase actuelle : **Phase 6 — Beta & Release**  
> Progression globale : **~95%**  
> Moteurs TTS : **Piper VITS `fr_FR-miro-high`** (local) + **Microsoft Edge TTS** (cloud, Vivienne & Henri)  
> Tests unitaires : **110 tests, 0 échec** (12 fichiers)  
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
| 2.13 | Test intégration : lecture TTS chapitre complet | ✅ Fait | 🔴 | Validé — Piper Miro, RTF ~0.24 |

### Phase 3 — UI & Intégration ✅ COMPLÉTÉE (Objectif : Semaines 11-16)

| # | Tâche | Statut | Priorité | Notes |
|---|---|---|---|---|
| 3.1 | `LibraryScreen` — Import EPUB via SAF + grille | ✅ Fait | 🔴 | Grid 3 colonnes, covers, filtres, tri |
| 3.2 | Table des matières navigable (NCX/NAV) | ✅ Fait | 🔴 | Drawer TOC dans ReaderScreen |
| 3.3 | `ReaderScreen` — Rendu texte immersif | ✅ Fait | 🔴 | Overlay, center-third tap, thèmes |
| 3.4 | Surlignage synchronisé phrase par phrase | ✅ Fait | 🔴 | Scroll automatique sur lecture |
| 3.5 | `UnifiedControlPanel` — Play, Pause, Next/Prev | ✅ Fait | 🔴 | 2 rangées, speed slider, voice chips |
| 3.6 | Indicateur de progression | ✅ Fait | 🟡 | Micro-indicateur % + chapitre |
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
| 5.1 | Profilage CPU/Batterie (Android Profiler) | ✅ Fait | 🔴 | RTF ~0.24, RAM ~150 Mo, APK 120 Mo debug / 106 Mo release |
| 5.2 | FTS5 `sentence_fts` — recherche in-book | ✅ Fait | 🔴 | FTS4 virtual table, SearchScreen avec wildcard MATCH |
| 5.3 | Process Death : `SavedStateHandle` + restoration | ✅ Fait | 🔴 | Sauvegarde chapter/sentence/voice/theme/font |
| 5.4 | Gestion EPUB corrompus / erreurs parsing | ✅ Fait | 🟡 | try/catch avec messages explicites + nettoyage fichiers |
| 5.5 | SAF : persistence permissions + réimport | ✅ Fait | 🟡 | takePersistableUriPermission() dans importEpub() |
| 5.6 | ProGuard/R8 config (ONNX + Sherpa + Readium) | ✅ Fait | 🔴 | Règles complètes — build release OK (106 Mo) |
| 5.7 | Backup/Restore données (bookmarks, progrès) | ✅ Fait | 🟡 | Export JSON via SAF, import avec restauration complète |
| 5.8 | Accessibilité : TalkBack, tailles min/max | ✅ Fait | 🟡 | contentDescription sur contrôles critiques + OpenDyslexic |
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

### 🔄 Historique TTS : Kokoro → Piper

Le modèle **Kokoro int8 multi-langue** (150 Mo, 53 locuteurs) a été testé puis abandonné :
- Crashs natifs OOM/SIGSEGV sur Snapdragon 680 (4 Go RAM)
- RTF ~3-4, instable
- Voix `ff_siwis` (sid=30) causait des crashs

**Retour à Piper VITS** avec `fr_FR-miro-high` (61 Mo) :
- RTF ~0.24, stable, pas de crash
- Voix masculine FR, volume corrigé (gain 1.8x)
- Nettoyage ponctuation multiples avant synthèse

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
| R2 | RTF > 1.0 sur chipsets milieu de gamme | Faible | Majeur | ✅ Résolu — Piper Miro RTF ~0.24 sur SD680 |
| R3 | Readium échoue sur certains EPUB3 complexes | Moyenne | Majeur | À tester avec plus d'EPUBs |
| R4 | Phonémisation FR de mauvaise qualité | Faible | Majeur | ✅ Résolu — eSpeak-NG + Piper OK |
| R5 | Kokoro OOM/SIGSEGV sur 4 Go RAM | — | — | ✅ Résolu — Retour à Piper VITS (61 Mo) |

---

## 📊 Métriques

| Métrique | Cible | Actuel |
|---|---|---|
| RTF (Real-Time Factor) | < 1.0 | **~0.24** (Piper Miro) |
| Temps synthèse par phrase | < 500ms | ~100-300ms |
| Gap inter-phrases | 0ms | 0ms (gapless) |
| Taille APK (debug) | < 200 Mo | **120 Mo** |
| Taille modèle ONNX | < 80 Mo | **61 Mo** (Piper Miro) |
| Tests unitaires | > 70% (domain) | **43 tests** (0 échec) |

---

## 📝 Notes de Session

### 2026-07-18
- **Audit professionnel complété** : 17 bugs/optimisations corrigés (3 critiques, 6 hautes, 3 moyennes, 5 optimisations)
- **43 tests unitaires** : 0 échec, couverture des classes critiques (PlaybackOrchestrator, OnnxInferenceService, ReaderViewModel)
- **FrenchSentenceSplitter** : 10/10 tests corrigés (abréviations M., Dr., etc., initiales, guillemets)
- **Cold Start** : corrections confirmées (Dispatchers.IO déjà en place) + baseline-prof.txt
- **CI/CD** : workflow GitHub Actions (lint + test + assemble)
- **Documentation** : CHANGELOG, PROJECT_STATUS, Plan_d_action, COLD_START_AUDIT mis à jour
- **Prochaine étape :** Beta fermée 10-20 lecteurs → Publication Play Store

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
