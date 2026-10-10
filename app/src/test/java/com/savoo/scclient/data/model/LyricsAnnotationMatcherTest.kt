package com.savoo.scclient.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsAnnotationMatcherTest {

    private fun annotation(id: Long, fragment: String) =
        GeniusAnnotation(id = id, fragment = fragment, body = "body", votes = 0, verified = false, reviewed = true, url = null)

    @Test
    fun partOfLineMatches() {
        val lines = listOf("Её отправили в частный пансионат", "Другая строка")
        val result = LyricsAnnotationMatcher.match(lines, listOf(annotation(1, "частный пансионат")))
        assertEquals(setOf(0), result.keys)
    }

    @Test
    fun punctuationCaseAndYoAreIgnored() {
        val lines = listOf("Нрав её исправит, как высококлассный остеопат!")
        val result = LyricsAnnotationMatcher.match(lines, listOf(annotation(1, "нрав ее исправит как высококлассный остеопат")))
        assertEquals(setOf(0), result.keys)
    }

    @Test
    fun twoLineFragmentHighlightsBothLines() {
        val lines = listOf("Nobody pray for me", "It been that day for me", "Way")
        val result = LyricsAnnotationMatcher.match(lines, listOf(annotation(1, "Nobody pray for me\nIt been that day for me")))
        assertEquals(setOf(0, 1), result.keys)
    }

    @Test
    fun wholeVerseFragmentIsSkipped() {
        val lines = listOf("line one here", "line two here", "line three here")
        val result = LyricsAnnotationMatcher.match(lines, listOf(annotation(1, "[Verse 1]\nline one here\nline two here\nline three here")))
        assertTrue(result.isEmpty())
    }

    @Test
    fun repeatedChorusIsHighlightedEverywhere() {
        val lines = listOf("Sit down, be humble", "verse line", "Sit down, be humble")
        val result = LyricsAnnotationMatcher.match(lines, listOf(annotation(1, "Sit down, be humble")))
        assertEquals(setOf(0, 2), result.keys)
    }

    @Test
    fun shortFragmentsAreIgnored() {
        val lines = listOf("Way", "Ayy")
        val result = LyricsAnnotationMatcher.match(lines, listOf(annotation(1, "Way")))
        assertTrue(result.isEmpty())
    }

    @Test
    fun adLibsInBracketsDoNotBreakMatch() {
        val lines = listOf("Way (Yeah, yeah) nobody pray for me")
        val result = LyricsAnnotationMatcher.match(lines, listOf(annotation(1, "Way nobody pray for me")))
        assertEquals(setOf(0), result.keys)
    }
}
