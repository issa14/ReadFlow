# Plan d'action — Parsing & Présentation EPUB
# À soumettre à Claude Code
# Branche : feature/epub-quality depuis main
# Convention commits : verbe français à l'impératif

---

## Contexte pour Claude Code

```
Projet : InkTone, lecteur EPUB + TTS neuronal, Android Kotlin + Jetpack Compose.
Package : com.inktone

Pipeline actuel (à améliorer) :
  EPUB ZIP → extractHtml() → stripHtml() → ChunkTextUseCase → SentenceCacheDao
  → BookRepositoryImpl.getChapter() → ReaderContent → SentenceRenderer

Dépendance EPUB : Readium Kotlin Toolkit 3.0.0
  - publication.metadata.* : accès aux métadonnées
  - publication.readingOrder : spine ordonné
  - publication.tableOfContents : TOC (nested Links)
  - publication.resources : accès aux assets (images, fonts)

Dépendance images : Coil déjà en dépendance (io.coil-kt:coil-compose:2.6.0)

RÈGLE IMPORTANTE : le modèle Sentence actuel ne doit PAS être modifié
pour les tâches 1 et 2 (cache Room existant incompatible). Le RichBlock
est un nouveau modèle additionnel — les Sentence continuent d'exister
pour la segmentation TTS.
```

---

## TÂCHE 1 — Nouveau modèle RichBlock : préserver la structure sémantique HTML

**Fichiers à créer / modifier :**
- `domain/model/RichBlock.kt` — CRÉER
- `data/repository/BookRepositoryImpl.kt` — MODIFIER `extractHtml()` + `stripHtml()`
- `data/database/entity/RichBlockCacheEntity.kt` — CRÉER
- `data/database/RichBlockCacheDao.kt` — CRÉER
- `data/database/InkToneDatabase.kt` — MODIFIER (ajouter entity + DAO)

**Durée estimée : 3-4 heures**

### 1.1 — Créer RichBlock.kt

```kotlin
// domain/model/RichBlock.kt
package com.inktone.domain.model

/**
 * Bloc de contenu sémantique extrait d'un fichier XHTML EPUB.
 *
 * Préserve la structure de l'auteur (headings, emphasis, blockquotes, images)
 * que [Sentence] ne peut pas représenter (texte plat uniquement).
 *
 * Coexistence avec [Sentence] :
 * - [Sentence] = unité TTS (segmentation phrases pour la synthèse vocale)
 * - [RichBlock] = unité d'affichage (rendu visuel avec structure typographique)
 * La même source HTML génère DEUX représentations pour DEUX usages distincts.
 */
sealed class RichBlock {

    /** Index du bloc dans le chapitre (0-based). */
    abstract val index: Int

    /** Paragraphe standard (texte courant). */
    data class Paragraph(
        override val index: Int,
        val spans: List<TextSpan>
    ) : RichBlock()

    /** Titre de section (h1…h4 EPUB). */
    data class Heading(
        override val index: Int,
        val level: Int,          // 1 = h1, 2 = h2, …
        val spans: List<TextSpan>
    ) : RichBlock()

    /** Citation (blockquote). */
    data class BlockQuote(
        override val index: Int,
        val spans: List<TextSpan>
    ) : RichBlock()

    /** Ligne de poème / vers (class="verse", "stanza", "poem"). */
    data class PoemLine(
        override val index: Int,
        val spans: List<TextSpan>
    ) : RichBlock()

    /** Image EPUB (src → path relatif dans le ZIP). */
    data class EpubImage(
        override val index: Int,
        val href: String,        // chemin relatif dans l'EPUB
        val alt: String = ""
    ) : RichBlock()

    /** Note de bas de page (epub:type="footnote", "endnote"). */
    data class Footnote(
        override val index: Int,
        val noteId: String,
        val spans: List<TextSpan>
    ) : RichBlock()

    /** Séparateur de section (hr, class="separator"). */
    data class SectionBreak(override val index: Int) : RichBlock()
}

/**
 * Span de texte avec style inline.
 *
 * Évite de matérialiser toutes les combinaisons de styles comme des
 * sous-classes hermétiques — un TextSpan peut être bold ET italic.
 */
data class TextSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val superscript: Boolean = false,  // exposants (notes de bas de page)
    val noteRef: String? = null        // ID de la note référencée (si lien footnote)
)
```

