package com.example.douqiuqiu.ui

import java.io.File
import java.io.DataInputStream
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

/** Checks PNG headers without desktop AWT or Android bitmap mocks. Pixel padding is checked by the exporter. */
class AttackAtlasAssetTest {
    private val assets get() = listOf(File("src/main/assets"), File("app/src/main/assets"))
        .first { File(it, "art/heroes/attack-atlas-v3.json").isFile }
    private val names = listOf("juggernaut", "phantom-assassin", "skywrath", "abaddon", "necrophos", "viper")
    private fun png(file: File, side: Int) {
        DataInputStream(file.inputStream()).use { input ->
            val signature = ByteArray(8); input.readFully(signature)
            assertArrayEquals(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10), signature)
            assertEquals(13, input.readInt()); assertEquals(0x49484452, input.readInt())
            assertEquals(side, input.readInt()); assertEquals(side, input.readInt())
            assertEquals(8, input.readUnsignedByte()); assertEquals(6, input.readUnsignedByte())
        }
    }
    @Test fun allSixRuntimeAtlasesExistAs1024RgbaPngs() {
        val manifest = File(assets, "art/heroes/attack-atlas-v3.json").readText()
        names.forEach { name ->
            val file = "$name-attack-atlas-v3.png"
            assertTrue(manifest.contains(file))
            png(File(assets, "art/heroes/atlases/$file"), 1024)
        }
    }
    @Test fun twentyFourIndividualFramesExistWithFourDistinctPosesPerHero() {
        val root = File(assets, "../../../..").canonicalFile
        names.forEach { name ->
            val hashes = (0..3).map { frame ->
                val file = File(root, "art-source/heroes/attack-v3/frames/$name-$frame.png")
                png(file, 512)
                MessageDigest.getInstance("SHA-256").digest(file.readBytes()).toList()
            }
            assertEquals("Duplicate pose $name", 4, hashes.distinct().size)
        }
    }
}
