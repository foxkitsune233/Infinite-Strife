package com.example.douqiuqiu.inventory

import java.io.*
import java.util.zip.CRC32

/** Read the legacy schema boundary instead of guessing how many bytes a current extension occupies. */
internal object LegacyQuestSaveFixture {
    fun payloadThrough(current: ByteArray, version: Int): ByteArray {
        require(version in 12..17)
        val payload = current.copyOfRange(20, current.size)
        val input = DataInputStream(ByteArrayInputStream(payload))
        repeat(7) { input.readInt() }
        input.readUTF()
        repeat(input.readInt()) {
            input.readUTF(); input.readUTF(); input.readUTF()
            input.readInt(); input.readInt(); input.readBoolean()
            input.readInt(); input.readBoolean(); input.readInt()
        }
        val campaignOffset = payload.size - input.available()
        val progress = CampaignBinaryCodec.read(input, InventoryStore.VERSION)
        val historicalCampaign = ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { CampaignBinaryCodec.write(it, progress, version) }
        }.toByteArray()
        return payload.copyOfRange(0, campaignOffset) + historicalCampaign
    }

    fun envelope(version: Int, payload: ByteArray): ByteArray = ByteArrayOutputStream().also { buffer ->
        DataOutputStream(buffer).use { out ->
            out.writeInt(0x44515131); out.writeInt(version); out.writeInt(payload.size)
            out.writeLong(CRC32().apply { update(payload) }.value); out.write(payload)
        }
    }.toByteArray()
}
