@file:OptIn(kotlin.js.ExperimentalJsExport::class)

package com.example.douqiuqiu.web

import com.example.douqiuqiu.combat.CombatMath
import com.example.douqiuqiu.content.HeroContent

/**
 * 网页版可行性探针：验证 shared 的通用代码能否被编译成 JS 并从 JS 侧调用。
 * 仅供阶段 0 验证使用，确认后由正式的 WebApi 门面替换。
 */
@JsExport
fun engineProbe(): String {
    val armor = 12.0
    val multiplier = CombatMath.physicalDamageMultiplier(armor)
    val skywrath = HeroContent.legacy("skywrath")
    val abaddon = HeroContent.legacy("abaddon")
    return "armor=$armor physicalMultiplier=$multiplier " +
        "skywrath(hits=${skywrath.hits}, perHit=${skywrath.damagePerHit}) " +
        "abaddon(shield=${abaddon.shield})"
}
