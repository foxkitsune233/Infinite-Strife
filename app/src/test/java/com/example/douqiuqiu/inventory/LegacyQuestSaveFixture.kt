package com.example.douqiuqiu.inventory

import java.io.*
import java.util.zip.CRC32

/** Read the legacy schema boundary instead of guessing how many bytes a current extension occupies. */
internal object LegacyQuestSaveFixture {
    fun payloadThrough(current: ByteArray, version: Int): ByteArray {
        require(version in 12..14)
        val payload = current.copyOfRange(20, current.size)
        val input = DataInputStream(ByteArrayInputStream(payload))
        repeat(7) { input.readInt() }
        input.readUTF()
        repeat(input.readInt()) {
            input.readUTF(); input.readUTF(); input.readUTF()
            input.readInt(); input.readInt(); input.readBoolean()
            input.readInt(); input.readBoolean(); input.readInt()
        }
        // v12/v13 fixtures have no active run; the v14 fixture covers active runs including loot/fights.
        CampaignBinaryCodec.read(input, version)
        return payload.copyOf(payload.size - input.available())
    }

    fun envelope(version: Int, payload: ByteArray): ByteArray = ByteArrayOutputStream().also { buffer ->
        DataOutputStream(buffer).use { out ->
            out.writeInt(0x44515131); out.writeInt(version); out.writeInt(payload.size)
            out.writeLong(CRC32().apply { update(payload) }.value); out.write(payload)
        }
    }.toByteArray()
}
