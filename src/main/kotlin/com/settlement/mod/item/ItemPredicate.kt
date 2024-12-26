package com.settlement.mod.item

import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.item.ArmorItem
import net.minecraft.item.ArrowItem
import net.minecraft.item.BowItem
import net.minecraft.item.CrossbowItem
import net.minecraft.item.FishingRodItem
import net.minecraft.item.HoeItem
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.PickaxeItem
import net.minecraft.item.RangedWeaponItem
import net.minecraft.item.ShieldItem
import net.minecraft.item.ShearsItem
import net.minecraft.item.ShovelItem
import net.minecraft.item.SwordItem
import net.minecraft.registry.tag.ItemTags

object ItemPredicate {
    val HOE: (Item) -> Boolean = { item -> item is HoeItem }
    val BOW: (Item) -> Boolean = { item -> item is BowItem }
    val ARROW: (Item) -> Boolean = { item -> item is ArrowItem }
    val SHIELD: (Item) -> Boolean = { item -> item is ShieldItem }
    val SWORD: (Item) -> Boolean = { item -> item is SwordItem }
    val PICKAXE: (Item) -> Boolean = { item -> item is PickaxeItem }
    val SHOVEL: (Item) -> Boolean = { item -> item is ShovelItem }
    val FISHING_ROD: (Item) -> Boolean = { item -> item is FishingRodItem }
    val ARMOR: (Item) -> Boolean = { item -> item is ArmorItem }
    val CROSSBOW: (Item) -> Boolean = { item -> item is CrossbowItem }
    val RANGED_WEAPON: (Item) -> Boolean = { item -> item is RangedWeaponItem }
    val SHEARS: (Item) -> Boolean = { item -> item is ShearsItem }
    val PLANTABLE: (Item) -> Boolean = { item -> item.defaultStack.isIn(ItemTags.VILLAGER_PLANTABLE_SEEDS) }
    val WOOLS: (Item) -> Boolean = { item -> item.defaultStack.isIn(ItemTags.WOOL) }
    val EDIBLE: (Item) -> Boolean = { item -> item.isFood() }

    // use this instead of MobEntity use, I need to change this to handle items based on profession
    fun prefersNewEquipment(
        newStack: ItemStack,
        oldStack: ItemStack,
    ): Boolean {
        if (oldStack.isEmpty) return true
        val newItem = newStack.item
        val oldItem = oldStack.item
        if (newItem is ArmorItem) {
            if (EnchantmentHelper.hasBindingCurse(oldStack)) {
                return false
            } else {
                val newArmorItem = newItem
                val oldArmorItem = oldItem as ArmorItem
                if (newArmorItem.protection != oldArmorItem.protection) {
                    newArmorItem.protection > oldArmorItem.protection
                } else if (newArmorItem.toughness != oldArmorItem.toughness) {
                    newArmorItem.toughness > oldArmorItem.toughness
                } else {
                    prefersNewDamageableItem(newStack, oldStack)
                }
            }
        }
        return false
    }

    fun prefersNewDamageableItem(
        newStack: ItemStack,
        oldStack: ItemStack,
    ): Boolean =
        if (newStack.damage < oldStack.damage || newStack.hasNbt() && !oldStack.hasNbt()) {
            true
        } else if (newStack.hasNbt() && oldStack.hasNbt()) {
            newStack.nbt!!.keys.any { it != "Damage" } && oldStack.nbt!!.keys.none { it != "Damage" }
        } else {
            false
        }
}