### 1.2 — Créer l'extracteur HTML sémantique

Dans `BookRepositoryImpl`, remplacer `stripHtml()` par une nouvelle méthode `extractRichBlocks()`.
Garder `stripHtml()` UNIQUEMENT pour alimenter `ChunkTextUseCase` (segmentation TTS).

```kotlin
// Dans BookRepositoryImpl.kt — AJOUTER après les imports existants
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
```

**IMPORTANT** : Jsoup doit être ajouté comme dépendance dans `app/build.gradle.kts` :
```kotlin
implementation("org.jsoup:jsoup:1.17.2")
```

```kotlin
// Dans BookRepositoryImpl — AJOUTER la méthode :
private fun extractRichBlocks(html: String): List<RichBlock> {
    if (html.isEmpty()) return emptyList()

    val doc = Jsoup.parse(html)
    val body = doc.body() ?: return emptyList()

    val blocks = mutableListOf<RichBlock>()
    var blockIndex = 0

    // Collecter les notes de bas de page d'abord (pour les relier aux références)
    val footnotes = mutableMapOf<String, List<TextSpan>>()
    doc.select("[epub\\:type=footnote], [epub\\:type=endnote], .footnote, .endnote").forEach { el ->
        val id = el.id().takeIf { it.isNotBlank() } ?: return@forEach
        footnotes[id] = extractSpans(el)
    }

    // Parcourir les enfants directs du body
    for (child in body.children()) {
        val block = processElement(child, blockIndex, footnotes) ?: continue
        blocks.add(block)
        blockIndex++
    }

    return blocks
}

private fun processElement(
    el: Element,
    index: Int,
    footnotes: Map<String, List<TextSpan>>
): RichBlock? {
    val tagName = el.tagName().lowercase()
    val epubType = el.attr("epub:type").lowercase()
    val cssClass = el.className().lowercase()

    // Ignorer les éléments de navigation
    if (epubType == "toc" || epubType == "landmarks") return null

    // Images
    if (tagName == "img" || tagName == "image") {
        val src = el.attr("src").ifBlank { el.attr("xlink:href") }
        return if (src.isNotBlank()) {
            RichBlock.EpubImage(index, src, el.attr("alt"))
        } else null
    }

    // Figure avec image
    if (tagName == "figure") {
        val img = el.selectFirst("img") ?: el.selectFirst("image")
        if (img != null) {
            val src = img.attr("src").ifBlank { img.attr("xlink:href") }
            return if (src.isNotBlank()) RichBlock.EpubImage(index, src, img.attr("alt")) else null
        }
    }

    // Séparateurs de section
    if (tagName == "hr" || cssClass.contains("separator") || cssClass.contains("ornament")) {
        return RichBlock.SectionBreak(index)
    }

    // Titres
    if (tagName.length == 2 && tagName[0] == 'h' && tagName[1] in '1'..'4') {
        val level = tagName[1].digitToInt()
        return RichBlock.Heading(index, level, extractSpans(el))
    }

    // Blockquote
    if (tagName == "blockquote") {
        return RichBlock.BlockQuote(index, extractSpans(el))
    }

    // Notes de bas de page / fin
    if (epubType == "footnote" || epubType == "endnote" ||
        cssClass.contains("footnote") || cssClass.contains("endnote")) {
        return null  // Déjà collectées, ne pas dupliquer
    }

    // Poèmes / vers
    if (cssClass.contains("verse") || cssClass.contains("stanza") ||
        cssClass.contains("poem") || cssClass.contains("poeme") ||
        epubType == "z3998:poem" || epubType == "z3998:verse") {
        return RichBlock.PoemLine(index, extractSpans(el))
    }

    // Tout le reste → paragraphe
    val spans = extractSpans(el)
    return if (spans.isNotEmpty()) RichBlock.Paragraph(index, spans) else null
}

private fun extractSpans(el: Element): List<TextSpan> {
    val spans = mutableListOf<TextSpan>()
    extractSpansRecursive(el, bold = false, italic = false, superscript = false, spans)
    return spans
}

private fun extractSpansRecursive(
    node: org.jsoup.nodes.Node,
    bold: Boolean,
    italic: Boolean,
    superscript: Boolean,
    result: MutableList<TextSpan>
) {
    when (node) {
        is TextNode -> {
            val text = node.text()
            if (text.isNotBlank()) {
                result.add(TextSpan(text, bold, italic, superscript))
            }
        }
        is Element -> {
            val tag = node.tagName().lowercase()
            val newBold = bold || tag == "strong" || tag == "b" ||
                node.className().lowercase().contains("bold")
            val newItalic = italic || tag == "em" || tag == "i" ||
                node.className().lowercase().contains("italic") ||
                node.className().lowercase().contains("italique")
            val newSup = superscript || tag == "sup"

            // Détecter les références footnote (a href="#noteX")
            if (tag == "a") {
                val href = node.attr("href")
                if (href.startsWith("#")) {
                    val noteRef = href.removePrefix("#")
                    val text = node.text()
                    if (text.isNotBlank()) {
                        result.add(TextSpan(text, newBold, newItalic, newSup, noteRef))
                        return
                    }
                }
            }

            for (child in node.childNodes()) {
                extractSpansRecursive(child, newBold, newItalic, newSup, result)
            }
        }
    }
}
```

