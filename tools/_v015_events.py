import io

p = "shared/src/commonMain/kotlin/com/example/douqiuqiu/event/EventCatalog.kt"
s = io.open(p, encoding="utf-8").read()

new_events = '''
    // ---------------- 0.1.5 新增八类事件（8 → 16） ----------------
    // 每个事件固定三个稳定结局；artKey 由 JourneyActivity 按 kind + optionId 生成，
    // 节点生成时把结果冻结进 NodeEventInstance.outcomes，读档不重抽。

    /** 冰封祭坛：以生命共鸣换寒霜奖励；安全祈祷只有小收益；离开。 */
    val frozenShrine: EventDefinition = EventDefinition(
        kind = EventKind.FROZEN_SHRINE,
        title = "冰封祭坛",
        options = listOf(
            EventOption(
                id = "frozen_shrine.attune", label = "以生命共鸣寒霜（可能受伤，可能死亡）",
                risk = EventRisk.INJURY, canLethal = true
            ),
            EventOption(id = "frozen_shrine.pray", label = "安静祈祷", risk = EventRisk.NONE),
            EventOption(id = "frozen_shrine.leave", label = "离开", risk = EventRisk.NONE)
        )
    )

    /** 战旗遗址：交金币领走怒火奖励；血祭以生命换更高价值奖励；离开。 */
    val warBannerRuins: EventDefinition = EventDefinition(
        kind = EventKind.WAR_BANNER_RUINS,
        title = "战旗遗址",
        options = listOf(
            EventOption(id = "war_banner.claim", label = "交金币取走战旗", risk = EventRisk.GOLD,
                cost = EventCost.Gold(currency = EventCurrency.RUN_GOLD, source = EventCostSource.INSTANCE_PRICE)),
            EventOption(
                id = "war_banner.blood_rite", label = "血祭战旗（可能受伤，可能死亡）",
                risk = EventRisk.INJURY, canLethal = true
            ),
            EventOption(id = "war_banner.leave", label = "离开", risk = EventRisk.NONE)
        )
    )

    /** 流动商队：本局金币购买；明确卖出一件物品；离开。 */
    val rovingCaravan: EventDefinition = EventDefinition(
        kind = EventKind.ROVING_CARAVAN,
        title = "流动商队",
        options = listOf(
            EventOption(id = "roving_caravan.buy", label = "用本局金币购买", risk = EventRisk.GOLD,
                cost = EventCost.Gold(currency = EventCurrency.RUN_GOLD, source = EventCostSource.INSTANCE_PRICE)),
            EventOption(id = "roving_caravan.sell", label = "卖出一件物品",
                risk = EventRisk.ITEM, requiresExplicitItemChoice = true,
                itemRequirement = EventItemRequirement(kind = EventItemRequirementKind.ANY_DELIVERABLE)),
            EventOption(id = "roving_caravan.depart", label = "离开", risk = EventRisk.NONE)
        )
    )

    /** 断桥索道：冒险过桥可能受伤换多件小掉落；花钱修桥稳妥取件；离开。 */
    val ropewayBridge: EventDefinition = EventDefinition(
        kind = EventKind.ROPEWAY_BRIDGE,
        title = "断桥索道",
        options = listOf(
            EventOption(
                id = "ropeway_bridge.cross", label = "攀索过桥（可能受伤，可能死亡）",
                risk = EventRisk.INJURY, canLethal = true
            ),
            EventOption(id = "ropeway_bridge.repair", label = "交金币修桥", risk = EventRisk.GOLD,
                cost = EventCost.Gold(currency = EventCurrency.RUN_GOLD, source = EventCostSource.INSTANCE_PRICE)),
            EventOption(id = "ropeway_bridge.leave", label = "离开", risk = EventRisk.NONE)
        )
    )

    /** 秘契祭坛：交一份药换卡；以生命换稀有奖励；拒绝。 */
    val pactAltar: EventDefinition = EventDefinition(
        kind = EventKind.PACT_ALTAR,
        title = "秘契祭坛",
        options = listOf(
            EventOption(id = "pact_altar.offer_medicine", label = "献上一份药品",
                risk = EventRisk.ITEM, requiresExplicitItemChoice = true,
                itemRequirement = EventItemRequirement(kind = EventItemRequirementKind.MEDICINE)),
            EventOption(
                id = "pact_altar.offer_blood", label = "以血为契（可能受伤，可能死亡）",
                risk = EventRisk.INJURY, canLethal = true
            ),
            EventOption(id = "pact_altar.refuse", label = "拒绝", risk = EventRisk.NONE)
        )
    )

    /** 失控炼金室：交药蒸馏换治疗型掉落；破封可能中毒或受伤换炼金组件；离开。 */
    val alchemyLab: EventDefinition = EventDefinition(
        kind = EventKind.ALCHEMY_LAB,
        title = "失控炼金室",
        options = listOf(
            EventOption(id = "alchemy_lab.distill", label = "投入药品蒸馏",
                risk = EventRisk.ITEM, requiresExplicitItemChoice = true,
                itemRequirement = EventItemRequirement(kind = EventItemRequirementKind.MEDICINE)),
            EventOption(
                id = "alchemy_lab.break_seal", label = "强行破封（可能中毒，可能死亡）",
                risk = EventRisk.POISON, canLethal = true
            ),
            EventOption(id = "alchemy_lab.leave", label = "离开", risk = EventRisk.NONE)
        )
    )

    /** 猎人营火：明确交物换战利品；分一份口粮换治疗与情报；离开。 */
    val hunterCamp: EventDefinition = EventDefinition(
        kind = EventKind.HUNTER_CAMP,
        title = "猎人营火",
        options = listOf(
            EventOption(id = "hunter_camp.barter", label = "以物易物",
                risk = EventRisk.ITEM, requiresExplicitItemChoice = true,
                itemRequirement = EventItemRequirement(kind = EventItemRequirementKind.ANY_DELIVERABLE)),
            EventOption(id = "hunter_camp.share_rations", label = "分一份药品给猎人",
                risk = EventRisk.ITEM, requiresExplicitItemChoice = true,
                itemRequirement = EventItemRequirement(kind = EventItemRequirementKind.MEDICINE)),
            EventOption(id = "hunter_camp.leave", label = "离开", risk = EventRisk.NONE)
        )
    )

    /** 月井回响：羁绊达标才能共鸣；抽取随机低档回响；离开。 */
    val moonwellEcho: EventDefinition = EventDefinition(
        kind = EventKind.MOONWELL_ECHO,
        title = "月井回响",
        options = listOf(
            EventOption(id = "moonwell_echo.resonate", label = "共鸣月井（需要两档羁绊）", risk = EventRisk.NONE),
            EventOption(id = "moonwell_echo.draw_echo", label = "抽取低语回响", risk = EventRisk.NONE),
            EventOption(id = "moonwell_echo.leave", label = "离开", risk = EventRisk.NONE)
        )
    )

'''

