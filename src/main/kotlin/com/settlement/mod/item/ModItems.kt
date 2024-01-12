package com.settlement.mod.item

import com.settlement.mod.MODID
import net.fabricmc.fabric.api.item.v1.FabricItemSettings
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroupEntries
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents
import net.minecraft.item.Item
import net.minecraft.item.ItemGroups
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.util.Identifier

object ModItems {
    @JvmField
    val HAND_BELL: Item = registerItem("hand_bell", HandBellItem(Item.Settings().maxDamage(64)))
    @JvmField
    val EMERALD_TOKEN: Item = registerItem("emerald_token", Item(FabricItemSettings()))
    @JvmField
    val EMERALD_BUNDLE: Item = registerItem("emerald_bundle", Item(FabricItemSettings()))

    private fun addItemsToIngredientItemGroup(entries: FabricItemGroupEntries) {
        entries.add(EMERALD_TOKEN)
        entries.add(EMERALD_BUNDLE)
    }

    public fun registerItem(
        name: String,
        item: Item,
    ): Item = Registry.register(Registries.ITEM, Identifier(MODID, name), item)

    public fun register() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(this::addItemsToIngredientItemGroup)
    }
}