### 1.3 — Créer RichBlockCacheEntity + DAO

```kotlin
// data/database/entity/RichBlockCacheEntity.kt
@Entity(
    tableName = "rich_block_cache",
    primaryKeys = ["bookId", "chapterIndex", "blockIndex"]
)
data class RichBlockCacheEntity(
    val bookId: String,
    val chapterIndex: Int,
    val blockIndex: Int,
    val type: String,        // "paragraph", "heading", "blockquote", "poem", "image", "footnote", "break"
    val level: Int = 0,      // Pour Heading uniquement
    val href: String = "",   // Pour EpubImage uniquement
    val alt: String = "",    // Pour EpubImage
    val spansJson: String = ""  // List<TextSpan> sérialisée en JSON avec Gson
)
```

```kotlin
// data/database/RichBlockCacheDao.kt
@Dao
interface RichBlockCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(blocks: List<RichBlockCacheEntity>)

    @Query("SELECT * FROM rich_block_cache WHERE bookId = :bookId AND chapterIndex = :chapterIndex ORDER BY blockIndex ASC")
    suspend fun getBlocks(bookId: String, chapterIndex: Int): List<RichBlockCacheEntity>

    @Query("DELETE FROM rich_block_cache WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: String)
}
```

Ajouter dans `InkToneDatabase.kt` :
```kotlin
@Database(
    entities = [..., RichBlockCacheEntity::class],  // Ajouter RichBlockCacheEntity
    version = /* incrémenter la version */
)
abstract class InkToneDatabase : RoomDatabase() {
    abstract fun richBlockCacheDao(): RichBlockCacheDao
    // ...
}
```

Ajouter une migration Room pour la nouvelle table :
```kotlin
val MIGRATION_X_Y = object : Migration(X, Y) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS rich_block_cache (
                bookId TEXT NOT NULL,
                chapterIndex INTEGER NOT NULL,
                blockIndex INTEGER NOT NULL,
                type TEXT NOT NULL,
                level INTEGER NOT NULL DEFAULT 0,
                href TEXT NOT NULL DEFAULT '',
                alt TEXT NOT NULL DEFAULT '',
                spansJson TEXT NOT NULL DEFAULT '',
                PRIMARY KEY(bookId, chapterIndex, blockIndex)
            )
        """)
    }
}
```

### 1.4 — Intégrer dans BookRepositoryImpl.importEpub()

Dans la boucle d'import (après `chunkText(bookId, i, combinedHtml)`) :
```kotlin
// Extraire ET cacher les blocs riches
val richBlocks = extractRichBlocks(combinedHtml)
val richEntities = richBlocks.map { it.toEntity(bookId, i) }
if (richEntities.isNotEmpty()) {
    richBlockCacheDao.insertAll(richEntities)
}
```

