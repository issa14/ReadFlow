package com.readflow.domain.usecase

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Résultat de la résolution de la position de reprise de lecture.
 */
data class ResolvedPosition(
    val chapterIndex: Int,
    val sentenceIndex: Int
)

/**
 * Résout la position de reprise de lecture en combinant
 * la progression sauvegardée en base (Room) et le SavedStateHandle
 * (Process Death).
 *
 * Priorité :
 * 1. Progression DB ([ReadingProgress]) si présente
 * 2. Fallback sur les valeurs du [SavedStateHandle]
 *
 * Extraite de [ReaderViewModel.loadBook()].
 */
@Singleton
class ResolveReadingPositionUseCase @Inject constructor() {

    /**
     * Résout la position de départ pour la lecture.
     *
     * @param dbChapterIndex Index du chapitre depuis la DB, ou null si absent.
     * @param dbSentenceIndex Index de la phrase depuis la DB, ou 0 si absent.
     * @param savedChapterIndex Index du chapitre depuis SavedStateHandle.
     * @param savedSentenceIndex Index de la phrase depuis SavedStateHandle.
     * @param totalChapters Nombre total de chapitres (pour clamp).
     * @return La position résolue.
     */
    operator fun invoke(
        dbChapterIndex: Int?,
        dbSentenceIndex: Int?,
        savedChapterIndex: Int,
        savedSentenceIndex: Int,
        totalChapters: Int
    ): ResolvedPosition {
        return if (dbChapterIndex != null) {
            ResolvedPosition(
                chapterIndex = dbChapterIndex.coerceIn(0, totalChapters - 1),
                sentenceIndex = dbSentenceIndex ?: 0
            )
        } else {
            ResolvedPosition(
                chapterIndex = savedChapterIndex.coerceIn(0, totalChapters - 1),
                sentenceIndex = savedSentenceIndex
            )
        }
    }
}
