package com.settlement.mod.action

import com.settlement.mod.LOGGER
import com.settlement.mod.entity.mob.AbstractVillagerEntity
import com.settlement.mod.entity.projectile.SimpleFishingBobberEntity
import com.settlement.mod.item.ItemPredicate
import com.settlement.mod.profession.Fisherman
import com.settlement.mod.util.BlockIterator
import com.settlement.mod.util.CombatUtils
import com.settlement.mod.util.Finder
import net.minecraft.block.BedBlock
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.CropBlock
import net.minecraft.block.DoorBlock
import net.minecraft.block.FarmlandBlock
import net.minecraft.block.FenceGateBlock
import net.minecraft.block.LadderBlock
import net.minecraft.block.PlantBlock
import net.minecraft.block.SlabBlock
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.passive.SheepEntity
import net.minecraft.item.BoneMealItem
import net.minecraft.item.CrossbowItem
import net.minecraft.item.Items
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.world.event.GameEvent
import net.minecraft.nbt.NbtCompound

data class Errand(
    val cid: Action.Type,
    val pos: BlockPos? = null,
    val priority: Byte = 0,
) : Comparable<Errand> {
    override fun compareTo(other: Errand): Int {
        return other.priority.compareTo(this.priority)
    }

    fun toNbt(): NbtCompound =
        NbtCompound().apply {
            putInt("ErrandType", cid.ordinal)
            pos?.let { 
                putInt("ErrandPosX", it.x)
                putInt("ErrandPosY", it.y)
                putInt("ErrandPosZ", it.z)
            }
            putByte("ErrandPriority", priority)
        }

    companion object {
        fun fromNbt(nbt: NbtCompound): Errand {
            val cid = Action.Type.values()[nbt.getInt("ErrandType")]
            val pos = if (nbt.contains("ErrandPosX")) {
                BlockPos(nbt.getInt("ErrandPosX"), nbt.getInt("ErrandPosY"), nbt.getInt("ErrandPosZ")) 
            } else {
                null
            }
            val priority = nbt.getByte("ErrandPriority")
            return Errand(cid, pos, priority)
        }
    }
}

typealias PositionInput = (entity: AbstractVillagerEntity, pos: BlockPos?) -> Byte
typealias ParallelInput = (entity: AbstractVillagerEntity) -> Byte

// TODO: add cancelable field (pops errand if interupted by another)
sealed class Position : Action() {
    abstract val scan: PositionInput // scan if errand can be tested
    open val test: PositionInput = { _, _ -> 1 } // verify if errand can be executed
    open val exec: PositionInput = { _, _ -> 1 } // execute lambda representing errand
    open val eval: PositionInput = { _, _ -> 1 } // scan if errand can end
    open val redo: PositionInput = { _, _ -> 1 } // check if action must be redone
    open val stop: PositionInput = { _, _ -> 1 } // handle action cancelation 

    open val radiusToAct: Float = 4.0f
    open val radiusToSee: Float = 4.0f

    open val speedModifier: Double = 1.0
    open val energyCost: Double = 0.5

    val shouldMove: (Double) -> Boolean = { d -> d > radiusToAct }
    val shouldLook: (Double) -> Boolean = { d -> d < radiusToSee }
}

sealed class Parallel : Action() {
    abstract val scan: ParallelInput
    open val test: ParallelInput = { _ -> 1 }
    open val exec: ParallelInput = { _ -> 1 }
    open val eval: ParallelInput = { _ -> 1 }
    open val redo: ParallelInput = { _ -> 1 }
}

sealed class Action {
    open val ticksToTest: Int = 5
    open val ticksToExec: Int = 5

    val shouldTest: (Int) -> Boolean = { c -> c >= ticksToTest }
    val shouldExec: (Int) -> Boolean = { c -> c >= ticksToExec }

    object Pick : Position() {
        override val scan: PositionInput = { _, _ -> 3 }
        override val radiusToAct: Float = 2.5f
        override val radiusToSee: Float = 5.0f
        override val ticksToTest: Int = 5
        override val ticksToExec: Int = 2
    }

