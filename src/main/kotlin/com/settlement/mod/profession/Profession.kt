package com.settlement.mod.profession

import com.settlement.mod.action.Action
import com.settlement.mod.entity.mob.AbstractVillagerEntity
import com.settlement.mod.entity.mob.ErrandType
import com.settlement.mod.item.ItemPredicate
import com.settlement.mod.structure.StructureType
import com.settlement.mod.util.CombatUtils
import com.settlement.mod.util.Finder
import net.minecraft.block.ShortPlantBlock
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.mob.HostileEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Item
import net.minecraft.item.Items
import net.minecraft.util.math.BlockPos

sealed class Profession {
    open val type: ProfessionType = ProfessionType.NONE
    open val structureInterest: StructureType = StructureType.NONE
    open val desiredItems: Set<(Item) -> Boolean> = setOf(ItemPredicate.EDIBLE)

    // this might backfire, pass added stack
    open val INVENTORY_HANDLER: (AbstractVillagerEntity) -> Unit = { e ->
        e.item[Action.Type.EAT] = -3
    }

    open val CLICK_HANDLER: (AbstractVillagerEntity, PlayerEntity) -> Boolean = { _, _ -> false }

    open val IDLE_HANDLER: (AbstractVillagerEntity) -> Boolean = { entity ->
        val manager = entity.errandManager
        val r = entity.random.nextFloat()
        if (r < 0.4f && !entity.isSitting() && !manager.containsErrand(Action.Type.SIT)) {
            Finder.findWanderBlock(entity)?.let { (cid, pos) -> entity.pushErrand(cid, pos) }
        } else if (r > 0.7f) {
            Finder.findSeekBlock(entity).let { (cid, pos) -> entity.pushErrand(cid, pos) }
        } else {
            entity.target?.let { target ->
                if (CombatUtils.isLookingAt(entity, target) &&
                    CombatUtils.isLookingAt(target, entity) &&
                    (target is AbstractVillagerEntity)
                ) {
                    entity.pushErrand(Action.Type.INTERACT)
                }
            }
        }
        true
    }

    open val BLOCK_HANDLER: (AbstractVillagerEntity) -> Unit = { e ->
        val set = mutableSetOf<BlockPos>()
        while (set.size < 24) {
            val offsetX = (-3..3).random()
            val offsetY = (-3..3).random()
            val offsetZ = (-3..3).random()
            set.add(e.blockPos.add(offsetX, offsetY, offsetZ))
        }
        set.forEach {
            if (e.world.getBlockState(it).getBlock() is ShortPlantBlock) {
                e.pushErrand(Action.Type.BREAK, it)
            }
        }
    }

