package com.settlement.mod.data

import com.settlement.mod.MODID
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput
import net.fabricmc.fabric.api.datagen.v1.provider.SimpleFabricLootTableProvider
import net.minecraft.item.Items
import net.minecraft.loot.LootPool
import net.minecraft.loot.LootTable
import net.minecraft.loot.context.LootContextTypes
import net.minecraft.loot.entry.ItemEntry
import net.minecraft.loot.provider.number.ConstantLootNumberProvider
import net.minecraft.registry.Registries
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.RegistryWrapper
import net.minecraft.util.Identifier
import java.util.concurrent.CompletableFuture
import java.util.function.BiConsumer

class ProfessionLootTableProvider(
    output: FabricDataOutput,
    registryLookup: CompletableFuture<RegistryWrapper.WrapperLookup>,
) : SimpleFabricLootTableProvider(output, registryLookup, LootContextTypes.EMPTY) {

    override fun accept(consumer: BiConsumer<RegistryKey<LootTable>, LootTable.Builder>) {

        consumer.accept(ModLootTables.GATHERER,
            LootTable.builder().pool(
                LootPool.builder()
                    .rolls(ConstantLootNumberProvider.create(1.0f))
                    .with(ItemEntry.builder(Items.STICK).weight(7))
                    .with(ItemEntry.builder(Items.FLINT).weight(3))
            )
        )

        consumer.accept(ModLootTables.FISHERMAN,
            LootTable.builder().pool(
                LootPool.builder()
                    .rolls(ConstantLootNumberProvider.create(1.0f))
                    .with(ItemEntry.builder(Items.COD).weight(1))
                    .with(ItemEntry.builder(Items.SALMON).weight(1))
                    .with(ItemEntry.builder(Items.PUFFERFISH).weight(1))
                    .with(ItemEntry.builder(Items.STICK).weight(2))
            )
        )

        consumer.accept(ModLootTables.BUILDER,
            LootTable.builder().pool(
                LootPool.builder()
                    .rolls(ConstantLootNumberProvider.create(1.0f))
                    .with(ItemEntry.builder(Items.BRICKS).weight(4))
                    .with(ItemEntry.builder(Items.COBBLESTONE).weight(6))
            )
        )
    }
}

object ModLootTables {
    val GATHERER: RegistryKey<LootTable> =
        RegistryKey.of(RegistryKeys.LOOT_TABLE, Identifier.of(MODID, "professions/gatherer"))

    val FISHERMAN: RegistryKey<LootTable> =
        RegistryKey.of(RegistryKeys.LOOT_TABLE, Identifier.of(MODID, "professions/fisherman"))

    val BUILDER: RegistryKey<LootTable> =
        RegistryKey.of(RegistryKeys.LOOT_TABLE, Identifier.of(MODID, "professions/builder"))

}