    object Reach : Position() {
        override val scan: PositionInput = { _, _ -> 3 }
        override val radiusToAct: Float = 32.0f
        override val radiusToSee: Float = -1.0f
        override val ticksToTest: Int = 1
        override val ticksToExec: Int = 1
    }

    object Sleep : Position() {
        override val scan: PositionInput = { e, _ -> (e.canSleep() || e.isSleeping()).toByte(5) }
        override val test: PositionInput = { e, p ->
            var ret: Byte = 0
            if (!e.isSleeping()) {
                val state = e.world.getBlockState(p!!)
                if (state.getBlock() is BedBlock) {
                    if (!state.get(BedBlock.OCCUPIED)) {
                        ret = 1
                    }
                    // add else check to drop housing
                }
                // add else check to trigger if enclosed space still a valid housing
                ret
            } else {
                1
            }
        }
        override val exec: PositionInput = { e, p ->
            e.sleep(p!!)
            1
        }
        override val eval: PositionInput = { e, _ ->
            var ret: Byte = ((!e.canSleep()) || (e.target != null)).toByte()
            if (ret.toInt() != 0) {
              e.getUp() 
            }
            ret
        }
    }

    object Till : Position() {
        override val scan: PositionInput = { e, _ ->
            e.inventory.hasItem(ItemPredicate.HOE).toByte(5)
        }
        override val test: PositionInput = { e, p ->
            (
                e.world.getBlockState(p!!.down()).block in BlockIterator.TILLABLE_BLOCKS &&
                    e.tryEquip(ItemPredicate.HOE, EquipmentSlot.MAINHAND)
            ).toByte()
        }
        override val exec: PositionInput = { e, p ->
            val stack = e.getStackInHand(Hand.MAIN_HAND)
            e.world.playSound(e, p, SoundEvents.ITEM_HOE_TILL, SoundCategory.BLOCKS, 1.0f, 1.0f)
            e.world.setBlockState(p, Blocks.FARMLAND.defaultState, Block.NOTIFY_LISTENERS)
            e.swingHand(Hand.MAIN_HAND)
            stack.damage(1, e, { j -> j.sendToolBreakStatus(Hand.MAIN_HAND) })
            1
        }
    }

    object Plant : Position() {
        override val scan: PositionInput = { e, _ -> e.inventory.hasItem(ItemPredicate.PLANTABLE).toByte(5) }
        override val test: PositionInput = { e, p ->
            (
                e.world.getBlockState(p!!.up()).isAir &&
                    e.world.getBlockState(p).block is FarmlandBlock &&
                    e.tryEquip(ItemPredicate.PLANTABLE, EquipmentSlot.MAINHAND)
            ).toByte()
        }

        override val exec: PositionInput = { e, p ->
            val stack = e.getStackInHand(Hand.MAIN_HAND)
            when (stack.item) {
                Items.WHEAT_SEEDS -> Blocks.WHEAT.defaultState
                Items.BEETROOT_SEEDS -> Blocks.BEETROOTS.defaultState
                Items.CARROT -> Blocks.CARROTS.defaultState
                Items.POTATO -> Blocks.POTATOES.defaultState
                else -> null
            }?.let { block ->
                val c = p!!.up().toCenterPos()
                e.world.setBlockState(p.up(), block, Block.NOTIFY_LISTENERS)
                e.world.playSound(null, c.x, c.y, c.z, SoundEvents.ITEM_CROP_PLANT, SoundCategory.BLOCKS, 1.0f, 1.0f)
                e.swingHand(Hand.MAIN_HAND)
                stack.decrement(1)
            }
            1
        }
    }

    object Powder : Position() {
        override val scan: PositionInput = { e, _ ->
            e.inventory.hasItem({ item -> item == Items.BONE_MEAL }).toByte(5)
        }

