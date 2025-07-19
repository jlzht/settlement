package com.settlement.mod.data

import com.mojang.serialization.Codec
import com.settlement.mod.MODID
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput
import net.fabricmc.fabric.api.datagen.v1.provider.FabricCodecDataProvider
import net.minecraft.data.DataOutput
import net.minecraft.item.Item
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import net.minecraft.registry.RegistryWrapper
import net.minecraft.util.Identifier
import java.util.concurrent.CompletableFuture
import java.util.function.BiConsumer

class ItemPriceProvider(
    output: FabricDataOutput,
    registriesFuture: CompletableFuture<RegistryWrapper.WrapperLookup>,
) : FabricCodecDataProvider<Map<String, Float>>(
        output,
        registriesFuture,
        DataOutput.OutputType.DATA_PACK,
        "prices",
        Codec.unboundedMap(Codec.STRING, Codec.FLOAT),
    ) {
    // The prices is measured in ModItems.EMERALD_HARDS
    private val priceBuilder = mutableMapOf<Item, Float>()

    // TODO: add items
    private fun generatePrices() {
        addPrice(
            Items.DIAMOND to 12.0f,
            Items.IRON_INGOT to 2.0f,
        )
    }

    protected fun addPrice(vararg pairs: Pair<Item, Float>) {
        priceBuilder.putAll(pairs)
    }

    override fun configure(
        provider: BiConsumer<Identifier, Map<String, Float>>,
        lookup: RegistryWrapper.WrapperLookup,
    ) {
        val priceMap =
            priceBuilder.entries.associate { (item, price) ->
                Registries.ITEM.getId(item).toString() to price
            }

        val file = Identifier.of(MODID, "prices")

        provider.accept(file, priceMap)
    }

    override fun getName() = "Price Provider"
}
