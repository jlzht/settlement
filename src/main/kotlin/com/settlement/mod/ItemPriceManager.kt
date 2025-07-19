package com.settlement.mod

import com.google.gson.JsonParser
import com.mojang.serialization.Codec
import com.mojang.serialization.JsonOps
import net.fabricmc.fabric.api.resource.SimpleResourceReloadListener
import net.minecraft.item.Item
import net.minecraft.registry.Registries
import net.minecraft.resource.ResourceManager
import net.minecraft.util.Identifier
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

object ItemPriceManager : SimpleResourceReloadListener<Map<String, Float>> {
    private val PATH = Identifier.of(MODID, "prices/prices.json")
    private val CODEC: Codec<Map<String, Float>> = Codec.unboundedMap(Codec.STRING, Codec.FLOAT)

    private val prices: MutableMap<String, Float> = ConcurrentHashMap()

    override fun load(
        manager: ResourceManager,
        executor: Executor,
    ): CompletableFuture<Map<String, Float>> =
        CompletableFuture.supplyAsync({
            val loadedPrices = mutableMapOf<String, Float>()
            manager.getResource(PATH).ifPresent { resource ->
                try {
                    resource.reader.use { reader ->
                        val jsonElement = JsonParser.parseReader(reader)
                        CODEC
                            .parse(JsonOps.INSTANCE, jsonElement)
                            .ifSuccess { parsedMap -> loadedPrices.putAll(parsedMap) }
                            .ifError { error -> LOGGER.error("Failed to parse prices file {}: {}", PATH, error.message()) }
                    }
                } catch (e: Exception) {
                    LOGGER.error("Error reading or parsing prices file: $PATH", e)
                }
            }
            loadedPrices
        }, executor)

    override fun apply(
        data: Map<String, Float>,
        manager: ResourceManager,
        executor: Executor,
    ): CompletableFuture<Void> =
        CompletableFuture.runAsync({
            this.prices.clear()
            this.prices.putAll(data)
            LOGGER.info("Successfully applied {} item prices.", data.size)
        }, executor)

    override fun getFabricId(): Identifier = Identifier.of(MODID, "price_manager")

    fun getPrice(item: Item): Float? {
        val idString = Registries.ITEM.getId(item).toString()
        return prices[idString]
    }
}
