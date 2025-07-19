package com.settlement.mod.action

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import com.settlement.mod.LOGGER
import com.settlement.mod.block.BlockPredicate
import com.settlement.mod.entity.mob.AbstractVillagerEntity
import com.settlement.mod.entity.mob.EntityController
import com.settlement.mod.entity.mob.ErrandType // rename to ErrandGroup
import com.settlement.mod.entity.mob.State
import com.settlement.mod.entity.projectile.SimpleFishingBobberEntity
import com.settlement.mod.item.ItemPredicate
import com.settlement.mod.util.BlockUtils
import com.settlement.mod.util.around
import com.settlement.mod.util.bottom
import com.settlement.mod.util.neighbours
import net.minecraft.block.BedBlock
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.block.ChestBlock
import net.minecraft.block.DoorBlock
import net.minecraft.block.FenceGateBlock
import net.minecraft.block.entity.BlastFurnaceBlockEntity
import net.minecraft.block.entity.ChestBlockEntity
import net.minecraft.block.entity.SmokerBlockEntity
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.ChargedProjectilesComponent
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.entity.EntityPose
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.ItemEntity
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.mob.EvokerFangsEntity
import net.minecraft.entity.passive.HorseEntity
import net.minecraft.entity.passive.SheepEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.projectile.ArrowEntity
import net.minecraft.entity.projectile.ProjectileEntity
import net.minecraft.entity.projectile.thrown.SplashPotionEntity
import net.minecraft.inventory.Inventory
import net.minecraft.item.ArrowItem
import net.minecraft.item.BoneMealItem
import net.minecraft.item.CrossbowItem
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.item.consume.UseAction
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.stat.Stats
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import net.minecraft.world.event.GameEvent
import org.joml.Vector3f
import java.util.Optional

enum class StepResult(
    val code: Byte,
) {
    FAIL(0),
    SUCCESS(1),
    COMPLETE(2),
    REPEAT(3),
    CONTINUE(4),
    CANCEL(5),
    SWAP(6),
    RETRY(7),
}

data class Errand(
    val type: Action.Type,
    val pos: BlockPos? = null,
    val priority: Byte = 1,
) {
    companion object {
        val CODEC: Codec<Errand> =
            RecordCodecBuilder.create { instance ->
                instance
                    .group(
                        Codec.INT.fieldOf("type").forGetter { it.type.ordinal },
                        BlockPos.CODEC.optionalFieldOf("pos").forGetter { Optional.ofNullable(it.pos) },
                        Codec.BYTE.fieldOf("priority").forGetter { it.priority },
                    ).apply(instance) { type, posOpt, priority ->
                        Errand(Action.Type.values()[type], posOpt.orElse(null), priority)
                    }
            }
    }
}

sealed class Action {
    abstract val group: ErrandType

    abstract fun scan(
        ctrl: EntityController,
        pos: BlockPos?,
    ): Int

    open fun test(
        ctrl: EntityController,
        pos: BlockPos?,
    ) = StepResult.SUCCESS

    open fun exec(
        ctrl: EntityController,
        pos: BlockPos?,
    ) = StepResult.SUCCESS

    open fun eval(
        ctrl: EntityController,
        pos: BlockPos?,
    ) = StepResult.SUCCESS

    open fun redo(
        ctrl: EntityController,
        pos: BlockPos?,
    ) = StepResult.SUCCESS

    open fun stop(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult {
        ctrl.stopNavigation()
        ctrl.path = null
        return StepResult.SUCCESS
    }

    open val ticksToTest = 1
    open val ticksToExec = 1
    open val ticksToEval = 1

    open val radiusToAct = 4.0f
    open val radiusToSee = 4.0f

    open val speedModifier = 1.0
    open val pathReach = 0
    open val restCost = 0.0

    open val shouldLockTarget = false
    open val shouldLockFocus = true

    fun shouldMove(distance: Double) = distance > radiusToAct

    fun shouldLook(distance: Double) = distance < radiusToSee

    fun shouldTest(ticks: Int) = ticks >= ticksToTest

    fun shouldExec(ticks: Int) = ticks >= ticksToExec

    fun shouldEval(ticks: Int) = ticks >= ticksToEval

    // when adding actions, put at the end of of enum
    enum class Type(
        val instance: Action,
    ) {
        PICK(Pick),
        REACH(Reach),
        FOLLOW(Follow),
        ANALYZE(Analyze),
        SLEEP(Sleep),
        TILL(Till),
        PLANT(Plant),
        POWDER(Powder),
        HARVEST(Harvest),
        BREAK(Break()),
        DIG(Dig),
        MINE(Mine),
        CHOP(Chop),
        FISH(Fish),
        SIT(Sit),
        FLEE(Flee),
        EAT(Eat),
        COOK(Cook),
        SMELT(Smelt),
        REFILL(Refill),
        COLLECT(Collect),
        DRINK(Drink),
        STORE(Store),
        CHARGE(Charge),
        THROW(Throw),
        CAST(Cast),
        ATTACK(Attack),
        AIM(Aim),
        LOCK(Lock),
        WEAR(Wear),
        OPEN(Open),
        CLOSE(Close),
        DEFEND(Defend),
        DASH(Dash),
        SHEAR(Shear),
        LOOK(Look),
        INTERACT(Interact),
        TRADE(Trade),
        WANDER(Wander),
        STRAFE(Strafe),
        SWIM(Swim),
        REPAIR(Repair),
        YIELD(Yield),
        RIDE(Ride),
        ;

        fun get(): Action = instance
    }

    companion object {
        fun Boolean.to(mult: Int): Int = if (this) (1 * mult) else 0

        fun Boolean.toInt(): Int = if (this) 1 else 0
    }
}

open class Break : Action() {
    override val group = ErrandType.WORK

    override fun scan(
        ctrl: EntityController,
        pos: BlockPos?,
    ): Int = 9

