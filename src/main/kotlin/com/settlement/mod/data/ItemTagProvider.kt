package com.settlement.mod.data

import net.minecraft.registry.tag.ItemTags
import com.settlement.mod.registry.tag.ModItemTags
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider
import net.minecraft.item.Item
import net.minecraft.item.Items
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.RegistryWrapper
import java.util.concurrent.CompletableFuture

class ItemTagProvider(
    output: FabricDataOutput,
    registries: CompletableFuture<RegistryWrapper.WrapperLookup>,
) : FabricTagProvider<Item>(output, RegistryKeys.ITEM, registries) {
    override fun configure(registryLookup: RegistryWrapper.WrapperLookup) {
        getOrCreateTagBuilder(ModItemTags.FOODS)
            .add(
                Items.APPLE,
                Items.COOKIE,
                Items.BREAD,
                Items.BAKED_POTATO,
                Items.COOKED_CHICKEN,
                Items.COOKED_MUTTON,
                Items.COOKED_COD,
                Items.COOKED_SALMON,
                Items.COOKED_BEEF,
            )

        getOrCreateTagBuilder(ModItemTags.COOKABLES)
            .add(Items.BEEF, Items.MUTTON, Items.CHICKEN, Items.COD, Items.SALMON)

        getOrCreateTagBuilder(ModItemTags.HOES)
            .add(Items.WOODEN_HOE, Items.STONE_HOE, Items.GOLDEN_HOE, Items.IRON_HOE)

        getOrCreateTagBuilder(ModItemTags.RODS)
            .add(Items.FISHING_ROD)

        getOrCreateTagBuilder(ModItemTags.FISHES)
            .add(Items.TROPICAL_FISH, Items.SALMON, Items.COD)
        // TODO: add melon and pumpkin logic to farms
        getOrCreateTagBuilder(ModItemTags.SEEDS)
            .add(Items.BEETROOT_SEEDS, Items.WHEAT_SEEDS, Items.POTATO, Items.CARROT)

        getOrCreateTagBuilder(ModItemTags.SMELTABLES)
            .add(Items.RAW_IRON, Items.RAW_GOLD, Items.RAW_COPPER)

        getOrCreateTagBuilder(ModItemTags.BASIC_COMBAT)
            .add(Items.BONE_MEAL)

        getOrCreateTagBuilder(ModItemTags.ARMORS)
            .add(
                Items.IRON_HELMET,
                Items.IRON_CHESTPLATE,
                Items.IRON_LEGGINGS,
                Items.IRON_BOOTS,
            )

        getOrCreateTagBuilder(ModItemTags.BASIC_COMBAT)
            .add(Items.STONE_SWORD, Items.WOODEN_SWORD)

        getOrCreateTagBuilder(ModItemTags.INTERMEDIATE_COMBAT)
            .add(
                Items.IRON_SWORD,
                Items.IRON_AXE,
                Items.GOLDEN_SWORD,
                Items.GOLDEN_AXE,
                Items.STONE_SWORD,
                Items.STONE_AXE,
                Items.WOODEN_SWORD,
                Items.WOODEN_AXE,
                Items.SHIELD,
                Items.BOW,
            )

        getOrCreateTagBuilder(ModItemTags.ADVANCED_COMBAT)
            .addTag(ModItemTags.INTERMEDIATE_COMBAT)
            .add(Items.CROSSBOW, Items.TRIDENT)
    }
}
