package com.example.douqiuqiu.ui.components

import com.example.douqiuqiu.inventory.InventoryState
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest

/** Stable business state, excluding clocks/shop recovery that legitimately change on resume. */
internal object InspectionOwner {
    fun chestKey(chest: com.example.douqiuqiu.journey.TargetChest): String {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeUTF(chest.id); out.writeUTF(chest.quality); out.writeInt(chest.appraisals)
            out.writeInt(chest.ticket); out.writeInt(chest.difficulty); out.writeUTF(chest.theme.name)
            out.writeInt(chest.items.size)
            chest.items.forEach { out.writeUTF(it.definitionId); out.writeInt(it.x); out.writeInt(it.y); out.writeInt(it.reveal) }
        }
        return MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()).joinToString("") { "%02x".format(it) }
    }
    fun key(s: InventoryState): String {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(s.gold); out.writeInt(s.runGold); out.writeUTF(s.phase.name)
            out.writeInt(s.backpackWidth); out.writeInt(s.backpackHeight)
            out.writeInt(s.safeWidth); out.writeInt(s.safeHeight); out.writeInt(s.skillSlots)
            out.writeInt(s.progress.clears); out.writeUTF(s.progress.lastEnding)
            out.writeUTF(s.progress.run?.id ?: "")
            out.writeInt(s.progress.run?.round ?: 0); out.writeUTF(s.progress.run?.stage?.name ?: "")
            out.writeInt(s.progress.run?.fight?.tick ?: 0)
            out.writeInt(s.items.size)
            s.items.sortedBy { it.instanceId }.forEach {
                out.writeUTF(it.instanceId); out.writeUTF(it.definitionId); out.writeInt(it.count)
                out.writeInt(it.star); out.writeBoolean(it.bound); out.writeUTF(it.place.container.name)
                out.writeInt(it.place.x); out.writeInt(it.place.y); out.writeBoolean(it.place.rotated)
            }
        }
        return MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