        override val test: PositionInput = { e, p ->
            p?.let {
                val state = e.world.getBlockState(it.up())
                (
                    state.block is CropBlock &&
                        !(state.block as CropBlock).isMature(state) &&
                        e.world.getBlockState(it).block is FarmlandBlock &&
                        e.tryEquip({ item -> item == Items.BONE_MEAL }, EquipmentSlot.MAINHAND)
                ).toByte()
            } ?: 0
        }

        override val exec: PositionInput = { e, p ->
            val stack = e.getStackInHand(Hand.MAIN_HAND)
            BoneMealItem.useOnFertilizable(e.getStackInHand(Hand.MAIN_HAND), e.world, p!!.up())
            e.swingHand(Hand.MAIN_HAND)
            stack.decrement(1)
            1
        }
    }

    object Harvest : Position() {
        override val scan: PositionInput = { _, _ -> 5 }
        override val test: PositionInput = { e, p ->
            (e.world.getBlockState(p!!.up()).block is CropBlock).toByte()
        }
        override val exec: PositionInput = { entity, pos ->
            entity.world.breakBlock(pos!!.up(), true)
            entity.swingHand(Hand.MAIN_HAND)
            1
        }
    }

    object Break : Position() {
        override val scan: PositionInput = { _, _ -> 2 }
        override val test: PositionInput = { e, p ->
            val block = e.world.getBlockState(p).block
            (block is CropBlock || block is PlantBlock).toByte()
        }
        override val exec: PositionInput = { e, p ->
            e.world.breakBlock(p!!, true)
            e.swingHand(Hand.MAIN_HAND)
            1
        }
    }

    object Dig : Position() {
        override val scan: PositionInput = { e, _ -> e.inventory.hasItem(ItemPredicate.SHOVEL).toByte(5) }
        override val test: PositionInput = { e, p ->
            (e.world.getBlockState(p).block == Blocks.DIRT).toByte()
        }
        override val exec: PositionInput = { e, p ->
            if (e.world.getBlockState(p).block == Blocks.DIRT) {
                e.world.breakBlock(p, true)
                e.swingHand(Hand.MAIN_HAND)
            }
            0
        }
    }

    object Mine : Position() {
        override val scan: PositionInput = { e, _ -> e.inventory.hasItem(ItemPredicate.PICKAXE).toByte(5) }
        override val test: PositionInput = { e, p ->
            (e.world.getBlockState(p).block == Blocks.STONE).toByte()
        }
        override val exec: PositionInput = { e, p ->
            e.world.breakBlock(p, true)
            e.swingHand(Hand.MAIN_HAND)
            0
        }
    }

    object Fish : Position() {
        override val scan: PositionInput = { e, _ -> e.inventory.hasItem(ItemPredicate.FISHING_ROD).toByte(4) }

        override val test: PositionInput = { e, p ->
            (
                (e.profession as Fisherman).getFishHook() == null &&
                e.tryEquip(ItemPredicate.FISHING_ROD, EquipmentSlot.MAINHAND) &&
                BlockIterator.BOTTOM(p!!).all { e.world.getBlockState(p).block == Blocks.WATER }
            ).toByte()
        }

        override val exec: PositionInput = { e, _ ->
            // TODO: calculate velocity and angle to throw fishing bobber as pos
            e.world.spawnEntity(SimpleFishingBobberEntity(e, e.world, 0, 0))
            e.swingHand(Hand.MAIN_HAND)
            1
        }

        override val eval: PositionInput = { e, _ ->
            // TODO: eval check to make fisherman look at block target
            (e.profession as Fisherman).getFishHook()?.let {
                0 
            } ?: run {
                val stack = e.getStackInHand(Hand.MAIN_HAND)
                stack.damage(1, e, { j -> j.sendToolBreakStatus(Hand.MAIN_HAND) })
                1
            }
        }

        override val radiusToAct: Float = 125.0f
        override val radiusToSee: Float = 75.0f
        override val ticksToTest: Int = 10
        override val ticksToExec: Int = 20
    }

