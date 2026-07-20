# 🎯 InkTone — Plan d'action Top-Tier (pour exécution par Claude Code)

> **Date** : 2026-07-20
> **Source** : [`AUDIT_INDEPENDANT_UX_PERFORMANCE_2026-07-20.md`](./AUDIT_INDEPENDANT_UX_PERFORMANCE_2026-07-20.md) — audit indépendant basé sur le code réel, commit `567d836`
> **Objectif** : amener InkTone à un niveau comparable au top 3 des lecteurs ebook (Kindle, Apple Books, Kobo, Moon+ Reader Pro), pas juste "app qui marche".
> **Principe directeur** : chaque tâche vise la cause racine, pas le symptôme. Si une correction ressemble à un contournement (try/catch qui masque, valeur codée en dur qui remplace une autre valeur codée en dur, flag ad-hoc), c'est qu'elle n'est pas terminée.

---

## Comment utiliser ce document

- Une tâche = une unité de travail cohérente, normalement une branche + un commit (ou une série de commits atomiques) + une validation.
- **Ne pas paralléliser les tâches d'une même phase entre elles si une dépendance est indiquée.** Les phases 0 à 2 sont séquentielles entre elles ; à l'intérieur d'une phase, l'ordre des tâches numérotées est l'ordre d'exécution recommandé.
- Chaque tâche a une case "Validation" : elle doit être vérifiée avant de passer à la suivante. Au minimum `./gradlew assembleDebug`, `./gradlew testDebugUnitTest`, `./gradlew lint` — comme déjà pratiqué sur ce dépôt.
- Après chaque phase complète : mettre à jour `PROJECT_STATUS.md` et `CHANGELOG.md` avec le détail réel des changements (pas juste "✅ Fait" — le format actuel du dépôt, avec la note technique, est le bon).
- Si une tâche révèle un sous-problème non listé ici, l'ajouter à ce document plutôt que de le corriger silencieusement en marge — on veut garder une trace complète, c'est tout l'objet de cette démarche.

---

## Vue d'ensemble des phases

| Phase | Thème | Sévérité dominante | Bloque la beta ? |
|---|---|---|---|
| 0 | Conformité store & exploitation | 🔴 | Oui — sans ça, publier est risqué ou aveugle |
| 1 | Progression de lecture (reprise exacte) | 🔴 | Oui — c'est la promesse de base d'un lecteur ebook |
| 2 | Performance de l'import EPUB | 🔴 | Oui pour l'expérience de première utilisation |
| 3 | Adaptation à l'écran | 🔴 | Oui pour la crédibilité "premium" |
| 4 | Bibliothèque à l'échelle | 🟠 | Non, mais dette qui grossit avec l'usage réel |
| 5 | Table des matières | 🟡 | Non |
| 6 | Robustesse & qualité (tests, DRM) | 🟠 | Partiellement — sans tests d'intégration, les régressions futures repasseront inaperçues |
| 7 | Synchronisation automatique | 🟡 | Non — améliore l'expérience, pas bloquant |
| 8 | Honnêteté produit (surlignage mot-à-mot) | 🟡 | Non, mais impacte la confiance des premiers testeurs |

---

## PHASE 0 — Conformité store & exploitation (avant tout le reste)

Coût faible, risque d'ignorer disproportionné. À faire en premier, avant même la Phase 1, parce que ces deux tâches conditionnent la capacité à publier et à savoir ce qui casse chez les testeurs.

### 0.1 — 🔴 Supprimer `MANAGE_EXTERNAL_STORAGE` et l'écran associé

**Problème** : `FilesScreen.kt` (accessible depuis `LibraryScreen.kt`) demande la permission "accès à tous les fichiers", redondante avec le SAF déjà utilisé ailleurs pour l'import. Risque de rejet/suspension Play Store.