anchor = "    /** 全部八类事件定义，顺序即文档表格顺序。 */"
assert anchor in s
s = s.replace(anchor, new_events + anchor, 1)

old_all = """        techTerminal,
        fieldClinic,
        blocker
    )"""
new_all = """        techTerminal,
        fieldClinic,
        blocker,
        frozenShrine,
        warBannerRuins,
        rovingCaravan,
        ropewayBridge,
        pactAltar,
        alchemyLab,
        hunterCamp,
        moonwellEcho
    )"""
assert old_all in s
s = s.replace(old_all, new_all, 1)

old_kinds = """enum class EventKind {
    ABANDONED_DEPOT,
    RUSTED_WORKSHOP,
    RUINED_LIBRARY,
    COLLAPSED_STORAGE,
    SAFE_WRECKAGE,
    TECH_TERMINAL,
    FIELD_CLINIC,
    BLOCKER
}"""
new_kinds = """enum class EventKind {
    ABANDONED_DEPOT,
    RUSTED_WORKSHOP,
    RUINED_LIBRARY,
    COLLAPSED_STORAGE,
    SAFE_WRECKAGE,
    TECH_TERMINAL,
    FIELD_CLINIC,
    BLOCKER,

    // 0.1.5 新增八类
    FROZEN_SHRINE,
    WAR_BANNER_RUINS,
    ROVING_CARAVAN,
    ROPEWAY_BRIDGE,
    PACT_ALTAR,
    ALCHEMY_LAB,
    HUNTER_CAMP,
    MOONWELL_ECHO
}"""
assert old_kinds in s
s = s.replace(old_kinds, new_kinds, 1)

io.open(p, "w", encoding="utf-8", newline="\n").write(s)
print("events added")
