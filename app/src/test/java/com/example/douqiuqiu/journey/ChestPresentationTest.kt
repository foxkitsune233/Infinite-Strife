package com.example.douqiuqiu.journey

import com.example.douqiuqiu.inventory.*
import org.junit.Assert.*
import org.junit.Test

class ChestPresentationTest {
    private val tiny = ItemDefinition("tiny", "测试物品", ItemKind.EQUIPMENT, 1, 1, price = 100)
    @Test fun c01CapacityAndAttemptCountsGrowByQuality() {
        val profiles = listOf("白", "蓝", "紫", "金").map(ChestLayout::capacity)
        assertEquals(listOf(12, 20, 30, 36), profiles.map { it.columns * it.rows })
        assertEquals(listOf(4, 7, 10, 14), profiles.map { it.attempts })
    }
    @Test fun c02FactoryActuallyUsesProfilesNotJustPreview() {
        val rules = InventoryRules(mapOf(tiny.id to tiny))
        val generated = ChestFactory(rules).batch(InventoryState())
        generated.progress.chests.forEach { box ->
            val capacity = ChestLayout.capacity(box.quality)
            assertEquals(capacity.attempts, box.items.size)
            assertTrue(box.items.all { it.x in 0 until capacity.columns && it.y in 0 until capacity.rows })
        }
        assertNull(rules.validate(generated))
    }
    @Test fun c03LegacyWhiteBoxCanStillDisplayOriginalSixBySixCoordinates() {
        val legacy = TargetChest("legacy", "白", 0, 60, listOf(ChestItem(tiny.id, 5, 5)))
        assertEquals(6, ChestLayout.display(legacy, mapOf(tiny.id to tiny)).columns)
        assertEquals(6, ChestLayout.display(legacy, mapOf(tiny.id to tiny)).rows)
        assertEquals(legacy, ChestFactory(InventoryRules(mapOf(tiny.id to tiny)))
            .batch(InventoryState(progress = CampaignProgress(chests = listOf(legacy)))).progress.chests.single())
    }
    @Test fun c04DurationsIncreaseWithSizeQualityAndPriceIndependently() {
        val base = tiny.copy(price = 0, quality = "白")
        val duration = AppraisalTiming.durationMs(base)
        assertTrue(AppraisalTiming.durationMs(base.copy(width = 3, height = 3)) > duration)
        assertTrue(AppraisalTiming.durationMs(base.copy(quality = "金")) > duration)
        assertTrue(AppraisalTiming.durationMs(base.copy(price = 1000)) > duration)
        assertTrue(AppraisalTiming.durationMs(base.copy(width = 6, height = 6, quality = "金", price = Int.MAX_VALUE)) <= 2400)
        assertTrue(duration >= 600)
    }
    @Test fun c05OnlyThreeItemsInspectAtOnceAndTimingIsDeterministic() {
        val items = List(8) { tiny }
        val starts = AppraisalTiming.startsMs(items)
        val duration = AppraisalTiming.durationMs(tiny)
        assertEquals(listOf(0L, 0L, 0L, duration, duration, duration, duration * 2, duration * 2), starts)
        assertEquals(starts, AppraisalTiming.startsMs(items))
        assertTrue(AppraisalTiming.startsMs(emptyList()).isEmpty())
    }
}
