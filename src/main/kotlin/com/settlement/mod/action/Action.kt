package com.settlement.mod.action

import com.settlement.mod.entity.mob.AbstractVillagerEntity
import com.settlement.mod.entity.mob.ErrandType
import com.settlement.mod.entity.projectile.SimpleFishingBobberEntity
import com.settlement.mod.item.ItemPredicate
import com.settlement.mod.util.BlockIterator
import com.settlement.mod.util.CombatUtils
import net.minecraft.block.BedBlock
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.block.CropBlock
import net.minecraft.block.DoorBlock
import net.minecraft.block.FarmlandBlock
import net.minecraft.block.FenceGateBlock
import net.minecraft.block.PlantBlock
import net.minecraft.block.SlabBlock
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.passive.SheepEntity
import net.minecraft.item.BoneMealItem
import net.minecraft.item.CrossbowItem
import net.minecraft.item.Items
import net.minecraft.nbt.NbtCompound
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.world.event.GameEvent

data class Errand(
    val cid: Action.Type,
    val pos: BlockPos? = null,
    val priority: Byte = 0,
) : Comparable<Errand> {
    override fun compareTo(other: Errand): Int = other.priority.compareTo(this.priority)

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
            val pos =
                if (nbt.contains("ErrandPosX")) {
                    BlockPos(nbt.getInt("ErrandPosX"), nbt.getInt("ErrandPosY"), nbt.getInt("ErrandPosZ"))
                } else {
                    null
                }
            val priority = nbt.getByte("ErrandPriority")
            return Errand(cid, pos, priority)
        }
    }
}

typealias ActionInput = (entity: AbstractVillagerEntity, pos: BlockPos?) -> Byte

// TODO:
// - fix item lookup [ATTACK and DEFEND] are already fixed
sealed class Action {
    // errand can be of two types [POSITION, PARALLEL]
    // position errands need to be executed in a block
    // parallel errands are triggered without confliting with position
    // for example settler can look at another settler, while eating food EAT[parallel] - SIT[position]
    abstract val type: ErrandType

    open val ticksToTest: Int = 5
    open val ticksToExec: Int = 5

    abstract val scan: ActionInput // scan if errand can be tested
    open val test: ActionInput = { _, _ -> 1 } // verify if errand can be executed
    open val exec: ActionInput = { _, _ -> 1 } // execute lambda representing errand
    open val eval: ActionInput = { _, _ -> 1 } // scan if errand can end
    open val redo: ActionInput = { _, _ -> 1 } // check if action must be redone
    open val stop: ActionInput = { _, _ -> 1 } // handle action cancelation

    open val radiusToAct: Float = 4.0f
    open val radiusToSee: Float = 4.0f

    open val speedModifier: Double = 1.0
    open val restCost: Double = 2.0
    open val pathReach: Int = 0

    open val isUnique: Boolean = true

    val shouldMove: (Double) -> Boolean = { d -> d > radiusToAct }
    val shouldLook: (Double) -> Boolean = { d -> d < radiusToSee }

    val shouldTest: (Int) -> Boolean = { c -> c >= ticksToTest }
    val shouldExec: (Int) -> Boolean = { c -> c >= ticksToExec }

    object Break : Action() {
        override val type = ErrandType.POSITION
        override val scan: ActionInput = { _, _ -> 2 }
        override val test: ActionInput = { e, p ->
            val block = e.world.getBlockState(p).block
            (block is CropBlock || block is PlantBlock).toByte()
        }
        override val exec: ActionInput = { e, p ->
            e.world.breakBlock(p!!, true)
            e.swingHand(Hand.MAIN_HAND)
            1
        }
    }

    object Dig : Action() {
        override val type = ErrandType.POSITION
        override val scan: ActionInput = { e, _ -> e.inventory.hasItem(ItemPredicate.SHOVEL).toByte(5) }
        override val test: ActionInput = { e, p ->
            (e.world.getBlockState(p).block == Blocks.DIRT).toByte()
        }
        override val exec: ActionInput = { e, p ->
            if (e.world.getBlockState(p).block == Blocks.DIRT) {
                e.world.breakBlock(p, true)
                e.swingHand(Hand.MAIN_HAND)
            }
            0
        }
    }

