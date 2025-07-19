package com.settlement.mod.data

import com.settlement.mod.registry.tag.ModBlockTags
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.RegistryWrapper
import net.minecraft.registry.tag.BlockTags
import java.util.concurrent.CompletableFuture

class BlockTagProvider(
    output: FabricDataOutput,
    registries: CompletableFuture<RegistryWrapper.WrapperLookup>,
) : FabricTagProvider<Block>(output, RegistryKeys.BLOCK, registries) {
    override fun configure(registryLookup: RegistryWrapper.WrapperLookup) {
        getOrCreateTagBuilder(ModBlockTags.STORAGE)
            .add(Blocks.CHEST)

        getOrCreateTagBuilder(ModBlockTags.ARABLE)
            .add(Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.GRASS_BLOCK, Blocks.GRAVEL, Blocks.SAND)

        getOrCreateTagBuilder(ModBlockTags.STONES)
            .add(Blocks.STONE, Blocks.ANDESITE, Blocks.DEEPSLATE, Blocks.COBBLESTONE)

        getOrCreateTagBuilder(ModBlockTags.COOKS)
            .add(Blocks.SMOKER)

        getOrCreateTagBuilder(ModBlockTags.SMELTERS)
            .add(Blocks.BLAST_FURNACE)
            .add(Blocks.FURNACE)

        getOrCreateTagBuilder(ModBlockTags.SEATINGS)
            .add(Blocks.OAK_SLAB, Blocks.SPRUCE_SLAB, Blocks.STONE_SLAB)

        getOrCreateTagBuilder(ModBlockTags.BARRIERS)
            .add(Blocks.OAK_DOOR, Blocks.SPRUCE_DOOR, Blocks.OAK_FENCE_GATE)

        getOrCreateTagBuilder(ModBlockTags.FENCES)
            .forceAddTag(BlockTags.FENCE_GATES)
            .forceAddTag(BlockTags.FENCES)

        getOrCreateTagBuilder(ModBlockTags.WINDOWS)
            .forceAddTag(BlockTags.WOOL_CARPETS)
            .forceAddTag(BlockTags.TRAPDOORS)
            .forceAddTag(BlockTags.WALLS)
            .forceAddTag(BlockTags.SLABS)
            .addTag(ModBlockTags.FENCES)
            .add(Blocks.GLASS_PANE)
            .add(Blocks.IRON_BARS)

        getOrCreateTagBuilder(ModBlockTags.HARVESTS)
            .add(Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES, Blocks.BEETROOTS)
    }
}