    open val TRACK_HANDLER: (AbstractVillagerEntity, LivingEntity?, Boolean) -> Unit = { entity, target, isAttacker ->
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

    companion object {
        private val professions =
            mapOf(
                ProfessionType.GATHERER to Gatherer,
                ProfessionType.HUNTER to Hunter,
                ProfessionType.FARMER to Farmer,
                ProfessionType.FISHERMAN to Fisherman,
                ProfessionType.MERCHANT to Merchant,
                ProfessionType.LUMBERJACK to Lumberjack,
                ProfessionType.SHEPHERD to Shepherd,
                ProfessionType.GUARD to Guard,
                ProfessionType.RECRUIT to Recruit,
                ProfessionType.MINER to Miner,
                ProfessionType.NONE to Unemployed,
            )

        fun get(type: ProfessionType): Profession = professions[type] ?: Unemployed
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
    override val INVENTORY_HANDLER: (AbstractVillagerEntity) -> Unit = { e ->
        super.INVENTORY_HANDLER(e)
        e.item[Action.Type.TILL] = -3
        e.item[Action.Type.PLANT] = -3
    }
}

// TODO: tweak fishing items and timing
object Fisherman : Profession() {
    override val type = ProfessionType.FISHERMAN
    override val structureInterest = StructureType.POND
    override val desiredItems = setOf(ItemPredicate.EDIBLE, ItemPredicate.FISHING_ROD)
    override val INVENTORY_HANDLER: (AbstractVillagerEntity) -> Unit = { e ->
        super.INVENTORY_HANDLER(e)
        e.item[Action.Type.FISH] = -3
    }
}

// TODO: model trading mini-game [renewable]
object Merchant : Profession() {
    override val type = ProfessionType.MERCHANT
    override val structureInterest = StructureType.MARKET
    override val desiredItems = setOf(ItemPredicate.EDIBLE, ItemPredicate.PICKAXE, { it.defaultStack.isOf(Items.EMERALD) })
    override val CLICK_HANDLER: (AbstractVillagerEntity, PlayerEntity) -> Boolean = { entity, player ->
        player.openHandledScreen(entity)
        true
    }
    override val INVENTORY_HANDLER: (AbstractVillagerEntity) -> Unit = { e ->
        super.INVENTORY_HANDLER(e)
        e.item[Action.Type.MINE] = -3
    }
}

object Lumberjack : Profession() {
    override val type = ProfessionType.LUMBERJACK
    override val structureInterest = StructureType.TREE
    override val desiredItems = setOf(ItemPredicate.EDIBLE, ItemPredicate.AXE)

    override val INVENTORY_HANDLER: (AbstractVillagerEntity) -> Unit = { e ->
        super.INVENTORY_HANDLER(e)
        e.item[Action.Type.CHOP] = -3
    }
}

object Recruit : Profession() {
    override val type = ProfessionType.RECRUIT
    override val structureInterest = StructureType.BARRACKS
    override val desiredItems = setOf(ItemPredicate.EDIBLE, ItemPredicate.SWORD, ItemPredicate.ARMOR)

    override val TARGET_HANDLER: (AbstractVillagerEntity) -> Unit = { entity ->
        entity.target?.let { target ->
            val distance = entity.squaredDistanceTo(target)
            if (entity.isFighting()) {
                val canDamage = entity.item[Action.Type.ATTACK]?.takeIf { it != -1 }?.let { entity.pushErrand(Action.Type.ATTACK) } != null
                if (!canDamage || entity.random.nextInt(10) == 0) {
                    Finder.findFleeBlock(entity, target)?.let { errand ->
                        entity.pushErrand(errand.cid, errand.pos)
                    }
                }
            }
        }
    }
}

// TODO: test this more
object Guard : Profession() {
    override val type = ProfessionType.GUARD
    override val structureInterest = StructureType.BARRACKS
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

    override val INVENTORY_HANDLER: (AbstractVillagerEntity) -> Unit = { e ->
        super.INVENTORY_HANDLER(e)
        e.item[Action.Type.CHARGE] = -3
        e.item[Action.Type.AIM] = -3
        e.item[Action.Type.ATTACK] = -3
        e.item[Action.Type.DEFEND] = -3
    }

    override val TARGET_HANDLER: (AbstractVillagerEntity) -> Unit = { entity ->
        entity.target?.let { target ->
            if (entity.isFighting()) {
                val manager = entity.errandManager
                if (!manager.hasErrands(ErrandType.FRAY)) {
                    val distance = entity.squaredDistanceTo(target)
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
                                                !entity.errandManager.containsErrand(Action.Type.STRAFE)
                                            ) {
                                                entity.pushErrand(Action.Type.STRAFE)
                                            }
                                            entity.pushErrand(Action.Type.ATTACK)
                                        }
                                } else {
                                    entity.item[Action.Type.ATTACK]?.takeIf { it != -1 }?.let {
                                        if (distance < 6 &&
                                            !entity.errandManager.containsErrand(Action.Type.STRAFE)
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
}

// TODO: trigger move to the pen and find a good distribution for shearing sheep
object Shepherd : Profession() {
    override val type = ProfessionType.SHEPHERD
    override val structureInterest = StructureType.PEN
    override val desiredItems = setOf(ItemPredicate.EDIBLE, ItemPredicate.SHEARS, ItemPredicate.WOOLS)
}

object Miner : Profession() {
    override val type = ProfessionType.MINER
    override val structureInterest = StructureType.TUNNEL
    override val desiredItems = setOf(ItemPredicate.EDIBLE, ItemPredicate.PICKAXE, ItemPredicate.SHOVEL)
}