### 1.5 — Mettre à jour getChapter()

Ajouter `richBlocks: List<RichBlock>` dans `Chapter` :
```kotlin
// domain/model/Chapter.kt
data class Chapter(
    val index: Int,
    val title: String,
    val sentences: List<Sentence>,
    val richBlocks: List<RichBlock> = emptyList()  // NOUVEAU
)
```

Dans `getChapter()`, après avoir chargé le cache `cachedSentences` :
```kotlin
val cachedRichBlocks = richBlockCacheDao.getBlocks(bookId, chapterIndex)
    .map { it.toDomain() }
return@withContext Chapter(
    index = chapterIndex,
    title = chapterTitle,
    sentences = cachedSentences.map { ... },
    richBlocks = cachedRichBlocks
)
```

**Vérification :**
```bash
./gradlew assembleDebug  # Doit compiler sans erreur
```

---

## TÂCHE 2 — Renderer sémantique dans ReaderContent

**Fichiers à modifier :**
- `ui/screen/reader/ReaderContent.kt`

**Durée estimée : 2-3 heures**

### Principe

Ajouter un nouveau composable `RichChapterContent` en parallèle du `SentenceRenderer` existant.
Le `SentenceRenderer` reste actif pour la phrase surlignée par le TTS.
Le `RichChapterContent` affiche les blocs autour de la phrase active.

### Implémentation

```kotlin
// Dans ReaderContent.kt — AJOUTER :

@Composable
fun RichBlockRenderer(
    block: RichBlock,
    activeIdx: Int,
    isSpeaking: Boolean,
    textStyle: TextStyle,
    textColor: Color,
    accentColor: Color,
    playbackState: PlaybackState
) {
    when (block) {
        is RichBlock.Heading -> {
            val scale = when (block.level) {
                1 -> 1.5f
                2 -> 1.3f
                3 -> 1.15f
                else -> 1.05f
            }
            Spacer(Modifier.height(if (block.level <= 2) 24.dp else 16.dp))
            Text(
                text = buildSpanString(block.spans, textColor),
                style = textStyle.copy(
                    fontSize = textStyle.fontSize * scale,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp
                )
            )
            Spacer(Modifier.height(8.dp))
        }

        is RichBlock.Paragraph -> {
            val annotated = buildSpanString(block.spans, textColor)
            Text(
                text = annotated,
                style = textStyle,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }

        is RichBlock.BlockQuote -> {
            val annotated = buildSpanString(block.spans, textColor)
            Row(modifier = Modifier.padding(vertical = 4.dp)) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(accentColor.copy(alpha = 0.4f))
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = annotated,
                    style = textStyle.copy(color = textColor.copy(alpha = 0.75f)),
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }

        is RichBlock.PoemLine -> {
            val annotated = buildSpanString(block.spans, textColor)
            Text(
                text = annotated,
                style = textStyle.copy(
                    textAlign = TextAlign.Center,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            )
        }

        is RichBlock.EpubImage -> {
            // Résolution du path : href relatif → chemin absolu dans le dossier EPUB
            // Le bookId est transmis depuis le Chapter via le Chapter.epubDir
            // (à ajouter : Chapter.epubDir: String)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = block.resolvedPath,  // voir note ci-dessous
                    contentDescription = block.alt.ifBlank { null },
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                )
            }
        }

        is RichBlock.SectionBreak -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(textColor.copy(alpha = 0.25f))
                    )
                    if (it < 2) Spacer(Modifier.width(8.dp))
                }
            }
        }

        is RichBlock.Footnote -> { /* Ignoré dans le flux principal */ }
    }
}

private fun buildSpanString(spans: List<TextSpan>, baseColor: Color): AnnotatedString {
    return buildAnnotatedString {
        spans.forEach { span ->
            withStyle(
                SpanStyle(
                    fontWeight = if (span.bold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (span.italic) androidx.compose.ui.text.font.FontStyle.Italic
                                else androidx.compose.ui.text.font.FontStyle.Normal,
                    baselineShift = if (span.superscript) BaselineShift.Superscript
                                    else BaselineShift.None,
                    fontSize = if (span.superscript) TextUnit(0.75f, TextUnitType.Em)
                               else TextUnit.Unspecified,
                    color = baseColor
                )
            ) {
                append(span.text)
            }
            if (span.noteRef != null) append(" ")  // Espace après référence footnote
        }
    }
}
```