    object Sit : Position() {
        override val scan: PositionInput = { _, _ -> 2 }

        override val test: PositionInput = { e, p ->
            var ret: Byte = 0
            if (p != null) {
                (e.world.getBlockState(p).block is SlabBlock).toByte()
            } else {
                ret = 1
            }
            ret
        }

        override val exec: PositionInput = { e, p ->
            e.sit(p)
            1
        }
        override val eval: PositionInput = { _, _ -> 1 }
    }

    object Flee : Position() {
        override val scan: PositionInput = { _, _ -> 9 }
        override val redo: PositionInput = { e, _ ->
            e.target?.let { t ->
                Finder.findFleeBlock(e, t)
            }
            1
        }
        override val radiusToAct: Float = 5.0f
        override val radiusToSee: Float = -1.0f
        override val ticksToTest: Int = 4
        override val ticksToExec: Int = 1
        override val speedModifier: Double = 1.25
    }

    object Eat : Parallel() {
        override val scan: ParallelInput = { e ->
            // TODO: add checks that increases priority like health and hunger bar
            val priority = (10 - (e.hunger / 2)).toInt()
            LOGGER.info("Priority: {}", priority)
            (e.hunger < 60 && e.inventory.hasItem(ItemPredicate.EDIBLE)).toByte(priority)
        }
        override val test: ParallelInput = { e ->
            (e.tryEquip(ItemPredicate.EDIBLE, EquipmentSlot.OFFHAND)).toByte()
        }
        override val exec: ParallelInput = { e ->
            e.setCurrentHand(Hand.OFF_HAND)
            1
        }
        override val eval: ParallelInput = { e -> (!e.isEating()).toByte() }
    }

    object Store : Position() {
        override val scan: PositionInput = { _, _ -> -1 }
        override val exec: PositionInput = { e, _ ->
            e.target = null
            1
        }
        override val speedModifier: Double = 1.2
        override val radiusToSee: Float = -1.0f
    }

    object Charge : Position() {
        override val scan: PositionInput = { e, _ ->
            (
                e.tryEquip(ItemPredicate.CROSSBOW, EquipmentSlot.MAINHAND) &&
                    e.target != null
            ).toByte(9)
        }
        override val test: PositionInput = { e, _ ->
            val item = e.getStackInHand(Hand.MAIN_HAND)
            if (!CrossbowItem.isCharged(item)) {
                e.setCurrentHand(Hand.MAIN_HAND)
            }
            1
        }

        override val eval: PositionInput = { e, _ ->
            var ret: Byte = 0
            val ready = e.getItemUseTime() >= CrossbowItem.getPullTime(e.getActiveItem())
            val item = e.getStackInHand(Hand.MAIN_HAND)
            e.target?.let { t ->
                if (t.isAlive) {
                    if (e.isUsingItem) {
                        if (ready) {
                            e.stopUsingItem()
                            CrossbowItem.setCharged(item, true)
                        }
                    } else if (e.random.nextInt(5) == 0) {
                        CombatUtils.shoot(e, t, item, Hand.MAIN_HAND, true)
                        ret = 1
                    }
                } else {
                    if (e.isUsingItem) {
                        if (ready) {
                            e.stopUsingItem()
                            CrossbowItem.setCharged(item, true)
                            ret = 1
                        }
                    } else {
                        ret = 1
                    }
                }
            } ?: run {
                ret = 1
            }
            ret
        }
        override val ticksToExec: Int = 10
        override val ticksToTest: Int = 2
        override val radiusToAct: Float = 175.0f
        override val radiusToSee: Float = 175.0f
    }

