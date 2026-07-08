# 📊 ReadFlow — Suivi d'Avancement Projet

> Dernière mise à jour : 2026-07-08  
> Phase actuelle : **Phase 2 — Parsing EPUB & Pipeline Audio**  
> Progression globale : **20%**

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
| 2.1 | Intégration Readium Kotlin Toolkit | ⬜ À faire | 🔴 | EPUB2 + EPUB3 |
| 2.2 | `ParseEpubUseCase` + extraction metadata | ⬜ À faire | 🔴 | Titre, auteur, cover, TOC |
| 2.3 | `ChunkTextUseCase` — segmenteur phrases FR | ⬜ À faire | 🔴 | Règles NLP custom |
| 2.4 | `FrenchSentenceSplitter` + tests unitaires | ⬜ À faire | 🔴 | Abréviations, dialogues, ellipses |
| 2.5 | `PhonemizationPipeline` (texte → phonèmes) | ⬜ À faire | 🔴 | Intégration NDK |
| 2.6 | `OnnxInferenceService` (bridge JNI) | ⬜ À faire | 🔴 | Thread dédié |
| 2.7 | `PlaybackOrchestrator` (buffer +3, async) | ⬜ À faire | 🔴 | Coroutines Flow |
| 2.8 | `GaplessAudioPlayer` (AudioTrack) | ⬜ À faire | 🔴 | Buffer circulaire PCM |
| 2.9 | `AudioPlaybackService` (MediaSessionService) | ⬜ À faire | 🔴 | Media3 |
| 2.10 | `MediaSessionConnector` + `AudioFocusManager` | ⬜ À faire | 🔴 | Notif, lockscreen, BT |
| 2.11 | `AudioCacheManager` (LRU, eviction, purge) | ⬜ À faire | 🟡 | 30 Mo / 10 min |
| 2.12 | `SynthesisResult` sealed class + error handling | ⬜ À faire | 🟡 | Skip après 3 échecs |
| 2.13 | Test intégration : lecture TTS chapitre complet | ⬜ À faire | 🔴 | Livrable clé |

### Phase 3 — UI & Intégration (Objectif : Semaines 11-16)

| # | Tâche | Statut | Priorité | Notes |
|---|---|---|---|---|
| 3.1 | `LibraryScreen` — Import EPUB via SAF | ⬜ À faire | 🔴 | Grid/list, covers, progression |
| 3.2 | Table des matières navigable (NCX/NAV) | ⬜ À faire | 🔴 | — |
| 3.3 | `ReaderScreen` — Rendu texte paginé | ⬜ À faire | 🔴 | Typographie, thèmes |
| 3.4 | `HighlightedText` — Surlignage synchronisé | ⬜ À faire | 🔴 | AnnotatedString + SpanStyle |
| 3.5 | `MediaControlBar` — Play, Pause, Seek, Next/Prev | ⬜ À faire | 🔴 | — |
| 3.6 | `ChapterProgressBar` — Progression chapitre | ⬜ À faire | 🟡 | — |
| 3.7 | `SettingsScreen` — Vitesse, voix, typographie | ⬜ À faire | 🟡 | — |
| 3.8 | `BookmarkScreen` — Gestion signets | ⬜ À faire | 🟡 | — |
| 3.9 | Thèmes : clair, sépia, sombre | ⬜ À faire | 🟢 | Material 3 |
| 3.10 | Police OpenDyslexic | ⬜ À faire | 🟢 | — |
| 3.11 | Tests UI + capture screenshots | ⬜ À faire | 🟡 | — |

### Phase 4 — Optimisation, Robustesse & Release (Objectif : Semaines 17-20)

| # | Tâche | Statut | Priorité | Notes |
|---|---|---|---|---|
| 4.1 | Profilage CPU/Batterie (Android Profiler) | ⬜ À faire | 🔴 | Optimiser buffer |
| 4.2 | FTS5 `chapter_content_fts` — recherche in-book | ⬜ À faire | 🔴 | — |
| 4.3 | Process Death : `SavedStateHandle` + restoration | ⬜ À faire | 🔴 | — |
| 4.4 | Gestion EPUB corrompus / erreurs parsing | ⬜ À faire | 🟡 | Graceful degradation |
| 4.5 | SAF : persistence permissions + réimport | ⬜ À faire | 🟡 | — |
| 4.6 | ProGuard/R8 config (ONNX + Sherpa) | ⬜ À faire | 🔴 | Sinon crash release |
| 4.7 | Backup/Restore données (bookmarks, progrès) | ⬜ À faire | 🟡 | — |
| 4.8 | Accessibilité : TalkBack, tailles min/max | ⬜ À faire | 🟡 | Obligatoire Play Store |
| 4.9 | Vérification licences (Sherpa, Readium, ONNX) | ⬜ À faire | 🟡 | Conformité légale |
| 4.10 | Build signed APK / AAB release | ⬜ À faire | 🔴 | — |
| 4.11 | Beta fermée — 10-20 lecteurs francophones | ⬜ À faire | 🔴 | Feedback UX |
| 4.12 | Publication Play Store (internal testing) | ⬜ À faire | 🔴 | — |

---

## 🚧 Bloqueurs Actifs

| # | Bloqueur | Impact | Date identifié | Résolution |
|---|---|---|---|---|
| — | Aucun pour le moment | — | — | — |

---

## 🔮 Risques

| # | Risque | Probabilité | Impact | Mitigation |
|---|---|---|---|---|
| R1 | Sherpa-ONNX timestamps non fiables en français | Moyenne | Critique | Prototype early (Phase 0), fallback Piper |
| R2 | RTF > 1.0 sur chipsets milieu de gamme (MediaTek) | Élevée | Majeur | Tester tôt, ajuster modèle/buffer |
| R3 | Readium échoue sur certains EPUB3 complexes | Moyenne | Majeur | Tester 5+ EPUBs variés en Phase 2 |
| R4 | Phonémisation FR de mauvaise qualité | Moyenne | Majeur | Valider avec un natif francophone |
| R5 | AudioTrack buffer underrun sur devices lents | Faible | Mineur | Buffer adaptatif, fallback silence 50ms |
| R6 | Process death perd l'état de lecture | Faible | Mineur | `SavedStateHandle` + Room |

---

## 📊 Métriques

| Métrique | Cible | Actuel |
|---|---|---|
| RTF (Real-Time Factor) | < 1.0 | — |
| Temps synthèse par phrase | < 500ms | — |
| Gap inter-phrases | 0ms | — |
| Taille APK (hors modèle) | < 15 Mo | — |
| Taille modèle ONNX | < 80 Mo | — |
| Utilisation RAM au runtime | < 200 Mo | — |
| Recomposition UI (surlignage) | < 16ms (60 FPS) | — |
| Couverture de tests unitaires | > 70% (domain layer) | — |

---

## 📝 Notes de Session

### 2026-07-07
- Architecture finalisée et auditée
- Corrections critiques : Sherpa-ONNX, AudioTrack, MediaSessionService, phonémisation FR
- Documents créés : `architecture.md`, `PROJECT_STATUS.md`, `README.md`
- **Prochaine étape :** Scaffold projet Android + prototype Sherpa-ONNX

---

## 📂 Structure des Documents

```
ReadFlow/
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
