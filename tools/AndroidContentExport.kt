import com.example.douqiuqiu.content.*
import com.example.douqiuqiu.inventory.EquipmentStats
import com.example.douqiuqiu.loot.ValuableLootCatalog
import java.io.File
import java.lang.reflect.Modifier

/** Runs current shared content sources on the JVM; no Android or Gradle dependency. */
object AndroidContentExport {
    private fun quote(s: String): String = buildString {
        append('"')
        for (c in s) when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (c.code < 32) append("\\u%04x".format(c.code)) else append(c)
        }
        append('"')
    }

    // Java field reflection preserves every data-class field, including typed EffectValue
    // payloads and nullable cooldowns. Sort keys so JVM reflection order cannot affect bytes.
    private fun fields(value: Any): Map<String, Any?> = value.javaClass.declaredFields
        .filterNot { Modifier.isStatic(it.modifiers) || it.isSynthetic }
        .associate { it.isAccessible = true; it.name to it.get(value) }.toSortedMap()

    private fun json(value: Any?): String = when (value) {
        null -> "null"
        is String -> quote(value)
        is Boolean -> value.toString()
        is Number -> { require(value.toDouble().isFinite()); value.toString() }
        is Enum<*> -> quote(value.name)
        is Map<*, *> -> value.entries.sortedBy { it.key.toString() }
            .joinToString(",", "{", "}") { quote(it.key.toString()) + ":" + json(it.value) }
        is Set<*> -> value.sortedBy { it.toString() }.joinToString(",", "[", "]") { json(it) }
        is Iterable<*> -> value.joinToString(",", "[", "]") { json(it) }
        is EffectValue -> json(mapOf("type" to value.javaClass.simpleName, "value" to fields(value).getValue("value")))
        else -> json(fields(value))
    }

    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 1) { "Usage: AndroidContentExport output.json" }
        val definitions = EquipmentCatalog.all
        require(definitions.map { it.id }.distinct().size == definitions.size) { "Duplicate Kotlin equipment IDs" }
        require(EquipmentCatalog.recipes.map { it.id }.distinct().size == EquipmentCatalog.recipes.size) { "Duplicate Kotlin recipes" }
        val rows = definitions.sortedBy { it.id }.map { gear ->
            val effects = gear.effects + EquipmentCatalog.componentEffects[gear.id].orEmpty()
            require(effects.map { it.id }.distinct().size == effects.size) { "Duplicate effect IDs: ${gear.id}" }
            val gate = if (gear.category == EquipmentCategory.SCROLL) definitions
                .filter { it.recipe?.ingredients?.containsKey(gear.id) == true }
                .minOfOrNull { it.minimumShopClears } ?: gear.minimumShopClears else gear.minimumShopClears
            fields(gear) + mapOf(
                "source" to "EquipmentCatalog", "quality" to gear.quality.label,
                "qualityTier" to gear.quality.name,
                "kind" to if (gear.category == EquipmentCategory.SCROLL) "SCROLL" else "EQUIPMENT",
                "stats" to (gear.stats ?: EquipmentStats()), "statsDeclared" to (gear.stats != null),
                "effects" to effects, "active_effects" to effects.filter { it.trigger == EffectTrigger.ACTIVE },
                "maxStack" to 1, "combatUsable" to false, "boundOnAcquire" to false,
                "minimumDropClears" to gate, "minimumShopClears" to gate,
                "shopStock" to if (gear.category == EquipmentCategory.SCROLL) 1 else gear.shopStock,
                "contentShopStock" to gear.shopStock, "contentMinimumShopClears" to gear.minimumShopClears
            )
        }
        // CardCombat.enabledCards is CardCatalog.confirmedOnly. Export definitions and
        // paramsAt, without applying runtime special cases or implementing battle logic.
        val cards = CardCatalog.confirmedOnly.sortedBy { it.id }.map { card ->
            fields(card) + mapOf("quality" to card.quality.label, "qualityTier" to card.quality.name,
                "schools" to card.tags.sorted(), "stars" to (1..3).map { star ->
                    mapOf("star" to star, "effects" to card.effects.map { effect ->
                        fields(effect) + mapOf("params" to effect.paramsAt(star))
                    })
                })
        }
        val cardItems = CardCatalog.confirmedOnly.sortedBy { it.id }.map { card ->
            mapOf("id" to card.id, "name" to card.name, "kind" to "CARD", "width" to card.width,
                "height" to card.height, "quality" to card.quality.label, "price" to card.price,
                "shopShelf" to card.shopShelf, "shopStock" to card.shopStock,
                "minimumShopClears" to card.minimumShopClears, "minimumDropClears" to card.minimumDropClears)
        }
        val loot = ValuableLootCatalog.entries.sortedBy { it.definition.id }.map {
            fields(it.definition) + mapOf("description" to it.description)
        }
        require(cards.map { it.getValue("id") }.distinct().size == cards.size) { "Duplicate cards" }
        File(args[0]).writeText(json(mapOf("items" to rows, "recipes" to EquipmentCatalog.recipes.sortedBy { it.id },
            "cards" to cards, "card_items" to cardItems, "loot_items" to loot,
            "schools" to School.entries.map { mapOf("id" to it.id, "name" to it.label) },
            "synergy_tiers" to CardCatalog.synergyTiers)) + "\n", Charsets.UTF_8)
        println("Evaluated ${rows.size} current Kotlin equipment definitions, ${EquipmentCatalog.recipes.size} recipes")
    }
}
