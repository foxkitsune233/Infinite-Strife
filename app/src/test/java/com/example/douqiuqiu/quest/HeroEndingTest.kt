package com.example.douqiuqiu.quest

import com.example.douqiuqiu.journey.FirstRunContent
import org.junit.Assert.*
import org.junit.Test

class HeroEndingTest {
    @Test fun everyPlayableHeroResolvesToItsOwnEnding() {
        for (hero in FirstRunContent.heroes) {
            val ending = HeroEndingCatalog.forHero(hero.id)
            assertNotNull("Missing ending for ${hero.id}", ending)
            assertEquals(hero.id, ending!!.heroId)
            assertTrue(ending.pages.isNotEmpty())
            assertTrue(ending.pages.all { it.isNotBlank() })
        }
        assertNull(HeroEndingCatalog.forHero("unknown"))
    }

    @Test fun finalSubtitleStaysVisibleUntilThePlayerContinues() {
        val playback = EndingPlayback(listOf("归途", "代价"))
        assertEquals("归途", playback.text)
        assertTrue(playback.advance())
        assertEquals("代价", playback.text)
        assertFalse(playback.finished)
        assertFalse(playback.advance())
        assertTrue(playback.finished)
        assertFalse(playback.advance())
        assertEquals(1, playback.page)
    }

    @Test fun savedPageRestoresTheSameSubtitleAndClampsStaleIndices() {
        val pages = listOf("第一段", "第二段", "第三段")
        val original = EndingPlayback(pages)
        original.advance()
        assertEquals("第二段", EndingPlayback(pages, original.page).text)
        assertEquals("第一段", EndingPlayback(pages, -1).text)
        assertEquals("第三段", EndingPlayback(pages, 99).text)
    }

    @Test fun skippingEndsPlaybackWithoutAdvancingIntoAnotherHeroStory() {
        val playback = EndingPlayback(listOf("第一段", "最后一段"))
        playback.skip()
        assertTrue(playback.finished)
        assertFalse(playback.advance())
        assertEquals(0, playback.page)
    }

    @Test fun emptySubtitleSequencesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { EndingPlayback(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { EndingPlayback(listOf(" ")) }
    }
}
