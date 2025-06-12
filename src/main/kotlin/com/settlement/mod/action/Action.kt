package com.settlement.mod.action

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import com.settlement.mod.LOGGER
import com.settlement.mod.entity.mob.AbstractVillagerEntity
import com.settlement.mod.entity.mob.ErrandType
import com.settlement.mod.entity.projectile.SimpleFishingBobberEntity
import com.settlement.mod.item.ItemPredicate
import com.settlement.mod.util.BlockIterator
import com.settlement.mod.util.CombatUtils
import com.settlement.mod.util.Finder
import net.minecraft.block.BedBlock
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.block.CropBlock
import net.minecraft.block.DoorBlock
import net.minecraft.block.FarmlandBlock
import net.minecraft.block.FenceGateBlock
import net.minecraft.block.SlabBlock
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.ChargedProjectilesComponent
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.mob.MobEntity
import net.minecraft.entity.passive.HorseEntity
import net.minecraft.entity.passive.SheepEntity
import net.minecraft.inventory.Inventory
import net.minecraft.item.ArrowItem
import net.minecraft.item.BoneMealItem
import net.minecraft.item.CrossbowItem
import net.minecraft.item.Items
import net.minecraft.registry.tag.BlockTags
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.world.event.GameEvent
import java.util.Optional

data class Errand(
    val cid: Action.Type,
    val pos: BlockPos? = null,
    val priority: Byte = 1,
) : Comparable<Errand> {
    override fun compareTo(other: Errand): Int = other.priority.compareTo(this.priority)

    companion object {
        val CODEC: Codec<Errand> =
            RecordCodecBuilder.create { instance ->
                instance
                    .group(
                        Codec.INT.fieldOf("type").forGetter { it.cid.ordinal },
                        BlockPos.CODEC.optionalFieldOf("pos").forGetter { Optional.ofNullable(it.pos) },
                        Codec.BYTE.fieldOf("priority").forGetter { it.priority },
                    ).apply(instance) { type, posOpt, priority ->
                        Errand(Action.Type.values()[type], posOpt.orElse(null), priority)
                    }
            }
    }
}

typealias ActionInput = (entity: AbstractVillagerEntity, pos: BlockPos?) -> Byte

// TODO:
//  - make a enum class to standard the ActionInput outputs
sealed class Action {
    abstract val type: ErrandType

    open val ticksToTest: Int = 5
    open val ticksToExec: Int = 5
    open val ticksToEval: Int = 5

    abstract val scan: ActionInput // scan if errand can be tested
    open val test: ActionInput = { _, _ -> 1 } // verify if errand can be executed
    open val exec: ActionInput = { _, _ -> 1 } // execute lambda representing errand
    open val eval: ActionInput = { _, _ -> 1 } // scan if errand can end
    open val redo: ActionInput = { _, _ -> 1 } // check if action must be redone

    // handle action cancelation
    open val stop: ActionInput = { e, _ ->
        e.navigation.stop()
        1
    }

    open val radiusToAct: Float = 4.0f
    open val radiusToSee: Float = 4.0f

    open val speedModifier: Double = 1.0
    open val restCost: Double = 0.5
    open val pathReach: Int = 0

    open val isUnique: Boolean = true

    val shouldMove: (Double) -> Boolean = { d -> d > radiusToAct }
    val shouldLook: (Double) -> Boolean = { d -> d < radiusToSee }

