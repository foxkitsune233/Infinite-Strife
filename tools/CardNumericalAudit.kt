import com.example.douqiuqiu.cards.*
import com.example.douqiuqiu.content.*
import java.io.File

/** Read-only export of evaluated production card parameters; does not advance or alter a profile. */
object CardNumericalAudit {
    private fun value(v: EffectValue): String = when (v) {
        is EffectValue.Num -> v.value.toString()
        is EffectValue.Count -> v.value.toString()
        is EffectValue.Flag -> v.value.toString()
        is EffectValue.Choice -> v.value
    }
    private fun params(e: BoundCardEffect) = e.params.toSortedMap().entries.joinToString("; ") { "${it.key}=${value(it.value)}" }
    private fun bound(id: String, star: Int) = CardCombat.effects(CardBattleState(listOf(CombatCard("audit", id, star))))

    @JvmStatic fun main(args: Array<String>) {
        val dir = File(args[0]).apply { mkdirs() }
        val prefix = args.getOrNull(1) ?: "66"
        val validation = ContentValidator.validate(CardCatalog.all, emptyList())
        val lines = mutableListOf("cardId\tname\tstar\teffectId\tkind\ttrigger\tparams\tintervalMs\tinternalCooldownMs\tmaxStacks\tstackDurationMs")
        val markdown = StringBuilder("# $prefix：卡牌实际数值导出\n\n")
        markdown.append("由 tools/CardNumericalAudit.kt 直接读取生产 CardCatalog、CardCombat.effects、paramsAt；不是从描述文本推断。\n\n")
        markdown.append("45张卡，8流派，24档羁绊。参数保留原单位：percent 通常是小数比例；slowPercent 和易伤 percent 在运行时按百分点解释。\n\n")
        markdown.append("内容契约检查 valid=${validation.valid}，issues=${validation.issues}。结构合法不等于平衡。\n\n")
        for (card in CardCatalog.all) {
            markdown.append("## ${card.id} ${card.name}\n\n${card.description}\n\n")
            markdown.append("| 效果／触发 | 1星 | 2星 | 3星 | 周期／内部CD／层上限／层时长（毫秒） |\n|---|---|---|---|---|\n")
            val stars = (1..3).map { bound(card.id, it) }
            for (index in card.effects.indices) {
                val e = stars[0][index]
                val details = (0..2).map { s -> stars[s][index] }
                markdown.append("| ${e.effect.id} / ${e.effect.trigger} | ${params(details[0])} | ${params(details[1])} | ${params(details[2])} | ")
                markdown.append("${details.joinToString("/") { it.effect.intervalMs?.toString() ?: "无" }}；${e.effect.internalCooldownMs ?: "无"}；${e.effect.maxStacks ?: "无"}；${e.effect.stackDurationMs ?: "无"} |\n")
                for (s in 0..2) {
                    val d = details[s]
                    lines += listOf(card.id, card.name, s+1, d.effect.id, d.effect.kind, d.effect.trigger, params(d),
                        d.effect.intervalMs ?: "", d.effect.internalCooldownMs ?: "", d.effect.maxStacks ?: "", d.effect.stackDurationMs ?: "").joinToString("\t")
                }
            }
            if (stars[0].map(::params) == stars[2].map(::params) && stars[0].map { it.effect.intervalMs } == stars[2].map { it.effect.intervalMs }) {
                markdown.append("\n注意：整张卡1至3星效果数值相同，升级的价值需单独审阅。\n")
            }
            markdown.append("\n")
        }
        markdown.append("## 羁绊（档位累加，不随星级成长）\n\n| 流派 | 档位 | 效果 | 触发 | 参数 | 周期ms |\n|---|---|---|---|---|---|\n")
        for (tier in CardCatalog.synergyTiers) for (e in tier.effects) {
            markdown.append("| ${tier.schoolId} | ${tier.tier} | ${e.kind} | ${e.trigger} | ${e.params.entries.joinToString("; ") { "${it.key}=${value(it.value)}" }} | ${e.intervalMs ?: "无"} |\n")
        }
        File(dir, "$prefix-card-numerical-values.md").writeText(markdown.toString())
        File(dir, "$prefix-card-numerical-values.tsv").writeText(lines.joinToString("\n"))
        println("cards=${CardCatalog.all.size}, tiers=${CardCatalog.synergyTiers.size}, effect-star rows=${lines.size-1}, structuralValid=${validation.valid}")
        val unboundedHeals = CardCatalog.all.flatMap { it.effects }.filter {
            it.trigger == EffectTrigger.LOW_HP && it.kind == EffectKind.HEAL && (it.internalCooldownMs ?: 0) <= 0
        }
        println("Unbounded low-HP heals: ${unboundedHeals.map { it.id }}")
        check(validation.valid)
        check(unboundedHeals.isEmpty())
    }
}
