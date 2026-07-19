package com.inktone.data.mapper

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.inktone.data.database.entity.RichBlockCacheEntity
import com.inktone.domain.model.RichBlock
import com.inktone.domain.model.TextSpan

private val gson = Gson()
private val textSpanListType = object : TypeToken<List<TextSpan>>() {}.type

private fun List<TextSpan>.toJson(): String = gson.toJson(this)
private fun String.toTextSpanList(): List<TextSpan> =
    if (isBlank()) emptyList() else gson.fromJson(this, textSpanListType) ?: emptyList()

fun RichBlock.toEntity(bookId: String, chapterIndex: Int): RichBlockCacheEntity =
    when (this) {
        is RichBlock.Paragraph -> RichBlockCacheEntity(
            bookId = bookId, chapterIndex = chapterIndex, blockIndex = index,
            type = "paragraph", spansJson = spans.toJson()
        )
        is RichBlock.Heading -> RichBlockCacheEntity(
            bookId = bookId, chapterIndex = chapterIndex, blockIndex = index,
            type = "heading", level = level, spansJson = spans.toJson()
        )
        is RichBlock.BlockQuote -> RichBlockCacheEntity(
            bookId = bookId, chapterIndex = chapterIndex, blockIndex = index,
            type = "blockquote", spansJson = spans.toJson()
        )
        is RichBlock.PoemLine -> RichBlockCacheEntity(
            bookId = bookId, chapterIndex = chapterIndex, blockIndex = index,
            type = "poem", spansJson = spans.toJson()
        )
        is RichBlock.EpubImage -> RichBlockCacheEntity(
            bookId = bookId, chapterIndex = chapterIndex, blockIndex = index,
            type = "image", href = href, alt = alt
        )
        is RichBlock.Footnote -> RichBlockCacheEntity(
            bookId = bookId, chapterIndex = chapterIndex, blockIndex = index,
            type = "footnote", noteId = noteId, spansJson = spans.toJson()
        )
        is RichBlock.SectionBreak -> RichBlockCacheEntity(
            bookId = bookId, chapterIndex = chapterIndex, blockIndex = index,
            type = "break"
        )
    }

fun RichBlockCacheEntity.toDomain(): RichBlock =
    when (type) {
        "heading" -> RichBlock.Heading(blockIndex, level, spansJson.toTextSpanList())
        "blockquote" -> RichBlock.BlockQuote(blockIndex, spansJson.toTextSpanList())
        "poem" -> RichBlock.PoemLine(blockIndex, spansJson.toTextSpanList())
        "image" -> RichBlock.EpubImage(blockIndex, href, alt)
        "footnote" -> RichBlock.Footnote(blockIndex, noteId, spansJson.toTextSpanList())
        "break" -> RichBlock.SectionBreak(blockIndex)
        else -> RichBlock.Paragraph(blockIndex, spansJson.toTextSpanList())
    }