    val shouldTest: (Int) -> Boolean = { c -> c >= ticksToTest }
    val shouldExec: (Int) -> Boolean = { c -> c >= ticksToExec }
    val shouldEval: (Int) -> Boolean = { c -> c >= ticksToEval }

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
        CHOP,
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
        INTERACT,
        WANDER,
        IDLE,
        WAKE,
        STRAFE,
        SWIM,
        RIDE,
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
                Type.BREAK to Break(),
                Type.DIG to Dig,
                Type.MINE to Mine,
                Type.CHOP to Chop,
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
                Type.SHEAR to Shear,
                Type.SEEK to Seek,
                Type.INTERACT to Interact,
                Type.WANDER to Wander,
                Type.STRAFE to Strafe,
                Type.SWIM to Swim,
                Type.RIDE to Ride,
            )

        fun get(type: Type): Action = map[type] ?: throw IllegalArgumentException("Unknown Action type: $type")

        fun Boolean.toByte(mult: Int): Byte = if (this) (1 * mult).toByte() else 0

        fun Boolean.toByte(): Byte = if (this) 1 else 0
    }
}

open class Break : Action() {
    override val type = ErrandType.WORK
    override val scan: ActionInput = { _, _ -> 9 }
    override val test: ActionInput = { e, p ->
        0x01
    }

    override val exec: ActionInput = { e, p ->
        val state = e.world.getBlockState(p)
        val hardness = state.getHardness(e.world, p) * 100
        if (hardness == -1.0f) {
            e.tickingValue = 1
        } else {
            val multiplier = e.getStackInHand(Hand.MAIN_HAND).getMiningSpeedMultiplier(state)
            e.tickingValue = (hardness / multiplier).toInt()
        }
        1
    }

    override val eval: ActionInput = { e, p ->
        val state = e.world.getBlockState(p)
        if (e.tickingValue % 5 == 4 || e.tickingValue == 0) {
            e.swingHand(Hand.MAIN_HAND)
            e.world.playSound(e, p, state.getSoundGroup().getHitSound(), SoundCategory.BLOCKS, 1.0f, 1.0f)
        }
        e.tickingValue--
        if (e.tickingValue <= 0) {
            e.world.breakBlock(p, true)
            1
        } else {
            0
        }
    }

    override val isUnique = false
    override val ticksToTest: Int = 30
    override val ticksToExec: Int = 20
    override val radiusToAct: Float = 12.0f
    override val radiusToSee: Float = 16.0f
    override val restCost: Double = 0.5
    override val pathReach: Int = 2
}

object Dig : Break() {
    override val scan: ActionInput = { e, _ ->
        e.item[Action.Type.DIG]?.let { i ->
            (e.tryEquip(ItemPredicate.SHOVEL, EquipmentSlot.MAINHAND, i)).toByte(8)
        } ?: run {
            e.item[Action.Type.DIG] = -3
            0
        }
    }

    // TODO: Figure out a way to give break errands matching tools capacity
    override val test: ActionInput = { e, p ->
        0x01
    }
}

object Mine : Break() {
    override val scan: ActionInput = { e, _ ->
        e.item[Action.Type.MINE]?.let { i ->
            (e.tryEquip(ItemPredicate.PICKAXE, EquipmentSlot.MAINHAND, i)).toByte(8)
        } ?: run {
            e.item[Action.Type.MINE] = -3
            0
        }
    }

    // TODO: Figure out a way to give break errands matching tools capacity
    override val test: ActionInput = { e, p ->
        0x01
    }
}

object Chop : Break() {
    override val scan: ActionInput = { e, _ ->
        e.item[Action.Type.CHOP]?.let { i ->
            (e.tryEquip(ItemPredicate.AXE, EquipmentSlot.MAINHAND, i)).toByte(8)
        } ?: run {
            e.item[Action.Type.CHOP] = -3
            0
        }
    }

    override val test: ActionInput = { e, p ->
        if (e.world.getBlockState(p).isIn(BlockTags.LOGS_THAT_BURN)) {
            1
        } else {
            2
        }
    }
}

