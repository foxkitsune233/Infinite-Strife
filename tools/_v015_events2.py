import io

# ---------- 1. EventOutcome: outcomeId / artKey ----------
p = "shared/src/commonMain/kotlin/com/example/douqiuqiu/event/EventCatalog.kt"
s = io.open(p, encoding="utf-8").read()

old = """    /** 该结果可能造成死亡；只允许出现在已向玩家披露致死风险的选项上。 */
    val possibleDeath: Boolean = false
) {"""
new = """    /** 该结果可能造成死亡；只允许出现在已向玩家披露致死风险的选项上。 */
    val possibleDeath: Boolean = false,
    /** 稳定结局 ID；节点生成时固定，读档不重算。 */
    val outcomeId: String = "",
    /** 结局插画键；视觉层只读取该键，不根据中文标题反推。 */
    val artKey: String = ""
) {"""
assert old in s
s = s.replace(old, new, 1)

# shared artKey helper on EventCatalog (insert before `fun definition(kind: EventKind)`)
anchor = "    fun definition(kind: EventKind): EventDefinition = all.first { it.kind == kind }"
helper = """    /**
     * 事件的稳定插画键：`事件前缀.选项后缀`（如 `frozen_shrine.attune`）。
     *
     * 旧八类事件没有配图，返回空串；新八类必须返回稳定值，视觉层不得按标题反推。
     */
    fun artKey(kind: EventKind, optionId: String): String {
        val prefix = when (kind) {
            EventKind.FROZEN_SHRINE -> "frozen_shrine"
            EventKind.WAR_BANNER_RUINS -> "war_banner"
            EventKind.ROVING_CARAVAN -> "roving_caravan"
            EventKind.ROPEWAY_BRIDGE -> "ropeway_bridge"
            EventKind.PACT_ALTAR -> "pact_altar"
            EventKind.ALCHEMY_LAB -> "alchemy_lab"
            EventKind.HUNTER_CAMP -> "hunter_camp"
            EventKind.MOONWELL_ECHO -> "moonwell_echo"
            else -> return ""
        }
        val option = optionId.substringAfterLast('.')
        return if (option.matches(Regex("[a-z_]+"))) "$prefix.$option" else ""
    }

""" + anchor
assert anchor in s
s = s.replace(anchor, helper, 1)

io.open(p, "w", encoding="utf-8", newline="\n").write(s)
print("catalog patched")

# ---------- 2. JourneyState: recentEventKinds ----------
p = "shared/src/commonMain/kotlin/com/example/douqiuqiu/journey/JourneyModels.kt"
s = io.open(p, encoding="utf-8").read()
old = """    val nodeEvent: com.example.douqiuqiu.event.NodeEventInstance? = null,"""
new = """    val nodeEvent: com.example.douqiuqiu.event.NodeEventInstance? = null,
    /** 最近两次已生成的事件类型，用于避免连续重复；随存档保存。 */
    val recentEventKinds: List<com.example.douqiuqiu.event.EventKind> = emptyList(),"""
assert old in s
s = s.replace(old, new, 1)
io.open(p, "w", encoding="utf-8", newline="\n").write(s)
print("journey state patched")

# ---------- 3. JourneyRules: record history ----------
p = "shared/src/commonMain/kotlin/com/example/douqiuqiu/journey/JourneyRules.kt"
s = io.open(p, encoding="utf-8").read()
old = """        if (updated.stage == JourneyStage.EVENT) updated = updated.copy(nodeEvent = JourneyEvents.generate(s, updated, defs))"""
new = """        if (updated.stage == JourneyStage.EVENT) {
            val node = JourneyEvents.generate(s, updated, defs)
            updated = updated.copy(nodeEvent = node,
                recentEventKinds = (updated.recentEventKinds + node.kind).takeLast(2))
        }"""
assert old in s
s = s.replace(old, new, 1)
io.open(p, "w", encoding="utf-8", newline="\n").write(s)
print("journey rules patched")
