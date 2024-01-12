package com.settlement.mod.profession

import com.settlement.mod.action.Action
import com.settlement.mod.entity.mob.AbstractVillagerEntity
import com.settlement.mod.item.ItemPredicate
import com.settlement.mod.structure.StructureType
import com.settlement.mod.util.Finder
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.mob.HostileEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Item
import net.minecraft.item.Items

sealed class Profession {
    open val type: ProfessionType = ProfessionType.NONE
    open val structureInterest: StructureType = StructureType.NONE
    open val desiredItems: Set<(Item) -> Boolean> = setOf(ItemPredicate.EDIBLE)

    // this might backfire
    open val INVENTORY_HANDLER: (AbstractVillagerEntity) -> Unit = { e ->
        if (!e.inventory.isHolding(ItemPredicate.CROSSBOW)) {
            e.item[Action.Type.CHARGE] = e.inventory.findItem(ItemPredicate.CROSSBOW)
        } else {
            e.item[Action.Type.CHARGE] = 0
        }

        if (!e.inventory.isHolding(ItemPredicate.BOW)) {
            e.item[Action.Type.AIM] = e.inventory.findItem(ItemPredicate.BOW)
        } else {
            e.item[Action.Type.AIM] = 0
        }

        if (!e.inventory.isHolding(ItemPredicate.SWORD)) {
            e.item[Action.Type.ATTACK] = e.inventory.findItem(ItemPredicate.SWORD)
        } else {
            e.item[Action.Type.ATTACK] = 0
        }

        if (!e.inventory.isHolding(ItemPredicate.SHIELD)) {
            e.item[Action.Type.DEFEND] = e.inventory.findItem(ItemPredicate.SHIELD)
        } else {
            e.item[Action.Type.DEFEND] = 0
        }
    }
    open val CLICK_HANDLER: (AbstractVillagerEntity, PlayerEntity) -> Boolean = { _, _ -> false }
    open val TRACK_HANDLER: (AbstractVillagerEntity, LivingEntity, Boolean) -> Unit = { entity, target, isAttacker ->
        when (target) {
            is PlayerEntity -> {
                entity.setFighting(isAttacker)
                entity.setTarget(target)
            }
            is HostileEntity -> {
                entity.setFighting(true)
                entity.setTarget(target)
            }
            is AbstractVillagerEntity -> {
                entity.setFighting(false)
                entity.setTarget(target)
            }
            else -> {
                entity.setFighting(isAttacker)
                entity.setTarget(target)
            }
        }
    }

    open val TARGET_HANDLER: (AbstractVillagerEntity) -> Unit = { entity ->
        entity.target?.let { target ->
            if (entity.isFighting()) {
                Finder.findFleeBlock(entity, target)?.let { errand ->
                    entity.pushErrand(errand.cid, errand.pos)
                }
            }
        }
    }

    object Unemployed : Profession() {
        override val type = ProfessionType.NONE
        override val structureInterest = StructureType.NONE
    }
    // this profession must look for grass blocks and yield (simulate work) generates -> sticks, seeds, apples, potatos and carrots
    object Gatherer : Profession() {
        override val type = ProfessionType.GATHERER
        override val structureInterest = StructureType.NONE
    }
    // this profession will kill chickens and pigs
    object Hunter : Profession() {
        override val type = ProfessionType.HUNTER
        override val structureInterest = StructureType.NONE
    }

