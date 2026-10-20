package com.example.douqiuqiu.equipment

/** Common selection boundary for lightning/extra projectiles; unused targets never become extra hits. */
data class EquipmentTarget(val unitId: Int, val alive: Boolean = true, val targetable: Boolean = true)

object EquipmentTargeting {
    fun select(primaryId: Int, enemies: List<EquipmentTarget>, maximum: Int): List<Int> {
        require(maximum > 0 && enemies.map { it.unitId }.distinct().size == enemies.size)
        val valid = enemies.filter { it.alive && it.targetable }.map { it.unitId }
        if (primaryId !in valid) return emptyList()
        return (listOf(primaryId) + valid.filter { it != primaryId }.sorted()).take(maximum)
    }
}
