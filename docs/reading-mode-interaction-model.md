# Reading Mode Interaction Model — InkTone

Document de référence pour l'expérience de lecture. Définit les gestes, états et affordances.

---

## États du mode lecture

| État | Description | UI visible |
|------|-------------|------------|
| **Repos** | Aucune lecture TTS en cours | Micro-indicateur de progression (XX.X%) en bas |
| **En écoute** | TTS actif, surlignage animé | Micro-indicateur + surlignage phrase active |
| **En contrôle** | HUD affiché (tap centre) | TopBar + UnifiedControlPanel + micro-indicateur |
| **Sélection** | Texte sélectionné (long press) | Barre d'actions : Copier, Surligner, Note, Signet |

---

## Gestes

### Mode Paginé (PAGED)

| Geste | Zone | Action |
|-------|------|--------|
| Tap | Tiers gauche | Page précédente |
| Tap | Tiers centre | Toggle HUD (afficher/masquer contrôles) |
| Tap | Tiers droit | Page suivante |
| Long press | Partout | Sélection de texte → barre d'actions |
| Swipe vertical | Bords écran | Barres système (transient) |

### Mode Défilement (SCROLL)

| Geste | Zone | Action |
|-------|------|--------|
| Tap | Partout | Toggle HUD |
| Long press | Partout | Sélection de texte → barre d'actions |
| Scroll vertical | Partout | Défilement du texte |
| Swipe vertical | Bords écran | Barres système (transient) |

### Contrôles HUD (quand visible)

| Contrôle | Position | Action |
|----------|----------|--------|
| ⏮ Phrase préc. | UnifiedControlPanel, rangée 1 | Phrase précédente (seekToPrevious) |
| ▶ Lecture/Pause | UnifiedControlPanel, rangée 1 | Démarrer/arrêter TTS |
| ⏭ Phrase suiv. | UnifiedControlPanel, rangée 1 | Phrase suivante (seekToNext) |
| 🎧 Options TTS | UnifiedControlPanel, rangée 1 | Ouvrir ModalBottomSheet TTS |
| 🎨 Thème | UnifiedControlPanel, rangée 1 | Cycle NIGHT → DAY → SEPIA |
| D | UnifiedControlPanel, rangée 1 | Toggle OpenDyslexic |
| Aa | UnifiedControlPanel, rangée 1 | Ouvrir ModalBottomSheet affichage |
| ◀ Chapitre préc. | UnifiedControlPanel, rangée 2 | Chapitre précédent |
| Chapitre suiv. ▶ | UnifiedControlPanel, rangée 2 | Chapitre suivant |
| ← Retour | TopBar, gauche | Retour bibliothèque |
| Titre/Chapitre | TopBar, centre | Information |
| 📄 Mode | TopBar, droite | Toggle paginé ↔ défilement |
| 🔍 Rechercher | TopBar, droite | Recherche dans le livre |
| 🔖 Signets | TopBar, droite | Liste des signets |
| 📋 TOC | TopBar, droite | Table des matières |

---

## Affordances visuelles

### Surlignage de lecture
- **Phrase active** : fond `accentColor` à 12% alpha, coins arrondis 4dp
- **Mot actif** : couleur `accentColor` + `FontWeight.Bold`, tracking à 60fps
- **Phrases lues** : texte grisé à 40% alpha (reading trail)
- **Phrases à venir** : texte normal à 88% alpha

### Progression
- **Micro-indicateur** : "%" en bas centre, `textColor` à 40% alpha, 12sp
- **HUD auto-hide** : disparaît après 4 secondes d'inactivité
- **HUD toggle** : tap centre tiers → affiche/masque

### Feedback
- **Changement de thème** : transition instantanée (pas d'animation crossfade)
- **Toggle OpenDyslexic** : le "D" passe en `ttsActive` quand actif
- **Snackbar** : feedback pour actions (signet ajouté, etc.)

---

## Accessibilité (TalkBack)

- Tous les `IconButton` ont un `contentDescription` explicite
- Les sliders annoncent leur valeur : "Taille de police, 18 points"
- Les sélecteurs de thème/police ont `semantics { role = Button }`
- Réduction de mouvement : si `prefersReducedMotion`, durée d'animation = 0

---

## Références

- `ReaderScreen.kt` — Orchestration des états et overlays
- `ReaderContent.kt` — Rendu du texte, surlignage, pagination
- `ReaderTopBar.kt` — Barre supérieure
- `ReaderBottomControls.kt` — UnifiedControlPanel
- `ReaderTtsPanel.kt` — ModalBottomSheet TTS
- `ReaderSettingsPanel.kt` — ModalBottomSheet affichage