    object Attack : Position() {
        override val scan: PositionInput = { e, _ ->
            (
                e.tryEquip(ItemPredicate.SWORD, EquipmentSlot.MAINHAND) &&
                    e.target != null
            ).toByte(9)
        }
        override val eval: PositionInput = { e, _ ->
            e.target?.let { t ->
                if (t.isAlive) {
                    e.swingHand(Hand.MAIN_HAND)
                    // TODO: add check for hitbox collision
                    if (e.squaredDistanceTo(t) <= 4.0f) {
                        e.tryAttack(t)
                    }
                }
            }
            1
        }
        override val speedModifier: Double = 1.25
        override val radiusToAct: Float = 4.0f
        override val radiusToSee: Float = 100.0f
    }

    // implement this
    object Follow : Position() {
        override val scan: PositionInput = { e, _ -> 4 }
        override val eval: PositionInput = { e, _ -> 1 }
        override val radiusToAct: Float = 3.5f
        override val radiusToSee: Float = 12.0f
    }

    // require one wheat
    object Shear : Position() {
        override val scan: PositionInput = { e, _ ->
            (
                e.tryEquip(ItemPredicate.SHEARS, EquipmentSlot.OFFHAND) &&
                    e.target != null
            ).toByte(6)
        }
        override val exec: PositionInput = { e, _ ->
            e.target?.let { target ->
                if (target is SheepEntity) {
                    val stack = e.getStackInHand(Hand.MAIN_HAND)
                    target.sheared(SoundCategory.PLAYERS)
                    target.emitGameEvent(GameEvent.SHEAR, e)
                    stack.damage(1, e, { j -> j.sendEquipmentBreakStatus(EquipmentSlot.MAINHAND) })
                }
            }
            1
        }
        override val radiusToAct: Float = 3.5f
        override val radiusToSee: Float = 15.0f
        override val ticksToTest: Int = 1
        override val ticksToExec: Int = 15
    }

    object Seek : Position() {
        override val scan: PositionInput = { e, _ -> (e.target != null).toByte(3) }
        override val eval: PositionInput = { e, _ ->
            var ret: Byte = 0
            if (e.random.nextInt(40) == 0) {
                ret = 1
            }
            ret
        }
        override val radiusToAct: Float = 175.0f
        override val radiusToSee: Float = 175.0f
        override val ticksToTest: Int = 5
        override val ticksToExec: Int = 25
    }

    object  Talk : Position() {
        override val scan: PositionInput = { e, _ ->
            (e.target != null && e.target is AbstractVillagerEntity && (e.target as AbstractVillagerEntity).target == e).toByte(6)
        }
        override val eval: PositionInput = { e, _ ->
            var ret: Byte = 0
            if (e.random.nextInt(100) == 0) {
                ret = 1
            }
            ret
        }
        override val radiusToAct: Float = 5.0f
        override val radiusToSee: Float = 20.0f
        override val ticksToTest: Int = 1
        override val ticksToExec: Int = 40
    }

    object Wander : Position() {
        override val scan: PositionInput = { e, _ -> (e.target != null).toByte(3) }
        override val radiusToAct: Float = 3.5f
        override val radiusToSee: Float = -1.0f
        override val ticksToTest: Int = 1
        override val ticksToExec: Int = 1
    }

    object Look : Position() {
        override val scan: PositionInput = { e, _ -> (e.target != null).toByte(3) }
        override val eval: PositionInput = { e, _ ->
            var ret: Byte = 0
            if (e.random.nextInt(50) == 0 && e.target != null) {
                e.target = null
                e.pushErrand(Action.Type.TALK)
                ret = 1
            }
            ret
        }
        override val radiusToAct: Float = 75.0f
        override val radiusToSee: Float = 75.0f
        override val ticksToTest: Int = 1
        override val ticksToExec: Int = 25
    }

    object Agree : Parallel() {
        override val scan: ParallelInput = { e ->
            (e.target != null).toByte(3)
        }
        override val test: ParallelInput = { e ->
            e.setState(ActionState.AGREE)
            e.playSound(SoundEvents.ENTITY_VILLAGER_YES, 1.0f, e.getSoundPitch())
            1
        }

