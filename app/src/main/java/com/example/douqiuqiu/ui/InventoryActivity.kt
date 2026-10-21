package com.example.douqiuqiu.ui

import android.graphics.Color
import android.graphics.Typeface
import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import android.view.DragEvent
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.douqiuqiu.inventory.*
import com.example.douqiuqiu.journey.SupplyRules
import com.example.douqiuqiu.journey.NodeLootRules
import com.example.douqiuqiu.journey.FirstRunContent
import com.example.douqiuqiu.journey.HeroStats
import com.example.douqiuqiu.journey.StatValue
import com.example.douqiuqiu.growth.GrowthContent
import com.example.douqiuqiu.shop.ShopShelf
import java.util.UUID
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import com.example.douqiuqiu.ui.components.GameUi
import com.example.douqiuqiu.ui.components.InventoryBoardView
import com.example.douqiuqiu.ui.components.ItemDragShadow
import com.example.douqiuqiu.ui.components.GameArt
import com.example.douqiuqiu.ui.components.ItemArtwork
import com.example.douqiuqiu.ui.components.PaintedItemIcons
import com.example.douqiuqiu.ui.components.IconListAdapter
import com.example.douqiuqiu.ui.components.GameCatalogSheet

/** Functional first preparation screen; all edits commit to disk before updating UI. */
class InventoryActivity : AppCompatActivity() {
    private lateinit var rules: InventoryRules
    private lateinit var store: InventoryStore
    private lateinit var state: InventoryState
    private lateinit var content: LinearLayout
    private lateinit var repository: PlayerRepository
    private lateinit var shell: GameUi.Shell
    private lateinit var fixed: LinearLayout
    private lateinit var filterHost: LinearLayout
    private var selectedId: String? = null
    private var placementRotated = false
    private var warehouseFilter: ItemKind? = null
    private var preparationTab = 0
    private var hasResumed = false
    private var restoredScroll = 0
    private var dragFailureShown = false
    private var previewDialog: android.app.Dialog? = null
    private var dragInProgress = false
    private var deferredLootRefresh = false
    private var showSafe = false
    private var batchMode = false
    private val saleSelection = linkedSetOf<String>()
    private var finishingLoot = false
    private val boards = mutableListOf<InventoryBoardView>()
    private val warehouseTiles = mutableMapOf<String, View>()
    private val ink = GameUi.ink
    private val accent = GameUi.gold
    private fun loadouts() = LoadoutRules(rules)
    private fun currentHeroId() = state.progress.run?.heroId ?: state.progress.activeHeroId
    private fun unitCellDp(): Int {
        val c = resources.configuration
        val paneWidth = if (c.screenWidthDp > c.screenHeightDp) (c.screenWidthDp * .48f).toInt() else c.screenWidthDp
        return ((paneWidth - 40) / 6).coerceIn(36, 60)
    }
    /** Equipment is a quick loadout summary; it must never compete with the pack for screen height. */
    private fun equipmentCellDp(): Int = unitCellDp().coerceAtMost(42)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedId = savedInstanceState?.getString("selectedItem")
        placementRotated = savedInstanceState?.getBoolean("placementRotated") ?: false
        preparationTab = (savedInstanceState?.getInt("preparationTab") ?: intent.getIntExtra("preparationTab", 0)).coerceIn(0, 2)
        warehouseFilter = savedInstanceState?.getString("warehouseFilter")?.let { saved -> ItemKind.entries.find { it.name == saved } }
        restoredScroll = savedInstanceState?.getInt("warehouseScroll") ?: 0
        try {
            repository = PlayerRepository(this)
            val catalog = repository.catalog
            rules = catalog.rules
            store = repository.store
            val loaded = repository.load()
            state = loaded.state
            if (savedInstanceState == null && state.progress.run?.nodeLoot != null) preparationTab = 2
            render()
            if (loaded.recoveredFromBackup) AlertDialog.Builder(this).setTitle("已恢复备份")
                .setMessage("主存档读取失败，已恢复上一次有效存档。请检查物品，原文件仍保留。")
                .setPositiveButton("知道了", null).show()
        } catch (e: Exception) {
            AlertDialog.Builder(this).setTitle("暂时无法打开仓库")
                .setMessage("${e.message ?: "读取失败"}\n未重置或覆盖存档。")
                .setCancelable(false).setPositiveButton("返回") { _, _ -> finish() }.show()
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasResumed && ::repository.isInitialized && ::state.isInitialized) {
            try { state = repository.load().state; render() }
            catch (e: Exception) { message("无法重新读取存档：${e.message}"); finish() }
        }
        hasResumed = true
    }

    private fun render() {
        if (leaveIfLootEmpty()) return
        val scrollPosition = if (::shell.isInitialized) shell.scroll.scrollY else restoredScroll
        boards.clear()
        warehouseTiles.clear()
        if (selectedId?.let(::findDragged) == null) selectedId = null
        shell = GameUi.screen(this, if (state.progress.run?.nodeLoot != null) "掉落与整备" else "仓库与整备", "${if (state.phase == InventoryPhase.HOME) state.gold else state.runGold} 金币  ·  技能栏 ${state.skillSlots}/8  ·  ${if (state.phase == InventoryPhase.HOME) "营地" else "本局资金"}", listOf(
            GameUi.Navigation("远征") { startActivity(Intent(this, JourneyActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)); finish() },
            GameUi.Navigation("整备", true) { shell.scroll.smoothScrollTo(0, 0) },
            GameUi.Navigation("营地") { startActivity(Intent(this, com.example.douqiuqiu.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)); finish() }
        ), compact = true)
        content = shell.body
        // Only the warehouse scrolls. Slot hit targets remain stationary during drag and selection.
        fixed = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(10), dp(4), dp(10), dp(4))
            background = GameUi.frame(this@InventoryActivity, GameUi.background, GameUi.line)
        }
        val root = shell.scroll.parent as LinearLayout
        root.setOnDragListener { _, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> { dragFailureShown = false; dragInProgress = event.localState is String; dragInProgress }
                DragEvent.ACTION_DROP -> false
                DragEvent.ACTION_DRAG_ENDED -> {
                    dragInProgress = false
                    if (!event.result && !dragFailureShown) message("未放置，物品仍在原位；移到目标栏位出现绿色提示后再松手")
                    if (deferredLootRefresh) { deferredLootRefresh = false; window.decorView.post { if (!isFinishing && !isDestroyed) render() } }
                    true
                }
                else -> true
            }
        }
        fixed.visibility = shell.body.visibility
        val sideBySide = resources.configuration.screenWidthDp > resources.configuration.screenHeightDp
        if (sideBySide) {
            val index = root.indexOfChild(shell.scroll)
            root.removeView(shell.scroll)
            val split = LinearLayout(this)
            root.addView(split, index, LinearLayout.LayoutParams(-1, 0, 1f))
            split.addView(fixed, LinearLayout.LayoutParams(0, -1, .48f))
            filterHost = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = shell.body.visibility }
            split.addView(filterHost, LinearLayout.LayoutParams(0, -1, .52f))
            filterHost.addView(shell.scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        } else {
            root.addView(fixed, root.indexOfChild(shell.scroll))
            filterHost = fixed
        }
        val tabs = LinearLayout(this)
        listOf("配装", "技能 ${state.skillSlots}/8", "物资").forEachIndexed { index, title ->
            tabs.addView(compactButton(title, preparationTab == index) {
                preparationTab = index; warehouseFilter = when (index) { 0 -> ItemKind.EQUIPMENT; 1 -> ItemKind.CARD; else -> null }
                render(); shell.scroll.post { shell.scroll.scrollTo(0, 0) }
            }, LinearLayout.LayoutParams(0, dp(36), 1f).apply { setMargins(dp(1), 0, dp(1), 0) })
        }
        tabs.addView(compactButton("更多") {
            GameUi.sheet(this, "整备工具", "${state.gold}金币", listOf("补给商店", "装备合成", "永久成长", "委托任务", "英雄等级", "补齐空装备与技能栏")) { index ->
                when (index) { 0 -> shop(); 1 -> crafting(); 2 -> growth(); 3 -> quests(); 4 -> heroLevels(); 5 -> if (state.phase == InventoryPhase.HOME) autoEquip() else message("挑战中不能访问仓库") }
            }
        }, LinearLayout.LayoutParams(dp(48), dp(36)))
        fixed.addView(tabs)
        when (preparationTab) {
            0 -> {
                val row = LinearLayout(this).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
                val heroId = currentHeroId()
                val hero = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = android.view.Gravity.CENTER }
                val portraitSide = if (sideBySide) 60 else 80
                val portrait = if (heroId == "juggernaut") android.graphics.BitmapFactory.decodeResource(resources, com.example.douqiuqiu.R.drawable.ic_launcher_portrait) else GameArt.portrait(this, heroId)
                portrait?.let { bitmap ->
                    hero.addView(GameArt.image(this, bitmap, "英雄预览").apply {
                        // Avatar viewport shows the upper part, never expands the fixed panel.
                        scaleType = ImageView.ScaleType.MATRIX
                        val scale = maxOf(dp(portraitSide).toFloat() / bitmap.width, dp(portraitSide).toFloat() / (bitmap.height * if (heroId == "juggernaut") 1f else .48f))
                        imageMatrix = android.graphics.Matrix().apply { setScale(scale, scale); postTranslate((dp(portraitSide) - bitmap.width * scale) / 2f, 0f) }
                        setOnClickListener { chooseHero() }
                    }, LinearLayout.LayoutParams(dp(portraitSide), dp(portraitSide)))
                }
                hero.addView(GameUi.label(this, if (state.phase == InventoryPhase.HOME) "点击切换" else "本局锁定", 10, GameUi.gold))
                row.addView(hero, LinearLayout.LayoutParams(dp(if (sideBySide) 60 else 86), -2))
                val equipment = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                val presetRow = LinearLayout(this)
                (0 until LoadoutRules.PRESET_COUNT).forEach { slot ->
                    presetRow.addView(presetChip(heroId, slot), LinearLayout.LayoutParams(0, dp(28), 1f).apply { setMargins(dp(1), 0, dp(1), dp(2)) })
                }
                val presetTools = LinearLayout(this)
                presetTools.addView(presetRow, LinearLayout.LayoutParams(0, dp(36), 1f))
                presetTools.addView(compactButton("保存") { savePreset() }.apply { contentDescription = "保存当前英雄的装备与技能卡套装" }, LinearLayout.LayoutParams(dp(48), dp(36)).apply { leftMargin = dp(3) })
                presetTools.addView(compactButton("属性") { showHeroStats() }, LinearLayout.LayoutParams(dp(44), dp(36)).apply { leftMargin = dp(3) })
                equipment.addView(presetTools)
                board(Container.EQUIPMENT, "", equipment, false, equipmentCellDp())
                row.addView(equipment, LinearLayout.LayoutParams(0, -2, 1f))
                fixed.addView(row)
            }
            1 -> {
                board(Container.SKILLS, "", fixed, false, unitCellDp())
                val tools = LinearLayout(this)
                tools.addView(compactButton("羁绊效果") { synergySummary() }, LinearLayout.LayoutParams(0, dp(36), 1f))
                tools.addView(compactButton("保存套装") { savePreset() }, LinearLayout.LayoutParams(0, dp(36), 1f))
                fixed.addView(tools)
                fixed.addView(GameUi.label(this, com.example.douqiuqiu.ui.components.SynergySheet.compactSummary(state), 11, GameUi.gold).apply {
                    setOnClickListener { synergySummary() }; contentDescription = text.toString() + "，点击查看全部羁绊收益"
                })
            }
            else -> {
                val tabs = LinearLayout(this)
                tabs.addView(compactButton("背包 ${state.backpackWidth}×${state.backpackHeight}", !showSafe) { showSafe = false; render() }, LinearLayout.LayoutParams(0, dp(32), 1f))
                tabs.addView(compactButton("安全箱 ${state.safeWidth}×${state.safeHeight}", showSafe) { showSafe = true; render() }, LinearLayout.LayoutParams(0, dp(32), 1f))
                val container = if (showSafe) Container.SAFE else Container.BACKPACK
                tabs.addView(compactButton("整理") { organize(container) }, LinearLayout.LayoutParams(dp(48), dp(32)))
                fixed.addView(tabs)
                board(container, "", fixed, false, unitCellDp())
                fixed.addView(GameUi.label(this, if (showSafe) "死亡保留 · 存放不生效" else "死亡掉落 · 落点自动尝试旋转", 10, GameUi.muted))
            }
        }
        warehouse()
        updateSelection()
        shell.scroll.post { shell.scroll.scrollTo(0, scrollPosition) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("selectedItem", selectedId); outState.putBoolean("placementRotated", placementRotated)
        outState.putInt("preparationTab", preparationTab)
        outState.putString("warehouseFilter", warehouseFilter?.name)
        outState.putInt("warehouseScroll", if (::shell.isInitialized) shell.scroll.scrollY else restoredScroll)
        super.onSaveInstanceState(outState)
    }
    private fun ground(id: String) = state.progress.run?.nodeLoot?.remaining?.find { it.instanceId == id }
    private fun findDragged(id: String): StoredItem? = state.items.find { it.instanceId == id && (state.phase == InventoryPhase.HOME || it.place.container != Container.WAREHOUSE) } ?: ground(id)
    private fun selected() = selectedId?.let(::findDragged)
    private fun moveItem(id: String, target: ItemPlace) = if (ground(id) != null) NodeLootRules.collect(state, rules, id, target) else rules.move(state, id, target)
    private fun select(item: StoredItem) {
        if (item.place.container == Container.WAREHOUSE && state.phase != InventoryPhase.HOME && ground(item.instanceId) == null) { message("挑战中不能访问局外仓库"); return }
        if (selectedId != item.instanceId) placementRotated = item.place.rotated
        selectedId = item.instanceId
        updateSelection(); boards.forEach { it.invalidate() }
    }
    private fun updateSelection() {
        shell.accessory.removeAllViews()
        val itemsById = state.items.associateBy { it.instanceId }
        warehouseTiles.forEach { (id, view) ->
            view.isSelected = if (batchMode) id in saleSelection else id == selectedId
            val quality = (itemsById[id] ?: ground(id))?.takeIf { view.tag != false }?.let { GameUi.quality(rules.definitions.getValue(it.definitionId).quality) } ?: GameUi.line
            view.background = GameUi.shape(this, if (view.isSelected) GameUi.raised else GameUi.surface,
                if (view.isSelected) GameUi.gold else quality)
        }
        if (state.progress.run?.nodeLoot != null) {
            val lootActions = LinearLayout(this)
            lootActions.addView(compactButton("一键放入") { collectRevealedLoot() }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { rightMargin = dp(3) })
            lootActions.addView(compactButton("返回远征") { returnToJourney() }, LinearLayout.LayoutParams(0, dp(40), 1f))
            shell.accessory.addView(lootActions)
        }
        if (batchMode) {
            val sellable = saleCandidates()
            saleSelection.retainAll(sellable.map { it.instanceId }.toSet())
            val total = sellable.filter { it.instanceId in saleSelection }.sumOf { SupplyRules(rules).saleValue(it) }
            val row = LinearLayout(this)
            row.addView(compactButton("出售${saleSelection.size}件 · $total 金币", true) { confirmBulkSale() }, LinearLayout.LayoutParams(0, dp(42), 1f))
            row.addView(compactButton("退出批售") { batchMode = false; saleSelection.clear(); render() }, LinearLayout.LayoutParams(dp(82), dp(42)))
            shell.accessory.addView(row)
            return
        }
        val item = selected()
        if (item == null) {
            shell.accessory.addView(GameUi.label(this, if (state.phase == InventoryPhase.BATTLE) "战斗中位置锁定 · 点按查看" else "点按预览 · 长按拖动 · 空格放置", 11, GameUi.muted))
            return
        }
        val def = rules.definitions.getValue(item.definitionId)
        val row = LinearLayout(this).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
        row.addView(GameUi.label(this, "${def.name}\n${if (placementRotated) def.height else def.width}×${if (placementRotated) def.width else def.height}", 11, GameUi.quality(def.quality), true).apply {
            maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0, -2, 1f))
        listOf<Pair<String, () -> Unit>>("↻ 90°" to {
            rotateSelected()
        }, "预览" to { selected()?.let(::previewItem) }, "取消" to {
            selectedId = null; updateSelection(); boards.forEach { it.invalidate() }
        }).forEach { (label, action) -> row.addView(compactButton(label, block = action), LinearLayout.LayoutParams(dp(48), dp(40))) }
        shell.accessory.addView(row)
    }
    private fun board(container: Container, detail: String, parent: LinearLayout = content, showTitle: Boolean = true, maxCell: Int = 48) {
        if (showTitle) GameUi.section(parent, label(container), detail)
        val view = InventoryBoardView(this, container, state, rules, ::selected, { placementRotated }, ::select,
            { id, target ->
                val previousRotation = placementRotated
                placementRotated = target.rotated
                if (!commit(moveItem(id, target))) { placementRotated = previousRotation; updateSelection() }
            }, {
                val items = state.items.filter { it.place.container == container }
                AlertDialog.Builder(this).setTitle(label(container)).setItems(items.map { rules.definitions.getValue(it.definitionId).name + " · " + position(it) }.toTypedArray()) { _, index -> edit(items[index]) }.show()
            }, maxCell, ::previewItem, { reason -> dragFailureShown = true; message(reason) }, ::findDragged, ::moveItem)
        boards += view; parent.addView(view, LinearLayout.LayoutParams(-1, -2))
    }
    private fun warehouse() {
        val home = state.phase == InventoryPhase.HOME
        var insertAt = if (filterHost === fixed) filterHost.childCount else 0
        val loot = state.progress.run?.nodeLoot
        if (loot != null) {
            if (loot.inspectionMs < NodeLootRules.duration(loot, rules.definitions)) {
                val header = com.example.douqiuqiu.ui.components.NodeLootHeaderView(this, { state.progress.run?.nodeLoot }, rules.definitions, ::advanceLootInspection)
                content.addView(header, LinearLayout.LayoutParams(-1, dp(56)))
            }
        }
        val warehouseHeading = LinearLayout(this).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
        warehouseHeading.addView(GameUi.label(this, if (home) "局外仓库" else if (loot != null) "地面掉落 · 拖入上方背包后获得" else "随身物品 · 局外仓库封存", 12, GameUi.gold, true), LinearLayout.LayoutParams(0, -2, 1f))
        if (home) warehouseHeading.addView(compactButton("一键整理") {
            val result = WarehouseOrder.organize(state, rules.definitions)
            if (result.ok) { commit(result); shell.scroll.post { shell.scroll.scrollTo(0, 0) }; message("已按种类、品质和名称整理仓库") }
            else message(result.error ?: "无法整理仓库")
        }, LinearLayout.LayoutParams(dp(76), dp(34)))
        filterHost.addView(warehouseHeading, insertAt)
        val filters = LinearLayout(this)
        listOf("全部" to null, "装备" to ItemKind.EQUIPMENT, "卡牌" to ItemKind.CARD, "材料" to ItemKind.MATERIAL, "战利品" to ItemKind.LOOT).forEach { (name, kind) ->
            filters.addView(compactButton(name, warehouseFilter == kind) {
                warehouseFilter = kind; render(); shell.scroll.post { shell.scroll.scrollTo(0, 0) }
            }, LinearLayout.LayoutParams(0, dp(32), 1f))
        }
        filterHost.addView(filters, insertAt + 1)
        if (home) {
            val tools = LinearLayout(this)
            tools.addView(compactButton("商店") { shop() }, LinearLayout.LayoutParams(0, dp(32), 1f))
            tools.addView(compactButton("合成表") { crafting() }, LinearLayout.LayoutParams(0, dp(32), 1f))
            tools.addView(compactButton(if (batchMode) "取消批售" else "批量出售", batchMode) {
                batchMode = !batchMode; saleSelection.clear(); render()
            }, LinearLayout.LayoutParams(0, dp(32), 1f))
            filterHost.addView(tools, insertAt + 2)
        }
        // The entire lower pane is a return-to-warehouse target, including empty space.
        shell.scroll.setOnDragListener { _, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> (home || loot != null) && event.localState is String
                DragEvent.ACTION_DROP -> {
                    val id = event.localState as? String
                    if (id != null) commit(if (home) rules.move(state, id, ItemPlace(Container.WAREHOUSE)) else NodeLootRules.putDown(state, rules, id))
                    id != null
                }
                else -> true
            }
        }
        // Loot pages deliberately keep the floor visible: the pack and safe-box already live in the stationary upper panel.
        val groundMode = loot != null
        val items = if (groundMode) loot!!.remaining.filter { warehouseFilter == null || rules.definitions.getValue(it.definitionId).kind == warehouseFilter } else state.items.filter {
            (if (home) it.place.container == Container.WAREHOUSE else it.place.container == Container.BACKPACK || it.place.container == Container.SAFE) &&
                (warehouseFilter == null || rules.definitions.getValue(it.definitionId).kind == warehouseFilter)
        }
        if (items.isEmpty()) content.addView(GameUi.label(this, "此分类暂无物品 · 可切换分类", 13, GameUi.muted))
        if (batchMode) {
            val selectionTools = LinearLayout(this)
            selectionTools.addView(compactButton("选择当前分类全部可售物") {
                saleSelection.addAll(saleCandidates().filter { warehouseFilter == null || rules.definitions.getValue(it.definitionId).kind == warehouseFilter }.map { it.instanceId }); updateSelection()
            }, LinearLayout.LayoutParams(0, dp(36), 1f))
            selectionTools.addView(compactButton("清空") { saleSelection.clear(); updateSelection() }, LinearLayout.LayoutParams(dp(60), dp(36)))
            content.addView(selectionTools)
        }
        content.addView(GameUi.label(this, "6列真实占格 · 每格与随身容器等大", 10, GameUi.muted))
        val shelf = com.example.douqiuqiu.ui.components.InventoryShelfView(this, unitCellDp())
        items.forEach { item ->
                val def = rules.definitions.getValue(item.definitionId)
                val tile = FrameLayout(this).apply { setPadding(dp(4), dp(4), dp(4), dp(4)); isFocusable = true }
                val revealed = !groundMode || NodeLootRules.revealed(loot!!, item.instanceId, rules.definitions)
                if (revealed) tile.addView(ItemArtwork.view(this, def), FrameLayout.LayoutParams(-1, -1))
                else tile.addView(GameUi.label(this, "?", 24, GameUi.muted, true).apply { gravity = android.view.Gravity.CENTER }, FrameLayout.LayoutParams(-1, -1))
                val badges = listOfNotNull("${def.width}×${def.height}", if (item.bound) "绑" else null,
                    if (def.kind == ItemKind.CARD) "★".repeat(item.star) else if (item.count > 1) "×${item.count}" else null).joinToString(" ")
                if (revealed && badges.isNotEmpty()) tile.addView(GameUi.label(this, badges, 9, GameUi.gold, true).apply {
                    setBackgroundColor(0xCC051F2F.toInt()); gravity = android.view.Gravity.CENTER
                }, FrameLayout.LayoutParams(-1, -2, android.view.Gravity.BOTTOM))
                tile.contentDescription = if (revealed) "${def.name}，${if (groundMode) "待拾取" else position(item)}，${if (item.bound) "绑定" else "可出售"}，${item.star}星，数量${item.count}。点按预览，长按拖动。" else "待鉴定物品，${def.width}乘${def.height}格"
                if (item.instanceId in state.progress.lockedItems) tile.addView(GameUi.label(this, "锁", 10, GameUi.gold, true).apply {
                    setBackgroundColor(0xDD051F2F.toInt())
                }, FrameLayout.LayoutParams(dp(22), dp(22), android.view.Gravity.TOP or android.view.Gravity.START))
                if (revealed && def.width != def.height && !batchMode) tile.addView(GameUi.label(this, "↻", 18, GameUi.gold, true).apply {
                    gravity = android.view.Gravity.CENTER; setBackgroundColor(0xDD051F2F.toInt())
                    contentDescription = "旋转${def.name}90度后放置"
                    setOnClickListener { select(item); rotateSelected() }
                }, FrameLayout.LayoutParams(dp(30), dp(28), android.view.Gravity.TOP or android.view.Gravity.END))
                tile.setOnClickListener {
                    if (batchMode) {
                        if (item !in saleCandidates()) message("锁定、绑定或非仓库物品不可批售")
                        else { if (!saleSelection.add(item.instanceId)) saleSelection.remove(item.instanceId); updateSelection() }
                    } else if (revealed) { select(item); previewItem(item) } else message("鉴定尚未完成")
                }
                val beginDrag: () -> Boolean = {
                    if (batchMode) { message("批售模式请点选物品；退出批售后可拖动"); false }
                    else if (!revealed) { message("鉴定尚未完成"); false }
                    else if (state.phase == InventoryPhase.BATTLE) { message("战斗中不能调整物品"); false } else {
                        select(item)
                        val started = tile.startDragAndDrop(ClipData.newPlainText("物品", item.instanceId), ItemDragShadow(tile, def, placementRotated, unitCellDp()), item.instanceId, 0)
                        if (started) AppSettings.feedback(tile, HapticFeedbackConstants.LONG_PRESS)
                        else message("未能开始拖动，请重试或点按后选择装备")
                        started
                    }
                }
                tile.setOnLongClickListener { beginDrag(); true }
                com.example.douqiuqiu.ui.components.InventoryDragGesture.install(tile, beginDrag)
                warehouseTiles[item.instanceId] = tile
                tile.tag = revealed
                val rotated = if (selectedId == item.instanceId) placementRotated else item.place.rotated
                shelf.addItem(tile, if (rotated) def.height else def.width, if (rotated) def.width else def.height)
        }
        content.addView(shelf, LinearLayout.LayoutParams(-1, -2))
    }

    private fun advanceLootInspection(delta: Long): Boolean {
        val old = state.progress.run?.nodeLoot ?: return false
        // Do not rebuild target views under an active drag or an item preview.
        val result = NodeLootRules.inspect(state, rules, delta)
        if (!result.ok) return false
        return try {
            store.save(result.state); state = result.state
            val next = state.progress.run!!.nodeLoot!!
            if (leaveIfLootEmpty()) return true
            if (next.items.any { NodeLootRules.revealed(old, it.instanceId, rules.definitions) != NodeLootRules.revealed(next, it.instanceId, rules.definitions) }) {
                GameAudio.play(this, GameAudio.Cue.REVEAL)
                if (previewDialog?.isShowing != true && !dragInProgress) render() else deferredLootRefresh = true
            }
            true
        } catch (e: Exception) { message("鉴定保存失败：${e.message}"); false }
    }

    private fun compactButton(title: String, primary: Boolean = false, block: () -> Unit) = GameUi.button(this, title, primary, block).apply {
        minHeight = 0; minimumHeight = 0; minWidth = 0; minimumWidth = 0
        setPadding(dp(2), 0, dp(2), 0); textSize = 12f * AppSettings.textScale(this@InventoryActivity)
        layoutParams = LinearLayout.LayoutParams(-1, dp(32)); maxLines = 1
    }

    private fun previewItem(item: StoredItem) {
        val current = findDragged(item.instanceId) ?: return
        val pending = ground(current.instanceId) != null
        val def = rules.definitions.getValue(current.definitionId)
        val sheet = GameCatalogSheet(this, def.name,
            "${if (pending) "待拾取，尚未获得" else label(current.place.container)} · ${def.width}×${def.height}格 · ${if (current.bound) "绑定" else "可出售"}${if (def.kind == ItemKind.CARD) " · ${current.star}星" else ""}", stoneHeader = true)
        previewDialog = sheet.dialog
        sheet.dialog.setOnDismissListener { previewDialog = null; deferredLootRefresh = false; if (!isFinishing && !isDestroyed) render() }
        sheet.body.addView(ItemArtwork.view(this, def), LinearLayout.LayoutParams(-1, dp(112)))
        if (state.phase != InventoryPhase.BATTLE) {
            if (def.width != def.height) sheet.body.addView(GameUi.button(this, "↻ 旋转90°并选择放置") {
                sheet.dialog.dismiss(); select(current); rotateSelected()
            })
            if (!pending && def.kind == ItemKind.CONSUMABLE) sheet.body.addView(GameUi.button(this, "使用恢复品") {
                sheet.dialog.dismiss(); commit(repository.journey.useConsumable(state, current.instanceId))
            })
            val destinations = mutableListOf<Pair<String, Container>>()
            if (def.kind == ItemKind.EQUIPMENT && current.place.container != Container.EQUIPMENT) destinations += "装备" to Container.EQUIPMENT
            if (def.kind == ItemKind.CARD && current.place.container != Container.SKILLS) destinations += "装入技能栏" to Container.SKILLS
            if (current.place.container != Container.BACKPACK) destinations += "放入背包" to Container.BACKPACK
            if (current.place.container != Container.SAFE) destinations += "放入安全箱" to Container.SAFE
            if (state.phase == InventoryPhase.HOME && current.place.container != Container.WAREHOUSE) destinations += "移回仓库" to Container.WAREHOUSE
            (if (pending) emptyList() else destinations.chunked(2)).forEach { pair ->
                val row = LinearLayout(this)
                pair.forEach { (name, target) -> row.addView(GameUi.button(this, name, true) {
                    // Read the live instance; a preview never grants an extra item or bypasses move validation.
                    sheet.dialog.dismiss(); quickMove(current.instanceId, target)
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(2), 0, dp(2), 0) }) }
                sheet.body.addView(row)
            }
            sheet.body.addView(GameUi.button(this, "手动选格放置") {
                sheet.dialog.dismiss(); select(current)
                message("已选中，点空格放置；底部可旋转或取消")
            })
        }
        sheet.body.addView(GameUi.label(this, description(def), 14))
        if (state.phase != InventoryPhase.BATTLE) {
            if (def.kind == ItemKind.CARD && state.phase == InventoryPhase.HOME && current.star < 3 && current.place.container == Container.WAREHOUSE) {
                sheet.body.addView(GameUi.button(this, "三张同名同星卡升星") { sheet.dialog.dismiss(); merge(current) })
            }
            if (!pending) {
                val locked = current.instanceId in state.progress.lockedItems
                sheet.actions.addView(GameUi.button(this, if (locked) "解锁道具 · 当前已防出售/合成" else "锁定道具 · 防止出售/合成") {
                    val result = ItemLockRules(rules).toggle(state, current.instanceId)
                    if (commit(result)) { sheet.dialog.dismiss(); previewItem(current) }
                })
                if (state.phase == InventoryPhase.HOME && current.place.container == Container.WAREHOUSE) {
                    val price = SupplyRules(rules).saleValue(current)
                    sheet.actions.addView(GameUi.button(this, when { locked -> "已锁定 · 不可出售"; current.bound -> "绑定物品 · 不可出售"; else -> "快速卖出 · $price 金币" }, true) {
                        val result = SupplyRules(rules).sell(state, current.instanceId)
                        if (commit(result)) { sheet.dialog.dismiss(); selectedId = null; message("已出售${def.name} · +$price 金币") }
                    }.apply { isEnabled = !locked && !current.bound; alpha = if (isEnabled) 1f else .45f })
                }
            }
        }
        if (!pending) sheet.body.addView(GameUi.button(this, "更多操作 · 精确选位／旋转／使用") { sheet.dialog.dismiss(); edit(current) })
        sheet.show()
    }

    private fun quickMove(id: String, container: Container) {
        val item = findDragged(id) ?: return
        val candidates = when (container) {
            Container.WAREHOUSE -> listOf(ItemPlace(container))
            Container.EQUIPMENT -> (0..8).map { ItemPlace(container, it) }
            Container.SKILLS -> (0 until state.skillSlots).map { ItemPlace(container, it) }
            else -> {
                val w = if (container == Container.BACKPACK) state.backpackWidth else state.safeWidth
                val h = if (container == Container.BACKPACK) state.backpackHeight else state.safeHeight
                buildList { for (y in 0 until h) for (x in 0 until w) for (rotated in listOf(placementRotated, !placementRotated)) add(ItemPlace(container, x, y, rotated)) }
            }
        }
        val target = candidates.firstOrNull { candidate ->
            val slot = candidate.container == Container.EQUIPMENT || candidate.container == Container.SKILLS
            (!slot || state.items.none { it.instanceId != id && it.place.container == candidate.container && it.place.x == candidate.x }) && moveItem(id, candidate).ok
        }
        if (target == null) { message("${label(container)}没有可用位置，请整理后重试"); return }
        placementRotated = target.rotated
        commit(moveItem(item.instanceId, target))
    }

    private fun rotateSelected() {
        val item = selected() ?: return
        val def = rules.definitions.getValue(item.definitionId)
        if (def.width == def.height) { message("方形物品无需旋转"); return }
        if (state.phase == InventoryPhase.BATTLE) { message("战斗中不能旋转物品"); return }
        placementRotated = !placementRotated
        if (item.place.container in setOf(Container.BACKPACK, Container.SAFE) && ground(item.instanceId) == null) {
            val result = rules.move(state, item.instanceId, item.place.copy(rotated = placementRotated))
            if (result.ok) { commit(result); return }
        }
        render()
        message("已转为${if (placementRotated) def.height else def.width}×${if (placementRotated) def.width else def.height}格，直接拖到目标或点格放置")
    }

    private fun saleCandidates() = state.items.filter { it.place.container == Container.WAREHOUSE && !it.bound && it.instanceId !in state.progress.lockedItems }

    private fun confirmBulkSale() {
        val chosen = saleCandidates().filter { it.instanceId in saleSelection }
        if (chosen.isEmpty()) { message("请先点选物品，或选择当前分类全部可售物"); return }
        val supply = SupplyRules(rules)
        val total = chosen.sumOf { supply.saleValue(it) }
        val sheet = GameCatalogSheet(this, "确认批量出售", "${chosen.size}件 · 共$total 金币 · 已排除绑定和锁定物品", "保留物品", stoneHeader = true)
        chosen.groupBy { it.definitionId to it.star }.forEach { (key, items) ->
            val def = rules.definitions.getValue(key.first)
            val row = LinearLayout(this).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
            row.addView(ItemArtwork.view(this, def), LinearLayout.LayoutParams(dp(44), dp(44)))
            row.addView(GameUi.label(this, "${def.name}${if (def.kind == ItemKind.CARD) " ${key.second}星" else ""} ×${items.sumOf { it.count }}\n${items.sumOf { supply.saleValue(it) }} 金币", 13), LinearLayout.LayoutParams(0, -2, 1f))
            sheet.body.addView(row)
        }
        sheet.actions.addView(GameUi.button(this, "确认出售 · $total 金币", true) {
            if (commit(supply.sellMany(state, chosen.map { it.instanceId }))) {
                sheet.dialog.dismiss(); batchMode = false; saleSelection.clear(); render(); message("已出售${chosen.size}件 · +$total 金币")
            }
        })
        sheet.show()
    }

    private fun returnToJourney() {
        startActivity(Intent(this, JourneyActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        finish()
    }

    private fun leaveIfLootEmpty(): Boolean {
        val loot = state.progress.run?.nodeLoot ?: return false
        if (finishingLoot || loot.remaining.isNotEmpty() || loot.inspectionMs < NodeLootRules.duration(loot, rules.definitions)) return false
        finishingLoot = true
        // Journey owns terminal-chest inspection and the next event/rest/preparation transition.
        window.decorView.post { if (!isFinishing && !isDestroyed) returnToJourney() }
        return true
    }

    private fun presetChip(heroId: String, slot: Int): TextView {
        val selected = state.progress.loadoutSelections[heroId] == slot
        val exists = state.progress.loadouts[heroId]?.getOrNull(slot)?.let { preset ->
            preset.equipment.any { it != null } || preset.skills.any { it != null }
        } == true
        return compactButton("${slot + 1}${if (exists) "●" else ""}", selected) {
            val result = loadouts().apply(state, heroId, slot)
            if (result.ok) { commit(result); message(result.notice ?: "已套用${FirstRunContent.hero(heroId).name}方案${slot + 1}") }
            else message(result.error ?: "无法套用方案")
        }.apply {
            contentDescription = "方案${slot + 1}${if (exists) "，已保存" else "，空方案"}。点击套用，长按保存当前配装"
            setOnLongClickListener {
                val result = loadouts().save(state, heroId, slot)
                if (result.ok) { commit(result); message("已保存${FirstRunContent.hero(heroId).name}方案${slot + 1}") }
                else message(result.error ?: "无法保存方案")
                true
            }
        }
    }

    private fun savePreset() {
        if (state.phase != InventoryPhase.HOME) { message("挑战中不能保存局外套装，请返回营地后保存"); return }
        val heroId = currentHeroId()
        val hero = FirstRunContent.hero(heroId).name
        val selected = state.progress.loadoutSelections[heroId] ?: 0
        val labels = (0 until LoadoutRules.PRESET_COUNT).map { slot ->
            val exists = state.progress.loadouts[heroId]?.getOrNull(slot) != null
            "方案${slot + 1} · ${if (exists) "覆盖已保存套装" else "空栏位"}${if (slot == selected) " · 当前" else ""}"
        }.toTypedArray()
        AlertDialog.Builder(this).setTitle("保存$hero 的当前套装")
            .setItems(labels) { _, slot ->
                val gear = state.items.count { it.place.container == Container.EQUIPMENT }
                val cards = state.items.count { it.place.container == Container.SKILLS }
                AlertDialog.Builder(this).setTitle("保存到方案${slot + 1}？")
                    .setMessage("保存当前 $gear 件装备／备用物品、$cards 张技能卡及装备自动设置。再次套用会使用这些已有物品。")
                    .setPositiveButton("保存套装") { _, _ ->
                        val result = loadouts().save(state, heroId, slot)
                        if (result.ok) { if (commit(result)) message("已保存$hero · 方案${slot + 1}") }
                        else message(result.error ?: "套装保存失败")
                    }.setNegativeButton("取消", null).show()
            }.setNegativeButton("取消", null).show()
    }

    private fun chooseHero() {
        if (state.phase != InventoryPhase.HOME) { message("挑战中英雄与局外配装均锁定"); return }
        val heroes = FirstRunContent.heroes.filter { it.id in state.progress.unlockedHeroes }
        AlertDialog.Builder(this).setTitle("切换英雄")
            .setItems(heroes.map { hero -> "${hero.name}${if (hero.id == currentHeroId()) "（当前）" else ""}" }.toTypedArray()) { _, index ->
                val hero = heroes[index]
                if (hero.id != currentHeroId()) {
                    val result = loadouts().activateHero(state, hero.id)
                    if (result.ok) { commit(result); message(result.notice ?: "已切换为${hero.name}，并套用最近方案") }
                    else message(result.error ?: "无法切换英雄")
                }
            }.show()
    }

    private fun organize(container: Container) {
        val before = state
        val result = rules.organize(before, container)
        if (!result.ok) { message(result.error ?: "无法整理${label(container)}"); return }
        val moved = result.state.items.count { item -> before.items.single { it.instanceId == item.instanceId }.place != item.place }
        commit(result)
        message(if (moved == 0) "${label(container)}已经整齐" else "已整理${label(container)}，调整${moved}件物品")
    }

    private fun synergySummary() {
        com.example.douqiuqiu.ui.components.SynergySheet.show(this, state)
    }

    private fun grid(container: Container) {
        val width = if (container == Container.BACKPACK) state.backpackWidth else state.safeWidth
        val height = if (container == Container.BACKPACK) state.backpackHeight else state.safeHeight
        val cells = Array(height) { Array(width) { "·" } }
        state.items.filter { it.place.container == container }.forEachIndexed { index, item ->
            val d = rules.definitions.getValue(item.definitionId)
            val w = if (item.place.rotated) d.height else d.width
            val h = if (item.place.rotated) d.width else d.height
            for (y in item.place.y until item.place.y + h) for (x in item.place.x until item.place.x + w) cells[y][x] = (index + 1).toString()
        }
        text("${width}×$height\n" + cells.joinToString("\n") { row -> row.joinToString("  ") { it.padStart(2) } }, 18)
            .typeface = Typeface.MONOSPACE
    }

    private fun edit(item: StoredItem) {
        if (item.place.container == Container.WAREHOUSE && state.phase != InventoryPhase.HOME) { message("挑战中不能访问局外仓库"); return }
        val guardable = item.place.container == Container.EQUIPMENT || item.place.container == Container.SKILLS
        val guarded = item.instanceId in state.progress.guardedEquipment || item.instanceId in state.progress.guardedSkills
        val guardIndex = if (guardable) 1 else -1
        val choices = buildList {
            add("移动物品")
            if (guardable) add(if (guarded) "取消死亡保护" else "设为死亡保护")
            add("旋转 90°"); add("卡牌升星（三张同名同星卡）"); add("查看属性"); add("使用恢复品"); add("出售（绑定不可售）"); add("丢弃物品")
        }
        GameUi.sheet(this, rules.definitions.getValue(item.definitionId).name,
            "${position(item)} · ${if (item.bound) "绑定，不可出售" else "可出售"}${if (guarded && guardable) " · 已受死亡保护" else ""}", choices) { which ->
            when {
                which == 0 -> AlertDialog.Builder(this).setTitle("移至")
                    .setItems(Container.entries.map(::label).toTypedArray()) { _, target -> choosePosition(item, Container.entries[target]) }.show()
                which == guardIndex -> commit(rules.setGuard(state, item.instanceId, !guarded))
                which == guardIndex + 1 -> {
                    if (item.place.container != Container.BACKPACK && item.place.container != Container.SAFE) message("请先放入背包或安全箱再旋转")
                    else commit(rules.move(state, item.instanceId, item.place.copy(rotated = !item.place.rotated)))
                }
                which == guardIndex + 2 -> merge(item)
                which == guardIndex + 3 -> AlertDialog.Builder(this).setTitle(rules.definitions.getValue(item.definitionId).name)
                    .setIcon(PaintedItemIcons.drawable(this, rules.definitions.getValue(item.definitionId)))
                    .setMessage(description(rules.definitions.getValue(item.definitionId))).setPositiveButton("关闭", null).show()
                which == guardIndex + 4 -> commit(repository.journey.useConsumable(state, item.instanceId))
                which == guardIndex + 5 -> AlertDialog.Builder(this).setTitle("出售物品？").setMessage("以基础价格40%回收；升星卡按3/9份成本计。绑定物不可出售。")
                    .setNegativeButton("取消", null).setPositiveButton("出售") { _, _ -> commit(SupplyRules(rules).sell(state, item.instanceId)) }.show()
                which == guardIndex + 6 -> {
                    if (state.phase == InventoryPhase.BATTLE) message("战斗中不能丢弃")
                    else AlertDialog.Builder(this).setTitle("永久丢弃？").setMessage("${rules.definitions.getValue(item.definitionId).name}无法找回。")
                        .setNegativeButton("取消", null).setPositiveButton("丢弃") { _, _ -> commit(InventoryResult(state.copy(items = state.items.filterNot { it.instanceId == item.instanceId }))) }.show()
                }
            }
        }
    }

    private fun choosePosition(item: StoredItem, container: Container) {
        if (container == Container.WAREHOUSE) {
            commit(rules.move(state, item.instanceId, ItemPlace(container))); return
        }
        val candidates = mutableListOf<ItemPlace>()
        when (container) {
            Container.EQUIPMENT -> (0..8).forEach { candidates += ItemPlace(container, it) }
            Container.SKILLS -> (0 until state.skillSlots).forEach { candidates += ItemPlace(container, it) }
            else -> {
                val width = if (container == Container.BACKPACK) state.backpackWidth else state.safeWidth
                val height = if (container == Container.BACKPACK) state.backpackHeight else state.safeHeight
                for (y in 0 until height) for (x in 0 until width) for (rotated in listOf(false, true))
                    candidates += ItemPlace(container, x, y, rotated)
            }
        }
        val valid = candidates.filter { rules.move(state, item.instanceId, it).ok }
        if (valid.isEmpty()) { message("没有可用位置，请检查物品类型、空格和当前阶段"); return }
        AlertDialog.Builder(this).setTitle("选择位置")
            .setItems(valid.map { position(item.copy(place = it)) }.toTypedArray()) { _, i ->
                commit(rules.move(state, item.instanceId, valid[i]))
            }.show()
    }

    private fun merge(item: StoredItem) {
        val matches = state.items.filter { it.definitionId == item.definitionId && it.star == item.star && it.place.container == Container.WAREHOUSE }
        if (item.place.container != Container.WAREHOUSE || matches.size < 3 || item.star >= 3 || rules.definitions.getValue(item.definitionId).kind != ItemKind.CARD) {
            message("只有卡牌可升星：需要仓库内三张同名同星卡，最高三星。装备请通过配方合成。"); return
        }
        // Prefer unbound materials where possible, and always include the selected card.
        val chosen = listOf(item) + matches.filter { it.instanceId != item.instanceId }.sortedBy { it.bound }.take(2)
        AlertDialog.Builder(this).setTitle("升为 ${item.star + 1} 星？")
            .setMessage("消耗三张同名同星卡，不花费金币。${if (chosen.any { it.bound }) "产物将绑定。" else ""}升星不可撤销。")
            .setNegativeButton("取消", null).setPositiveButton("确认升星") { _, _ -> commit(rules.mergeCards(state, chosen.map { it.instanceId })) }.show()
    }

    private fun commit(result: InventoryResult): Boolean {
        if (!result.ok) { message(result.error ?: "操作失败"); return false }
        try {
            val next = repository.journey.syncLoadout(result.state)
            store.save(next); state = next
            if (!leaveIfLootEmpty()) render()
            return true
        }
        catch (e: Exception) { message("保存失败，物品未变动：${e.message}"); return false }
    }

    private fun position(item: StoredItem): String = when (item.place.container) {
        Container.WAREHOUSE -> "局外仓库"
        Container.EQUIPMENT -> "第 ${item.place.x + 1} 栏${if (item.place.x >= 6) "（备用）" else ""}"
        Container.SKILLS -> "第 ${item.place.x + 1} 技能栏"
        else -> "第 ${item.place.y + 1} 行 · 第 ${item.place.x + 1} 列${if (item.place.rotated) " · 已旋转" else ""}"
    }
    private fun label(c: Container) = when (c) {
        Container.WAREHOUSE -> "局外仓库"
        Container.BACKPACK -> "普通背包"
        Container.SAFE -> "安全箱"
        Container.EQUIPMENT -> "装备栏"
        Container.SKILLS -> "技能栏"
    }
    private fun text(value: String, size: Int, color: Int = ink): TextView = TextView(this).apply {
        text = value; textSize = size * AppSettings.textScale(this@InventoryActivity); setTextColor(color); setPadding(0, dp(10), 0, dp(6)); content.addView(this)
    }
    private fun message(value: String) { Toast.makeText(this, value, Toast.LENGTH_LONG).show() }
    private fun action(label: String, block: () -> Unit) { content.addView(GameUi.button(this, label, block = block)) }
    private fun autoEquip() {
        var next = state
        for (item in state.items.filter { it.place.container == Container.WAREHOUSE }) {
            val kind = rules.definitions.getValue(item.definitionId).kind
            val target = when (kind) { ItemKind.CARD -> Container.SKILLS; ItemKind.EQUIPMENT -> Container.EQUIPMENT; else -> continue }
            val limit = if (target == Container.SKILLS) next.skillSlots else 6
            val slot = (0 until limit).firstOrNull { x -> next.items.none { it.place.container == target && it.place.x == x } } ?: continue
            val result = rules.move(next, item.instanceId, ItemPlace(target, slot))
            if (result.ok) next = result.state
        }
        commit(InventoryResult(next))
    }
    /** Batch pickup only fills the ordinary backpack; it never auto-equips, protects, or discards loot. */
    private fun collectRevealedLoot() {
        val result = NodeLootRules.collectRevealedIntoBackpack(state, rules)
        if (result == null) { message("当前没有可自动拾取的掉落"); return }
        if (result.pickedCount == 0) {
            message(if (result.pendingCount > 0) "仍有${result.pendingCount}件正在鉴定" else "背包没有可连续放入物品的空位")
            return
        }
        if (commit(InventoryResult(result.state))) {
            val notes = mutableListOf("已放入${result.pickedCount}件")
            if (result.remainingCount > 0) notes += "${result.remainingCount}件放不下"
            if (result.pendingCount > 0) notes += "${result.pendingCount}件待鉴定"
            message(notes.joinToString(" · "))
        }
    }

    /** On a stale quote, persist the current shelf so the sheet can immediately redraw its price and stock. */
    private fun buyShopItem(id: String, revision: Int): InventoryResult {
        val result = repository.economy.buy(state, id, UUID.randomUUID().toString(), System.currentTimeMillis().coerceAtLeast(0), revision)
        if (result.ok) return if (commit(result)) result else InventoryResult(state, "保存失败")
        val refreshed = result.refreshed ?: return result
        return try {
            store.save(refreshed)
            state = refreshed
            result
        } catch (e: Exception) {
            InventoryResult(state, "更新货架失败：${e.message ?: "存档写入失败"}")
        }
    }

    /** Read-only stats: the shared snapshot only exposes sources it can prove without duplicating battle formulas. */
    private fun showHeroStats() {
        val heroId = currentHeroId()
        val battle = state.progress.run?.fight?.player?.takeIf { it.heroId == heroId }
        val snapshot = HeroStats.snapshot(state, rules, heroId, battle)
        val heroName = FirstRunContent.hero(heroId).name
        val sheet = GameCatalogSheet(this, "$heroName · 数值", if (snapshot.inBattle) "战斗实时总值" else "当前配装 · 点按属性查看来源", "返回整备", stoneHeader = true)
        fun display(value: Double, fractionalPercent: Boolean = false, percentagePoints: Boolean = false): String = when {
            fractionalPercent -> "${(value * 100).roundToInt()}%"
            percentagePoints -> "${value.roundToInt()}%"
            abs(value - value.roundToInt()) < 0.01 -> value.roundToInt().toString()
            else -> String.format(Locale.US, "%.1f", value)
        }
        fun source(value: StatValue, fractionalPercent: Boolean, percentagePoints: Boolean): String {
            if (!value.hasBreakdown) return "当前仅记录总值"
            return listOfNotNull(value.base?.let { "基础 ${display(it, fractionalPercent, percentagePoints)}" }, value.equipment?.let { "装备 ${display(it, fractionalPercent, percentagePoints)}" }, value.cards?.let { "卡牌/羁绊 ${display(it, fractionalPercent, percentagePoints)}" }).joinToString(" · ")
        }
        fun add(label: String, value: StatValue, fractionalPercent: Boolean = false, percentagePoints: Boolean = false) {
            val tile = GameUi.panel(this)
            tile.addView(GameUi.label(this, label, 12, GameUi.muted, true))
            tile.addView(GameUi.label(this, display(value.total, fractionalPercent, percentagePoints), 20, GameUi.gold, true))
            tile.setOnClickListener { AlertDialog.Builder(this).setTitle(label).setMessage(source(value, fractionalPercent, percentagePoints)).setPositiveButton("关闭", null).show() }
            sheet.body.addView(tile)
        }
        sheet.body.addView(GameUi.label(this, "核心", 16, GameUi.gold, true))
        add("生命上限", snapshot.hp); add("魔法上限", snapshot.mana); add("攻击", snapshot.attack)
        add("护甲", snapshot.armor); add("魔抗", snapshot.magicResistance, percentagePoints = true); add("攻击速度", snapshot.attackSpeed)
        sheet.body.addView(GameUi.label(this, "恢复与抗性", 16, GameUi.gold, true))
        add("每秒回血", snapshot.healthRegen); add("每秒回蓝", snapshot.manaRegen)
        add("吸血", snapshot.lifesteal, fractionalPercent = true); add("冷却缩减", snapshot.cooldownReduction, fractionalPercent = true); add("状态抗性", snapshot.statusResistance, fractionalPercent = true)

        // 技能信息：基础值 + 当前值 + 触发条件 + 主要数值来源（全部读共享表，不复制公式）
        val skill = com.example.douqiuqiu.content.HeroSkillInfo.of(heroId)
        sheet.body.addView(GameUi.label(this, "技能", 16, GameUi.gold, true))
        val skillTile = GameUi.panel(this)
        skillTile.addView(GameUi.label(this, skill.skillName, 14, GameUi.gold, true))
        skillTile.addView(GameUi.label(this, com.example.douqiuqiu.content.HeroSkillInfo.currentLine(
            heroId, battle?.mana, battle?.maxMana, battle?.ultIn), 12))
        skillTile.addView(GameUi.label(this, "触发：${skill.trigger}", 12))
        skill.values.forEach { v -> skillTile.addView(GameUi.label(this, "${v.label}：${v.value}", 12)) }
        if (skill.note.isNotEmpty()) skillTile.addView(GameUi.label(this, skill.note, 11, GameUi.muted))
        sheet.body.addView(skillTile)
        sheet.show()
    }

    private fun shop() {
        if (state.phase != InventoryPhase.HOME) { message("挑战中不能购物"); return }
        try { state = repository.load().state } catch (e: Exception) { message("无法更新商店：${e.message}"); return }
        com.example.douqiuqiu.ui.components.SupplyShopSheet(this, rules.definitions, { state }, ::description,
            countdown = { repository.economy.shopCountdown(state, System.currentTimeMillis().coerceAtLeast(0)) },
            buy = ::buyShopItem,
            refresh = { commit(repository.economy.refreshShop(state, System.currentTimeMillis().coerceAtLeast(0))) }
        ).show()
    }
    private fun growth() {
        if (state.phase != InventoryPhase.HOME) { message("挑战中不能升级"); return }
        val choices = GrowthContent.nextChoices(state)
        if (choices.isEmpty()) { message("当前所有成长已达上限"); return }
        val sheet = GameCatalogSheet(this, "营地成长", "${state.gold}金币 · 解锁永久保留 · 不增加永久战斗属性")
        choices.forEach { upgrade ->
                val status = repository.growth.status(state, upgrade.id)
                val tile = GameUi.panel(this)
                val row = LinearLayout(this).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
                if (upgrade.heroId != null) GameArt.hero(this, upgrade.heroId!!)?.let {
                    row.addView(GameArt.image(this, it, upgrade.name).apply { scaleType = ImageView.ScaleType.FIT_CENTER }, LinearLayout.LayoutParams(dp(72), dp(88)))
                } else upgrade.materials.keys.firstOrNull()?.let { id -> row.addView(ItemArtwork.view(this, rules.definitions.getValue(id)), LinearLayout.LayoutParams(dp(64), dp(64))) }
                row.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(GameUi.label(this@InventoryActivity, upgrade.name, 18, GameUi.gold, true))
                    addView(GameUi.label(this@InventoryActivity, "${upgrade.gold}金币", 14))
                }, LinearLayout.LayoutParams(0, -2, 1f))
                tile.addView(row)
                tile.addView(GameUi.label(this, status.message, 12, if (status.canPurchase) GameUi.green else GameUi.muted))
                tile.addView(GameUi.button(this, if (status.canPurchase) "查看并升级" else "查看解锁条件") {
                val costs = upgrade.materials.entries.joinToString("\n") { (id, count) -> "${rules.definitions.getValue(id).name} ×$count" }
                val confirm = AlertDialog.Builder(this).setTitle(upgrade.name).setMessage("花费${upgrade.gold}金币及仓库材料：\n$costs\n\n${status.message}\n绑定材料优先使用，升级后永久保留。")
                    .setNegativeButton("返回", null).setPositiveButton("购买升级") { _, _ ->
                        sheet.dialog.dismiss(); commit(repository.growth.purchase(state, upgrade.id)); growth()
                    }.show()
                confirm.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = status.canPurchase
                })
                sheet.body.addView(tile)
        }
        sheet.show()
    }
    /** 0.1.6 任务面板：引导/挑战/转化三类分组；内容表为空时显示暂无任务。 */
    private fun quests() {
        if (state.phase != InventoryPhase.HOME) { message("挑战中不能查看任务"); return }
        val sheet = GameCatalogSheet(this, "委托任务", "${state.gold}金币 · 完成任务获取英雄与材料")
        val quests = com.example.douqiuqiu.quest.QuestContent.all
        if (quests.isEmpty()) {
            sheet.body.addView(GameUi.label(this, "暂无任务", 16, GameUi.gold, true))
            sheet.body.addView(GameUi.label(this, "任务内容正在补充中。完成任务可解锁新英雄、获得技能刻片与护芯。", 12, GameUi.muted))
            sheet.show(); return
        }
        val claimable = com.example.douqiuqiu.quest.QuestTracker.claimable(state).map { it.id }.toSet()
        val convertible = com.example.douqiuqiu.quest.QuestTracker.convertible(state).map { it.id }.toSet()
        val groups = listOf(
            "引导" to com.example.douqiuqiu.quest.QuestKind.GUIDE,
            "挑战" to com.example.douqiuqiu.quest.QuestKind.CHALLENGE,
            "转化" to com.example.douqiuqiu.quest.QuestKind.CONVERSION
        )
        groups.forEach { (title, kind) ->
            val list = quests.filter { it.kind == kind }
            if (list.isEmpty()) return@forEach
            GameUi.section(sheet.body, title)
            list.forEach { quest ->
                val tile = GameUi.panel(this)
                val done = quest.id in state.progress.claimedQuests
                val ready = quest.id in claimable || quest.id in convertible
                tile.addView(GameUi.label(this, quest.name, 16, if (done) GameUi.muted else GameUi.gold, true))
                tile.addView(GameUi.label(this, quest.description, 12))
                val progress = when {
                    done -> "已领取"
                    quest.kind == com.example.douqiuqiu.quest.QuestKind.CONVERSION ->
                        if (quest.id in convertible) "材料足够，可兑换" else "材料不足：${quest.cost.entries.joinToString("、") { (id, n) -> "${rules.definitions.getValue(id).name}×$n" }}"
                    else -> "进度 ${com.example.douqiuqiu.quest.QuestTracker.progressText(state, quest)}" +
                        if (state.progress.clears < quest.minClears) " · 还需通关${quest.minClears - state.progress.clears}次" else ""
                }
                tile.addView(GameUi.label(this, progress, 12, if (ready) GameUi.green else GameUi.muted))
                val rewardText = quest.rewards.joinToString("、") { r ->
                    when (r.type) {
                        com.example.douqiuqiu.quest.RewardType.GOLD -> "${r.amount}金币"
                        com.example.douqiuqiu.quest.RewardType.HERO_UNLOCK -> "解锁英雄"
                        com.example.douqiuqiu.quest.RewardType.MATERIAL -> "${rules.definitions.getValue(r.materialId!!).name}×${r.amount}"
                        com.example.douqiuqiu.quest.RewardType.GUARD_CORE -> "${rules.definitions.getValue(r.materialId!!).name}×${r.amount}"
                        com.example.douqiuqiu.quest.RewardType.HERO_LEVEL_UP -> "英雄等级+1"
                    }
                }
                if (rewardText.isNotEmpty()) tile.addView(GameUi.label(this, "奖励：$rewardText", 12, GameUi.gold))
                val buttonLabel = when {
                    done -> "已完成"
                    quest.kind == com.example.douqiuqiu.quest.QuestKind.CONVERSION -> "兑换"
                    quest.id in claimable -> "领取奖励"
                    else -> "进行中"
                }
                tile.addView(GameUi.button(this, buttonLabel) {
                    val result = if (quest.kind == com.example.douqiuqiu.quest.QuestKind.CONVERSION)
                        com.example.douqiuqiu.quest.QuestTracker.convert(state, quest.id, rules)
                    else com.example.douqiuqiu.quest.QuestTracker.claim(state, quest.id, rules)
                    if (!result.ok) message(result.error ?: "操作失败")
                    else { commit(result); message("已获得${quest.name}的奖励"); quests() }
                }.apply { isEnabled = ready && !done })
                sheet.body.addView(tile)
            }
        }
        sheet.show()
    }

    /** 0.1.6 英雄等级面板：金币升级，每英雄独立；显示当前等级加成与下一级费用。 */
    private fun heroLevels() {
        if (state.phase != InventoryPhase.HOME) { message("挑战中不能升级英雄"); return }
        val sheet = GameCatalogSheet(this, "英雄等级", "${state.gold}金币 · 上限${com.example.douqiuqiu.hero.HeroLevelRules.LEVEL_CAP}级 · 提升永久战斗属性")
        com.example.douqiuqiu.journey.FirstRunContent.heroes.filter { it.id in state.progress.unlockedHeroes }.forEach { hero ->
            val level = com.example.douqiuqiu.hero.HeroLevelRules.level(state, hero.id)
            val capped = com.example.douqiuqiu.hero.HeroLevelRules.isCapped(level)
            val cost = com.example.douqiuqiu.hero.HeroLevelRules.upgradeCost(level)
            val tile = GameUi.panel(this)
            val row = LinearLayout(this).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
            GameArt.hero(this, hero.id)?.let {
                row.addView(GameArt.image(this, it, hero.name).apply { scaleType = ImageView.ScaleType.FIT_CENTER }, LinearLayout.LayoutParams(dp(56), dp(68)))
            }
            row.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(GameUi.label(this@InventoryActivity, hero.name, 16, GameUi.gold, true))
                addView(GameUi.label(this@InventoryActivity, "等级 $level / ${com.example.douqiuqiu.hero.HeroLevelRules.LEVEL_CAP}", 14))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            tile.addView(row)
            val bonus = buildList {
                val atk = (com.example.douqiuqiu.hero.HeroLevelRules.attackMultiplier(level) - 1) * 100
                val hp = (com.example.douqiuqiu.hero.HeroLevelRules.healthMultiplier(level) - 1) * 100
                add("攻击 +${"%.1f".format(atk)}% · 生命 +${"%.1f".format(hp)}%")
                add("护甲 +${com.example.douqiuqiu.hero.HeroLevelRules.armorBonus(level)} · 魔法抗性 +${com.example.douqiuqiu.hero.HeroLevelRules.magicResistBonus(level)}%")
            }
            tile.addView(GameUi.label(this, bonus.joinToString("\n"), 12, GameUi.muted))
            tile.addView(GameUi.button(this, when {
                capped -> "已达版本上限"
                state.gold < cost -> "升级需${cost}金币（不足）"
                else -> "花费${cost}金币升级"
            }) {
                val result = com.example.douqiuqiu.hero.HeroLevelRules.upgrade(state, hero.id, rules)
                if (!result.ok) message(result.error ?: "升级失败")
                else { commit(result); message("${hero.name}升至${level + 1}级"); heroLevels() }
            }.apply { isEnabled = !capped && state.gold >= cost })
            sheet.body.addView(tile)
        }
        sheet.show()
    }

    private fun crafting() {
        if (state.phase != InventoryPhase.HOME) { message("仅局外可合成"); return }
        com.example.douqiuqiu.ui.components.CraftingSheet(this, rules.definitions,
            repository.catalog.crafting.recipes.values, { state }, ::description) { option ->
            val result = repository.catalog.crafting.craft(state, option.recipe.id, option.ingredientIds, UUID.randomUUID().toString())
            if (!result.ok) { message(result.error ?: "无法合成"); false }
            else try {
                store.save(result.state); state = result.state; render()
                GameAudio.play(this, GameAudio.Cue.REVEAL)
                message("已合成${rules.definitions.getValue(option.recipe.outputId).name}，已放入仓库"); true
            } catch (e: Exception) { message("合成保存失败：${e.message}"); false }
        }.show()
    }
    private fun description(d: ItemDefinition): String {
        val s = d.stats
        val lines = mutableListOf("${d.quality} · ${d.width}×${d.height}格")
        com.example.douqiuqiu.loot.ValuableLootCatalog.find(d.id)?.let {
            lines += it.description
            lines += "纯变卖战利品，无战斗效果。出售可得${d.price.toLong() * 40 / 100}金币；安全箱保护仍按所在位置判断。"
        }
        listOf("力量" to s.strength, "敏捷" to s.agility, "智力" to s.intelligence, "生命" to s.health,
            "每秒回血" to s.healthRegen, "护甲" to s.armor, "攻速" to s.attackSpeed, "魔法上限%" to s.manaPercent,
            "每秒回蓝" to s.manaRegen, "攻击" to s.attack).filter { it.second != 0.0 }.forEach { lines += "${it.first} +${it.second}" }
        val card = com.example.douqiuqiu.content.CardCatalog.all.find { it.id == d.id }?.description ?: ""
        if (card.isNotEmpty()) {
            lines += card
            lines += "卡牌升星：仓库内三张同名同星卡可合成，最高三星。"
        }
        com.example.douqiuqiu.content.EquipmentCatalog.all.find { it.id == d.id }?.let { gear ->
            if (gear.description.isNotEmpty()) lines += gear.description
            lines += "价格 ${gear.price} 金币 · 商店刷新 ${gear.minimumShopClears} 次后投放"
            gear.effects.forEach { e ->
                val trigger = when (e.trigger) {
                    com.example.douqiuqiu.content.EffectTrigger.PASSIVE -> "常驻"
                    com.example.douqiuqiu.content.EffectTrigger.BATTLE_START -> "开场"
                    com.example.douqiuqiu.content.EffectTrigger.ATTACK_LAND -> "命中"
                    com.example.douqiuqiu.content.EffectTrigger.ATTACK_COUNT -> "每N次命中"
                    com.example.douqiuqiu.content.EffectTrigger.CRIT_SUCCESS -> "暴击后"
                    com.example.douqiuqiu.content.EffectTrigger.ULT_START -> "大招开始"
                    com.example.douqiuqiu.content.EffectTrigger.ULT_DAMAGE -> "大招伤害"
                    com.example.douqiuqiu.content.EffectTrigger.INTERVAL -> "固定周期"
                    com.example.douqiuqiu.content.EffectTrigger.LOW_HP -> "低血"
                    com.example.douqiuqiu.content.EffectTrigger.HEAL_ACCUMULATED -> "累计治疗"
                    com.example.douqiuqiu.content.EffectTrigger.DAMAGE_TAKEN -> "受击"
                    com.example.douqiuqiu.content.EffectTrigger.POISON_TICK -> "毒跳"
                    com.example.douqiuqiu.content.EffectTrigger.ACTIVE -> "主动"
                    com.example.douqiuqiu.content.EffectTrigger.ON_CONTROLLED -> "受控"
                }
                val params = e.params.entries.joinToString("、") { (key, value) ->
                    val text = when (value) {
                        is com.example.douqiuqiu.content.EffectValue.Num -> if (value.value == value.value.toLong().toDouble()) value.value.toLong().toString() else value.value.toString()
                        is com.example.douqiuqiu.content.EffectValue.Count -> value.value.toString()
                        is com.example.douqiuqiu.content.EffectValue.Flag -> if (value.value) "是" else "否"
                        is com.example.douqiuqiu.content.EffectValue.Choice -> value.value
                    }
                    "$key=$text"
                }
                val cd = e.internalCooldownMs?.takeIf { it > 0 }?.let { " · 内置CD ${it / 1000.0}秒" } ?: ""
                lines += "效果 ${e.id}（$trigger）${if (params.isEmpty()) "" else "：$params"}$cd"
            }
            lines += "装备不可升星；装备只能通过配方合成。"
            val groups = gear.effects.mapNotNull { it.uniqueGroup }.distinct().map { group -> when (group) {
                "sange_family" -> "散华家族"; "yasha_family" -> "夜叉家族"; "kaya_family" -> "慧光家族"
                "poison_blade" -> "巫师之刃／圣斧"; "force_staff" -> "原力法杖／长戟"
                "physical_block" -> "固定格挡"; "cooldown_reduction" -> "玲珑心冷却缩减"
                "maelstrom" -> "闪电"; "expert_array" -> "阵列追加伤害"
                else -> gear.name
            } }.distinct()
            if (groups.isNotEmpty()) lines += "唯一效果组：${groups.joinToString("、")}（基础属性正常叠加）"
            gear.recipe?.let { recipe -> lines += "合成：" + recipe.ingredients.entries.joinToString(" + ") { (id, n) -> "${rules.definitions.getValue(id).name}×$n" } }
        }
        return lines.joinToString("\n")
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