object Till : Action() {
    override val type = ErrandType.WORK
    override val scan: ActionInput = { e, _ ->
        e.item[Action.Type.TILL]?.let { i ->
            (e.tryEquip(ItemPredicate.HOE, EquipmentSlot.MAINHAND, i)).toByte(6)
        } ?: run {
            e.item[Action.Type.TILL] = -3
            0
        }
    }
    override val test: ActionInput = { e, p ->
        if (e.world.getBlockState(p!!.up()).isAir && e.world.getBlockState(p).block in BlockIterator.TILLABLE_BLOCKS) {
            1
        } else {
            2
        }
    }
    override val exec: ActionInput = { e, p ->
        val stack = e.getStackInHand(Hand.MAIN_HAND)
        e.world.playSound(e, p, SoundEvents.ITEM_HOE_TILL, SoundCategory.BLOCKS, 1.0f, 1.0f)
        e.world.setBlockState(p, Blocks.FARMLAND.defaultState, Block.NOTIFY_LISTENERS)
        e.swingHand(Hand.MAIN_HAND)
        stack.damage(1, e, EquipmentSlot.MAINHAND)
        1
    }
    override val isUnique = false
    override val ticksToTest: Int = 10
    override val ticksToExec: Int = 10
}

object Plant : Action() {
    override val type = ErrandType.WORK
    override val scan: ActionInput = { e, _ ->
        e.item[Action.Type.PLANT]?.let { i ->
            (e.tryEquip(ItemPredicate.PLANTABLE, EquipmentSlot.OFFHAND, i)).toByte(6)
        } ?: run {
            e.item[Action.Type.PLANT] = -3
            0
        }
    }
    override val test: ActionInput = { e, p ->
        if (e.world.getBlockState(p!!.up()).isAir && e.world.getBlockState(p).block is FarmlandBlock) {
            1
        } else {
            2
        }
    }

    override val exec: ActionInput = { e, p ->
        val stack = e.getStackInHand(Hand.OFF_HAND)
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
            e.swingHand(Hand.OFF_HAND)
            stack.decrement(1)
        }
        1
    }

    override val isUnique = false
    override val ticksToTest: Int = 10
    override val ticksToExec: Int = 10
    override val restCost: Double = 1.2
}

object Powder : Action() {
    override val type = ErrandType.WORK
    override val scan: ActionInput = { e, _ ->
        e.item[Action.Type.POWDER]?.let { i ->
            (e.tryEquip({ item -> item == Items.BONE_MEAL }, EquipmentSlot.OFFHAND, i)).toByte(5)
        } ?: run {
            e.item[Action.Type.POWDER] = -3
            0
        }
    }

    override val test: ActionInput = { e, p ->
        val up = p!!.up()
        val state = e.world.getBlockState(up)
        if (state.block is CropBlock &&
            !(state.block as CropBlock).isMature(state) &&
            e.world.getBlockState(p).block is FarmlandBlock
        ) {
            1
        } else {
            2
        }
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
    override val type = ErrandType.WORK
    override val scan: ActionInput = { _, _ -> 6 }
    override val test: ActionInput = { e, p ->
        if (e.world.getBlockState(p!!.up()).block is CropBlock) {
            1
        } else {
            2
        }
    }
    override val exec: ActionInput = { entity, pos ->
        entity.world.breakBlock(pos!!.up(), true)
        entity.swingHand(Hand.MAIN_HAND)
        1
    }

    override val isUnique = false
    override val ticksToTest: Int = 5
    override val ticksToExec: Int = 10
    override val restCost: Double = 0.8
}

object Fish : Action() {
    override val type = ErrandType.WORK
    override val scan: ActionInput = { e, _ ->
        e.item[Action.Type.FISH]?.let { i ->
            (e.tryEquip(ItemPredicate.FISHING_ROD, EquipmentSlot.MAINHAND, i)).toByte(6)
        } ?: run {
            e.item[Action.Type.FISH] = -3
            0
        }
    }