    override fun exec(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult {
        val world = ctrl.world
        val state = world.getBlockState(pos)
        val hardness = state.getHardness(world, pos) * 100
        if (hardness == -1.0f) {
            ctrl.mainTicker = 1
        } else {
            val multiplier = ctrl.stackInMainHand.getMiningSpeedMultiplier(state)
            ctrl.mainTicker = (hardness / multiplier).toInt()
        }
        return StepResult.SUCCESS
    }

    override fun eval(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult {
        val state = ctrl.world.getBlockState(pos)
        if (ctrl.mainTicker % 5 == 4 || ctrl.mainTicker == 0) {
            ctrl.swingMainHand()
            ctrl.world.playSound(
                ctrl.entity,
                pos,
                state.getSoundGroup().getHitSound(),
                SoundCategory.BLOCKS,
                1.0f,
                1.0f,
            )
        }

        return if (ctrl.decayMainTicker() <= 0) {
            ctrl.world.breakBlock(pos, true)
            StepResult.SUCCESS
        } else {
            StepResult.CONTINUE
        }
    }

    override val shouldLockFocus = true
    override val restCost: Double = 0.8
}

object Dig : Break() {
    override fun scan(
        ctrl: EntityController,
        pos: BlockPos?,
    ): Int = ctrl.tryEquip(Action.Type.DIG, ItemPredicate.SHOVEL, EquipmentSlot.MAINHAND).to(8)

    override fun test(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult = if (BlockPredicate.SEDIMENT(ctrl.world, pos!!)) StepResult.SUCCESS else StepResult.CANCEL
}

object Mine : Break() {
    override fun scan(
        ctrl: EntityController,
        pos: BlockPos?,
    ): Int = ctrl.tryEquip(Action.Type.MINE, ItemPredicate.PICKAXE, EquipmentSlot.MAINHAND).to(8)

    override fun test(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult = if (BlockPredicate.STONE(ctrl.world, pos!!)) StepResult.SUCCESS else StepResult.CANCEL

    override val ticksToTest: Int = 10
    override val ticksToExec: Int = 10
    override val radiusToAct: Float = 16.0f
    override val radiusToSee: Float = 16.0f
    override val pathReach: Int = 4
}

object Chop : Break() {
    override fun scan(
        ctrl: EntityController,
        pos: BlockPos?,
    ): Int = ctrl.tryEquip(Action.Type.CHOP, ItemPredicate.AXE, EquipmentSlot.MAINHAND).to(8)

    override fun test(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult = if (BlockPredicate.LOG(ctrl.world, pos!!)) StepResult.SUCCESS else StepResult.CANCEL
}

object Harvest : Break() {
    override val group = ErrandType.WORK

    override fun scan(
        ctrl: EntityController,
        pos: BlockPos?,
    ): Int = 6

    override fun test(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult = if (BlockPredicate.HARVEST(ctrl.world, pos!!.up())) StepResult.SUCCESS else StepResult.CANCEL

    override val ticksToTest: Int = 5
    override val ticksToExec: Int = 10
}

object Till : Action() {
    override val group = ErrandType.WORK

    override fun scan(
        ctrl: EntityController,
        pos: BlockPos?,
    ): Int = ctrl.tryEquip(Action.Type.TILL, ItemPredicate.HOE, EquipmentSlot.MAINHAND).to(5)

    override fun test(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult = if (BlockPredicate.ARABLE(ctrl.world, pos!!)) StepResult.SUCCESS else StepResult.CANCEL

    override fun exec(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult {
        ctrl.swingMainHand()
        ctrl.damageStackInMainHand()
        ctrl.setBlock(pos!!, Blocks.FARMLAND.defaultState, SoundEvents.ITEM_HOE_TILL, 1.0f, 1.0f)
        return StepResult.SUCCESS
    }

    override val ticksToTest: Int = 10
    override val ticksToExec: Int = 10
    override val restCost: Double = 0.4
}

object Plant : Action() {
    override val group = ErrandType.WORK

    override fun scan(
        ctrl: EntityController,
        pos: BlockPos?,
    ): Int = ctrl.tryEquip(Action.Type.PLANT, ItemPredicate.PLANTABLE, EquipmentSlot.OFFHAND).to(6)

    override fun test(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult = if (BlockPredicate.OPEN_FARMLAND(ctrl.world, pos!!)) StepResult.SUCCESS else StepResult.CANCEL

    override fun exec(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult {
        val stack = ctrl.stackInOffHand
        return when (stack.item) {
            Items.WHEAT_SEEDS -> Blocks.WHEAT.defaultState
            Items.BEETROOT_SEEDS -> Blocks.BEETROOTS.defaultState
            Items.CARROT -> Blocks.CARROTS.defaultState
            Items.POTATO -> Blocks.POTATOES.defaultState
            else -> null
        }?.let { state ->
            val up = pos!!.up()
            ctrl.swingOffHand()
            ctrl.decrementStackInOffHand(1)
            ctrl.setBlock(up, state, SoundEvents.ITEM_CROP_PLANT, 1.0f, 1.0f)
            StepResult.SUCCESS
        } ?: StepResult.CANCEL
    }

    override val ticksToTest: Int = 10
    override val ticksToExec: Int = 10
    override val restCost: Double = 1.2
}

object Powder : Action() {
    override val group = ErrandType.WORK

    override fun scan(
        ctrl: EntityController,
        pos: BlockPos?,
    ): Int = ctrl.tryEquip(Action.Type.POWDER, ItemPredicate.FERTILIZER, EquipmentSlot.OFFHAND).to(5)

    override fun test(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult = if (!BlockPredicate.MATURE_CROP(ctrl.world, pos!!)) StepResult.SUCCESS else StepResult.CANCEL

    override fun exec(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult {
        val stack = ctrl.stackInOffHand
        BoneMealItem.useOnFertilizable(stack, ctrl.world, pos!!.up())
        // emit sound
        ctrl.swingMainHand()
        stack.decrement(1)
        return StepResult.SUCCESS
    }
}

object Fish : Action() {
    override val group = ErrandType.WORK

    override fun scan(
        ctrl: EntityController,
        pos: BlockPos?,
    ): Int = ctrl.tryEquip(Action.Type.FISH, ItemPredicate.FISHING_ROD, EquipmentSlot.MAINHAND).to(6)

    override fun test(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult {
        ctrl.notify = false
        return if (
            pos!!.bottom().all { BlockPredicate.WATER(ctrl.world, pos) }
        ) {
            StepResult.SUCCESS
        } else {
            StepResult.CANCEL
        }
    }

    override fun exec(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult {
        val world = ctrl.world
        ctrl.swingMainHand()
        val bobber = SimpleFishingBobberEntity(ctrl.entity, pos!!, world)
        bobber.setCondition({ ctrl.notify })
        ctrl.callback = { bobber.isClosed }
        world.spawnEntity(bobber)
        return StepResult.SUCCESS
    }

    override fun eval(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult {
        if (ctrl.nearbyHostile.isNotEmpty()) {
            return StepResult.CANCEL
        }
        if (ctrl.callback?.invoke() == true) {
            ctrl.notify = true
            ctrl.swingMainHand()
            ctrl.damageStackInMainHand()
            return StepResult.SUCCESS
        }
        return StepResult.CONTINUE
    }

    override fun stop(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult {
        ctrl.notify = true
        ctrl.swingMainHand()
        return super.stop(ctrl, pos)
    }

    override val shouldLockFocus: Boolean = true
    override val radiusToAct: Float = 72.0f
    override val radiusToSee: Float = 128.0f
    override val ticksToTest: Int = 10
    override val ticksToExec: Int = 20
    override val pathReach: Int = 4
    override val restCost: Double = 1.8
}

// add Ticker for body actions
object Sleep : Action() {
    override val group = ErrandType.BODY

    // TODO: make the value priority be relative to tiredness
    override fun scan(
        ctrl: EntityController,
        pos: BlockPos?,
    ): Int = (ctrl.tiredness >= 80 && ctrl.satiation >= 20 && !ctrl.isSleeping()).to(4)

    override fun test(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult = if (BlockPredicate.BED(ctrl.world, pos!!)) StepResult.SWAP else StepResult.CANCEL

    override fun exec(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult {
        ctrl.sleep(pos!!)
        return StepResult.SUCCESS
    }

    override fun eval(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult =
        when {
            (
                ctrl.tiredness <= 0f ||
                    ctrl.satiation <= 0f ||
                    ctrl.nearbyHostile.isEmpty() ||
                    ctrl.isMoving
            ) -> {
                StepResult.CANCEL
            }

            ctrl.entity.age % 4 == 0 -> {
                ctrl.addTiredness(-0.8f)
                StepResult.CONTINUE
            }

            else -> {
                StepResult.CONTINUE
            }
        }

    override fun stop(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult {
        ctrl.wakeUp()
        return super.stop(ctrl, pos)
    }

    override val pathReach: Int = 1

    private fun EntityController.wakeUp() {
        val entity = this.entity
        val world = entity.world as ServerWorld

        entity
            .getErrandPosition()
            .filter(world::isChunkLoaded)
            .ifPresent { pos ->
                val state = world.getBlockState(pos)
                if (state.block is BedBlock) {
                    val direction = state.get(BedBlock.FACING)
                    world.setBlockState(pos, state.with(BedBlock.OCCUPIED, false), Block.NOTIFY_ALL)

                    val wakePos =
                        BedBlock
                            .findWakeUpPosition(entity.type, world, pos, direction, entity.yaw)
                            .orElseGet {
                                val up = pos.up()
                                Vec3d(up.x + 0.5, up.y + 0.1, up.z + 0.5)
                            }

                    val diff = Vec3d.ofBottomCenter(pos).subtract(wakePos).normalize()
                    val f =
                        MathHelper.wrapDegrees(
                            (Math.atan2(diff.z, diff.x).toFloat() * 180f / Math.PI.toFloat()) - 90f,
                        )

                    entity.setPosition(wakePos.x, wakePos.y, wakePos.z)
                    entity.setYaw(f)
                    entity.setPitch(0f)
                }
            }

        val current = entity.pos
        entity.setPose(EntityPose.STANDING)
        entity.setPosition(current.x, current.y, current.z)
        entity.clearErrandPosition()
    }

    private fun EntityController.sleep(pos: BlockPos) {
        val entity = this.entity
        val world = entity.world as ServerWorld
        val state = world.getBlockState(pos)
        if (state.block is BedBlock) {
            world.setBlockState(pos, state.with(BedBlock.OCCUPIED, true), Block.NOTIFY_ALL)
        }

        entity.setPose(EntityPose.SLEEPING)
        entity.setPosition(pos.getX() + 0.5, pos.getY() + 0.6875, pos.getZ() + 0.5)
        entity.setErrandPosition(pos)
        entity.setVelocity(Vec3d.ZERO)
        entity.velocityDirty = true
    }

    private fun EntityController.isSleeping(): Boolean = this.entity.pose == EntityPose.SLEEPING
}

object Sit : Action() {
    override val group = ErrandType.BODY

    // TODO: make the value priority be relative to tiredness
    override fun scan(
        ctrl: EntityController,
        pos: BlockPos?,
    ): Int = (ctrl.tiredness >= 30 && !ctrl.isSitting()).to(3)

    override fun test(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult =
        pos?.let {
            if (BlockPredicate.SEAT(ctrl.world, it)) StepResult.SWAP else StepResult.CANCEL
        } ?: StepResult.CANCEL

    override fun exec(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult {
        pos?.let {
            ctrl.sit(it.up())
        }
        return StepResult.SUCCESS
    }

    override fun eval(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult =
        when {
            (
                ctrl.tiredness <= 0f ||
                    ctrl.satiation <= 0f ||
                    ctrl.nearbyHostile.isEmpty() ||
                    ctrl.isMoving ||
                    ctrl.random.nextFloat() < 0.001f
            ) -> {
                ctrl.getUp()
                StepResult.SUCCESS
            }

            ctrl.entity.age % 20 == 0 -> {
                ctrl.addTiredness(-0.002f)
                StepResult.CONTINUE
            }

            else -> {
                StepResult.CONTINUE
            }
        }

    override fun stop(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult {
        ctrl.getUp()
        return super.stop(ctrl, pos)
    }

    override val radiusToAct: Float = 3.5f
    override val radiusToSee: Float = -1.0f
    override val ticksToTest: Int = 5
    override val ticksToExec: Int = 5

    private fun EntityController.getUp() {
        val entity = this.entity
        val world = this.world

        entity
            .getErrandPosition()
            .filter(world::isChunkLoaded)
            .ifPresent { pos ->
                pos
                    .around(Direction.Axis.Y, false)
                    .firstOrNull {
                        entity.world.getBlockState(it).isAir && entity.world.getBlockState(it.add(0, -1, 0)).isAir
                    }?.let {
                        val v = Vec3d(it.getX().toDouble() + 0.5, it.getY() + 0.5, it.getZ() + 0.5)
                        entity.setPosition(v.x, v.y, v.z)
                    }
            }
        val vec3d = entity.getPos()
        entity.setPose(EntityPose.STANDING)
        entity.setPosition(vec3d.x, vec3d.y, vec3d.z)
        entity.clearErrandPosition()
    }

    private fun EntityController.sit(pos: BlockPos?) {
        val entity = this.entity

        entity.setPose(EntityPose.SITTING)
        pos?.let {
            val target = it.toCenterPos()
            entity.setErrandPosition(it)
            entity.setPosition(target.getX(), target.getY() - 0.5, target.getZ())
        } ?: run {
            val target = entity.getBlockPos().toCenterPos()
            entity.setErrandPosition(entity.getBlockPos())
            entity.setPosition(target.getX(), target.getY() - 0.5, target.getZ())
        }
        entity.setVelocity(Vec3d.ZERO)
        entity.velocityDirty = true
    }

    private fun EntityController.isSitting(): Boolean = this.entity.pose == EntityPose.SITTING
}

object Ride : Action() {
    override val group = ErrandType.BODY

    override fun scan(
        ctrl: EntityController,
        pos: BlockPos?,
    ): Int = (ctrl.target is HorseEntity && !ctrl.isRiding()).to(5)

    override fun exec(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult {
        ctrl.startRiding()
        return StepResult.SUCCESS
    }

    override fun eval(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult =
        if (!ctrl.isRiding()) {
            ctrl.stopRiding()
            StepResult.SUCCESS
        } else {
            StepResult.FAIL
        }

    private fun EntityController.isRiding() = this.entity.hasVehicle()

    private fun EntityController.stopRiding() = this.entity.stopRiding()

    private fun EntityController.startRiding() = this.entity.startRiding(this.target, true)
}

object Eat : Action() {
    override val group = ErrandType.DUAL

    // TODO: add checks that increases priority like health and hunger bar
    override fun scan(
        ctrl: EntityController,
        pos: BlockPos?,
    ): Int = ctrl.tryEquip(Action.Type.EAT, ItemPredicate.EDIBLE, EquipmentSlot.OFFHAND).to(3)

    override fun exec(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult {
        ctrl.entity.setCurrentHand(Hand.OFF_HAND)
        return StepResult.SUCCESS
    }

    override fun eval(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult =
        if (ctrl.isEating()) {
            StepResult.CONTINUE
        } else {
            val stack = ctrl.stackInOffHand
            stack.components.getOrDefault(DataComponentTypes.FOOD, null)?.let { satiation ->
                ctrl.addSatiation(satiation.nutrition().toFloat())
                stack.decrement(1)
            }
            StepResult.SUCCESS
        }

    override val radiusToAct: Float = 1024.0f
    override val radiusToSee: Float = -1.0f
    override val restCost: Double = 0.0

    private fun EntityController.isEating(): Boolean = this.entity.getActiveItem().getUseAction() == UseAction.EAT
}

object Drink : Action() {
    override val group = ErrandType.DUAL

    override fun scan(
        ctrl: EntityController,
        pos: BlockPos?,
    ): Int = ctrl.tryEquip(Action.Type.DRINK, ItemPredicate.POTION, EquipmentSlot.OFFHAND).to(3)

    override fun exec(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult {
        ctrl.entity.setCurrentHand(Hand.OFF_HAND)
        return StepResult.SUCCESS
    }

    override fun eval(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult =
        if (ctrl.isDrinking()) {
            StepResult.CONTINUE
        } else {
            StepResult.SUCCESS
        }

    override val radiusToAct: Float = 1024.0f
    override val radiusToSee: Float = -1.0f
    override val restCost: Double = 0.0

    private fun EntityController.isDrinking(): Boolean = this.entity.getActiveItem().getUseAction() == UseAction.DRINK
}

object Store : Action() {
    override val group = ErrandType.WORK

    override fun scan(
        ctrl: EntityController,
        pos: BlockPos?,
    ): Int = (ctrl.tryEquip(Action.Type.STORE, ItemPredicate.STORABLE, EquipmentSlot.OFFHAND)).to(5)

    override fun test(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult = if (BlockPredicate.STORAGE(ctrl.world, pos!!)) StepResult.SUCCESS else StepResult.CANCEL

    // TODO: find way to trigger open chest animation
    override fun exec(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult {
        ctrl.tryStore(pos!!)
        if (ctrl.stackInOffHand.isEmpty) ctrl.items[Action.Type.STORE] = -3
        return StepResult.SUCCESS
    }

    override fun eval(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult = StepResult.SUCCESS

    override val radiusToAct: Float = 4.0f
    override val radiusToSee: Float = 6.0f
    override val ticksToTest: Int = 10
    override val ticksToExec: Int = 5

    private fun EntityController.tryStore(pos: BlockPos): Boolean {
        val state = world.getBlockState(pos)
        val block = state.block as? ChestBlock ?: return false
        val chestEntity = world.getBlockEntity(pos) as? ChestBlockEntity ?: return false
        val inv: Inventory =
            ChestBlock
                .getInventory(block, state, world, pos, true)
                ?: return false

        val stack = stackInOffHand
        if (stack.isEmpty) return false
        val before = stack.count

        for (i in 0 until inv.size()) {
            val slot = inv.getStack(i)
            if (!slot.isEmpty && ItemStack.areItemsAndComponentsEqual(slot, stack)) {
                val space = slot.maxCount - slot.count
                if (space > 0) {
                    val toMove = minOf(space, stack.count)
                    slot.increment(toMove)
                    stack.decrement(toMove)
                    inv.setStack(i, slot)
                    if (stack.isEmpty) break
                }
            }
        }

        if (!stack.isEmpty) {
            for (i in 0 until inv.size()) {
                val slot = inv.getStack(i)
                if (slot.isEmpty) {
                    val toMove = minOf(stack.count, stack.maxCount)
                    val insert = stack.copy().also { it.count = toMove }
                    inv.setStack(i, insert)
                    stack.decrement(toMove)
                    if (stack.isEmpty) break
                }
            }
        }

        if (stack.count < before) {
            if (stack.isEmpty) {
                this.entity.setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY)
            }
            chestEntity.markDirty()
            val s = world.getBlockState(pos)
            world.updateListeners(pos, s, s, Block.NOTIFY_ALL)
            return true
        }

        return false
    }
}

object Cook : Action() {
    override val group = ErrandType.WORK

    override fun scan(
        ctrl: EntityController,
        pos: BlockPos?,
    ): Int = ctrl.tryEquip(Action.Type.COOK, ItemPredicate.COOKABLE, EquipmentSlot.OFFHAND).to(7)

    override fun test(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult = if (BlockPredicate.COOK(ctrl.world, pos!!)) StepResult.SUCCESS else StepResult.CANCEL

    override fun exec(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult {
        ctrl.tryCook(pos!!)
        if (ctrl.stackInOffHand.isEmpty) ctrl.items[Action.Type.COOK] = -3
        return StepResult.SUCCESS
    }

    override val radiusToAct: Float = 4.0f
    override val radiusToSee: Float = 6.0f
    override val ticksToTest: Int = 20
    override val ticksToExec: Int = 5

    fun EntityController.tryCook(pos: BlockPos): Boolean {
        val smoker = this.world.getBlockEntity(pos) as? SmokerBlockEntity ?: return false
        val stack = this.stackInOffHand
        if (stack.isEmpty) return false

        if (!smoker.getStack(0).isEmpty) return false

        val move = Math.min(stack.count, stack.maxCount)
        val insert = stack.copy().apply { count = move }
        smoker.setStack(0, insert)
        this.decrementStackInOffHand(move)
        return true
    }
}

object Smelt : Action() {
    override val group = ErrandType.WORK

    override fun scan(
        ctrl: EntityController,
        pos: BlockPos?,
    ): Int = ctrl.tryEquip(Action.Type.SMELT, ItemPredicate.SMELTABLE, EquipmentSlot.OFFHAND).to(7)

    override fun test(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult = if (BlockPredicate.SMELTER(ctrl.world, pos!!)) StepResult.SUCCESS else StepResult.CANCEL

    override fun exec(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult {
        ctrl.trySmelt(pos!!)
        if (ctrl.stackInOffHand.isEmpty) ctrl.items[Action.Type.SMELT] = -3
        return StepResult.SUCCESS
    }

    override val radiusToAct: Float = 4.0f
    override val radiusToSee: Float = 6.0f
    override val ticksToTest: Int = 20
    override val ticksToExec: Int = 5

    fun EntityController.trySmelt(pos: BlockPos): Boolean {
        val smoker = this.world.getBlockEntity(pos) as? BlastFurnaceBlockEntity ?: return false
        val stack = this.stackInOffHand
        if (stack.isEmpty) return false

        if (!smoker.getStack(0).isEmpty) return false

        val move = Math.min(stack.count, stack.maxCount)
        val insert = stack.copy().apply { count = move }
        smoker.setStack(0, insert)
        this.decrementStackInOffHand(move)
        return true
    }
}

object Refill : Action() {
    override val group = ErrandType.WORK

    override fun scan(
        ctrl: EntityController,
        pos: BlockPos?,
    ): Int = ctrl.tryEquip(Action.Type.REFILL, ItemPredicate.FUEL, EquipmentSlot.OFFHAND).to(8)

    override fun test(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult =
        if (BlockPredicate.COOK(ctrl.world, pos!!) ||
            BlockPredicate.SMELTER(ctrl.world, pos)
        ) {
            StepResult.SUCCESS
        } else {
            StepResult.CANCEL
        }

    override fun exec(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult {
        ctrl.tryRefill(pos!!)
        if (ctrl.stackInOffHand.isEmpty) ctrl.items[Action.Type.REFILL] = -3
        return StepResult.SUCCESS
    }

    override val radiusToAct: Float = 4.0f
    override val radiusToSee: Float = 6.0f
    override val ticksToTest: Int = 20
    override val ticksToExec: Int = 5

    fun EntityController.tryRefill(pos: BlockPos): Boolean {
        val smoker = this.world.getBlockEntity(pos) as? SmokerBlockEntity ?: return false
        val stack = this.stackInOffHand
        if (stack.isEmpty) return false
        // check if full is low
        if (!smoker.getStack(1).isEmpty) return false

        val move = Math.min(stack.count, stack.maxCount)
        val insert = stack.copy().apply { count = move }
        smoker.setStack(1, insert)
        this.decrementStackInOffHand(move)
        return true
    }
}

// will be used in smoker, blast furnace, chests
object Collect : Action() {
    override val group = ErrandType.WORK

    override fun scan(
        ctrl: EntityController,
        pos: BlockPos?,
    ): Int = 9

    override fun test(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult =
        if (BlockPredicate.COOK(ctrl.world, pos!!) ||
            BlockPredicate.SMELTER(ctrl.world, pos)
        ) {
            StepResult.SUCCESS
        } else {
            StepResult.CANCEL
        }

    override fun exec(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult {
        ctrl.tryCollect(pos!!)
        return StepResult.SUCCESS
    }

    override val radiusToAct: Float = 4.0f
    override val radiusToSee: Float = 8.0f
    override val ticksToTest: Int = 5
    override val ticksToExec: Int = 10

    fun EntityController.tryCollect(pos: BlockPos): Boolean {
        val smoker = this.world.getBlockEntity(pos) as? SmokerBlockEntity ?: return false

        val output = smoker.getStack(2)
        if (output.isEmpty) return false

        smoker.setStack(2, ItemStack.EMPTY)
        smoker.markDirty()

        val state = world.getBlockState(pos)
        world.updateListeners(pos, state, state, Block.NOTIFY_ALL)

        this.entity.pickStack(output.copy())
        return true
    }
}

object Shear : Action() {
    override val group = ErrandType.WORK

    override fun scan(
        ctrl: EntityController,
        pos: BlockPos?,
    ): Int {
        val target = ctrl.target
        return (
            target != null &&
                target is SheepEntity &&
                target.isShearable() &&
                ctrl.tryEquip(
                    Action.Type.SHEAR,
                    ItemPredicate.SHEARS,
                    EquipmentSlot.MAINHAND,
                )
        ).to(3)
    }

    // put this as eval
    override fun exec(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult {
        if (ctrl.tryShear()) ctrl.damageStackInMainHand()
        return StepResult.SUCCESS
    }

    override val shouldLockTarget: Boolean = true
    override val radiusToAct: Float = 3.8f
    override val radiusToSee: Float = 15.0f
    override val ticksToTest: Int = 10
    override val ticksToExec: Int = 5

    private fun EntityController.tryShear(): Boolean =
        (this.target as? SheepEntity)?.let { target ->
            val stack = this.stackInMainHand
            target.sheared(this.world as ServerWorld, SoundCategory.PLAYERS, stack)
            target.emitGameEvent(GameEvent.SHEAR, this.entity)
            true
        } ?: false
}

object Pick : Action() {
    override val group = ErrandType.IDLE

    override fun scan(
        ctrl: EntityController,
        pos: BlockPos?,
    ): Int = 3

    override fun eval(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult {
        val items =
            ctrl.world.getNonSpectatingEntities(
                ItemEntity::class.java,
                ctrl.entity.boundingBox.expand(1.toDouble(), 0.toDouble(), 1.toDouble()),
            )

        return if (!items.isEmpty()) {
            var match = false
            for (item in items) {
                if (!item.isRemoved &&
                    !item.stack.isEmpty &&
                    !item.cannotPickup() &&
                    ctrl.entity.canAcceptStack(item.stack)
                ) {
                    ctrl.entity.pickItem(item)
                    match = true
                    break
                }
            }
            if (match) StepResult.SUCCESS else StepResult.SUCCESS
        } else {
            StepResult.SUCCESS
        }
    }

    override val radiusToAct: Float = 2.5f
    override val radiusToSee: Float = 5.0f
    override val ticksToTest: Int = 10
    override val ticksToExec: Int = 15
    override val pathReach: Int = 0
}

object Reach : Action() {
    override val group = ErrandType.IDLE

    override fun scan(
        ctrl: EntityController,
        pos: BlockPos?,
    ): Int = 7

    override val radiusToAct: Float = 32.0f
    override val radiusToSee: Float = -1.0f
}

object Lock : Action() {
    override val group = ErrandType.IDLE

    override fun scan(
        ctrl: EntityController,
        pos: BlockPos?,
    ): Int = (ctrl.target is PlayerEntity).to(10)

    override fun test(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult =
        ctrl.target?.let { target ->
            when (target) {
                is PlayerEntity -> {
                    target.openHandledScreen(ctrl.entity)
                    target.incrementStat(Stats.TALKED_TO_VILLAGER)
                }
                else -> {}
            }
            StepResult.SUCCESS
        } ?: StepResult.CANCEL

    override fun eval(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult =
        when {
            ctrl.target == null || ctrl.nearbyHostile.isEmpty() -> {
                StepResult.CANCEL
            }

            ctrl.getDistanceTo() > 32.0f -> {
                StepResult.CANCEL
            }

            ctrl.callback?.invoke() ?: true -> {
                ctrl.target = null
                StepResult.SUCCESS
            }

            else -> {
                StepResult.FAIL
            }
        }

    override fun stop(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult {
        ctrl.target = null
        return StepResult.SUCCESS
    }

    override val radiusToAct: Float = 32.0f
    override val radiusToSee: Float = 16.0f

    override val pathReach: Int = 4
}

// TODO: add equipment preference, and create separate action to wear 'vanity'
object Wear : Action() {
    override val group = ErrandType.IDLE

    override fun scan(
        ctrl: EntityController,
        pos: BlockPos?,
    ): Int = (ctrl.tryEquip(Action.Type.WEAR, ItemPredicate.ARMOR, EquipmentSlot.MAINHAND)).to(2)

    override fun exec(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult {
        val entity = ctrl.entity
        val stack = ctrl.stackInMainHand
        val slot = entity.getPreferredEquipmentSlot(stack)
        val old = entity.getEquippedStack(slot)
        if (!old.isEmpty) {
            if (entity.inventory.canInsert(old)) {
                entity.inventory.addStack(old)
            } else {
                entity.dropStack(ctrl.world as ServerWorld, old)
            }
        }
        entity.equipStack(slot, stack)
        ctrl.stackInMainHand = ItemStack.EMPTY
        return StepResult.SUCCESS
    }

    override fun stop(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult {
        val entity = ctrl.entity
        val stack = ctrl.stackInMainHand
        val slot = entity.getPreferredEquipmentSlot(stack)
        val old = entity.getEquippedStack(slot)
        if (!old.isEmpty) {
            entity.inventory.addStack(old)
        }
        entity.equipStack(slot, stack)
        ctrl.stackInMainHand = ItemStack.EMPTY
        return StepResult.SUCCESS
    }

    override val radiusToAct: Float = 32.0f
    override val radiusToSee: Float = 16.0f

    override val ticksToTest: Int = 10
    override val ticksToExec: Int = 10

    override val restCost: Double = 0.0
    override val pathReach: Int = 4
}

// TODO: add reputation increase
object Analyze : Action() {
    override val group = ErrandType.IDLE

    override fun scan(
        ctrl: EntityController,
        pos: BlockPos?,
    ): Int = (ctrl.target is PlayerEntity && ctrl.stack != null).to(10)

    override fun test(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult =
        ctrl.stack?.let { new ->
            ctrl.state = State.OFFER
            val stack = ctrl.stackInMainHand
            ctrl.entity.equipStack(EquipmentSlot.MAINHAND, new.copy())
            ctrl.stack = stack
            StepResult.SUCCESS
        } ?: StepResult.CANCEL

    override fun exec(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult {
        ctrl.mainTicker = 20 + ctrl.random.nextInt(10)
        return StepResult.SUCCESS
    }

    override fun eval(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult =
        when {
            ctrl.target == null || !ctrl.nearbyHostile.isEmpty() -> {
                StepResult.CANCEL
            }

            ctrl.decayMainTicker() <= 0 -> {
                ctrl.state = State.NONE
                ctrl.target = null
                ctrl.stack?.let { old ->
                    val stack = ctrl.stackInMainHand.copy()
                    ctrl.entity.equipStack(EquipmentSlot.MAINHAND, old.copy())
                    if (!stack.isEmpty) ctrl.entity.pickStack(stack)
                }
                ctrl.stack = null
                StepResult.SUCCESS
            }

            else -> {
                StepResult.CONTINUE
            }
        }

    override fun stop(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult {
        ctrl.state = State.NONE
        ctrl.target = null
        ctrl.stack?.let { old ->
            val stack = ctrl.stackInMainHand
            ctrl.entity.equipStack(EquipmentSlot.MAINHAND, old)
            if (!stack.isEmpty) ctrl.entity.dropStack(ctrl.world as ServerWorld, stack)
        }
        ctrl.stack = null
        return StepResult.SUCCESS
    }

    override val radiusToAct: Float = 32.0f
    override val radiusToSee: Float = 16.0f

    override val restCost: Double = 0.0
    override val pathReach: Int = 4
}

object Wander : Action() {
    override val group = ErrandType.IDLE

    override fun scan(
        ctrl: EntityController,
        pos: BlockPos?,
    ): Int = 3

    override val radiusToAct: Float = 8.0f
    override val radiusToSee: Float = -1.0f
}

object Follow : Action() {
    override val group = ErrandType.IDLE

    override fun scan(
        ctrl: EntityController,
        pos: BlockPos?,
    ): Int = (ctrl.target is PlayerEntity).to(9)

    override fun eval(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult =
        when {
            ctrl.target == null || !ctrl.nearbyHostile.isEmpty() -> {
                StepResult.CANCEL
            }

            ctrl.getDistanceTo(ctrl.target) > 256.0f -> {
                StepResult.CANCEL
            }

            else -> {
                (ctrl.target as? PlayerEntity)?.let {
                    if (it.isSneaking) StepResult.SUCCESS else StepResult.CONTINUE
                } ?: StepResult.CANCEL
            }
        }

    override val radiusToAct: Float = 3.5f
    override val radiusToSee: Float = 12.0f
    override val ticksToTest: Int = 20
}

// TODO: give a ticker for each Provider
object Look : Action() {
    override val group = ErrandType.IDLE

    override fun scan(
        ctrl: EntityController,
        pos: BlockPos?,
    ): Int = (ctrl.target != null).to(3)

    override fun exec(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult {
        ctrl.mainTicker = 20 + ctrl.random.nextInt(120)
        return StepResult.SUCCESS
    }

    override fun eval(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult =
        when {
            ctrl.target == null || !ctrl.nearbyHostile.isEmpty() -> {
                StepResult.CANCEL
            }

            ctrl.getDistanceTo(ctrl.target) > 128.0f -> {
                StepResult.CANCEL
            }

            ctrl.decayMainTicker() <= 0 -> {
                StepResult.SUCCESS
            }

            !ctrl.hasErrands(ErrandType.DUAL) && ctrl.isLooking() -> {
                ctrl.expectedState = State.values().random()
                ctrl.pushErrand(Action.Type.INTERACT)
                StepResult.CONTINUE
            }

            else -> {
                StepResult.CONTINUE
            }
        }

    private fun EntityController.isLooking(): Boolean =
        (this.target as? AbstractVillagerEntity)?.let { target ->
            val entity = this.entity
            val dx = entity.x - target.x
            val dz = entity.z - target.z
            val angleToB = (Math.toDegrees(Math.atan2(dz, dx)) - 90 + 360) % 360
            val headYaw = (target.headYaw + 360) % 360
            val angleDiff = Math.abs(((angleToB - headYaw + 180) % 360) - 180)
            angleDiff <= 15f
        } ?: false

    private fun EntityController.isTalking(): Boolean =
        (this.target as? AbstractVillagerEntity)?.let { it.controller.target == this.entity } ?: false

    override val radiusToAct: Float = 64.0f
    override val radiusToSee: Float = 48.0f
    override val ticksToTest: Int = 5
    override val ticksToExec: Int = 5
    override val ticksToEval: Int = 5
    override val pathReach: Int = 4
}

// TODO: Implement this
object Trade : Action() {
    override val group = ErrandType.IDLE

    override fun scan(
        ctrl: EntityController,
        pos: BlockPos?,
    ): Int = 0

    override val shouldLockTarget: Boolean = true
    override val radiusToAct: Float = 128.0f
    override val radiusToSee: Float = 128.0f
    override val ticksToTest: Int = 5
    override val ticksToExec: Int = 5
    override val ticksToEval: Int = 10
    override val restCost: Double = 0.1
}

object Interact : Action() {
    override val group = ErrandType.DUAL

    override fun scan(
        ctrl: EntityController,
        pos: BlockPos?,
    ): Int = (ctrl.expectedState != State.NONE).to(3)

    override fun exec(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult {
        ctrl.state = ctrl.expectedState
        ctrl.mainTicker = 15 + ctrl.random.nextInt(10)
        return StepResult.SUCCESS
    }

    override fun eval(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult =
        when {
            ctrl.target == null || !ctrl.nearbyHostile.isEmpty() -> {
                StepResult.CANCEL
            }

            ctrl.decayMainTicker() <= 0 -> {
                ctrl.state = State.NONE
                ctrl.expectedState = State.NONE
                if (ctrl.random.nextInt(5) == 0) StepResult.REPEAT else StepResult.SUCCESS
                StepResult.SUCCESS
            }

            else -> {
                StepResult.CONTINUE
            }
        }

    override fun stop(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult {
        ctrl.expectedState = State.NONE
        ctrl.state = State.NONE
        return StepResult.SUCCESS
    }

    override val radiusToAct: Float = 48.0f
    override val radiusToSee: Float = 64.0f
    override val restCost: Double = 0.1
}

object Yield : Action() {
    override val group = ErrandType.DUAL

    override fun scan(
        ctrl: EntityController,
        pos: BlockPos?,
    ): Int = 5

    override fun exec(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult {
        // query loot table
        return StepResult.SUCCESS
    }

    override val radiusToAct: Float = 512.0f
    override val radiusToSee: Float = -1.0f
    override val ticksToExec: Int = 4
    override val restCost: Double = 0.12
}

object Repair : Action() {
    override val group = ErrandType.WORK

    override fun scan(
        ctrl: EntityController,
        pos: BlockPos?,
    ): Int = ctrl.tryEquip(Action.Type.REPAIR, ItemPredicate.REPAIRABLE, EquipmentSlot.OFFHAND).to(6)

    override fun eval(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult = StepResult.CANCEL

    override val radiusToAct: Float = 512.0f
    override val radiusToSee: Float = -1.0f
    override val ticksToExec: Int = 4
    override val restCost: Double = 0.04
}

object Swim : Action() {
    override val group = ErrandType.DUAL

    override fun scan(
        ctrl: EntityController,
        pos: BlockPos?,
    ): Int = (ctrl.entity.isTouchingWater()).to(8)

    override fun eval(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult {
        val entity = ctrl.entity
        return if (!entity.isSwimming()) {
            StepResult.SUCCESS
        } else {
            if (entity.random.nextFloat() < 0.8f) {
                entity.jumpControl.setActive()
            }
            StepResult.CONTINUE
        }
    }

    override val radiusToAct: Float = 512.0f
    override val radiusToSee: Float = -1.0f
    override val ticksToExec: Int = 4
    override val restCost: Double = 0.04
}

object Open : Action() {
    override val group = ErrandType.DUAL

    override fun scan(
        ctrl: EntityController,
        pos: BlockPos?,
    ): Int {
        val state = ctrl.world.getBlockState(pos)
        val block = state.getBlock()

        val canOpen =
            when (block) {
                is DoorBlock -> DoorBlock.canOpenByHand(ctrl.world, pos!!)
                is FenceGateBlock -> !state.get(FenceGateBlock.OPEN)
                else -> false
            }

        return canOpen.to(7)
    }

    override fun exec(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult {
        // TODO: use a mixin method for fence instead
        val state = ctrl.world.getBlockState(pos)
        val block = state.getBlock()
        when (block) {
            is DoorBlock -> {
                if (!block.isOpen(state)) {
                    block.setOpen(ctrl.entity, ctrl.world, state, pos, true)
                    ctrl.swingMainHand()
                }
            }
            is FenceGateBlock -> {
                val entity = ctrl.entity
                val direction = entity.horizontalFacing
                val fence =
                    if (state.get(FenceGateBlock.FACING) == direction.opposite) {
                        state.with(FenceGateBlock.FACING, direction)
                    } else {
                        state
                    }.with(FenceGateBlock.OPEN, true)

                ctrl.swingMainHand()
                val pitch = ctrl.random.nextFloat() * 0.1f + 0.9f
                ctrl.setBlock(pos!!, fence, SoundEvents.BLOCK_FENCE_GATE_OPEN, 1.0f, pitch)
                ctrl.world.emitGameEvent(entity, GameEvent.BLOCK_OPEN, pos)
            }
            else -> { }
        }
        return StepResult.SUCCESS
    }

    override fun eval(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult {
        val state = ctrl.world.getBlockState(pos!!)
        val block = state.getBlock()
        when (block) {
            is DoorBlock -> {
                val entity = ctrl.entity
                val direction = entity.horizontalFacing
                if (state.get(DoorBlock.FACING) == direction.opposite && pos != entity.blockPos) {
                    state.get(DoorBlock.FACING).getOpposite()
                } else {
                    state.get(DoorBlock.FACING)
                }
            }
            is FenceGateBlock -> {
                val entity = ctrl.entity
                val direction = entity.horizontalFacing
                if (state.get(FenceGateBlock.FACING) == direction.opposite) {
                    state.get(FenceGateBlock.FACING).getOpposite()
                } else {
                    state.get(FenceGateBlock.FACING)
                }
            }
            else -> {
                null
            }
        }?.let {
            ctrl.pushErrand(
                Action.Type.CLOSE,
                pos.offset(it),
            )
        }

        return StepResult.SUCCESS
    }

    override val radiusToAct: Float = 4.5f
    override val ticksToExec: Int = 5
    override val ticksToEval: Int = 5
}

object Close : Action() {
    override val group = ErrandType.DUAL

    override fun scan(
        ctrl: EntityController,
        pos: BlockPos?,
    ): Int = 6

    override fun exec(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult {
        val world = ctrl.world
        pos!!
            .neighbours()
            .find {
                BlockPredicate.BARRIER(world, it)
            }?.let { tpos ->
                val state = world.getBlockState(tpos)
                val block = state.getBlock()
                val entity = ctrl.entity
                when (block) {
                    is DoorBlock -> {
                        block.setOpen(entity, world, state, tpos, false)
                        ctrl.swingMainHand()
                    }
                    is FenceGateBlock -> {
                        if (state.get(FenceGateBlock.OPEN)) {
                            val fence = state.with(FenceGateBlock.OPEN, false)
                            ctrl.swingMainHand()
                            val pitch = world.random.nextFloat() * 0.1f + 0.9f
                            ctrl.setBlock(tpos, fence, SoundEvents.BLOCK_FENCE_GATE_CLOSE, 1.0f, pitch)
                            world.emitGameEvent(entity, GameEvent.BLOCK_CLOSE, tpos)
                        }
                    }
                }
            }
        return StepResult.SUCCESS
    }

    override val radiusToAct: Float = 2.5f
    override val ticksToExec = 5
    override val ticksToTest = 5
}

object Strafe : Action() {
    override val group = ErrandType.DUAL

    override fun scan(
        ctrl: EntityController,
        pos: BlockPos?,
    ): Int = (ctrl.target != null && !ctrl.nearbyHostile.isEmpty()).to(8)

    override fun eval(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult {
        // push fail, that triggers stop
        return when {
            ctrl.target == null || !ctrl.nearbyHostile.isEmpty() -> {
                StepResult.CANCEL
            }

            ctrl.getDistanceTo() > 5.0f -> {
                StepResult.SUCCESS
            }

            else -> {
                ctrl.entity.moveControl.strafeTo(-0.5f, 0.0f)
                StepResult.FAIL
            }
        }
    }

    override val shouldLockTarget: Boolean = true
}

open class Comflict : Action() {
    override val group = ErrandType.FRAY

    override fun scan(
        ctrl: EntityController,
        pos: BlockPos?,
    ): Int = (!ctrl.nearbyHostile.isEmpty() && ctrl.isEquipped()).toInt()

    open fun EntityController.isEquipped(): Boolean = false

    override val shouldLockTarget: Boolean = true
}

object Flee : Comflict() {
    override fun scan(
        ctrl: EntityController,
        pos: BlockPos?,
    ): Int = super.scan(ctrl, pos) * 9

    override fun eval(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult =
        when {
            ctrl.target == null || ctrl.nearbyHostile.isEmpty() -> {
                StepResult.CANCEL
            }

            else -> {
                StepResult.REPEAT
            }
        }

    override fun redo(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult {
        val target = ctrl.target
        return if (target != null && !ctrl.nearbyHostile.isEmpty()) {
            val range = (16 - Math.sqrt(ctrl.getDistanceTo()) / 2).coerceIn(4.0, 16.0).toInt()

            BlockUtils.findFleeBlock(ctrl.entity, target, range)?.let { fpos ->
                ctrl.pushErrand(Action.Type.FLEE, fpos)
                StepResult.SUCCESS
            } ?: StepResult.FAIL
        } else {
            StepResult.FAIL
        }
    }

    override fun EntityController.isEquipped() = true

    override val radiusToAct: Float = 4.0f
    override val radiusToSee: Float = -1.0f
    override val speedModifier: Double = 1.33
    override val pathReach: Int = 1
}

// map this to a shield
object Dash : Comflict() {
    override fun scan(
        ctrl: EntityController,
        pos: BlockPos?,
    ): Int = super.scan(ctrl, pos) * 9

    override fun eval(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult =
        when {
            ctrl.target == null || !ctrl.nearbyHostile.isEmpty() ->
                StepResult.CANCEL

            ctrl.getDistanceTo() > 8.0f -> {
                StepResult.CANCEL
            }

            else -> {
                ctrl.tryKnockback()
                StepResult.SUCCESS
            }
        }

    private fun EntityController.tryKnockback() {
        val entity = this.entity
        this.target?.let { target ->
            val dx = target.x - entity.x
            val dz = target.z - entity.z
            val dist = Math.sqrt(dx * dx + dz * dz).takeIf { it > 0.0 } ?: 0.2

            target.takeKnockback(
                1.0,
                -(dx / dist),
                -(dz / dist),
            )
        }
    }

    override val radiusToAct: Float = 3.0f
    override val radiusToSee: Float = 32.0f
    override val speedModifier: Double = 1.33
    override val pathReach: Int = 3
}

object Throw : Comflict() {
    override fun scan(
        ctrl: EntityController,
        pos: BlockPos?,
    ): Int = super.scan(ctrl, pos) * 9

    override fun eval(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult =
        when {
            ctrl.target == null || !ctrl.nearbyHostile.isEmpty() ->
                StepResult.SUCCESS

            ctrl.getDistanceTo() < 8.0f ->
                StepResult.SUCCESS

            else -> {
                val stack = ctrl.stackInMainHand
                if (ctrl.tryThrow()) stack.decrement(1)
                StepResult.SUCCESS
            }
        }

    override fun EntityController.isEquipped() = this.tryEquip(Action.Type.THROW, ItemPredicate.SPLASH_POTION, EquipmentSlot.MAINHAND)

    override val radiusToAct: Float = 125.0f
    override val radiusToSee: Float = 225.0f
    override val speedModifier: Double = 1.10
    override val pathReach: Int = 3

    private fun EntityController.tryThrow(): Boolean {
        val target = this.target
        if (target == null) return false
        val entity = this.entity
        val vel = target.velocity
        val d = target.x + vel.x - entity.x
        val e = target.eyeY - 1.1f - entity.y
        val f = target.z + vel.z - entity.z
        val g = Math.sqrt(d * d + f * f)

        val stack = this.stackInMainHand

        ProjectileEntity.spawnWithVelocity(
            ::SplashPotionEntity,
            (entity.world as ServerWorld),
            stack,
            entity,
            d,
            e + g * 0.2,
            f,
            0.75f,
            8.0f,
        )

        entity.world.playSound(
            null,
            entity.x,
            entity.y,
            entity.z,
            SoundEvents.ENTITY_WITCH_THROW,
            SoundCategory.HOSTILE,
            1.0f,
            0.8f + entity.world.random.nextFloat() * 0.4f,
        )
        return true
    }
}

object Cast : Comflict() {
    override fun scan(
        ctrl: EntityController,
        pos: BlockPos?,
    ): Int = super.scan(ctrl, pos) * 9

    override fun exec(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult {
        ctrl.state = State.CAST
        ctrl.mainTicker = 10
        return StepResult.SUCCESS
    }

    override fun eval(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult =
        when {
            ctrl.target == null || ctrl.nearbyHostile.isEmpty() -> {
                StepResult.CANCEL
            }

            ctrl.decayMainTicker() <= 0 -> {
                ctrl.mainTicker = 5
                ctrl.tryConjure()
                if (ctrl.random.nextFloat() > 0.5) StepResult.CONTINUE else StepResult.CANCEL
            }

            else -> {
                StepResult.CONTINUE
            }
        }

    override fun stop(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult {
        ctrl.state = State.NONE
        return super.stop(ctrl, pos)
    }

    private fun EntityController.tryConjure(): Boolean {
        val target = this.target
        if (target == null) return false
        val entity = this.entity
        val maxY = Math.max(target.y, entity.y) + 1.0

        val yaw =
            Math
                .atan2(
                    (target.z - entity.z).toDouble(),
                    (target.x - entity.x).toDouble(),
                ).toFloat()

        val world = this.world
        var blockPos = target.blockPos
        var found = false
        var heightOffset = 0.0

        do {
            val below = blockPos.down()
            val state = world.getBlockState(below)
            if (state.isSideSolidFullSquare(world, below, Direction.UP)) {
                if (!world.isAir(blockPos)) {
                    val shape =
                        world
                            .getBlockState(blockPos)
                            .getCollisionShape(world, blockPos)
                    if (!shape.isEmpty) {
                        heightOffset = shape.getMax(Direction.Axis.Y)
                    }
                }
                found = true
                break
            }
            blockPos = below
        } while (blockPos.y >= Math.floor(maxY).toInt() - 1)

        if (found) {
            world.spawnEntity(
                EvokerFangsEntity(
                    world,
                    target.blockPos.x.toDouble(),
                    blockPos.y + heightOffset,
                    target.blockPos.z.toDouble(),
                    yaw,
                    0,
                    entity,
                ),
            )
            return true
        }
        return false
    }

    override val radiusToAct: Float = 125.0f
    override val radiusToSee: Float = 225.0f
    override val ticksToTest: Int = 5
    override val ticksToExec: Int = 5
    override val ticksToEval: Int = 10
    override val speedModifier: Double = 1.10
    override val pathReach: Int = 3
}

object Attack : Comflict() {
    override fun scan(
        ctrl: EntityController,
        pos: BlockPos?,
    ): Int = super.scan(ctrl, pos) * 8

    override fun eval(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult =
        when {
            ctrl.target == null || ctrl.nearbyHostile.isEmpty() ->
                StepResult.CANCEL

            ctrl.getDistanceTo() > 4.5f ->
                StepResult.CONTINUE

            else -> {
                ctrl.swingMainHand()
                if (ctrl.tryAttack()) ctrl.damageStackInMainHand()
                if (ctrl.target?.isAlive ?: false) StepResult.REPEAT else StepResult.SUCCESS
            }
        }

    override fun redo(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult {
        ctrl.pushErrand(Action.Type.ATTACK)
        return StepResult.SUCCESS
    }

    override fun EntityController.isEquipped(): Boolean = this.tryEquip(Action.Type.ATTACK, ItemPredicate.SWORD, EquipmentSlot.MAINHAND)

    private fun EntityController.tryAttack(): Boolean {
        val target = this.target
        if (target == null) return false

        val entity = this.entity
        val world = this.world as ServerWorld
        var damage = entity.getAttributeValue(EntityAttributes.ATTACK_DAMAGE).toFloat()

        val stack = this.stackInMainHand

        val damageSource =
            stack.item
                .getDamageSource(entity)
                ?: entity.getDamageSources().mobAttack(entity)

        damage = EnchantmentHelper.getDamage(world, stack, target, damageSource, damage)
        damage += stack.item.getBonusAttackDamage(target, damage, damageSource)

        target.damage(world, damageSource, damage)
        val f = entity.getAttributeValue(EntityAttributes.ATTACK_KNOCKBACK)
        val knockback = EnchantmentHelper.modifyKnockback(world, stack, target, damageSource, f.toFloat())
        if (knockback > 0f) {
            val radians = (entity.yaw.toDouble() * (Math.PI / 180.0)).toFloat()
            target.takeKnockback(
                (knockback * 0.5f).toDouble(),
                MathHelper.sin(radians).toDouble(),
                -MathHelper.cos(radians).toDouble(),
            )
            val vel = entity.velocity
            entity.velocity = vel.multiply(0.6, 1.0, 0.6)
        }

        stack.postHit(target, entity)

        EnchantmentHelper.onTargetDamaged(world, target, damageSource)
        return true
    }

    override val speedModifier: Double = 1.33
    override val radiusToAct: Float = 4.5f
    override val radiusToSee: Float = 64.0f
    override val ticksToEval: Int = 6
    override val pathReach: Int = 1
}

object Defend : Comflict() {
    override fun scan(
        ctrl: EntityController,
        pos: BlockPos?,
    ): Int = (super.scan(ctrl, pos) * 9)

    override fun test(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult {
        ctrl.entity.setCurrentHand(Hand.OFF_HAND)
        ctrl.mainTicker = 15
        return StepResult.SUCCESS
    }

    override fun eval(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult =
        when {
            ctrl.target == null || ctrl.nearbyHostile.isEmpty() -> {
                StepResult.CANCEL
            }

            else -> {
                if (ctrl.decayMainTicker() <= 0) {
                    ctrl.entity.stopUsingItem()
                    StepResult.SUCCESS
                } else {
                    StepResult.CONTINUE
                }
            }
        }

    override fun stop(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult {
        ctrl.entity.stopUsingItem()
        return StepResult.SUCCESS
    }

    override fun EntityController.isEquipped() = this.tryEquip(Action.Type.DEFEND, ItemPredicate.SHIELD, EquipmentSlot.OFFHAND)

    override val speedModifier: Double = 1.25
    override val radiusToAct: Float = 12.0f
    override val ticksToExec: Int = 3
    override val ticksToTest: Int = 2
    override val pathReach: Int = 2
}

open class Ranged : Comflict() {
    protected fun EntityController.tryShoot(isCrossbow: Boolean): Boolean {
        val target = this.target
        if (target == null) return false
        val entity = this.entity
        val stack = this.stackInMainHand

        val arrowStack = (Items.ARROW as ArrowItem).defaultStack.copyWithCount(1)
        val projectile = ArrowEntity(entity.world, entity, arrowStack, stack)
        projectile.applyDamageModifier(1.02f)
        if (entity.random.nextFloat() < 0.05f) {
            projectile.isCritical = true
        }

        if (isCrossbow) projectile.setSound(SoundEvents.ITEM_CROSSBOW_HIT)

        val dx = target.x - entity.x
        val dz = target.z - entity.z
        val horizDist = Math.sqrt(dx * dx + dz * dz)
        val dy = target.getBodyY(0.3333333) - projectile.y + horizDist * 0.2f

        val base = Vec3d(dx, dy, dz).toVector3f().normalize()

        var axis = Vector3f(base).cross(Vector3f(0f, 0f, 1f))
        if (axis.lengthSquared() <= 1e-7f) {
            axis =
                Vector3f(base)
                    .cross(entity.getOppositeRotationVector(1.0f).toVector3f())
        }

        val sprayAngle = 1.0f * (Math.PI.toFloat() / 180f)
        val spreadBase = base.rotateAxis(0f, axis.x, axis.y, axis.z)
        val finalDir = base.rotateAxis(sprayAngle, spreadBase.x, spreadBase.y, spreadBase.z)

        val speed = 1.6f
        val inaccuracy = 14 - entity.world.difficulty.id * 4.0f
        projectile.setVelocity(
            finalDir.x.toDouble(),
            finalDir.y.toDouble(),
            finalDir.z.toDouble(),
            speed,
            inaccuracy,
        )

        entity.world.spawnEntity(projectile)
        entity.playSound(
            SoundEvents.ITEM_CROSSBOW_SHOOT,
            1.0f,
            1.0f / (entity.random.nextFloat() * 0.4f + 0.8f),
        )
        return true
    }
}

object Aim : Ranged() {
    // TODO: make arrow items be prefered in slot 0
    override fun scan(
        ctrl: EntityController,
        pos: BlockPos?,
    ): Int = (super.scan(ctrl, pos) * 9)

    override fun exec(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult {
        ctrl.entity.setCurrentHand(Hand.MAIN_HAND)
        ctrl.mainTicker = 10
        return StepResult.SUCCESS
    }

    override fun eval(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult {
        when {
            ctrl.target == null || ctrl.nearbyHostile.isEmpty() -> {
                return StepResult.CANCEL
            }

            ctrl.getDistanceTo() < 7.0f ->
                return StepResult.CANCEL

            else -> {
                val entity = ctrl.entity
                val ready = entity.getItemUseTime() >= 25

                if (ready && ctrl.decayMainTicker() <= 0) {
                    entity.stopUsingItem()
                    if (ctrl.tryShoot(false)) ctrl.damageStackInMainHand()
                    return StepResult.SUCCESS
                } else {
                    return StepResult.CONTINUE
                }
            }
        }
    }

    override fun stop(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult {
        ctrl.entity.stopUsingItem()
        return super.stop(ctrl, pos)
    }

    override fun EntityController.isEquipped(): Boolean = this.tryEquip(Action.Type.AIM, ItemPredicate.BOW, EquipmentSlot.MAINHAND)

    override val radiusToAct: Float = 128.0f
    override val radiusToSee: Float = 128.0f
}

object Charge : Ranged() {
    override fun scan(
        ctrl: EntityController,
        pos: BlockPos?,
    ): Int = super.scan(ctrl, pos) * 8

    override fun test(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult {
        val stack = ctrl.stackInMainHand
        if (!CrossbowItem.isCharged(stack)) {
            ctrl.entity.setCurrentHand(Hand.MAIN_HAND)
            ctrl.mainTicker = 20 + ctrl.random.nextInt(10)
            stack.set(DataComponentTypes.CHARGED_PROJECTILES, ChargedProjectilesComponent.DEFAULT)
        } else {
            ctrl.mainTicker = 5 + ctrl.random.nextInt(5)
        }
        return StepResult.SUCCESS
    }

    override fun eval(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult =
        when {
            ctrl.target == null || ctrl.nearbyHostile.isEmpty() -> {
                StepResult.CANCEL
            }

            ctrl.getDistanceTo() < 18.0f ->
                StepResult.CANCEL

            else -> {
                val entity = ctrl.entity
                val stack = ctrl.stackInMainHand
                val pullTime = entity.itemUseTime >= CrossbowItem.getPullTime(entity.activeItem, entity) + 5

                if (entity.isUsingItem) {
                    if (pullTime) {
                        entity.stopUsingItem()
                        val arrow = (Items.ARROW as ArrowItem).defaultStack.copyWithCount(1)
                        stack.set(DataComponentTypes.CHARGED_PROJECTILES, ChargedProjectilesComponent.of(arrow))
                    }
                    StepResult.CONTINUE
                } else {
                    if (ctrl.decayMainTicker() <= 0) {
                        if (ctrl.tryShoot(true)) ctrl.damageStackInMainHand()
                        stack.set(DataComponentTypes.CHARGED_PROJECTILES, ChargedProjectilesComponent.DEFAULT)
                        StepResult.SUCCESS
                    } else {
                        StepResult.CONTINUE
                    }
                }
            }
        }

    override fun stop(
        ctrl: EntityController,
        pos: BlockPos?,
    ): StepResult {
        ctrl.entity.stopUsingItem()
        val stack = ctrl.stackInMainHand
        stack.set(DataComponentTypes.CHARGED_PROJECTILES, ChargedProjectilesComponent.DEFAULT)
        return super.stop(ctrl, pos)
    }

    override fun EntityController.isEquipped() = this.tryEquip(Action.Type.CHARGE, ItemPredicate.CROSSBOW, EquipmentSlot.MAINHAND)

    override val ticksToExec: Int = 10
    override val ticksToTest: Int = 5
    override val radiusToAct: Float = 175.0f
    override val radiusToSee: Float = 175.0f
}