**Note sur `EpubImage.resolvedPath`** : ajouter `epubDir: String` dans `Chapter` et construire
le chemin absolu `File(epubDir, block.href).absolutePath` pour le passer à `AsyncImage`.

### Intégration dans ScrollContent et PagedContent

Dans les deux modes de lecture, remplacer l'usage actuel de `SelectableSentence` / `SentenceRenderer`
par une liste de `RichBlockRenderer`, avec une exception : la phrase active (celle que le TTS lit)
continue d'utiliser `ActiveSentenceText` pour le surlignage mot-à-mot.

Stratégie de coexistence :
```kotlin
// Dans l'itération des blocs, trouver si le bloc correspond à la phrase active
// (utiliser blockIndex vs sentence.startOffset pour la correspondance)
// Si oui, utiliser ActiveSentenceText dans le bloc, sinon RichBlockRenderer normal.
```

---

## TÂCHE 3 — TOC réel dans ChapterPicker

**Fichiers à modifier :**
- `domain/model/Book.kt`
- `data/database/entity/BookEntity.kt`
- `data/mapper/BookMapper.kt`
- `data/repository/BookRepositoryImpl.kt`
- `data/database/BookDao.kt`
- `ui/screen/reader/ReaderTopBar.kt`
- `ui/screen/reader/ReaderViewModel.kt`

**Durée estimée : 2 heures**

### 3.1 — Ajouter TocEntry dans le domaine

```kotlin
// domain/model/TocEntry.kt — CRÉER
data class TocEntry(
    val index: Int,        // Index dans la liste plate du TOC
    val title: String,     // Titre réel ("Chapitre 7 — La Trahison")
    val level: Int = 0,    // Profondeur d'imbrication (0 = racine)
)
```

### 3.2 — Ajouter tocEntries dans Book

```kotlin
// domain/model/Book.kt — MODIFIER
data class Book(
    ...
    val tocEntries: List<TocEntry> = emptyList()  // NOUVEAU
)
```

### 3.3 — Persister les titres TOC en JSON dans BookEntity

```kotlin
// data/database/entity/BookEntity.kt — MODIFIER
@Entity(tableName = "books")
data class BookEntity(
    ...
    val tocJson: String = "[]"  // NOUVEAU — List<TocEntry> sérialisée en JSON
)
```

Ajouter une migration Room :
```kotlin
database.execSQL("ALTER TABLE books ADD COLUMN tocJson TEXT NOT NULL DEFAULT '[]'")
```

### 3.4 — Remplir tocJson à l'import

Dans `BookRepositoryImpl.importEpub()`, après avoir aplati le TOC :
```kotlin
val tocEntries = flatToc.mapIndexed { i, link ->
    TocEntry(
        index = i,
        title = link.title?.takeIf { it.isNotBlank() } ?: "Chapitre ${i + 1}",
        level = 0  // à calculer depuis la structure arborescente si nécessaire
    )
}
val tocJson = Gson().toJson(tocEntries)
// Passer tocJson à bookDao.insert() via toEntity()
```

### 3.5 — Mettre à jour ChapterPicker

```kotlin
// ReaderTopBar.kt — MODIFIER ChapterPicker :
@Composable
fun ChapterPicker(
    tocEntries: List<TocEntry>,   // REMPLACE totalChapters: Int
    currentChapter: Int,
    onSelect: (Int) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Table des matières", ...)
        tocEntries.forEachIndexed { i, entry ->
            val isCurrent = i == currentChapter
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = (entry.level * 16).dp,  // Indentation par niveau
                        vertical = 2.dp
                    ),
                ...
            ) {
                Text(
                    text = entry.title,
                    color = if (isCurrent) MaterialTheme.colorScheme.ttsActive
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    ...
                )
            }
        }
    }
}
```

