class_name JourneyState
extends RefCounted

enum NodeType { BATTLE, EVENT, CHEST, BOSS, REST }
const DIFFICULTY_NAMES := ["简单", "普通", "困难", "精英", "大师", "噩梦", "炼狱"]

var seed: int
var difficulty := 0
var run_gold := 0
var current_node := 0
var nodes: Array[Dictionary] = []
var loot: Array[ItemInstance] = []
var appraisal_ms := 0.0
var reveal_at := {}
var resolved := false
var battle_started := false
var frozen_event_id := ""
var frozen_event_options: Array[Dictionary] = []
var event_result := {}
var drop_serial := 0
var battle_snapshot: Dictionary = {}
var player_hp_ratio := 1.0
var ending := ""
var target_chest: Dictionary = {}
## 最近生成的事件 kind 列表，用于"最近两次不重复"去重（对齐 Android JourneyState.recentEventKinds）。
var recent_event_kinds: Array[String] = []
## 确定性随机序列的当前游标，取代按节点重算的哈希，保证同种子跨端一致。
var event_rng := 0

func _init(p_seed: int) -> void:
	seed = p_seed
	event_rng = p_seed
	nodes = [
		{"type": NodeType.BATTLE, "label": "第一战"},
		{"type": NodeType.EVENT, "label": "沿途事件"},
		{"type": NodeType.BATTLE, "label": "第二战"},
		{"type": NodeType.EVENT, "label": "沿途事件"},
		{"type": NodeType.BATTLE, "label": "第三战"},
		{"type": NodeType.REST, "label": "休息或撤离"},
		{"type": NodeType.BATTLE, "label": "第四战"},
		{"type": NodeType.EVENT, "label": "沿途事件"},
		{"type": NodeType.BOSS, "label": "第五战"},
	]

func current() -> Dictionary:
	return nodes[current_node] if current_node >= 0 and current_node < nodes.size() else {}

func finished() -> bool:
	return not ending.is_empty() or current_node >= nodes.size()
