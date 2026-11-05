package com.example.douqiuqiu.quest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class QuestStoryCatalogTest {
    @Test fun draftSlotsCoverEveryQuestNodeButDoNotPlayBeforeTextIsApproved() {
        assertEquals((1..24).toSet(), QuestStoryCatalog.drafts.map { it.node }.toSet())
        assertFalse(QuestStoryCatalog.drafts.any { it.isPlayable })
        assertEquals(null, QuestStoryCatalog.chapterForNode(1))
    }

    @Test fun completeDraftBecomesAPlayableChapterWithStableId() {
        val draft = QuestStoryDraft(
            node = 7,
            id = "story.journey.07",
            title = "测试章节",
            portraits = listOf(QuestStoryPortrait("juggernaut", "主宰", QuestStorySide.LEFT)),
            lines = listOf(QuestStoryLine("juggernaut", "主宰", "测试台词"))
        )

        assertNotNull(draft.toChapterOrNull())
        assertEquals("story.journey.07", draft.toChapterOrNull()!!.id)
    }

    @Test fun dualPortraitChapterKeepsBothCharactersAndFindsTheActiveSpeaker() {
        val draft = QuestStoryDraft(
            node = 8,
            id = "story.journey.08",
            title = "测试双人对话",
            portraits = listOf(
                QuestStoryPortrait("juggernaut", "主宰", QuestStorySide.LEFT),
                QuestStoryPortrait("abaddon", "亚巴顿", QuestStorySide.RIGHT)
            ),
            lines = listOf(QuestStoryLine("abaddon", "亚巴顿", "轮到我说话。"))
        )

        val chapter = draft.toChapterOrNull()!!
        assertEquals(listOf("juggernaut", "abaddon"), chapter.portraits.map { it.heroId })
        assertEquals("abaddon", chapter.speakerFor(0).heroId)
    }
}