**À faire** :
- Supprimer `app/src/main/java/com/inktone/ui/screen/library/FilesScreen.kt` entièrement (pas juste retirer son point d'entrée dans la navigation).
- Retirer `<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />` de `AndroidManifest.xml`.
- Retirer toute référence de navigation vers cet écran dans `LibraryScreen.kt` / le nav graph.
- Vérifier qu'aucun autre chemin du code ne dépend de `Environment.isExternalStorageManager()`.
- Si un besoin réel de "parcourir tous les fichiers" existe (au-delà de l'import SAF standard), le refaire via `ACTION_OPEN_DOCUMENT`/`ACTION_OPEN_DOCUMENT_TREE` (SAF), pas via cette permission.

**Validation** : build OK, permission absente du manifeste final (vérifier avec `./gradlew :app:dumpManifest` ou inspection de l'APK), import de livres toujours fonctionnel via le flux SAF existant.

---

### 0.2 — 🔴 Intégrer un système de crash reporting

**Problème** : aucun SDK de télémétrie de crash dans le dépôt (vérifié : Crashlytics, Sentry, Bugsnag, ACRA — aucun présent). Une beta sans ça ne remonte rien d'exploitable.

**À faire** :
- Ajouter Firebase Crashlytics (choix par défaut recommandé, écosystème Android standard, gratuit) : dépendance Gradle, plugin, `google-services.json` (nécessite un projet Firebase — à créer si pas déjà fait), règles ProGuard/R8 dédiées.
- Initialiser dans `InkToneApplication.onCreate()`, en cohérence avec le style déjà minimaliste de cette classe.
- Ajouter un identifiant de version/commit dans les logs de crash pour pouvoir recouper avec les commits (utile vu le rythme de développement).
- Configurer la capture des exceptions non-fatales pertinentes déjà loggées en `Log.e()` dans les zones critiques (`PlaybackOrchestrator`, `BookRepositoryImpl.importEpub`, `OnnxInferenceService`) — actuellement beaucoup d'erreurs sont juste loggées localement (`Log.e(...)`) sans jamais remonter nulle part en dehors de l'appareil du testeur.
- Vérifier avec un crash de test volontaire en debug que le pipeline fonctionne bout en bout avant de considérer la tâche terminée.

**Validation** : un crash provoqué manuellement en debug apparaît dans le dashboard Crashlytics dans les minutes qui suivent.

---

## PHASE 1 — Progression de lecture (le chantier central)

**Dépend de** : rien (peut démarrer juste après la Phase 0). **Bloque** : Phase 3.6 (pagination robuste à la rotation) et Phase 6.2 (test d'intégration) en dépendent partiellement.

### 1.1 — 🔴 Concevoir le schéma unifié de position de lecture

**À faire avant d'écrire du code** : documenter dans `architecture.md` la nouvelle source de vérité unique :
- Une seule table (garder `reading_progress` comme base, elle a le meilleur design) avec les colonnes : `bookId`, `chapterIndex`, `sentenceIndex`, `characterOffset`, `totalProgressFraction`, `updatedAt`, `source` (enum : `TTS` / `MANUAL_SCROLL` — utile pour debug et pour ne pas laisser une position TTS obsolète écraser une lecture manuelle plus récente, ou l'inverse).
- Stocker à l'import (voir 1.3) les métadonnées de longueur par chapitre nécessaires au calcul de progression pondérée.
- Ce document de conception sert de référence pour 1.2 à 1.8 — ne pas coder avant de l'avoir écrit, pour éviter de re-découvrir les mêmes questions à chaque sous-tâche.

**Validation** : section `architecture.md` relue et cohérente avant de passer à 1.2.

---

### 1.2 — 🔴 Migration Room : fusionner `progress` et `reading_progress`

**À faire** :
- Bump de version Room, `Migration` explicite (pas de `fallbackToDestructiveMigration`) qui :
  - Pour chaque `bookId` présent dans l'une ou l'autre table, garde la ligne avec le `updatedAt` le plus récent.
  - Recalcule `totalProgressFraction` avec la nouvelle formule pondérée (dépend de 1.3, donc cette étape peut nécessiter un recalcul différé au premier lancement post-migration plutôt qu'en pur SQL).
  - Supprime les anciennes tables `progress` et l'ancien schéma `reading_progress` au profit du schéma unifié.
- Supprimer `ProgressEntity.kt`, `ProgressDao.kt`, et toutes leurs références (`CalculateReadingProgressUseCase` doit lire/écrire uniquement la nouvelle table).
- Mettre à jour `BackupManager.kt` et `SyncManager.kt` (`BackupPayload.kt`) pour le nouveau schéma — **important** : ne pas casser le format de sauvegarde existant sans plan de compatibilité ascendante si des testeurs ont déjà des exports.

**Validation** : test de migration Room dédié (Room fournit `MigrationTestHelper`) qui vérifie qu'une base avec des données dans les deux anciennes tables migre correctement sans perte.

---

### 1.2bis — 🟠 Sous-problème découvert : trou de migration Room `6→13`

**Découvert pendant la conception du schéma unifié (tâche 1.1)**, hors périmètre initial de cette tâche — noté ici plutôt que corrigé silencieusement en marge, conformément au principe directeur de ce document.

**Problème** : `InkToneDatabase.kt` est en version 15, mais seules les migrations `1→2`, `2→3`, `3→4`, `4→5`, `5→6`, `13→14`, `14→15` sont définies. Le chemin `6→13` n'a aucune `Migration` explicite. `AppModule.kt` configure `Room.databaseBuilder(...).fallbackToDestructiveMigration()`, ce qui signifie que toute base d'un testeur bloquée quelque part entre la version 6 et 13 (ou tout futur saut de version non couvert) est **silencieusement effacée** plutôt que migrée ou de faire planter le build en signalant le trou.

**À faire** :
- Déterminer si des versions 7 à 12 ont réellement existé en historique (`git log -p` sur `InkToneDatabase.kt`) pour reconstituer les migrations manquantes, ou si ces numéros de version ont été sautés sans changement de schéma réel (auquel cas des migrations `no-op` explicites suffisent).
- Ajouter les `Migration` manquantes en conséquence.
- Une fois le chemin `1→15` (puis `1→16` après 1.2) entièrement couvert par des migrations explicites, évaluer si `fallbackToDestructiveMigration()` doit être retiré complètement ou seulement restreint (ex. `fallbackToDestructiveMigrationOnDowngrade()` uniquement) — actuellement il masque ce genre de trou plutôt que de le signaler.

**Validation** : test de migration Room couvrant le chemin complet `1→16` sans passer par le fallback destructif ; suppression ou restriction justifiée de `fallbackToDestructiveMigration()`.

---

### 1.3 — 🔴 Pondération réelle de la progression par longueur de contenu

**Problème initial** : `(chapterIndex + sentenceIndex/totalSentences) / totalChapters` traite chaque chapitre comme équivalent, sans lien avec sa longueur réelle.

**À faire** :
- Dans `BookRepositoryImpl.importEpub()` (la boucle `for (i in 0 until totalChapters)`), calculer et stocker pour chaque chapitre son nombre de caractères (déjà disponible via `combinedHtml`/`sentences` à ce point du code), ainsi que le cumul avant chaque chapitre.
- Stocker ça soit dans `TocEntry` (ajout d'un champ), soit dans une table dédiée `chapter_metadata` — trancher selon ce qui perturbe le moins le modèle `Book`/`TocEntry` existant.
- Refactorer `CalculateReadingProgressUseCase` pour calculer `fraction = (cumulativeCharsBeforeChapter[chapterIndex] + charOffsetDansLeChapitre) / totalCaractèresLivre`.

**Validation** : test unitaire avec un livre fictif à chapitres très inégaux (ex. préface de 200 caractères, chapitre de 50 000 caractères) — vérifier que le % reflète la longueur réelle, pas juste la position dans la TOC. Mettre à jour `CalculateReadingProgressUseCaseTest.kt`.

---

### 1.4 — 🔴 Découpler la sauvegarde de position du TTS (lecture silencieuse)

**Problème** : la position n'est aujourd'hui persistée que via `orchestrator.playbackState.collect{}`, donc jamais pendant une lecture silencieuse (scroll sans audio).

**À faire** :
- Dans `ScrollContent` (`ReaderContent.kt`), ajouter un `snapshotFlow { lazyListState.firstVisibleItemIndex }` débouncé (~500ms), remonté vers `ReaderViewModel` via un callback dédié (ex. `onManualPositionChanged(sentenceIndex)`).
- Faire de même côté `PagedContent` avec l'index de page courant → sentence correspondante.
- Introduire un flag (`isProgrammaticScroll` ou équivalent) actif le temps de l'`animateScrollToItem` déclenché par la lecture TTS, pour ne pas que ce scroll auto-déclenché soit interprété comme une action manuelle de l'utilisateur (éviter la boucle de rétroaction).
- `ReaderViewModel` persiste cette position manuelle via le même chemin que `calculateProgress` (§1.3), avec `source = MANUAL_SCROLL`.

**Validation** : scénario manuel — ouvrir un livre, scroller sans jamais lancer l'audio, tuer l'app (pas juste mise en arrière-plan), rouvrir : la position doit être conservée. À terme, ce scénario doit être couvert par un test d'intégration (Phase 6.2).

---

### 1.5 — 🔴 Unifier la source de vérité affichée (`activeIdx`)

**Problème** : `ReaderContent.kt` utilise `playbackState.activeSentenceIndex` (qui vaut 0 par défaut) au lieu de la position restaurée par `ReaderViewModel`.

**À faire** :
- Remplacer la source unique `val activeIdx = playbackState.activeSentenceIndex` par une valeur dérivée : `if (isSpeaking) playbackState.activeSentenceIndex else viewModel.uiState.currentSentenceIndex`.
- S'assurer que cette valeur est correcte dès le premier rendu après `loadBook()`, sans attendre une interaction utilisateur.

**Validation** : ouvrir un livre à une position non-nulle (chapitre > 0, phrase > 0) sans presser Play — l'écran doit s'ouvrir scrollé/paginé exactement à cette position, immédiatement.

---

### 1.6 — 🔴 Corriger `startFrom = 0` codé en dur

**À faire** :
- Dans `ReaderViewModel.play()`, remplacer `startFrom = 0` par `startFrom = s.currentSentenceIndex`.
- Vérifier dans `PlaybackOrchestrator.play()` que la synthèse démarre bien à l'index fourni (buffer de synthèse initialisé à la bonne phrase, pas de décalage).
- Revoir `stop()` : `currentSentenceIndex = 0` remet l'affichage à zéro alors que la DB garde la bonne valeur — décider si `stop()` doit vraiment réinitialiser l'affichage local, ou seulement arrêter l'audio en conservant la position affichée (probablement la seconde option, plus cohérente avec l'expérience attendue).

**Validation** : rouvrir un livre à une position mi-chapitre, presser Play sans naviguer manuellement au préalable — la narration doit démarrer à la phrase affichée, pas au début du chapitre.

---

### 1.7 — 🟡 Réutiliser ou retirer proprement `characterOffset`

**À faire** : une fois 1.2-1.6 en place, décider explicitement :
- Soit l'utiliser pour un positionnement infra-phrase (utile si un mode de rendu plus fin que "phrase" est envisagé) — dans ce cas le brancher réellement dans `ResolveReadingPositionUseCase`.
- Soit le retirer du schéma si aucun usage n'est prévu à court terme, plutôt que de garder une donnée écrite mais jamais lue.

**Validation** : pas de champ "mort" (écrit sans jamais être lu) restant dans le schéma final.

---

### 1.8 — 🔴 Test de non-régression dédié à la reprise de lecture

**À faire** :
- Test d'intégration (voir aussi Phase 6.2) qui simule : ouverture → scroll manuel sans audio → simulation de fermeture de process → réouverture → assertion sur chapitre/phrase/scroll restaurés.
- Test unitaire complémentaire sur `ResolveReadingPositionUseCase` avec le nouveau schéma unifié.
- Mettre à jour `CalculateReadingProgressUseCaseTest.kt` pour la pondération par longueur (déjà mentionné en 1.3, à ne pas dupliquer si déjà fait).

**Validation** : `./gradlew testDebugUnitTest` + le nouveau test d'intégration passent, et échouent délibérément si on réintroduit `startFrom = 0` (vérifier que le test est bien capable de détecter la régression, pas juste de passer par hasard).

---

## PHASE 2 — Performance de l'import EPUB

**Dépend de** : rien, peut être fait en parallèle de la Phase 1 par un autre flux de travail si besoin, mais pas en même temps que 1.2 (les deux touchent `BookRepositoryImpl.kt`).

### 2.1 — 🔴 Ouvrir le ZIP une seule fois par import

**Problème** : `extractRawHtml()` et `extractAndSaveImage()` rouvrent `ZipFile(epubFile)` et scannent linéairement toutes les entrées à chaque appel — potentiellement 70-100+ ouvertures/scans redondants par import.

**À faire** :
- Ouvrir un unique `ZipFile` en tête de `importEpub()`.
- Construire une fois une `Map<String, ZipEntry>` indexée (clé normalisée par chemin) à partir de `zip.entries()`.
- Faire transiter cette map dans `extractRawHtml()`, `extractAndSaveImage()`, `extractCoverHeuristic()` au lieu de rouvrir/rescanner l'archive.
- Fermer le `ZipFile` proprement en fin d'import (`use {}` englobant toute la fonction, ou fermeture explicite en fin de traitement).

**Validation** : benchmark avant/après sur un livre de référence illustré (au moins 20 chapitres, 15+ images) — mesurer le temps d'import total et documenter le gain dans `CHANGELOG.md`.

---

### 2.2 — 🟠 Paralléliser le traitement des chapitres

**À faire** :
- Rendre l'indexation des blocs riches indépendante du compteur global partagé entre chapitres (actuellement `richBlocks.size` sert d'offset cumulatif dans la boucle séquentielle — à revoir pour un calcul local par chapitre, recombiné après coup).
- Traiter les chapitres avec une concurrence limitée : `Dispatchers.IO.limitedParallelism(4)` (ou valeur ajustée après mesure) + `async`/`awaitAll`.
- Conserver un `onProgress` cohérent malgré le traitement parallèle (agrégation des progressions individuelles plutôt qu'un simple `i/totalChapters` séquentiel).

**Validation** : même benchmark que 2.1, sur le même livre de référence, cumulé avec le gain de 2.1.

---

### 2.2bis — 🟠 Sous-problème découvert : hrefs de TOC avec ancres percent-encodées non résolues dans le spine

**Découvert pendant la validation sur appareil de la tâche 2.2** (en testant l'import de *Anna Karénine*, un EPUB probablement exporté par Calibre), hors périmètre initial — noté ici plutôt que corrigé silencieusement en marge.

**Problème** : `SpineIndex.normalizeHref()` (`app/src/main/java/com/inktone/data/epub/SpineIndex.kt`) ne supprime que l'ancre après un caractère `#` littéral (`href.substringBefore("#")`). Sur ce livre, les hrefs de TOC contiennent leurs ancres percent-encodées (`%23` au lieu de `#`, ex. `content/....html%23cfs_3`), donc la normalisation ne les détecte pas et conserve tout le suffixe encodé collé au nom de fichier — qui ne correspond alors plus à aucune entrée du spine. Résultat observé : **les 25 entrées de la table des matières du livre échouent à se résoudre** (`"TOC '...' non trouvé dans le spine — ignoré"` pour chacune), et le livre s'importe avec zéro contenu réel mis en cache. À la lecture, `getChapter()` échoue ensuite à retrouver certaines entrées ZIP pour les mêmes raisons de href mal résolu.

**Confirmé indépendant de la Phase 2** : `SpineIndex.kt` n'a été modifié par aucune tâche de la Phase 2 (2.1/2.2) — la logique de résolution TOC→spine est antérieure et intacte. Le bug affecte potentiellement tout EPUB dont les hrefs de TOC contiennent des caractères percent-encodés dans leur ancre (espaces `%20` étant une autre variante possible du même problème).

**À faire** :
- Décoder les hrefs (URL-decode) avant normalisation dans `SpineIndex.normalizeHref()` et partout où un href brut de `Link` est comparé à un nom d'entrée ZIP (`BookRepositoryImpl.resolveRelativeHref()`, `EpubZipIndex.find()`), pas seulement gérer `%23` en plus de `#` — le problème est plus général (n'importe quel caractère percent-encodé dans un href).
- Ajouter un EPUB de test avec ce pattern d'ancres encodées aux tests de régression du parsing.

**Validation** : réimporter *Anna Karénine* (ou un EPUB de test reproduisant le pattern) — la table des matières doit se résoudre correctement dans le spine, le livre doit s'importer avec du contenu réel (chapitres non vides, phrases en cache).

---

## PHASE 3 — Adaptation à l'écran

**Dépend de** : Phase 1.5 pour la tâche 3.6 (pagination), sinon indépendante.

### 3.1 — 🔴 Introduire une couche `WindowSizeClass`

**À faire** :
- Ajouter la dépendance adaptive Compose Material 3 correspondante.
- Calculer la classe de taille au niveau de `MainActivity`/racine de navigation, la propager via `CompositionLocal` ou paramètre explicite aux écrans qui en ont besoin (bibliothèque, lecteur).
- Ne pas la calculer localement écran par écran (ça recréerait la même incohérence que le problème actuel).

**Validation** : build OK sur émulateurs téléphone étroit / large / tablette (au moins 3 profils testés manuellement).

---

### 3.2 — 🟠 Grille de bibliothèque adaptative

**À faire** : remplacer `GridCells.Fixed(3)` par `GridCells.Adaptive(minSize = ...)` dans `LibraryScreen.kt`, avec une taille minimale de cellule choisie pour rester cohérente avec le design actuel des jaquettes.

**Validation** : nombre de colonnes qui varie visiblement entre téléphone et tablette, sans jaquettes déformées.

---

### 3.3 — 🔴 Plafonner la largeur de texte en lecture

**À faire** : dans `ReaderContent.kt`, contraindre la largeur effective du bloc de texte (ex. ~65-75 caractères par ligne au lieu de la largeur d'écran moins marge fixe), avec centrage et marges plus généreuses sur grand écran. `horizontalMarginDp` reste un réglage utilisateur, mais s'applique en plus de ce plafond, pas à sa place.

**Validation** : sur tablette en paysage, la largeur de ligne de texte reste confortable (comparable à ce que produirait un plafond de largeur, pas la largeur brute de l'écran).

---

### 3.4 — 🔴 Alléger la top bar du lecteur

**À faire** :
- `ReaderTopBar.kt` : ne garder que retour + un point d'entrée unique (menu) vers les actions secondaires actuellement en icônes séparées (mode lecture, recherche, signets, TOC).
- Déplacer ces actions vers le `UnifiedControlPanel` existant (déjà conçu pour ça) plutôt que créer un nouveau menu ad-hoc.
- Remplacer les imports directs `Icons.Outlined.*` avec `@Suppress("DEPRECATION")` par les entrées correspondantes du système centralisé `AppIcons.kt`, déjà utilisé ailleurs dans l'app.

**Validation** : sur un écran étroit de référence (ex. 360dp de largeur), le titre du livre ne devrait plus être tronqué dans les cas courants.

---

### 3.5 — 🟡 Pagination robuste à la rotation d'écran

**Dépend de 1.5.**

**À faire** :
- Dans `PagedContent`, ancrer la position sur un repère de contenu stable (index de phrase, pas numéro de page) avant un recalcul de pagination.
- Après recalcul (rotation, redimensionnement), re-résoudre le numéro de page correspondant à ce repère plutôt que de garder le `pagerState` sur le même numéro brut.

**Validation** : ouvrir un livre en mode paginé, noter la phrase affichée, tourner l'écran — la même phrase doit rester visible après recalcul.

---

## PHASE 4 — Bibliothèque à l'échelle

### 4.1 — 🟠 Requête groupée pour la progression de la bibliothèque

**À faire** : remplacer la boucle `books.forEach { getProgress(book.id) }` dans `LibraryViewModel.loadBooks()` par une requête unique (`SELECT * FROM reading_progress WHERE bookId IN (:ids)`, avec le nouveau schéma unifié de la Phase 1), puis construction de la map en mémoire.

**Validation** : mesurer le temps de chargement de la bibliothèque avant/après sur un jeu de données d'au moins 100 livres.

---

### 4.2 — 🟢 Pagination de `getAllBooks()` (à anticiper, pas urgent)

**À faire** : si la stratégie produit vise des bibliothèques de plusieurs centaines/milliers de livres, prévoir une requête paginée (`LIMIT`/`OFFSET` ou pagination Room `PagingSource`) plutôt que de charger toute la table d'un coup. Peut être différé après la beta initiale si le volume réel des testeurs reste modeste — à réévaluer avec les données de la beta plutôt que de sur-ingénierer maintenant.

---

## PHASE 5 — Table des matières

### 5.1 — 🟡 `LazyColumn` + scroll vers le chapitre courant

**À faire** :
- `ChapterPicker` (`ReaderTopBar.kt`) : remplacer `Column` + `forEach` + `verticalScroll` par `LazyColumn` + `items(tocEntries, key = { it.index })`.
- Initialiser le `LazyListState` pour que le chapitre courant soit visible à l'ouverture du bottom sheet (`scrollToItem` sans animation, ou `initialFirstVisibleItemIndex`).

**Validation** : sur un livre à TOC longue (30+ entrées), ouvrir la table des matières en étant avancé dans le livre — le chapitre courant doit être visible sans scroll manuel.

---

## PHASE 6 — Robustesse & qualité

### 6.1 — 🟡 Détection des EPUB protégés par DRM

**À faire** : dans `BookRepositoryImpl.importEpub()`, détecter la présence de DRM (ex. présence de `META-INF/encryption.xml` dans l'archive, ou échec spécifique de Readium identifiable) et remonter un message dédié ("Ce livre est protégé et ne peut pas être importé") plutôt que le message générique "fichier corrompu".

**Validation** : test avec un EPUB de test contenant un `encryption.xml` factice — message spécifique affiché, pas le message générique.

---

### 6.2 — 🔴 Premier vrai test d'intégration UI

**Problème** : le seul test instrumenté du dépôt (`ReaderScreenTest.kt`) est un placeholder vide.

**À faire** :
- Remplacer ce test par un test réel couvrant au minimum le scénario de reprise de lecture (§1.8), avec un ViewModel injecté/mocké correctement (le commentaire actuel du placeholder identifie déjà ce besoin — "would need a mock ViewModel").
- Établir l'infrastructure de test UI réutilisable (fakes/mocks des dépendances Hilt pour les tests Compose) pour que les prochains tests d'intégration ne repartent pas de zéro.

**Validation** : `./gradlew connectedDebugAndroidTest` (ou équivalent) exécute ce test avec succès, et le test échoue si on réintroduit délibérément le bug de la Phase 1 (vérification croisée).

---

## PHASE 7 — Synchronisation automatique

### 7.1 — 🟡 Synchronisation périodique en arrière-plan

**Dépend de** : Phase 1 terminée (pas de sens à synchroniser une position de lecture non fiable).

**À faire** :
- Introduire `WorkManager` pour déclencher `SyncManager.backup()` automatiquement (périodique + à la fermeture d'un livre), en respectant les contraintes déjà en place (mot de passe/chiffrement — voir comment gérer l'authentification sans interaction utilisateur à chaque sync, probablement via un token/clé dérivée stockée de façon sécurisée après la première configuration).
- Garder le déclenchement manuel existant en complément, pas en remplacement.

**Validation** : modifier la position de lecture sur un appareil, vérifier qu'elle apparaît sur un second appareil sans action manuelle, dans un délai raisonnable.

---

## PHASE 8 — Honnêteté produit

### 8.1 — 🟡 Documenter le surlignage mot-à-mot comme approximation

**À faire** : mettre à jour `architecture.md`/`PROJECT_STATUS.md` pour refléter précisément que le surlignage au mot est une interpolation par proportion de caractères sur la durée de la phrase, pas une synchronisation par timestamps réels — pour éviter de sur-promettre en beta.

### 8.2 — 🟢 Améliorer légèrement l'algorithme d'interpolation

**À faire** (optionnel, après le reste) : pondérer l'estimation par une approximation phonétique plutôt que le nombre brut de caractères (poids différent pour les lettres muettes fréquentes en français, micro-pause simulée sur la ponctuation forte). Reste une simulation, mais réduit le décalage perçu. Ne pas confondre avec un vrai alignement audio (hors scope de ce plan — noté comme piste future dans l'audit, §8).

---

## Séquencement recommandé pour Claude Code

1. **Phase 0** (0.1 → 0.2) — une session courte, deux tâches indépendantes.
2. **Phase 1** dans l'ordre (1.1 → 1.8) — c'est le chantier le plus long et le plus couplé, ne pas le découper entre plusieurs branches parallèles à cause des dépendances internes fortes (le schéma DB de 1.2 conditionne tout le reste).
3. **Phase 2** (2.1 → 2.2) — peut démarrer dès que Phase 1.2 (migration DB) est mergée, pour éviter deux migrations concurrentes sur `BookRepositoryImpl.kt`.
4. **Phase 3** (3.1 → 3.5) — indépendante des phases précédentes sauf 3.5 qui a besoin de 1.5.
5. **Phase 4, 5** — courtes, peuvent être glissées entre deux phases plus lourdes.
6. **Phase 6** — 6.2 en particulier doit être fait une fois la Phase 1 stabilisée, pour tester le comportement final plutôt qu'un état intermédiaire.
7. **Phase 7, 8** — après une première beta fermée, pas avant. Ce sont des améliorations, pas des bloquants.

À la fin de chaque phase : valider avec le scénario manuel correspondant décrit dans l'audit (§11 pour la Phase 1), mettre à jour `PROJECT_STATUS.md` avec le détail réel (pas une case cochée sans contexte), et seulement ensuite passer à la phase suivante.
