# 📝 Changelog — ReadFlow

Toutes les modifications notables de ce projet sont documentées dans ce fichier.

Le format est basé sur [Keep a Changelog](https://keepachangelog.com/fr/1.0.0/),
et ce projet adhère au [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

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