        override val eval: ParallelInput = { e ->
            e.setState(ActionState.NONE)
            1
        }
        override val ticksToTest: Int = 1
        override val ticksToExec: Int = 20
    }

    object Disagree : Parallel() {
        override val scan: ParallelInput = { e ->
            (e.target != null).toByte(3)
        }
        override val test: ParallelInput = { e ->
            e.setState(ActionState.DISAGREE)
            e.playSound(SoundEvents.ENTITY_VILLAGER_NO, 1.0f, e.getSoundPitch())
            1
        }

        override val eval: ParallelInput = { e ->
            e.setState(ActionState.NONE)
            1
        }
        override val ticksToTest: Int = 5
        override val ticksToExec: Int = 30
    }

    object Open : Position() {
        override val scan: PositionInput = { _, _ -> 10 }
        override val exec: PositionInput = { e, p ->
            // TODO: use a mixin method instead
            val state = e.world.getBlockState(p)
            val block = state.getBlock()
            when (block) {
                is DoorBlock -> {
                    if (!block.isOpen(state)) {
                        block.setOpen(e, e.getWorld(), state, p, true)
                        e.swingHand(Hand.MAIN_HAND)
                    }
                }
                is FenceGateBlock -> {
                    var ret: BlockState
                    if (state.get(FenceGateBlock.OPEN) == true) {
                        ret = state.with(FenceGateBlock.OPEN, false)
                        e.world.setBlockState(p, state, Block.NOTIFY_LISTENERS or Block.REDRAW_ON_MAIN_THREAD)
                    } else {
                        val direction = e.horizontalFacing
                        if (state.get(FenceGateBlock.FACING) == direction.opposite) {
                            ret = state.with(FenceGateBlock.FACING, direction)
                        }
                        ret = state.with(FenceGateBlock.OPEN, true)
                        e.world.setBlockState(p, state, Block.NOTIFY_LISTENERS or Block.REDRAW_ON_MAIN_THREAD)
                    }
                    val isOpen = ret.get(FenceGateBlock.OPEN)
                    e.world.playSound(
                        e,
                        p,
                        if (isOpen) SoundEvents.BLOCK_FENCE_GATE_OPEN else SoundEvents.BLOCK_FENCE_GATE_CLOSE,
                        SoundCategory.BLOCKS,
                        1.0f,
                        e.world.random.nextFloat() * 0.1f + 0.9f,
                    )
                    e.world.emitGameEvent(e, if (isOpen) GameEvent.BLOCK_OPEN else GameEvent.BLOCK_CLOSE, p)
                }
                else -> { }
            }
            1
        }
        override val eval: PositionInput = { e, p ->
            val state = e.world.getBlockState(p!!)
            when (state.getBlock()) {
                is DoorBlock -> {
                    val d = state.get(DoorBlock.FACING)
                    val r = e.blockPos.getSquaredDistance(p.offset(d).toCenterPos())
                    val l = e.blockPos.getSquaredDistance(p.offset(d.getOpposite()).toCenterPos())
                    e.pushErrand(
                        Action.Type.CLOSE,
                        if (r > l) {
                            p.offset(d)
                        } else {
                            p.offset(d.getOpposite())
                        },
                    )
                }
                else -> { }
            }
            1
        }
        override val radiusToAct: Float = 3.5f
        override val ticksToExec: Int = 5
        override val ticksToTest: Int = 5
    }

    object Close : Position() {
        override val scan: PositionInput = { _, _ -> 8 }

        override val exec: PositionInput = { e, p ->
            BlockIterator.NEIGHBOURS(p!!).find { e.world.getBlockState(it).getBlock() is DoorBlock }?.let { t ->
                val state = e.world.getBlockState(t)
                if (state.getBlock() is DoorBlock) {
                    (state.getBlock() as DoorBlock).setOpen(e, e.getWorld(), state, t, false)
                    e.swingHand(Hand.MAIN_HAND)
                }
            }
            1
        }

        override val radiusToAct: Float = 2.5f
        override val ticksToExec: Int = 10
        override val ticksToTest: Int = 5
    }