    object Mine : Action() {
        override val type = ErrandType.POSITION

        override val scan: ActionInput = { e, _ -> e.inventory.hasItem(ItemPredicate.PICKAXE).toByte(5) }
        override val test: ActionInput = { e, p ->
            (e.world.getBlockState(p).block == Blocks.STONE).toByte()
        }
        override val exec: ActionInput = { e, p ->
            e.world.breakBlock(p, true)
            e.swingHand(Hand.MAIN_HAND)
            0
        }
    }

    object Till : Action() {
        override val type = ErrandType.POSITION
        override val scan: ActionInput = { e, _ ->
            e.inventory.hasItem(ItemPredicate.HOE).toByte(5)
        }
        override val test: ActionInput = { e, p ->
            var ret: Byte =
                (
                    e.world.getBlockState(p!!.down()).block in BlockIterator.TILLABLE_BLOCKS &&
                        e.tryEquip(ItemPredicate.HOE, EquipmentSlot.MAINHAND)
                ).toByte()
            if (ret == 0.toByte()) ret = 2
            ret
        }
        override val exec: ActionInput = { e, p ->
            val stack = e.getStackInHand(Hand.MAIN_HAND)
            e.world.playSound(e, p, SoundEvents.ITEM_HOE_TILL, SoundCategory.BLOCKS, 1.0f, 1.0f)
            e.world.setBlockState(p, Blocks.FARMLAND.defaultState, Block.NOTIFY_LISTENERS)
            e.swingHand(Hand.MAIN_HAND)
            stack.damage(1, e, { j -> j.sendToolBreakStatus(Hand.MAIN_HAND) })
            1
        }
    }

    object Plant : Action() {
        override val type = ErrandType.POSITION
        override val scan: ActionInput = { e, _ -> e.inventory.hasItem(ItemPredicate.PLANTABLE).toByte(5) }
        override val test: ActionInput = { e, p ->
            var ret: Byte =
                (
                    e.world.getBlockState(p!!.up()).isAir &&
                        e.world.getBlockState(p).block is FarmlandBlock &&
                        e.tryEquip(ItemPredicate.PLANTABLE, EquipmentSlot.MAINHAND)
                ).toByte()
            if (ret == 0.toByte()) ret = 2
            ret
        }