    override val test: ActionInput = { e, p ->
        var ret: Byte =
            (
                BlockIterator.BOTTOM(p!!).all { e.world.getBlockState(p).block == Blocks.WATER } &&
                    e.isHolding(Items.FISHING_ROD)
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
        if (!e.isWorking()) {
            val stack = e.getStackInHand(Hand.MAIN_HAND)
            stack.damage(1, e, EquipmentSlot.MAINHAND)
            1
        } else {
            0
        }
    }

    override val radiusToAct: Float = 125.0f
    override val radiusToSee: Float = 75.0f
    override val ticksToTest: Int = 10
    override val ticksToExec: Int = 20
    override val pathReach: Int = 8
}

object Sleep : Action() {
    override val type = ErrandType.IDLE
    override val scan: ActionInput = { e, _ -> (e.canSleep() && !e.isSleeping()).toByte(5) }
    override val test: ActionInput = { e, p ->
        val state = e.world.getBlockState(p!!)
        if (state.getBlock() is BedBlock) {
            if (!state.get(BedBlock.OCCUPIED)) {
                1
            } else {
                0
            }
            // if no bed is found, villager should consider leaving housing
        } else {
            0
        }
        // add else check to trigger if enclosed space still a valid housing
    }
    override val exec: ActionInput = { e, p ->
        e.sleep(p!!)
        1
    }
    override val pathReach: Int = 1
}

object Sit : Action() {
    override val type = ErrandType.IDLE

    override val scan: ActionInput = { e, _ -> (!e.isSitting()).toByte(5) }

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

object Eat : Action() {
    override val type = ErrandType.DUAL
    override val scan: ActionInput = { e, _ ->
        // TODO: add checks that increases priority like health and hunger bar
        e.item[Action.Type.EAT]?.let { i ->
            (e.tryEquip(ItemPredicate.EDIBLE, EquipmentSlot.OFFHAND, i)).toByte(3)
        } ?: run {
            e.item[Action.Type.EAT] = -3
            0
        }
    }

    override val exec: ActionInput = { e, _ ->
        val stack = e.getStackInHand(Hand.OFF_HAND)
        stack.components.getOrDefault(DataComponentTypes.FOOD, null)?.let { satiation ->
            e.pendingValue = satiation.nutrition().toDouble()
            stack.decrement(1)
        }
        e.setCurrentHand(Hand.OFF_HAND)
        1
    }

    override val eval: ActionInput = { e, _ ->
        if (e.isEating()) {
            0x00
        } else {
            e.errandManager.satiation += e.pendingValue.toFloat()
            0x01
        }
    }

    override val radiusToAct: Float = 1024.0f
    override val radiusToSee: Float = -1.0f
    override val restCost: Double = 0.0
}

object Store : Action() {
    override val type = ErrandType.IDLE
    override val scan: ActionInput = { _, _ -> 7 }
    override val exec: ActionInput = { e, p ->
        e.world.getBlockEntity(p)?.let { b ->
            e.getExcessItems().forEach { item ->
                e.insertStackIntoInventory(b as Inventory, item)
            }
        }
        0x01
    }

    override val eval: ActionInput = { e, p ->
        0x01
    }

    override val radiusToAct: Float = 4.0f
    override val radiusToSee: Float = 6.0f
}

object Shear : Action() {
    override val type = ErrandType.WORK
    override val scan: ActionInput = { e, _ ->
        e.item[Action.Type.SHEAR]?.let { i ->
            (e.tryEquip(ItemPredicate.SHEARS, EquipmentSlot.MAINHAND, i)).toByte(3)
        } ?: run {
            e.item[Action.Type.SHEAR] = -3
            0
        }
    }

