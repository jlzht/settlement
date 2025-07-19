package com.settlement.mod

import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.MinecraftClient
import net.minecraft.client.texture.NativeImage
import net.minecraft.client.texture.NativeImageBackedTexture
import net.minecraft.util.Identifier
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Supplier

object SettlementConfig {
    private val base: Path = FabricLoader.getInstance().configDir
    val settlement: Path = base.resolve("settlement")
    val skins: Path = settlement.resolve("skins")

    private val skinPattern = Regex("""skin_(\d+)\.png""")

    var useAlternativeModel = true

    var failedLoadingSkins = false
        private set

    var skinCount = 0
        private set

    val skinTextures: MutableMap<Int, Identifier> = mutableMapOf()

    fun ensureFolders() {
        try {
            Files.createDirectories(settlement)
            Files.createDirectories(skins)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun hasValidSkins(): Boolean {
        val found = mutableSetOf<Int>()

        try {
            Files.list(skins).use { paths ->
                paths.forEach { path ->
                    val match = skinPattern.matchEntire(path.fileName.toString())
                    val index = match?.groupValues?.getOrNull(1)?.toIntOrNull()
                    if (index != null) found += index
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }

        if (found.isEmpty()) return false

        val expected = (1..found.max()).toSet()
        val valid = found == expected

        if (valid) skinCount = found.size

        return valid
    }

    fun loadSkins(): Boolean {
        if (!hasValidSkins()) {
            failedLoadingSkins = true
            return false
        }

        val manager = MinecraftClient.getInstance().textureManager

        Files.list(skins).use { paths ->
            paths.forEach { path ->
                val match = skinPattern.matchEntire(path.fileName.toString())
                val index = match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return@forEach

                try {
                    val image = NativeImage.read(path.toFile().inputStream())
                    val texture = NativeImageBackedTexture(Supplier { "skin_$index" }, image)
                    val id = Identifier.of(MODID, "external/skin_$index")

                    manager.registerTexture(id, texture)
                    skinTextures[index] = id
                } catch (e: Exception) {
                    System.err.println("Failed to load skin_$index: ${e.message}")
                }
            }
        }
        return true
    }
}