        override val exec: ActionInput = { e, p ->
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

    object Powder : Action() {
        override val type = ErrandType.POSITION
        override val scan: ActionInput = { e, _ ->
            e.inventory.hasItem({ item -> item == Items.BONE_MEAL }).toByte(5)
        }

        override val test: ActionInput = { e, p ->
            var ret: Byte =
                p?.let {
                    val state = e.world.getBlockState(it.up())
                    (
                        state.block is CropBlock &&
                            !(state.block as CropBlock).isMature(state) &&
                            e.world.getBlockState(it).block is FarmlandBlock &&
                            e.tryEquip({ item -> item == Items.BONE_MEAL }, EquipmentSlot.MAINHAND)
                    ).toByte()
                } ?: 2.toByte()
            if (ret == 0.toByte()) ret = 2
            ret
        }

        override val exec: ActionInput = { e, p ->
            val stack = e.getStackInHand(Hand.MAIN_HAND)
            BoneMealItem.useOnFertilizable(e.getStackInHand(Hand.MAIN_HAND), e.world, p!!.up())
            e.swingHand(Hand.MAIN_HAND)
            stack.decrement(1)
            1
        }
    }

    object Harvest : Action() {
        override val type = ErrandType.POSITION
        override val scan: ActionInput = { _, _ -> 5 }
        override val test: ActionInput = { e, p ->
            var ret: Byte = (e.world.getBlockState(p!!.up()).block is CropBlock).toByte()
            if (ret == 0.toByte()) ret = 2
            ret
        }
        override val exec: ActionInput = { entity, pos ->
            entity.world.breakBlock(pos!!.up(), true)
            entity.swingHand(Hand.MAIN_HAND)
            1
        }
    }

    object Fish : Action() {
        override val type = ErrandType.POSITION
        override val scan: ActionInput = { e, _ -> e.inventory.hasItem(ItemPredicate.FISHING_ROD).toByte(7) }

        override val test: ActionInput = { e, p ->
            var ret: Byte =
                (
                    e.tryEquip(ItemPredicate.FISHING_ROD, EquipmentSlot.MAINHAND) &&
                        BlockIterator.BOTTOM(p!!).all { e.world.getBlockState(p).block == Blocks.WATER }
                ).toByte()

            if (ret == 0.toByte()) ret = 2
            ret
        }

        // TODO: calculate velocity and angle to throw fishing bobber as pos
        override val exec: ActionInput = { e, _ ->
            e.setWorking(true)
            e.swingHand(Hand.MAIN_HAND)
            e.world.spawnEntity(SimpleFishingBobberEntity(e, e.world, 0, 0))
            1
        }

        // TODO: eval check to make fisherman look at block target
        override val eval: ActionInput = { e, _ ->
            var ret: Byte = 0
            if (!e.isWorking()) {
                ret = 1
                val stack = e.getStackInHand(Hand.MAIN_HAND)
                stack.damage(1, e, { j -> j.sendToolBreakStatus(Hand.MAIN_HAND) })
            }
            ret
        }

        override val radiusToAct: Float = 125.0f
        override val radiusToSee: Float = 75.0f
        override val ticksToTest: Int = 10
        override val ticksToExec: Int = 20
        override val pathReach: Int = 8
    }

    object Sleep : Action() {
        override val type = ErrandType.POSITION
        override val scan: ActionInput = { e, _ -> (e.canSleep() || e.isSleeping()).toByte(5) }
        override val test: ActionInput = { e, p ->
            var ret: Byte = 2
            if (!e.isSleeping()) {
                val state = e.world.getBlockState(p!!)
                if (state.getBlock() is BedBlock) {
                    if (!state.get(BedBlock.OCCUPIED)) {
                        ret = 1
                    }
                    // if no bed is found, villager should consider leaving housing
                }
                // add else check to trigger if enclosed space still a valid housing
                ret
            } else {
                1
            }
        }
        override val exec: ActionInput = { e, p ->
            e.sleep(p!!)
            1
        }
        override val pathReach: Int = 1
    }

    object Sit : Action() {
        override val type = ErrandType.POSITION

        override val scan: ActionInput = { _, _ -> 4 }

        override val test: ActionInput = { e, p ->
            if (p != null) {
                (e.world.getBlockState(p).block is SlabBlock).toByte()
            } else {
                1
            }
        }

        override val exec: ActionInput = { e, p ->
            p?.let {
                e.sit(it.up())
            }
            1
        }

        override val radiusToAct: Float = 3.5f
        override val radiusToSee: Float = -1.0f
        override val ticksToTest: Int = 5
        override val ticksToExec: Int = 5
    }

    object Wake : Action() {
        override val type = ErrandType.POSITION

        override val scan: ActionInput = { e, _ -> (e.isSleeping() || e.isSitting()).toByte(9) }

        override val exec: ActionInput = { e, _ ->
            e.getUp()
            1
        }

        override val radiusToAct: Float = 3.5f
        override val radiusToSee: Float = -1.0f
        override val ticksToTest: Int = 1
        override val ticksToExec: Int = 1
    }

    object Eat : Action() {
        override val type = ErrandType.PARALLEL
        override val scan: ActionInput = { e, _ ->
            // TODO: add checks that increases priority like health and hunger bar
            (e.inventory.hasItem(ItemPredicate.EDIBLE)).toByte(2)
        }
        override val test: ActionInput = { e, _ ->
            var ret: Byte = (e.tryEquip(ItemPredicate.EDIBLE, EquipmentSlot.OFFHAND)).toByte()
            if (ret == 0.toByte()) ret = 2
            ret
        }
        override val exec: ActionInput = { e, _ ->
            e.setCurrentHand(Hand.OFF_HAND)
            1
        }
        override val eval: ActionInput = { e, _ -> (!e.isEating()).toByte() }
        override val restCost: Double = 0.0
    }

    object Store : Action() {
        override val type = ErrandType.POSITION
        override val scan: ActionInput = { _, _ -> -1 }
        override val exec: ActionInput = { e, _ ->
            e.target = null
            1
        }
        override val speedModifier: Double = 1.2
        override val radiusToSee: Float = -1.0f
    }

    object Shear : Action() {
        override val type = ErrandType.POSITION
        override val scan: ActionInput = { e, _ ->
            e.target?.let { t ->
                (t is SheepEntity && !t.isSheared() && e.tryEquip(ItemPredicate.SHEARS, EquipmentSlot.OFFHAND)).toByte(5)
            } ?: 0
        }
        override val exec: ActionInput = { e, _ ->
            e.target?.let { t ->
                if (t is SheepEntity) {
                    val stack = e.getStackInHand(Hand.MAIN_HAND)
                    t.sheared(SoundCategory.PLAYERS)
                    t.emitGameEvent(GameEvent.SHEAR, e)
                    stack.damage(1, e, { j -> j.sendEquipmentBreakStatus(EquipmentSlot.MAINHAND) })
                }
            }
            1
        }
        override val radiusToAct: Float = 3.8f
        override val radiusToSee: Float = 15.0f
        override val ticksToTest: Int = 1
        override val ticksToExec: Int = 15
    }

    object Pick : Action() {
        override val type = ErrandType.POSITION

        override val scan: ActionInput = { _, _ -> 3 }
        override val test: ActionInput = { e, _ ->
            e.setPicking(true)
            1
        }
        override val exec: ActionInput = { e, _ ->
            e.setPicking(false)
            1
        }
        override val radiusToAct: Float = 2.5f
        override val radiusToSee: Float = 5.0f
        override val ticksToTest: Int = 10
        override val ticksToExec: Int = 5
    }

    object Reach : Action() {
        override val type = ErrandType.POSITION
        override val scan: ActionInput = { _, _ -> 6 }
        override val radiusToAct: Float = 32.0f
        override val radiusToSee: Float = -1.0f
        override val ticksToTest: Int = 1
        override val ticksToExec: Int = 1
    }

    object Wander : Action() {
        override val type = ErrandType.POSITION
        override val scan: ActionInput = { e, _ -> (e.target != null).toByte(3) }
        override val radiusToAct: Float = 8.0f
        override val radiusToSee: Float = -1.0f
        override val ticksToTest: Int = 1
        override val ticksToExec: Int = 15
    }

    // TODO: implement this
    object Follow : Action() {
        override val type = ErrandType.POSITION
        override val scan: ActionInput = { e, _ -> 4 }
        override val eval: ActionInput = { e, _ -> 1 }
        override val radiusToAct: Float = 3.5f
        override val radiusToSee: Float = 12.0f
    }

    object Seek : Action() {
        override val type = ErrandType.POSITION
        override val scan: ActionInput = { e, _ -> (e.target != null).toByte(2) }
        override val exec: ActionInput = { e, _ ->
            e.target?.let { t ->
                if ((e.random.nextInt(5) == 0 && t is AbstractVillagerEntity)) {
                    e.pushErrand(Action.Type.TALK)
                }
            }
            1
        }

        override val radiusToAct: Float = 128.0f
        override val radiusToSee: Float = 128.0f
        override val ticksToTest: Int = 10
        override val ticksToExec: Int = 20
        override val pathReach: Int = 8
    }

    object Talk : Action() {
        override val type = ErrandType.POSITION
        override val scan: ActionInput = { e, _ ->
            (e.target != null && e.target is AbstractVillagerEntity && (e.target as AbstractVillagerEntity).target == e).toByte(6)
        }
        override val eval: ActionInput = { e, _ ->
            var ret: Byte = 0
            if (!e.errandManager.has(Action.Type.DISAGREE) && !e.errandManager.has(Action.Type.AGREE)) {
                if (e.random.nextInt(20) == 0) {
                    e.pushErrand(Action.Type.AGREE)
                } else if (e.random.nextInt(20) == 0) {
                    e.pushErrand(Action.Type.DISAGREE)
                }
                if (e.random.nextInt(40) == 0) {
                    ret = 1
                }
            }
            ret
        }
        override val stop: ActionInput = { e, _ ->
            e.navigation.stop()
            1
        }
        override val radiusToAct: Float = 5.0f
        override val radiusToSee: Float = 20.0f
        override val ticksToTest: Int = 5
        override val ticksToExec: Int = 20
    }

    object Agree : Action() {
        override val type = ErrandType.PARALLEL
        override val scan: ActionInput = { e, _ ->
            (e.target != null).toByte(3)
        }
        override val test: ActionInput = { e, _ ->
            e.setState(ActionState.AGREE)
            e.playSound(SoundEvents.ENTITY_VILLAGER_YES, 1.0f, e.getSoundPitch())
            1
        }

        override val eval: ActionInput = { e, _ ->
            e.setState(ActionState.NONE)
            1
        }
        override val ticksToTest: Int = 1
        override val ticksToExec: Int = 10
    }

    object Disagree : Action() {
        override val type = ErrandType.PARALLEL
        override val scan: ActionInput = { e, _ ->
            (e.target != null).toByte(3)
        }
        override val test: ActionInput = { e, _ ->
            e.setState(ActionState.DISAGREE)
            e.playSound(SoundEvents.ENTITY_VILLAGER_NO, 1.0f, e.getSoundPitch())
            1
        }

        override val eval: ActionInput = { e, _ ->
            e.setState(ActionState.NONE)
            1
        }
        override val ticksToTest: Int = 5
        override val ticksToExec: Int = 10
    }

    object Swim : Action() {
        override val type = ErrandType.PARALLEL

        override val scan: ActionInput = { e, _ -> (e.isTouchingWater()).toByte(8) }
        override val eval: ActionInput = { e, _ ->
            var ret: Byte = 0
            if (!e.isSwimming()) {
                ret = 1
            } else {
                if (e.getRandom().nextFloat() < 0.8f) {
                    e.getJumpControl().setActive()
                }
            }
            ret
        }

        override val radiusToAct: Float = 512.0f
        override val radiusToSee: Float = -1.0f
        override val ticksToTest: Int = 1
        override val ticksToExec: Int = 4
    }

    object Open : Action() {
        override val type = ErrandType.PARALLEL

        override val scan: ActionInput = { e, p -> 7.toByte() }
        override val exec: ActionInput = { e, p ->
            // TODO: use a mixin method for fence instead
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
                    val direction = e.horizontalFacing
                    val fence =
                        if (state.get(FenceGateBlock.FACING) == direction.opposite) {
                            state.with(FenceGateBlock.FACING, direction)
                        } else {
                            state
                        }.with(FenceGateBlock.OPEN, true)

                    e.swingHand(Hand.MAIN_HAND)

                    e.world.setBlockState(p, fence, Block.NOTIFY_LISTENERS or Block.REDRAW_ON_MAIN_THREAD)
                    e.world.playSound(
                        e,
                        p,
                        SoundEvents.BLOCK_FENCE_GATE_OPEN,
                        SoundCategory.BLOCKS,
                        1.0f,
                        e.world.random.nextFloat() * 0.1f + 0.9f,
                    )
                    e.world.emitGameEvent(e, GameEvent.BLOCK_OPEN, p)
                }
                else -> { }
            }
            1
        }
        override val eval: ActionInput = { e, p ->
            val state = e.world.getBlockState(p!!)
            val block = state.getBlock()
            when (block) {
                is DoorBlock -> {
                    val direction = e.horizontalFacing
                    var offset =
                        if (state.get(DoorBlock.FACING) == direction.opposite) {
                            state.get(DoorBlock.FACING).getOpposite()
                        } else {
                            state.get(DoorBlock.FACING)
                        }
                    e.pushErrand(
                        Action.Type.CLOSE,
                        p.offset(offset),
                    )
                }
                is FenceGateBlock -> {
                    val direction = e.horizontalFacing
                    var offset =
                        if (state.get(FenceGateBlock.FACING) == direction.opposite) {
                            state.get(DoorBlock.FACING).getOpposite()
                        } else {
                            state.get(DoorBlock.FACING)
                        }
                    e.pushErrand(
                        Action.Type.CLOSE,
                        p.offset(offset),
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

    object Close : Action() {
        override val type = ErrandType.PARALLEL
        override val scan: ActionInput = { _, _ -> 7 }

        override val exec: ActionInput = { e, p ->
            BlockIterator
                .NEIGHBOURS(p!!)
                .find {
                    e.world.getBlockState(it).getBlock() is DoorBlock ||
                        e.world.getBlockState(it).getBlock() is FenceGateBlock
                }?.let { t ->
                    val state = e.world.getBlockState(t)
                    val block = state.getBlock()
                    when (block) {
                        is DoorBlock -> {
                            block.setOpen(e, e.getWorld(), state, t, false)
                            e.swingHand(Hand.MAIN_HAND)
                        }
                        is FenceGateBlock -> {
                            if (state.get(FenceGateBlock.OPEN)) {
                                val fence = state.with(FenceGateBlock.OPEN, false)
                                e.world.setBlockState(t, fence, Block.NOTIFY_LISTENERS or Block.REDRAW_ON_MAIN_THREAD)

                                e.swingHand(Hand.MAIN_HAND)
                                e.world.playSound(
                                    e,
                                    p,
                                    SoundEvents.BLOCK_FENCE_GATE_CLOSE,
                                    SoundCategory.BLOCKS,
                                    1.0f,
                                    e.world.random.nextFloat() * 0.1f + 0.9f,
                                )
                                e.world.emitGameEvent(e, GameEvent.BLOCK_CLOSE, t)
                            }
                        }
                    }
                }
            1
        }

        override val radiusToAct: Float = 2.5f
        override val ticksToExec: Int = 5
        override val ticksToTest: Int = 5
    }

    object Strafe : Action() {
        override val type = ErrandType.PARALLEL
        override val scan: ActionInput = { e, _ ->
            (true).toByte(8)
        }
        override val eval: ActionInput = { e, _ ->
            var ret: Byte = 0

            e.target?.let { t ->
                if (t.isAlive) {
                    e.getMoveControl().strafeTo(-0.5f, 0.0f)
                } else if (!t.isAlive || e.squaredDistanceTo(e.target) > 5) {
                    ret = 1
                }
            } ?: run {
                ret = 1
            }
            if (ret == 1.toByte()) {
                e.getNavigation().stop()
            }
            ret
        }
        override val ticksToTest: Int = 1
        override val ticksToExec: Int = 5
    }

    object Flee : Action() {
        override val type = ErrandType.POSITION

        override val scan: ActionInput = { _, _ -> 9 }
        override val radiusToAct: Float = 8.0f
        override val radiusToSee: Float = -1.0f
        override val ticksToTest: Int = 1
        override val ticksToExec: Int = 1
        override val speedModifier: Double = 1.25
        override val pathReach: Int = 2
    }

    object Attack : Action() {
        override val type = ErrandType.POSITION
        override val scan: ActionInput = { e, _ ->
            e.item[Action.Type.ATTACK]?.let { i ->
                (
                    e.tryEquip(ItemPredicate.SWORD, EquipmentSlot.MAINHAND, i) &&
                        e.target != null
                ).toByte(8)
            } ?: run {
                0
            }
        }

        // (
        //    e.tryEquip(ItemPredicate.SWORD, EquipmentSlot.MAINHAND) &&
        //        e.target != null
        // ).toByte(8)
        override val exec: ActionInput = { e, _ ->
            e.target?.let { t ->
                if (t.isAlive) {
                    e.swingHand(Hand.MAIN_HAND)
                    if (e.squaredDistanceTo(t) <= 4.2f) {
                        e.tryAttack(t)
                    }
                }
            }
            1
        }
        override val speedModifier: Double = 1.25
        override val radiusToAct: Float = 4.2f
        override val radiusToSee: Float = 64.0f
        override val ticksToExec: Int = 1
        override val ticksToTest: Int = 4
        override val pathReach: Int = 1
    }

    object Defend : Action() {
        override val type = ErrandType.POSITION
        override val scan: ActionInput = { e, _ ->
            e.item[Action.Type.DEFEND]?.let { i ->
                (
                    e.tryEquip(ItemPredicate.SHIELD, EquipmentSlot.OFFHAND, i) &&
                        e.target != null
                ).toByte(9)
            } ?: run {
                0
            }
        }
        override val test: ActionInput = { e, _ ->
            e.setCurrentHand(Hand.OFF_HAND)
            1
        }
        override val eval: ActionInput = { e, _ ->
            var ret: Byte = 0
            e.target?.let { t ->
                if (e.random.nextInt(20) == 0) {
                    e.pushErrand(Action.Type.ATTACK)
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
        override val stop: ActionInput = { e, _ ->
            e.stopUsingItem()
            0x01
        }
        override val speedModifier: Double = 1.25
        override val radiusToAct: Float = 12.0f
        override val ticksToExec: Int = 3
        override val ticksToTest: Int = 2
        override val pathReach: Int = 2
    }

    object Aim : Action() {
        override val type = ErrandType.POSITION
        override val scan: ActionInput = { e, _ ->
            (
                e.inventory.hasItem(ItemPredicate.ARROW) &&
                    e.tryEquip(ItemPredicate.BOW, EquipmentSlot.MAINHAND) &&
                    e.target != null
            ).toByte(9)
        }

        override val test: ActionInput = { e, _ ->
            e.setCurrentHand(Hand.MAIN_HAND)
            1
        }

        override val eval: ActionInput = { e, _ ->
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

    object Charge : Action() {
        override val type = ErrandType.POSITION
        override val scan: ActionInput = { e, _ ->
            (
                e.tryEquip(ItemPredicate.CROSSBOW, EquipmentSlot.MAINHAND) &&
                    e.target != null
            ).toByte(9)
        }
        override val test: ActionInput = { e, _ ->
            val item = e.getStackInHand(Hand.MAIN_HAND)
            if (!CrossbowItem.isCharged(item)) {
                e.setCurrentHand(Hand.MAIN_HAND)
            }
            1
        }

        override val eval: ActionInput = { e, _ ->
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
        DISAGREE,
        AGREE,
        SHEAR,
        SEEK,
        TALK,
        WANDER,
        IDLE,
        WAKE,
        STRAFE,
        SWIM,
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
                Type.OPEN to Open,
                Type.CLOSE to Close,
                Type.DEFEND to Defend,
                Type.AIM to Aim,
                Type.DISAGREE to Disagree,
                Type.AGREE to Agree,
                Type.SHEAR to Shear,
                Type.SEEK to Seek,
                Type.TALK to Talk,
                Type.WANDER to Wander,
                Type.WAKE to Wake,
                Type.STRAFE to Strafe,
                Type.SWIM to Swim,
            )

        fun get(type: Type): Action = map[type] ?: throw IllegalArgumentException("Unknown Action type: $type")

        fun Boolean.toByte(mult: Int): Byte = if (this) (1 * mult).toByte() else 0

        fun Boolean.toByte(): Byte = if (this) 1 else 0
    }
}