Dans `ReaderViewModel`, exposer `state.book?.tocEntries ?: emptyList()` et passer à `ChapterPicker`.

---

## TÂCHE 4 — Extraction couverture EPUB3 via Readium

**Fichier à modifier :** `data/repository/BookRepositoryImpl.kt`

**Durée estimée : 45 minutes**

Remplacer l'heuristique ZIP par l'API Readium :

```kotlin
// Dans importEpub(), après openPublication() :
var coverPath: String? = null

// 1. EPUB3 : couverture via Readium metadata
val coverLink = try {
    publication.coverLink
} catch (_: Exception) { null }

if (coverLink != null) {
    val coverHref = coverLink.href.toString()
    val coverExtension = coverHref.substringAfterLast(".").lowercase()
    val outputExt = if (coverExtension in listOf("jpg", "jpeg", "png", "webp")) coverExtension else "jpg"
    val coverFile = File(epubDir, "cover.$outputExt")

    try {
        ZipFile(epubFile).use { zip ->
            val entry = zip.entries().asSequence()
                .find { it.name.endsWith(coverHref) || it.name == coverHref }
            if (entry != null) {
                zip.getInputStream(entry).use { it.copyTo(coverFile.outputStream()) }
                coverPath = coverFile.absolutePath
            }
        }
    } catch (e: Exception) {
        Log.w("BookRepo", "Cover via Readium échoué : ${e.message}")
    }
}

// 2. Fallback EPUB2 : heuristique ZIP (comportement existant)
if (coverPath == null) {
    // ... code existant avec contains("cover") ...
}
```

---

## TÂCHE 5 — Métadonnées étendues

**Fichier à modifier :** `data/repository/BookRepositoryImpl.kt`, `domain/model/Book.kt`, `data/database/entity/BookEntity.kt`

**Durée estimée : 1 heure**

### 5.1 — Ajouter champs dans Book et BookEntity

```kotlin
// domain/model/Book.kt
data class Book(
    ...
    val publisher: String? = null,          // NOUVEAU
    val publishedDate: String? = null,       // NOUVEAU ("2024-03-15")
    val subjects: List<String> = emptyList(), // NOUVEAU (genres/tags)
    val isbn: String? = null                 // NOUVEAU
)
```

```kotlin
// data/database/entity/BookEntity.kt
data class BookEntity(
    ...
    val publisher: String? = null,
    val publishedDate: String? = null,
    val subjects: String = "[]",     // JSON array
    val isbn: String? = null
)
```

Migration Room :
```kotlin
database.execSQL("ALTER TABLE books ADD COLUMN publisher TEXT")
database.execSQL("ALTER TABLE books ADD COLUMN publishedDate TEXT")
database.execSQL("ALTER TABLE books ADD COLUMN subjects TEXT NOT NULL DEFAULT '[]'")
database.execSQL("ALTER TABLE books ADD COLUMN isbn TEXT")
```

### 5.2 — Extraire depuis Readium

Dans `importEpub()`, après `publication.metadata.languages` :
```kotlin
val publisher = publication.metadata.publishers.firstOrNull()?.name
val publishedDate = publication.metadata.published?.toString()?.take(10)  // "2024-03-15"
val subjects = publication.metadata.subjects.map { it.localizedName?.string ?: it.name }
val isbn = publication.metadata.identifier
    ?.takeIf { it.startsWith("urn:isbn:") }
    ?.removePrefix("urn:isbn:")
```

---

## TÂCHE 6 — (Optionnel) Support multi-ancres dans SpineIndex

**Fichier à modifier :** `data/epub/SpineIndex.kt`

**Durée estimée : 1.5 heure**

Problème : plusieurs entrées TOC peuvent pointer vers le même fichier spine avec des ancres différentes.
Actuellement, elles mappent toutes sur le même `IntRange` → contenu dupliqué.

### Solution proposée