    override val exec: ActionInput = { e, _ ->
        e.target?.let { t ->
            if (t is SheepEntity) {
                val stack = e.getStackInHand(Hand.MAIN_HAND)
                t.sheared(e.world as ServerWorld, SoundCategory.PLAYERS, stack)
                t.emitGameEvent(GameEvent.SHEAR, e)
                stack.damage(1, e, EquipmentSlot.MAINHAND)
            }
        }
        1
    }
    override val radiusToAct: Float = 3.8f
    override val radiusToSee: Float = 15.0f
    override val ticksToTest: Int = 10
    override val ticksToExec: Int = 5
}

object Pick : Action() {
    override val type = ErrandType.IDLE

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
    override val type = ErrandType.IDLE
    override val scan: ActionInput = { _, _ -> 7 }
    override val radiusToAct: Float = 32.0f
    override val radiusToSee: Float = -1.0f
    override val ticksToTest: Int = 1
    override val ticksToExec: Int = 1
}

object Wander : Action() {
    override val type = ErrandType.IDLE
    override val scan: ActionInput = { _, _ -> 3 }
    override val radiusToAct: Float = 8.0f
    override val radiusToSee: Float = -1.0f
}

// TODO: implement this
object Follow : Action() {
    override val type = ErrandType.IDLE
    override val scan: ActionInput = { _, _ -> 4 }
    override val eval: ActionInput = { _, _ -> 1 }
    override val radiusToAct: Float = 3.5f
    override val radiusToSee: Float = 12.0f
}

// TODO: Implement this
object Ride : Action() {
    override val type = ErrandType.IDLE
    override val scan: ActionInput = { e, _ ->
        e.target?.let { t ->
            (t is HorseEntity).toByte(6)
        } ?: 0
    }
    override val exec: ActionInput = { e, _ ->
        (e as MobEntity).startRiding(e.target, true)
        1
    }
}

object Seek : Action() {
    override val type = ErrandType.IDLE
    override val scan: ActionInput = { e, _ -> (e.target != null && e.squaredDistanceTo(e.target) < 12).toByte(3) }
    override val test: ActionInput = { e, _ ->
        e.tickingValue = 10 + e.world.random.nextInt(10)
        0x01
    }

    override val eval: ActionInput = { e, _ ->
        if (--e.tickingValue <= 0) {
            0x01
        } else {
            0x00
        }
    }
    override val radiusToAct: Float = 64.0f
    override val radiusToSee: Float = 48.0f
    override val ticksToTest: Int = 5
    override val ticksToExec: Int = 5
    override val ticksToEval: Int = 5
    override val restCost: Double = 0.02
    override val pathReach: Int = 4
}

object Interact : Action() {
    override val type = ErrandType.IDLE
    override val scan: ActionInput = { e, _ ->
        (
            e.target != null &&
                e.target is AbstractVillagerEntity &&
                (e.target as AbstractVillagerEntity).target == e
        ).toByte(3)
    }
    override val exec: ActionInput = { e, _ ->
        val state =
            AbstractVillagerEntity.Companion.State
                .values()
                .random()
        e.setState(state)
        0x01
    }
    override val eval: ActionInput = { e, _ ->
        e.target?.let {
            if (e.random.nextInt(20) == 0) {
                e.setState(AbstractVillagerEntity.Companion.State.NONE)
                0x01
            } else {
                0x00
            }
        } ?: run {
            e.setState(AbstractVillagerEntity.Companion.State.NONE)
            0x01
        }
    }

    override val radiusToAct: Float = 128.0f
    override val radiusToSee: Float = 128.0f
    override val ticksToTest: Int = 5
    override val ticksToExec: Int = 5
    override val ticksToEval: Int = 10
    override val restCost: Double = 0.1
}

object Swim : Action() {
    override val type = ErrandType.DUAL

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
    override val restCost: Double = 0.04
}

object Open : Action() {
    override val type = ErrandType.DUAL

