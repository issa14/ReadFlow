# 📝 Changelog — ReadFlow

Toutes les modifications notables de ce projet sont documentées dans ce fichier.

Le format est basé sur [Keep a Changelog](https://keepachangelog.com/fr/1.0.0/),
et ce projet adhère au [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Added
- Architecture technique complète (`architecture.md`)
- Suivi de projet (`PROJECT_STATUS.md`)
- Présentation projet (`README.md`)
- Configuration Git (`.gitignore`)
- Phase 0 : Préparation du projet

### 2026-07-08 — Scaffold & Build initial
- **Scaffold projet Android** : Gradle, Hilt, Room, Compose Navigation, Media3, ONNX Runtime
- **Version Catalog** (`libs.versions.toml`) avec toutes les dépendances
- **Gradle Wrapper** généré (Gradle 8.9)
- **Android SDK** configuré (API 34 + 35, Build-Tools 35.0.0)
- **Premier build réussi** : `app-debug.apk` (38 Mo)
- Corrections : downgrade `compileSdk` 35 → 34 (compatibilité AGP 8.5.2), ONNX Runtime 1.18.1 → 1.19.2
- `local.properties` créé avec chemin SDK

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