    object Defend : Position() {
        override val scan: PositionInput = { e, _ ->
            (
                e.tryEquip(ItemPredicate.SHIELD, EquipmentSlot.OFFHAND) &&
                    e.target != null
            ).toByte(9)
        }
        override val test: PositionInput = { e, _ ->
            e.setCurrentHand(Hand.OFF_HAND)
            1
        }
        override val eval: PositionInput = { e, _ ->
            var ret: Byte = 0
            e.target?.let { t ->
                if (e.random.nextInt(20) == 0) {
                    e.stopUsingItem()
                    ret = 1
                }
                if (!t.isAlive) {
                    e.stopUsingItem()
                    ret = 1
                }
            } ?: run {
                e.stopUsingItem()
                ret = 1
            }
            ret
        }
    }

    object Aim : Position() {
        override val scan: PositionInput = { e, _ ->
            (
                e.inventory.hasItem(ItemPredicate.ARROW) &&
                    e.tryEquip(ItemPredicate.BOW, EquipmentSlot.MAINHAND) &&
                    e.target != null
            ).toByte(9)
        }

        override val test: PositionInput = { e, _ ->
            e.setCurrentHand(Hand.MAIN_HAND)
            1
        }

        override val eval: PositionInput = { e, _ ->
            var ret: Byte = 0
            val ready = e.getItemUseTime() >= 25
            val item = e.getStackInHand(Hand.MAIN_HAND)
            e.target?.let { t ->
                if (t.isAlive) {
                    if (ready && e.random.nextInt(5) == 0) {
                        CombatUtils.shoot(e, t, item, Hand.MAIN_HAND, false)
                        e.stopUsingItem()
                        ret = 1
                    }
                }
            } ?: run {
                e.stopUsingItem()
                ret = 1
            }
            ret
        }
        override val ticksToExec: Int = 15
        override val ticksToTest: Int = 2
        override val radiusToAct: Float = 150.0f
        override val radiusToSee: Float = 150.0f
    }

    enum class Type {
        PICK,
        REACH,
        SLEEP,
        TILL,
        PLANT,
        POWDER,
        HARVEST,
        BREAK,
        DIG,
        MINE,
        FISH,
        SIT,
        FLEE,
        MOVE,
        EAT,
        STORE,
        CHARGE,
        ATTACK,
        AIM,
        LOOK,
        OPEN,
        CLOSE,
        DEFEND,
        YIELD, // placeholder action
        DISAGREE,
        AGREE,
        SHEAR,
        SEEK,
        TALK,
        WANDER
    }

    companion object {
        private val map =
            mapOf(
                Type.REACH to Reach,
                Type.PICK to Pick,
                Type.SLEEP to Sleep,
                Type.TILL to Till,
                Type.PLANT to Plant,
                Type.POWDER to Powder,
                Type.HARVEST to Harvest,
                Type.BREAK to Break,
                Type.DIG to Dig,
                Type.MINE to Mine,
                Type.FISH to Fish,
                Type.SIT to Sit,
                Type.FLEE to Flee,
                Type.EAT to Eat,
                Type.STORE to Store,
                Type.CHARGE to Charge,
                Type.ATTACK to Attack,
                Type.LOOK to Look,
                Type.OPEN to Open,
                Type.CLOSE to Close,
                Type.DEFEND to Defend,
                Type.AIM to Aim,
                Type.YIELD to Pick,
                Type.DISAGREE to Disagree,
                Type.AGREE to Agree,
                Type.SHEAR to Shear,
                Type.SEEK to Seek,
                Type.TALK to Talk,
                Type.WANDER to Wander
            )

        fun get(type: Type): Action = map[type] ?: Pick

        // TODO: puts these on a general utils object
        fun Boolean.toByte(mult: Int): Byte = if (this) (1 * mult).toByte() else 0

        fun Boolean.toByte(): Byte = if (this) 1 else 0
    }
}