    override val scan: ActionInput = { _, _ -> 7.toByte() }
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
                    if (state.get(DoorBlock.FACING) == direction.opposite && p != e.blockPos) {
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
    override val radiusToAct: Float = 4.5f
    override val ticksToExec: Int = 5
    override val ticksToTest: Int = 5
}

object Close : Action() {
    override val type = ErrandType.DUAL
    override val scan: ActionInput = { _, _ -> 8.toByte() }

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
    override val type = ErrandType.DUAL
    override val scan: ActionInput = { _, _ -> 8 }
    override val eval: ActionInput = { e, _ ->
        e.target?.let { t ->
            if (t.isAlive && e.isFighting()) {
                e.getMoveControl().strafeTo(-0.5f, 0.0f)
                0x00
            } else if (e.squaredDistanceTo(e.target) < 5) {
                0x01
            } else {
                0x00
            }
        } ?: run {
            e.getNavigation().stop()
            0x01
        }
    }
    override val ticksToTest: Int = 1
    override val ticksToExec: Int = 5
}

// retreat
object Flee : Action() {
    override val type = ErrandType.FRAY

    override val scan: ActionInput = { e, _ -> (e.target != null && e.isFighting()).toByte(9) }

    override val eval: ActionInput = { e, _ ->
        e.target?.let { t ->
            if (t.isAlive) {
                0x02
            } else {
                0x00
            }
        } ?: 0x01
    }
    override val redo: ActionInput = { e, _ ->
        e.target?.let { t ->
            if (e.isFighting()) {
                Finder.findFleeBlock(e, t)?.let { errand ->
                    e.pushErrand(errand.cid, errand.pos)
                    0x01
                }
            }
            0x00
        } ?: 0x00
    }
    override val radiusToAct: Float = 8.0f
    override val radiusToSee: Float = -1.0f
    override val ticksToTest: Int = 1
    override val ticksToExec: Int = 1
    override val speedModifier: Double = 1.25
    override val pathReach: Int = 2
}

object Attack : Action() {
    override val type = ErrandType.FRAY
    override val scan: ActionInput = { e, _ ->
        e.item[Action.Type.ATTACK]?.let { i ->
            (e.isFighting() && e.target != null && e.tryEquip(ItemPredicate.SWORD, EquipmentSlot.MAINHAND, i)).toByte(8)
        } ?: run {
            e.item[Action.Type.ATTACK] = -3
            0
        }
    }

    override val test: ActionInput = { e, _ ->
        0x01
    }

    override val eval: ActionInput = { e, _ ->
        e.target?.let { t ->
            if (t.isAlive) {
                if (e.squaredDistanceTo(t) <= 4.2f) {
                    e.swingHand(Hand.MAIN_HAND)
                    e.tryAttack(e.world as ServerWorld, t)
                    0x02
                } else {
                    0x01
                }
            } else {
                0x01
            }
        } ?: run {
            0x01
        }
    }
    override val redo: ActionInput = { e, _ ->
        if (e.isFighting()) {
            e.pushErrand(Action.Type.ATTACK)
        }
        0x01
    }
    override val speedModifier: Double = 1.33
    override val radiusToAct: Float = 4.2f
    override val radiusToSee: Float = 64.0f
    override val ticksToTest: Int = 3
    override val ticksToExec: Int = 3
    override val ticksToEval: Int = 4
}

object Defend : Action() {
    override val type = ErrandType.FRAY
    override val scan: ActionInput = { e, _ ->
        e.item[Action.Type.DEFEND]?.let { i ->
            (e.target != null && e.tryEquip(ItemPredicate.SHIELD, EquipmentSlot.OFFHAND, i)).toByte(9)
        } ?: run {
            e.item[Action.Type.DEFEND] = -3
            0
        }
    }

    override val test: ActionInput = { e, _ ->
        e.setCurrentHand(Hand.OFF_HAND)
        e.tickingValue = 15
        1
    }

