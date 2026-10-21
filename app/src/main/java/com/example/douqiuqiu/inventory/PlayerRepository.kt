package com.example.douqiuqiu.inventory

import com.example.douqiuqiu.quest.*

import android.content.Context
import com.example.douqiuqiu.journey.JourneyRules
import java.io.File

/** One persisted profile for preparation and challenges; never uses separate run wallets. */
class PlayerRepository(context: Context) {
    val catalog = InventoryCatalog(context)
    val journey = JourneyRules(catalog.rules)
    val economy = HomeEconomyRules(catalog.rules)
    val growth = GrowthRules(catalog.rules)
    private val file = TestProfile.file(context)
    val store = InventoryStore(file, catalog.rules, catalog::initial)
    fun load(): LoadedInventory {
        val loaded = store.load()
        var state = loaded.state
        state = economy.recover(state, System.currentTimeMillis().coerceAtLeast(0))
        state = journey.ensureBatch(state)
        state = journey.battleLoot(state)
        state = QuestTracker.observe(state)
        if (state != loaded.state || !file.exists() && !loaded.recoveredFromBackup) store.save(state)
        return LoadedInventory(state, loaded.recoveredFromBackup)
    }
}
