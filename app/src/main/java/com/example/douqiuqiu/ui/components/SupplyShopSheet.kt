package com.example.douqiuqiu.ui.components

import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.douqiuqiu.inventory.InventoryPhase
import com.example.douqiuqiu.inventory.InventoryState
import com.example.douqiuqiu.inventory.InventoryResult
import com.example.douqiuqiu.inventory.ItemDefinition
import com.example.douqiuqiu.journey.EconomyBalance
import com.example.douqiuqiu.shop.ShopShelf
import com.example.douqiuqiu.shop.StoredShopOffer

/** Displayed quotes retain their revision; all durable purchases belong to the owner. */
class SupplyShopSheet(
    private val activity: AppCompatActivity,
    private val definitions: Map<String, ItemDefinition>,
    private val state: () -> InventoryState,
    private val description: (ItemDefinition) -> String,
    private val countdown: () -> Long,
    private val buy: (definitionId: String, expectedRevision: Int) -> InventoryResult,
    private val refresh: () -> Boolean
) {
    private val sheet = GameCatalogSheet(activity, "补给商店", "点按商品查看详情 · 购买后送入仓库", stoneHeader = true)
    private val wallet = GameUi.label(activity, "", 17, GameUi.gold, true)
    private val clock = GameUi.label(activity, "", 12, GameUi.muted)
    private val tabs = LinearLayout(activity)
    private val goods = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private val refreshButton = GameUi.button(activity, "提前刷新") { confirmRefresh() }
    private val handler = Handler(Looper.getMainLooper())
    private var shelf = ShopShelf.STAPLE
    private var rendered: InventoryState? = null
    private var detail: Detail? = null
    private val tick = object : Runnable {
        override fun run() {
            if (!sheet.dialog.isShowing || activity.isFinishing || activity.isDestroyed) return
            render()
            handler.postDelayed(this, 1000)
        }
    }
    private data class Quote(val offer: StoredShopOffer, val revision: Int, val stock: Int)
    private class Detail(val id: String, val sheet: GameCatalogSheet, val status: TextView, val button: Button) {
        var quote: Quote? = null
    }
    private fun dp(value: Int) = GameUi.dp(activity, value)

    init {
        sheet.body.addView(wallet)
        sheet.body.addView(clock)
        sheet.body.addView(tabs)
        sheet.body.addView(goods)
        sheet.body.setPadding(0, dp(8), 0, dp(36))
        sheet.actions.addView(GameUi.label(activity, "提前刷新花费${EconomyBalance.SHOP_REFRESH}金币，不推迟自动刷新。", 11, GameUi.muted))
        sheet.actions.addView(refreshButton)
        sheet.dialog.setOnDismissListener {
            handler.removeCallbacks(tick)
            detail?.sheet?.dialog?.dismiss()
            detail = null
        }
    }

    fun show() {
        render(force = true)
        sheet.show()
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, 1000)
    }

    private fun quote(snapshot: InventoryState, id: String): Quote? {
        val shop = snapshot.progress.shop ?: return null
        val offer = shop.offers.find { it.definitionId == id } ?: return null
        return Quote(offer, shop.revision, snapshot.progress.shopStock[id] ?: offer.stock)
    }

    private fun render(force: Boolean = false) {
        val snapshot = state()
        wallet.text = "金币  ${snapshot.gold}"
        val remaining = countdown().coerceAtLeast(0)
        val seconds = remaining / 1000 + if (remaining % 1000 == 0L) 0 else 1
        clock.text = if (seconds == 0L) "自动换货时间已到 · 刷新免费更新" else "自动换货  ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
        val canShop = snapshot.phase == InventoryPhase.HOME && snapshot.progress.run == null
        refreshButton.text = if (remaining == 0L) "免费更新货架" else "提前刷新 · ${EconomyBalance.SHOP_REFRESH}金币"
        refreshButton.isEnabled = canShop && (remaining == 0L || snapshot.gold >= EconomyBalance.SHOP_REFRESH)
        refreshButton.alpha = if (refreshButton.isEnabled) 1f else .45f
        val prior = rendered
        if (force || prior == null || prior.gold != snapshot.gold || prior.phase != snapshot.phase ||
            prior.progress.run != snapshot.progress.run || prior.progress.shop != snapshot.progress.shop || prior.progress.shopStock != snapshot.progress.shopStock) {
            renderGoods(snapshot)
            rendered = snapshot
        }
        detail?.let { renderDetail(it, snapshot) }
    }

    private fun renderTabs() {
        tabs.removeAllViews()
        listOf(ShopShelf.STAPLE to "常备", ShopShelf.ROTATING to "轮换", ShopShelf.RARE to "珍品").forEach { (kind, title) ->
            tabs.addView(GameUi.button(activity, title, shelf == kind) {
                shelf = kind
                render(force = true)
                (sheet.body.parent as? ScrollView)?.let { scroll -> scroll.post { scroll.smoothScrollTo(0, 0) } }
            }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(dp(2), dp(8), dp(2), dp(8)) })
        }
    }

    private fun renderGoods(snapshot: InventoryState) {
        renderTabs()
        val scroll = sheet.body.parent as? ScrollView
        val scrollY = scroll?.scrollY ?: 0
        goods.removeAllViews()
        val offers = snapshot.progress.shop?.offers.orEmpty().filter { it.shelf == shelf && it.definitionId in definitions }
        if (offers.isEmpty()) goods.addView(GameUi.label(activity, "本轮暂无商品，换个货架看看。", 14, GameUi.muted).apply {
            setPadding(dp(8), dp(28), dp(8), dp(32))
        })
        offers.chunked(3).forEach { group ->
            val row = LinearLayout(activity)
            group.forEach { offer ->
                val definition = definitions.getValue(offer.definitionId)
                val stock = snapshot.progress.shopStock[definition.id] ?: offer.stock
                val tile = GameUi.panel(activity).apply { setPadding(dp(5), dp(6), dp(5), dp(8)) }
                val icon = object : FrameLayout(activity) {
                    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                        val side = View.MeasureSpec.getSize(widthMeasureSpec)
                        val square = View.MeasureSpec.makeMeasureSpec(side, View.MeasureSpec.EXACTLY)
                        super.onMeasure(square, square)
                    }
                }.apply {
                    background = GameUi.frame(activity, GameUi.background, GameUi.quality(definition.quality))
                    addView(ItemArtwork.view(activity, definition), FrameLayout.LayoutParams(-1, -1))
                    if (stock <= 0) {
                        addView(View(activity).apply { setBackgroundColor(0xAA051F2F.toInt()) }, FrameLayout.LayoutParams(-1, -1))
                        addView(GameUi.label(activity, "售罄", 14, GameUi.muted, true).apply { gravity = Gravity.CENTER }, FrameLayout.LayoutParams(-1, -1))
                    }
                }
                tile.addView(icon)
                tile.addView(GameUi.label(activity, definition.name, 12, GameUi.quality(definition.quality), true).apply {
                    gravity = Gravity.CENTER; maxLines = 2; minHeight = dp(38)
                })
                tile.addView(GameUi.label(activity, "${offer.price}金币", 12, GameUi.gold, true).apply { gravity = Gravity.CENTER })
                tile.addView(GameUi.label(activity, if (stock > 0) "库存 $stock" else "本轮售罄", 11, GameUi.muted).apply { gravity = Gravity.CENTER })
                tile.isFocusable = true
                tile.contentDescription = "${definition.name}，${offer.price}金币，库存$stock，查看详情"
                tile.setOnClickListener { showDetail(definition.id) }
                row.addView(tile, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(3), dp(4), dp(3), dp(4)) })
            }
            repeat(3 - group.size) { row.addView(Space(activity), LinearLayout.LayoutParams(0, 1, 1f)) }
            goods.addView(row)
        }
        scroll?.post { if (sheet.dialog.isShowing) scroll.scrollTo(0, scrollY) }
    }

    private fun showDetail(id: String) {
        detail?.sheet?.dialog?.dismiss()
        val definition = definitions.getValue(id)
        val preview = GameCatalogSheet(activity, "商品详情", definition.name, "返回商店", stoneHeader = true)
        preview.body.addView(ItemArtwork.view(activity, definition), LinearLayout.LayoutParams(-1, dp(128)))
        preview.body.addView(GameUi.label(activity, definition.name, 22, GameUi.quality(definition.quality), true).apply { gravity = Gravity.CENTER })
        preview.body.addView(GameUi.label(activity, description(definition), 14).apply { setPadding(dp(8), dp(12), dp(8), dp(32)) })
        val status = GameUi.label(activity, "", 14, GameUi.gold, true)
        lateinit var session: Detail
        val purchase = GameUi.button(activity, "购买", true) {
            val displayed = session.quote ?: return@button
            val result = buy(id, displayed.revision)
            if (result.ok) {
                Toast.makeText(activity, "${definition.name}已送入仓库", Toast.LENGTH_SHORT).show()
            } else if (result.refreshed != null) {
                Toast.makeText(activity, "货架已自动更新，请按新价格选择", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(activity, result.error ?: "购买失败", Toast.LENGTH_SHORT).show()
            }
            // A rejected transaction is never presented as a purchase; re-read its actual result.
            render(force = true)
        }
        session = Detail(id, preview, status, purchase)
        preview.actions.addView(status)
        preview.actions.addView(purchase)
        preview.dialog.setOnDismissListener { if (detail === session) detail = null }
        detail = session
        renderDetail(session, state())
        preview.show()
    }

    private fun renderDetail(session: Detail, snapshot: InventoryState) {
        val displayed = quote(snapshot, session.id)
        session.quote = displayed
        val canShop = snapshot.phase == InventoryPhase.HOME && snapshot.progress.run == null
        val enabled = canShop && displayed != null && displayed.stock > 0 && snapshot.gold >= displayed.offer.price
        session.status.text = if (displayed == null) "商品已换货 · 当前金币 ${snapshot.gold}" else
            "${displayed.offer.price}金币 · 库存 ${displayed.stock}\n当前金币 ${snapshot.gold}"
        session.button.text = when {
            !canShop -> "挑战中不能购买"
            displayed == null -> "本轮未上架"
            displayed.stock <= 0 -> "本轮售罄"
            snapshot.gold < displayed.offer.price -> "金币不足"
            else -> "购买 · ${displayed.offer.price}金币"
        }
        session.button.isEnabled = enabled
        session.button.alpha = if (enabled) 1f else .45f
    }

    private fun confirmRefresh() {
        val confirm = GameCatalogSheet(activity, "刷新补给库存", "立即更新本轮商品", "取消", stoneHeader = true)
        confirm.body.addView(GameUi.label(activity, "提前刷新需要${EconomyBalance.SHOP_REFRESH}金币，不推迟下一次自动刷新。\n\n若操作时已到自动换货时间，则免费更新一次。", 14).apply {
            setPadding(dp(8), dp(16), dp(8), dp(32))
        })
        confirm.actions.addView(GameUi.button(activity, "确认刷新", true) {
            val updated = refresh()
            render(force = true)
            if (updated) confirm.dialog.dismiss()
        })
        confirm.show()
    }
}