    override val eval: ActionInput = { e, _ ->
        e.target?.let { t ->
            if (!t.isAlive) {
                e.stopUsingItem()
                0x01
            } else {
                if (e.tickingValue-- <= 0) {
                    e.stopUsingItem()
                    0x01
                } else {
                    0x00
                }
            }
        } ?: run {
            e.stopUsingItem()
            0x01
        }
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
    override val type = ErrandType.FRAY

    // TODO: make arrow items be prefered in slot 0
    override val scan: ActionInput = { e, _ ->
        e.item[Action.Type.AIM]?.let { i ->
            (
                e.target != null &&
                    e.tryEquip(ItemPredicate.BOW, EquipmentSlot.MAINHAND, i)
            ).toByte(9)
        } ?: run {
            e.item[Action.Type.AIM] = -3
            0
        }
    }

    override val test: ActionInput = { e, _ ->
        e.setCurrentHand(Hand.MAIN_HAND)
        e.tickingValue = 10
        0x01
    }

    override val eval: ActionInput = { e, _ ->
        val ready = e.getItemUseTime() >= 25
        val item = e.getStackInHand(Hand.MAIN_HAND)
        e.target?.let { t ->
            if (t.isAlive && ready && e.tickingValue-- <= 0) {
                CombatUtils.shoot(e, t, item, Hand.MAIN_HAND, false)
                e.stopUsingItem()
                0x01
            } else {
                0x00
            }
        } ?: run {
            e.stopUsingItem()
            0x01
        }
    }

    override val ticksToExec: Int = 5
    override val ticksToTest: Int = 5
    override val radiusToAct: Float = 128.0f
    override val radiusToSee: Float = 128.0f
}

object Charge : Action() {
    override val type = ErrandType.FRAY
    override val scan: ActionInput = { e, _ ->
        e.item[Action.Type.CHARGE]?.let { i ->
            (
                e.target != null &&
                    e.tryEquip(ItemPredicate.CROSSBOW, EquipmentSlot.MAINHAND, i)
            ).toByte(9)
        } ?: run {
            e.item[Action.Type.AIM] = -3
            0
        }
    }

    override val test: ActionInput = { e, _ ->
        val stack = e.getStackInHand(Hand.MAIN_HAND)
        if (e.isHolding(Items.CROSSBOW)) {
            if (!CrossbowItem.isCharged(stack)) {
                e.setCurrentHand(Hand.MAIN_HAND)
                e.tickingValue = 20 + e.random.nextInt(10)
                stack.set(DataComponentTypes.CHARGED_PROJECTILES, ChargedProjectilesComponent.DEFAULT)
            } else {
                e.tickingValue = 5 + e.random.nextInt(5)
            }
            0x01
        } else {
            0x00
        }
    }

    override val eval: ActionInput = { e, _ ->
        val stack = e.getStackInHand(Hand.MAIN_HAND)
        val chargingTimeReached = e.itemUseTime >= CrossbowItem.getPullTime(e.activeItem, e) + 5
        e.target?.let { target ->
            if (!target.isAlive) return@let 0x01
            if (e.isUsingItem) {
                if (chargingTimeReached) {
                    e.stopUsingItem()
                    val item = e.getProjectileType(e.getStackInHand(Hand.MAIN_HAND))
                    val arrow = (Items.ARROW as ArrowItem).defaultStack.copyWithCount(1)
                    stack.set(DataComponentTypes.CHARGED_PROJECTILES, ChargedProjectilesComponent.of(arrow))
                }
                0x00
            } else {
                if (e.tickingValue-- <= 0) {
                    CombatUtils.shoot(e, target, stack, Hand.MAIN_HAND, true)
                    stack.set(DataComponentTypes.CHARGED_PROJECTILES, ChargedProjectilesComponent.DEFAULT)
                    0x01
                } else {
                    0x00
                }
            }
        } ?: run {
            e.stopUsingItem()
            stack.set(DataComponentTypes.CHARGED_PROJECTILES, ChargedProjectilesComponent.DEFAULT)
            0x01
        }
    }

    override val ticksToExec: Int = 10
    override val ticksToTest: Int = 5
    override val radiusToAct: Float = 175.0f
    override val radiusToSee: Float = 175.0f
}
