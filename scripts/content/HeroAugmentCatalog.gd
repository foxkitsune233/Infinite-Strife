extends RefCounted
## Effects follow the owner's Android M4 design, not live Dota item rules.
const SHARDS := {
	"juggernaut": "暴击造成的生命伤害额外提供40%吸血。",
	"skywrath": "大招无固定魔耗；每秒消耗10点魔法并逐秒翻倍，魔法耗尽或20秒后结束。",
	"phantom_assassin": "获得30%闪避，与装备闪避组合。",
	"abaddon": "普攻附加12点冰霜伤害，15%概率冻结目标0.5秒。",
	"necrophos": "竭心光环造成的生命扣除提供50%治疗。",
	"viper": "毒素额外降低目标3点护甲，持续3秒。",
	"anti_mage": "额外降低15%魔法与生命扣除伤害，并反射本场首次敌方技能。",
	"faceless_void": "每6秒延长敌方冷却1秒，并造成每秒8点伤害，持续3秒。",
	"axe": "战斗饥渴每秒造成8点伤害，并使目标受到的伤害提高10%，持续3秒。",
	"legion_commander": "每8秒强驱散自身。",
}
const AGHANIMS := {
	"juggernaut": "小无敌斩：持续1秒的无敌与快速攻击。",
	"skywrath": "沉默目标2秒。",
	"phantom_assassin": "连续5次必定暴击攻击，随后5秒不能触发普攻暴击。",
	"abaddon": "预先开启回光返照：致命伤害触发1秒伤害转治疗窗口。",
	"necrophos": "死亡脉冲：造成200%攻击力的魔法伤害，实际生命伤害等量治疗自身。",
	"viper": "5秒减伤30%，当前魔法提升50%（不超过上限）。",
	"anti_mage": "0.5秒技能反射窗口。",
	"faceless_void": "回到2秒前的生命值，并强驱散自身。",
	"axe": "造成400点纯粹伤害。",
	"legion_commander": "造成150点物理伤害，并获得50攻速，持续3秒。",
}
static func owned(state: PreparationState, hero: String, kind: String) -> bool:
	return hero in state.quest_progress.get("tower_shard" if kind == "shard" else "tower_aghs", [])
static func summary(state: PreparationState, hero: String) -> String:
	return "魔晶：%s\n神杖：%s" % [SHARDS.get(hero, "") if owned(state, hero, "shard") else "未授予", AGHANIMS.get(hero, "") if owned(state, hero, "aghanim") else "未授予"]
