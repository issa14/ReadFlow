package com.inktone.domain.usecase

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FrenchSentenceSplitterTest {

    @Test
    fun `simple sentences`() {
        val result = FrenchSentenceSplitter.split("Bonjour. Comment allez-vous ? Très bien !")
        assertEquals(3, result.size)
        assertEquals("Bonjour.", result[0].text)
        assertEquals("Comment allez-vous ?", result[1].text)
        assertEquals("Très bien !", result[2].text)
    }

    @Test
    fun `abbreviation M dot`() {
        val result = FrenchSentenceSplitter.split("M. Dupont est arrivé. Il est content.")
        assertEquals(2, result.size)
        assertTrue(result[0].text.contains("M. Dupont"))
    }

    @Test
    fun `abbreviation Dr dot`() {
        val result = FrenchSentenceSplitter.split("Le Dr. Martin a prescrit un traitement. Le patient va mieux.")
        assertEquals(2, result.size)
    }

    @Test
    fun `etc abbreviation`() {
        // "etc." est une abréviation → le texte reste en une phrase
        val result = FrenchSentenceSplitter.split("Il aime les fruits, les légumes, etc. C'est bien.")
        assertEquals(1, result.size)
        assertTrue(result[0].text.contains("etc."))
    }

    @Test
    fun `decimal numbers`() {
        val result = FrenchSentenceSplitter.split("Le prix est de 3.14 euros. C'est raisonnable.")
        assertEquals(2, result.size)
        assertTrue(result[0].text.contains("3.14"))
    }

    @Test
    fun `ellipsis`() {
        val result = FrenchSentenceSplitter.split("Je ne sais pas... Peut-être demain.")
        assertEquals(2, result.size)
        assertTrue(result[0].text.contains("..."))
    }

    @Test
    fun `dialogue with guillemets`() {
        val result = FrenchSentenceSplitter.split("Il dit : « Bonjour. Comment allez-vous ? » Puis il partit.")
        assertEquals(2, result.size)
    }

    @Test
    fun `initials`() {
        val result = FrenchSentenceSplitter.split("J. K. Rowling est une auteure célèbre. Elle a écrit Harry Potter.")
        assertEquals(2, result.size)
        assertTrue(result[0].text.contains("J. K. Rowling"))
    }

    @Test
    fun `empty text`() {
        val result = FrenchSentenceSplitter.split("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `single sentence no punctuation`() {
        val result = FrenchSentenceSplitter.split("Bonjour tout le monde")
        assertEquals(1, result.size)
        assertEquals("Bonjour tout le monde", result[0].text)
    }
}
