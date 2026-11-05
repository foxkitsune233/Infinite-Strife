package com.example.douqiuqiu.quest

enum class QuestStorySide { LEFT, RIGHT }

data class QuestStoryPortrait(
    val heroId: String,
    val displayName: String,
    val side: QuestStorySide
) {
    init {
        require(heroId.isNotBlank()) { "剧情立绘角色 ID 不能为空" }
        require(displayName.isNotBlank()) { "剧情立绘角色名称不能为空" }
    }
}

/** One speaker page in the lightweight quest-story presentation. */
data class QuestStoryLine(
    val speakerId: String,
    val speakerName: String,
    val text: String
) {
    init {
        require(speakerId.isNotBlank()) { "剧情说话者 ID 不能为空" }
        require(speakerName.isNotBlank()) { "剧情说话者名称不能为空" }
        require(text.isNotBlank()) { "剧情台词不能为空" }
    }
}

/** A draft stays inert until every required line has been agreed and added. */
data class QuestStoryDraft(
    val node: Int,
    val id: String,
    val title: String = "",
    val portraits: List<QuestStoryPortrait> = emptyList(),
    val lines: List<QuestStoryLine> = emptyList()
) {
    init {
        require(node in 1..24) { "剧情节点必须在 1..24: $node" }
        require(id == "story.journey.%02d".format(node)) { "剧情 ID 必须与节点一致: $id" }
    }

    val isPlayable: Boolean get() = title.isNotBlank() && portraits.size in 1..2 &&
        portraits.map { it.heroId }.distinct().size == portraits.size &&
        portraits.map { it.side }.distinct().size == portraits.size &&
        lines.isNotEmpty() && lines.all { line -> portraits.any { it.heroId == line.speakerId } }

    fun toChapterOrNull(): QuestStoryChapter? = if (isPlayable) QuestStoryChapter(id, node, title, portraits, lines) else null
}

data class QuestStoryChapter(
    val id: String,
    val node: Int,
    val title: String,
    val portraits: List<QuestStoryPortrait>,
    val lines: List<QuestStoryLine>
) {
    init {
        require(title.isNotBlank()) { "剧情标题不能为空" }
        require(portraits.size in 1..2) { "剧情需要一到两位立绘角色" }
        require(portraits.map { it.heroId }.distinct().size == portraits.size) { "剧情立绘角色重复" }
        require(portraits.map { it.side }.distinct().size == portraits.size) { "剧情立绘位置重复" }
        require(lines.isNotEmpty()) { "剧情至少需要一句台词" }
        require(lines.all { line -> portraits.any { it.heroId == line.speakerId } }) { "台词说话者必须在立绘角色中" }
    }

    fun speakerFor(page: Int): QuestStoryPortrait = portraits.single { it.heroId == lines[page].speakerId }
}

/**
 * Stable slots for the 24 quest nodes. The final dialogue is deliberately empty
 * until its wording is approved with the project owner.
 */
object QuestStoryCatalog {
    val drafts: List<QuestStoryDraft> = (1..24).map { node ->
        QuestStoryDraft(node, "story.journey.%02d".format(node))
    }

    fun chapterForNode(node: Int): QuestStoryChapter? = drafts.firstOrNull { it.node == node }?.toChapterOrNull()
}
