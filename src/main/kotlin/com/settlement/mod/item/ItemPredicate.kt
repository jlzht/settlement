package com.settlement.mod.item

import com.settlement.mod.action.Action
import com.settlement.mod.registry.tag.ModItemTags
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.tag.ItemTags
import net.minecraft.registry.tag.TagKey

object ItemPredicate {
    val HOE = isOn(ItemTags.HOES)
    val BOW = isOf(Items.BOW)
    val ARROW = isOf(Items.ARROW)
    val SHIELD = isOf(Items.SHIELD)
    val SWORD = isOn(ItemTags.SWORDS)
    val PICKAXE = isOn(ItemTags.PICKAXES)
    val AXE = isOn(ItemTags.AXES)
    val SHOVEL = isOn(ItemTags.SHOVELS)
    val FISHING_ROD = isOf(Items.FISHING_ROD)
    val ARMOR = isOn(ItemTags.TRIMMABLE_ARMOR)
    val CROSSBOW = isOf(Items.CROSSBOW)
    val POTION = isOf(Items.POTION)
    val SPLASH_POTION = isOf(Items.SPLASH_POTION)
    val SHEARS = isOf(Items.SHEARS)
    val PLANTABLE = isOn(ItemTags.VILLAGER_PLANTABLE_SEEDS)
    val TRADEABLE = isOf(Items.EMERALD)
    val FERTILIZER = isOn(ModItemTags.FERTILIZERS)
    val WOOLS = isOn(ItemTags.WOOL)
    val EDIBLE = isOn(ModItemTags.FOODS)
    val FUEL = isOn(ModItemTags.FUELS)
    val COOKABLE = isOn(ModItemTags.COOKABLES)
    val SMELTABLE = isOn(ModItemTags.SMELTABLES)

    val REPAIRABLE: (ItemStack) -> Boolean = { it.isDamaged }
    val STORABLE: (ItemStack) -> Boolean = { true }

    private fun isOn(tag: TagKey<Item>): (ItemStack) -> Boolean = { it.isIn(tag) }

    private fun isOf(item: Item): (ItemStack) -> Boolean = { it.isOf(item) }

    val predicateMap: Map<(ItemStack) -> Boolean, Action.Type> =
        mapOf(
            COOKABLE to Action.Type.COOK,
            FUEL to Action.Type.REFILL,
            EDIBLE to Action.Type.EAT,
            ARMOR to Action.Type.WEAR,
            SHEARS to Action.Type.SHEAR,
            HOE to Action.Type.TILL,
            FERTILIZER to Action.Type.POWDER,
            PLANTABLE to Action.Type.PLANT,
            SWORD to Action.Type.ATTACK,
            BOW to Action.Type.AIM,
            CROSSBOW to Action.Type.CHARGE,
            SHIELD to Action.Type.DEFEND,
            SPLASH_POTION to Action.Type.THROW,
            POTION to Action.Type.DRINK,
            FISHING_ROD to Action.Type.FISH,
            PICKAXE to Action.Type.MINE,
            SHOVEL to Action.Type.DIG,
            AXE to Action.Type.CHOP,
            STORABLE to Action.Type.STORE,
        )

    val actionMap: Map<Action.Type, (ItemStack) -> Boolean> =
        predicateMap.entries.associate { (pred, act) -> act to pred }

    fun getActionFromStack(stack: ItemStack): Action.Type? = predicateMap.entries.firstOrNull { (pred, _) -> pred(stack) }?.value
}
