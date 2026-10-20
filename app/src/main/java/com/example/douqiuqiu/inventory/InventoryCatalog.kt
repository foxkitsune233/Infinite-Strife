package com.example.douqiuqiu.inventory

import android.content.Context
import org.json.JSONObject
import java.util.UUID

/** Separate v1 catalog: legacy simulation data is not silently reinterpreted. */
class InventoryCatalog(context: Context) {
    private val json = context.assets.open("config/inventory.json").bufferedReader().use { JSONObject(it.readText()) }
    val rules: InventoryRules
    val crafting: CraftingRules
    init {
        val array = json.getJSONArray("definitions")
        val definitions = (0 until array.length()).map { index ->
            val d = array.getJSONObject(index)
            val s = d.optJSONObject("stats") ?: JSONObject()
            ItemDefinition(d.getString("id"), d.getString("name"), ItemKind.valueOf(d.getString("kind")),
                d.getInt("width"), d.getInt("height"), d.getString("quality"),
                d.optInt("maxStack", 1), d.optBoolean("combatUsable", false),
                EquipmentStats(s.optDouble("strength", 0.0), s.optDouble("agility", 0.0), s.optDouble("intelligence", 0.0),
                    s.optDouble("health", 0.0), s.optDouble("healthRegen", 0.0), s.optDouble("armor", 0.0),
                    s.optDouble("attackSpeed", 0.0), s.optDouble("manaPercent", 0.0), s.optDouble("manaRegen", 0.0), s.optDouble("attack", 0.0)),
                d.optInt("price", 0), d.optInt("shopStock", 0),
                com.example.douqiuqiu.shop.ShopShelf.valueOf(d.optString("shopShelf", "STAPLE")),
                d.optInt("minimumShopClears", 0), d.optBoolean("boundOnAcquire", false), d.optInt("minimumDropClears", 0))
        }
        require(definitions.map { it.id }.toSet().size == definitions.size) { "物品定义重复" }
        val additionalCards = com.example.douqiuqiu.cards.CardCombat.enabledCards.filter { card -> definitions.none { it.id == card.id } }.map { card ->
            ItemDefinition(card.id, card.name, ItemKind.CARD, card.width, card.height, card.quality.label,
                price = card.price, shopStock = card.shopStock,
                shopShelf = com.example.douqiuqiu.shop.ShopShelf.valueOf(card.shopShelf),
                minimumShopClears = card.minimumShopClears, minimumDropClears = card.minimumDropClears)
        }
        val additionalGear = com.example.douqiuqiu.content.EquipmentCatalog.all.filter { gear -> definitions.none { it.id == gear.id } }.map { gear ->
            val kind = if (gear.category == com.example.douqiuqiu.content.EquipmentCategory.SCROLL) ItemKind.SCROLL else ItemKind.EQUIPMENT
            val gate = if (kind == ItemKind.SCROLL) com.example.douqiuqiu.content.EquipmentCatalog.all
                .filter { it.recipe?.ingredients?.containsKey(gear.id) == true }.minOfOrNull { it.minimumShopClears } ?: gear.minimumShopClears else gear.minimumShopClears
            ItemDefinition(gear.id, gear.name, kind, gear.width, gear.height, gear.quality.label,
                stats = gear.stats ?: EquipmentStats(), price = gear.price, shopStock = if (kind == ItemKind.SCROLL) 1 else gear.shopStock,
                shopShelf = com.example.douqiuqiu.shop.ShopShelf.valueOf(gear.shopShelf),
                minimumShopClears = gate, minimumDropClears = gate)
        }
        val valuables = com.example.douqiuqiu.loot.ValuableLootCatalog.definitions
        val allDefinitions = definitions + additionalCards + additionalGear + valuables
        require(allDefinitions.map { it.id }.distinct().size == allDefinitions.size) { "扩展物品定义重复" }
        rules = InventoryRules(allDefinitions.associateBy { it.id })
        val recipes = json.getJSONArray("recipes")
        val recipeList = (0 until recipes.length()).map { i ->
            val recipe = recipes.getJSONObject(i)
            val ingredients = recipe.getJSONObject("ingredients")
            CraftingRecipe(recipe.getString("id"), recipe.getString("outputId"), ingredients.keys().asSequence().associateWith { ingredients.getInt(it) })
        }
        val allRecipes = recipeList + com.example.douqiuqiu.content.EquipmentCatalog.recipes
        val report = RecipeGraphValidator.validate(rules.definitions.keys, allRecipes)
        require(report.valid) { "装备配方无效：${report.issues}" }
        crafting = CraftingRules(rules, allRecipes)
    }

    fun initial(): InventoryState {
        val starter = json.getJSONArray("starterItems")
        return InventoryState(gold = json.getInt("initialGold"), items = (0 until starter.length()).map { index ->
            StoredItem(UUID.randomUUID().toString(), starter.getString(index), ItemPlace(Container.WAREHOUSE), bound = true)
        })
    }
}
