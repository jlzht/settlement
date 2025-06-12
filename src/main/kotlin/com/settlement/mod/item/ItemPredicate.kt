package com.settlement.mod.item

import com.settlement.mod.action.Action
import net.minecraft.component.DataComponentTypes
import net.minecraft.item.ArrowItem
import net.minecraft.item.AxeItem
import net.minecraft.item.BowItem
import net.minecraft.item.CrossbowItem
import net.minecraft.item.FishingRodItem
import net.minecraft.item.HoeItem
import net.minecraft.item.Item
import net.minecraft.item.Items
import net.minecraft.item.RangedWeaponItem
import net.minecraft.item.ShearsItem
import net.minecraft.item.ShieldItem
import net.minecraft.item.ShovelItem
import net.minecraft.registry.tag.ItemTags

object ItemPredicate {
    val HOE: (Item) -> Boolean = { item -> item is HoeItem }
    val BOW: (Item) -> Boolean = { item -> item is BowItem }
    val ARROW: (Item) -> Boolean = { item -> item is ArrowItem }
    val SHIELD: (Item) -> Boolean = { item -> item is ShieldItem }
    val SWORD: (Item) -> Boolean = { item -> item.defaultStack.isIn(ItemTags.SWORDS) }
    val PICKAXE: (Item) -> Boolean = { item -> item.defaultStack.isIn(ItemTags.PICKAXES) }
    val AXE: (Item) -> Boolean = { item -> item is AxeItem }
    val SHOVEL: (Item) -> Boolean = { item -> item is ShovelItem }
    val FISHING_ROD: (Item) -> Boolean = { item -> item is FishingRodItem }
    val ARMOR: (Item) -> Boolean = { item -> item.defaultStack.isIn(ItemTags.TRIMMABLE_ARMOR) }
    val CROSSBOW: (Item) -> Boolean = { item -> item is CrossbowItem }
    val RANGED_WEAPON: (Item) -> Boolean = { item -> item is RangedWeaponItem }
    val SHEARS: (Item) -> Boolean = { item -> item is ShearsItem }
    val PLANTABLE: (Item) -> Boolean = { item -> item.defaultStack.isIn(ItemTags.VILLAGER_PLANTABLE_SEEDS) }
    val WOOLS: (Item) -> Boolean = { item -> item.defaultStack.isIn(ItemTags.WOOL) }
    val EDIBLE: (Item) -> Boolean = { item -> item.defaultStack.getComponents().contains(DataComponentTypes.FOOD) }

    val predicateToActionMap: Map<(Item) -> Boolean, Action.Type> =
        mapOf(
            ItemPredicate.EDIBLE to Action.Type.EAT,
            ItemPredicate.HOE to Action.Type.TILL,
            ItemPredicate.PLANTABLE to Action.Type.PLANT,
            ItemPredicate.SWORD to Action.Type.ATTACK,
            ItemPredicate.BOW to Action.Type.AIM,
            ItemPredicate.CROSSBOW to Action.Type.CHARGE,
            ItemPredicate.SHIELD to Action.Type.DEFEND,
            ItemPredicate.FISHING_ROD to Action.Type.FISH,
            ItemPredicate.PICKAXE to Action.Type.MINE,
            ItemPredicate.AXE to Action.Type.CHOP,
        )

    val actionToPredicateMap: Map<Action.Type, (Item) -> Boolean> =
        predicateToActionMap.entries.associate { (pred, act) -> act to pred }

    fun getActionFromItem(item: Item): Action.Type? =
        ItemPredicate.predicateToActionMap.entries
            .firstOrNull { (pred, _) -> pred(item) }
            ?.value
}
