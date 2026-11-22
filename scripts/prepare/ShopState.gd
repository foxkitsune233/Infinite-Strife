class_name ShopState
extends RefCounted

## Persisted as quest_progress.shop. Android ShopSnapshot + stock/clock.
var revision: int = 0
var purchase_serial: int = 0
var offers: Array[Dictionary] = []
var next_seed: int = 1729
var purchases: Dictionary = {}
var stock: Dictionary = {}
var cycle: int = 0
var seen_ms: int = 0

func to_data() -> Dictionary:
	return {"version": 1, "revision": revision, "purchase_serial": purchase_serial,
		"next_seed": next_seed, "offers": offers.duplicate(true),
		"purchases": purchases.duplicate(true), "stock": stock.duplicate(true),
		"cycle": cycle, "seen_ms": seen_ms}

func load_data(data: Dictionary) -> void:
	revision = int(data.get("revision", 0))
	purchase_serial = int(data.get("purchase_serial", 0))
	next_seed = int(data.get("next_seed", 1729))
	cycle = int(data.get("cycle", 0))
	seen_ms = int(data.get("seen_ms", 0))
	offers.clear()
	for offer in data.get("offers", []):
		offers.append(offer.duplicate(true))
	purchases = data.get("purchases", {}).duplicate(true)
	stock = data.get("stock", {}).duplicate(true)