    object Farmer : Profession() {
        override val type = ProfessionType.FARMER
        override val structureInterest = StructureType.FARM
        override val desiredItems = setOf(ItemPredicate.EDIBLE, ItemPredicate.HOE, { it == Items.BONE_MEAL }, ItemPredicate.PLANTABLE)
    }
    // TODO: tweak fishing items and timing
    object Fisherman : Profession() {
        override val type = ProfessionType.FISHERMAN
        override val structureInterest = StructureType.POND
        override val desiredItems = setOf(ItemPredicate.EDIBLE, ItemPredicate.FISHING_ROD)
    }
    // TODO: model trading mini-game [renewable]
    object Merchant : Profession() {
        override val type = ProfessionType.MERCHANT
        override val structureInterest = StructureType.MARKET
        override val desiredItems = setOf(ItemPredicate.EDIBLE, { it.defaultStack.isOf(Items.EMERALD) })
        override val CLICK_HANDLER: (AbstractVillagerEntity, PlayerEntity) -> Boolean = { entity, player ->
            player.openHandledScreen(entity)
            true
        }
    }
    // TODO: create o TARGET_HANDLER behavior
    object Recruit : Profession() {
        override val type = ProfessionType.RECRUIT
        override val structureInterest = StructureType.PEN
        override val desiredItems = setOf(ItemPredicate.EDIBLE, ItemPredicate.SWORD, ItemPredicate.ARMOR)
    }
    // TODO: test this more
    object Guard : Profession() {
        override val type = ProfessionType.GUARD
        override val structureInterest = StructureType.PEN
        override val desiredItems =
            setOf(
                ItemPredicate.EDIBLE,
                ItemPredicate.BOW,
                ItemPredicate.ARROW,
                ItemPredicate.CROSSBOW,
                ItemPredicate.SWORD,
                ItemPredicate.ARMOR,
                ItemPredicate.SHIELD,
            )
        override val TARGET_HANDLER: (AbstractVillagerEntity) -> Unit = { entity ->
            entity.target?.let { target ->
                val distance = entity.squaredDistanceTo(target)
                if (entity.isFighting()) {
                    val canDamage =
                        when {
                            distance >= 18 ->
                                listOf(
                                    Action.Type.CHARGE,
                                    Action.Type.AIM,
                                    Action.Type.ATTACK,
                                ).any { action ->
                                    entity.item[action]?.takeIf { it != -1 }?.let { entity.pushErrand(action) } != null
                                }
                            else -> {
                                if (entity.random.nextInt(3) == 0) {
                                    entity.item[Action.Type.DEFEND]?.takeIf { it != -1 }?.let { entity.pushErrand(Action.Type.DEFEND) }
                                        ?: entity.item[Action.Type.ATTACK]?.takeIf { it != -1 }?.let {
                                            if (distance < 6 &&
                                                !entity.errandManager.has(Action.Type.STRAFE)
                                            ) {
                                                entity.pushErrand(Action.Type.STRAFE)
                                            }
                                            entity.pushErrand(Action.Type.ATTACK)
                                        }
                                } else {
                                    entity.item[Action.Type.ATTACK]?.takeIf { it != -1 }?.let {
                                        if (distance < 6 &&
                                            !entity.errandManager.has(Action.Type.STRAFE)
                                        ) {
                                            entity.pushErrand(Action.Type.STRAFE)
                                        }
                                        entity.pushErrand(Action.Type.ATTACK)
                                    }
                                } != null
                            }
                        }
                    if (!canDamage) {
                        Finder.findFleeBlock(entity, target)?.let { errand ->
                            entity.pushErrand(errand.cid, errand.pos)
                        }
                    }
                }
            }
        }
    }
    // TODO: trigger move to the pen and find a good distribution for shearing sheep
    object Shepherd : Profession() {
        override val type = ProfessionType.SHEPHERD
        override val structureInterest = StructureType.PEN
        override val desiredItems = setOf(ItemPredicate.EDIBLE, ItemPredicate.SHEARS, ItemPredicate.WOOLS)
    }

    companion object {
        private val professions =
            mapOf(
                ProfessionType.GATHERER to Gatherer,
                ProfessionType.HUNTER to Hunter,
                ProfessionType.FARMER to Farmer,
                ProfessionType.FISHERMAN to Fisherman,
                ProfessionType.MERCHANT to Merchant,
                ProfessionType.SHEPHERD to Shepherd,
                ProfessionType.GUARD to Guard,
                ProfessionType.RECRUIT to Recruit,
                ProfessionType.NONE to Unemployed,
            )

        fun get(type: ProfessionType): Profession = professions[type] ?: Unemployed
    }
}
