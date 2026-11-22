class_name ItemInstance
extends RefCounted

enum Kind {
	EQUIPMENT,
	CARD,
	MATERIAL,
	CONSUMABLE,
}

enum Location {
	WAREHOUSE,
	BACKPACK,
	SAFE,
	EQUIPMENT,
	CARD,
}

var id: String
var definition_id: String
var kind: Kind
var location: Location = Location.WAREHOUSE
var slot: int = -1
var locked := false
var bound := false
var star := 1
var footprint := Vector2i(1, 1)
var grid_position := Vector2i.ZERO
var rotated := false
var value := 0
## 地面掉落在鉴定完成前不展示名称与属性。
var identified := true

func _init(p_id: String, p_definition_id: String, p_kind: Kind) -> void:
	id = p_id
	definition_id = p_definition_id
	kind = p_kind