```kotlin
// Dans SpineIndex — AJOUTER :
/**
 * Résout une plage spine EN TENANT COMPTE des ancres HTML.
 *
 * Si deux entrées TOC consécutives pointent vers le même fichier
 * mais avec des ancres différentes (#part1, #part2), retourne
 * un [AnchoredRange] au lieu d'un simple IntRange.
 */
data class AnchoredRange(
    val spineRange: IntRange,
    val startAnchor: String?,   // Ancre de début dans le premier fichier
    val endAnchor: String?      // Ancre de fin dans le dernier fichier
)

fun resolveAnchoredRange(tocLink: Link, nextTocLink: Link? = null): AnchoredRange {
    val href = tocLink.href.toString()
    val startAnchor = if ("#" in href) href.substringAfter("#") else null
    val normalizedHref = href.substringBefore("#").substringAfterLast("/")

    val start = lookupIndex(normalizedHref) ?: return AnchoredRange(IntRange.EMPTY, null, null)

    val endExclusive: Int
    val endAnchor: String?
    if (nextTocLink != null) {
        val nextHref = nextTocLink.href.toString()
        endAnchor = if ("#" in nextHref) nextHref.substringAfter("#") else null
        val normalizedNextHref = nextHref.substringBefore("#").substringAfterLast("/")
        endExclusive = lookupIndex(normalizedNextHref) ?: spine.size
    } else {
        endExclusive = spine.size
        endAnchor = null
    }

    return AnchoredRange(start until endExclusive, startAnchor, endAnchor)
}
```

Dans `extractHtml()`, si `startAnchor != null` et `endAnchor != null` pour le même fichier,
extraire uniquement le contenu entre `<... id="startAnchor">` et `<... id="endAnchor">`.

---

## Récapitulatif et ordre d'exécution

| # | Tâche | Impact | Durée | Priorité |
|---|-------|--------|-------|----------|
| 1 | RichBlock : parser HTML sémantique | Italique, gras, titres, images, poèmes | 3-4h | 🔴 Critique |
| 2 | RichBlockRenderer dans ReaderContent | Affichage typo riche | 2-3h | 🔴 Critique |
| 3 | TOC réel dans ChapterPicker | Navigation par titres | 2h | 🔴 Critique |
| 4 | Couverture EPUB3 via Readium | Cover rate +25% | 45min | 🟠 Haute |
| 5 | Métadonnées étendues | Genres, éditeur, ISBN | 1h | 🟡 Moyenne |
| 6 | Support ancres TOC multi-sections | EPUB académiques | 1.5h | 🟡 Moyenne |

**Total estimé : ~11 heures**

### Ordre recommandé

```
Tâche 1 (RichBlock modèle + extracteur)
  → Tâche 3 (TOC titres — indépendant, rapide)
  → Tâche 2 (Renderer — dépend de Tâche 1)
  → Tâche 4 (Couverture — standalone)
  → Tâche 5 (Métadonnées — standalone)
  → Tâche 6 (Ancres — si temps disponible)
```

### Vérification après chaque tâche

```bash
# Build obligatoire après chaque tâche
./gradlew assembleDebug

# Après tâche 1 : vérifier la migration Room
./gradlew :app:kspDebugKotlin  # Génère le code Room

# Test rapide tâche 3 : ouvrir un EPUB, vérifier que le TOC affiche
# "Chapitre 7 — La Trahison" au lieu de "Chapitre 8"

# Test rapide tâche 1 : ouvrir un roman avec dialogues en italique,
# vérifier que l'italique est visible dans le reader
```

### Messages de commit

```
Ajoute le modèle RichBlock pour la préservation de la structure EPUB
Intègre l'extracteur HTML sémantique Jsoup dans BookRepositoryImpl
Persiste les RichBlocks dans une table Room dédiée
Ajoute le composable RichBlockRenderer avec support heading/blockquote/image
Expose les vrais titres TOC dans ChapterPicker
Corrige l'extraction de couverture EPUB3 via Readium metadata
Étend les métadonnées Book avec publisher, subjects, ISBN
Ajoute le support des ancres HTML dans SpineIndex
```
