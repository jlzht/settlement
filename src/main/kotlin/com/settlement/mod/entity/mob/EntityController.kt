package com.settlement.mod.entity.mob

import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult
import com.mojang.serialization.codecs.RecordCodecBuilder
import com.settlement.mod.action.Action
import com.settlement.mod.action.Errand
import com.settlement.mod.action.StepResult
import com.settlement.mod.block.BlockPredicate
import com.settlement.mod.profession.Profession
import com.settlement.mod.structure.Structure
import com.settlement.mod.util.BlockUtils
import com.settlement.mod.util.neighbours
import com.settlement.mod.world.SettlementAccessor
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.entity.Entity
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.ItemEntity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.ai.pathing.Path
import net.minecraft.entity.mob.HostileEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvent
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.random.Random
import java.util.PriorityQueue
import java.util.function.Supplier

enum class ErrandType { FRAY, WORK, IDLE, BODY, DUAL }

enum class ErrandSource { HOME, WORK, FREE }

enum class Key { ALOC, SELF, HOME, WORK, FREE }

enum class State {
    NONE,
    DISAGREE,
    AGREE,
    TALK,
    OFFER,
    SWEAT,
    GREET,
    CAST,
}

sealed class Executor(
    private val peek: EntityController.() -> Errand?,
    private val exec: ErrandExecutor.(EntityController) -> Unit,
) {
    fun push(
        ex: ErrandExecutor,
        ctrl: EntityController,
    ) {
        val errand = ctrl.peek() ?: return

        val action = errand.type.get()
        val pos = errand.pos

        if (ex.action != action || ex.pos != pos) {
            ex.action?.stop(ctrl, ex.pos)
            ex.clear()
            ex.action = action
            ex.pos = pos
        }

        ex.action ?: return

        exec(ex, ctrl)
    }
}

object PositionExecutor : Executor(
    peek = EntityController::peekMain,
    exec = { ctrl ->
        val distance = ctrl.getDistanceTo(pos)
        val action = action!!
        if (action.shouldLook(distance)) {
            pos?.let {
                ctrl.entity.getLookControl().lookAt(it.toCenterPos())
            } ?: ctrl.target?.let { ctrl.entity.getLookControl().lookAt(it) }
        } else if (!action.shouldLockFocus) {
            ctrl.target?.let {
                if (ctrl.freeLooking) ctrl.entity.getLookControl().lookAt(it)
            }
        }

        if (action.shouldMove(distance)) {
            if (ctrl.navigation.isIdle()) {
                ctrl.path?.let { ctrl.navigation.startMovingAlong(it, 1.0) }
                if (
                    distance > action.pathReach &&
                    --giveUpCounter <= 0
                ) {
                    giveUpCounter = 80
                    ctrl.popMain()
                } else {
                    val target = ctrl.target
                    if (ctrl.path == null || ctrl.path!!.isFinished() == true) {
                        when {
                            pos != null -> ctrl.path = ctrl.createPathTo(pos!!, action.pathReach)
                            target != null -> ctrl.path = ctrl.createPathTo(target, action.pathReach)
                        }
                    }
                }
            }
        } else {
            ctrl.stopNavigation()
            giveUpCounter = 80
            tickTested()
        }
    },
)

object LinkableExecutor : Executor(
    peek = EntityController::peekBody,
    exec = { ctrl ->
        val distance = ctrl.getDistanceTo(pos)
        val action = action!!
        if (!action.shouldMove(distance)) {
            tickTested()
        }
    },
)

object ParallelExecutor : Executor(
    peek = EntityController::peekDual,
    exec = { ctrl ->
        val action = action!!
        val distance = ctrl.getDistanceTo(pos)
        if (!action.shouldMove(distance)) {
            giveUpCounter = 800
            tickTested()
        } else if (--giveUpCounter <= 0) {
            ctrl.popDual()
            clear()
        }
    },
)

class ErrandExecutor(
    private val ticker: Executor,
    var action: Action? = null,
    var pos: BlockPos? = null,
    var giveUpCounter: Int = 0,
) {
    private enum class State { PENDING, TESTED, COMPLETED }

    private val ticks = IntArray(State.values().size)
    private var status = State.PENDING

    fun clear() {
        status = State.PENDING
        ticks.fill(0)
        action = null
        pos = null
    }

    fun tickTested() {
        ticks[State.PENDING.ordinal]++
    }

    fun execErrand(ctrl: EntityController) {
        ticker.push(this, ctrl)

        action?.let {
            val speed =
                when {
                    ctrl.entity.isUsingItem -> 0.5
                    ctrl.satiation >= 20f -> it.speedModifier
                    else -> 0.9
                }
            ctrl.setSpeed(speed)

            when (status) {
                State.PENDING -> handlePending(ctrl, it)
                State.TESTED -> handleTested(ctrl, it)
                State.COMPLETED -> handleCompleted(ctrl, it)
            }
        }
    }

    private fun handlePending(
        ctrl: EntityController,
        action: Action,
    ) {
        if (action.shouldTest(ticks[State.PENDING.ordinal])) {
            when (action.test(ctrl, pos)) {
                StepResult.SUCCESS -> status = State.TESTED
                StepResult.CANCEL -> finish(ctrl, action)
                StepResult.SWAP -> {
                    ctrl.offerToBody()
                    clear()
                }
                else -> { /* CONTINUE */ }
            }
        }
        ctrl.lockTarget = action.shouldLockTarget
    }

    private fun handleTested(
        ctrl: EntityController,
        action: Action,
    ) {
        if (action.shouldExec(ticks[State.TESTED.ordinal])) {
            when (action.exec(ctrl, pos)) {
                StepResult.SUCCESS -> status = State.COMPLETED
                StepResult.CANCEL -> finish(ctrl, action)
                else -> { /* CONTINUE */ }
            }
        } else {
            ticks[status.ordinal]++
        }
    }

    private fun handleCompleted(
        ctrl: EntityController,
        action: Action,
    ) {
        if (action.shouldEval(ticks[State.COMPLETED.ordinal])) {
            when (action.eval(ctrl, pos)) {
                StepResult.REPEAT -> {
                    finish(ctrl, action)
                    action.redo(ctrl, pos)
                }
                StepResult.SUCCESS -> {
                    finish(ctrl, action)
                }
                StepResult.FAIL -> {
                    finish(ctrl, action)
                }
                StepResult.CANCEL -> {
                    finish(ctrl, action)
                    action.stop(ctrl, pos)
                }
                else -> { /* CONTINUE */ }
            }
        } else {
            ticks[status.ordinal]++
        }
    }

    private fun finish(
        ctrl: EntityController,
        action: Action,
    ) {
        ctrl.addTiredness(action.restCost.toFloat())
        if (action.group == ErrandType.DUAL) {
            ctrl.popDual()
        } else if (action.group == ErrandType.BODY) {
            ctrl.popBody()
        } else {
            ctrl.stopNavigation()
            ctrl.path = null
            ctrl.popMain()
        }
        ctrl.lockTarget = false
        clear()
    }
}

interface Producer {
    fun test(ctrl: EntityController): Boolean = true

    fun exec(ctrl: EntityController)

    val interval: Int
    val maxNoise: Int
}

object StructureAccessProducer : Producer {
    override val interval: Int = 220
    override val maxNoise: Int = 80

    override fun exec(ctrl: EntityController) {
        // if entity is not fighting or sleeping it should look for these actions
        if (!ctrl.hasErrands(ErrandType.FRAY) &&
            !ctrl.hasErrands(ErrandType.WORK) &&
            !ctrl.containsErrand(Action.Type.SLEEP)
        ) {
            // if it has no ALOC key (settlement to live in, tries to visit and attach)
            if (!ctrl.hasKey(Key.ALOC)) {
                SettlementAccessor.visitSettlement(ctrl)
                SettlementAccessor.findSettlementToAttach(ctrl)
            } else {
                // if the entity has a settlement, it tries to do actions based on its energy level
                if (ctrl.profession.home != Structure.Type.NONE &&
                    (ctrl.tiredness >= 80 || ctrl.entity.canSleep()) &&
                    !ctrl.containsErrand(Action.Type.SIT)
                ) {
                    if (!ctrl.hasKey(Key.HOME)) {
                        SettlementAccessor.findStructureToAttach(ctrl, ctrl.profession.home)
                    } else if (!ctrl.hasProvider(ErrandSource.HOME)) {
                        SettlementAccessor.getStructureToAttach(ctrl, ErrandSource.HOME)
                    } else {
                        ctrl.getProviderErrands(ErrandSource.HOME, Key.SELF)?.forEach { errand ->
                            ctrl.pushErrand(errand.type, errand.pos)
                        }
                    }
                } else if (ctrl.profession.work != Structure.Type.NONE && ctrl.tiredness <= 60) {
                    if (!ctrl.hasKey(Key.WORK)) {
                        SettlementAccessor.findStructureToAttach(ctrl, ctrl.profession.work)
                    } else if (!ctrl.hasProvider(ErrandSource.WORK)) {
                        SettlementAccessor.getStructureToAttach(ctrl, ErrandSource.WORK)
                    } else {
                        ctrl.getProviderErrands(ErrandSource.WORK, Key.SELF)?.forEach { errand ->
                            ctrl.pushErrand(errand.type, errand.pos)
                        }
                    }
                } else if (ctrl.profession.free != Structure.Type.NONE) {
                    if (!ctrl.hasKey(Key.FREE)) {
                        SettlementAccessor.findStructureToAttach(ctrl, ctrl.profession.free)
                    } else if (!ctrl.hasProvider(ErrandSource.FREE)) {
                        SettlementAccessor.getStructureToAttach(ctrl, ErrandSource.FREE)
                    } else {
                        ctrl.getProviderErrands(ErrandSource.FREE, Key.SELF)?.forEach { errand ->
                            ctrl.pushErrand(errand.type, errand.pos)
                        }
                    }
                }
            }
        }
    }
}

object HostileHandlerProducer : Producer {
    override val interval: Int = 1
    override val maxNoise: Int = 1

    override fun exec(ctrl: EntityController) {
        (ctrl.entity.getRecentDamageSource()?.attacker as? LivingEntity)?.let { attacker ->
            val dist = ctrl.getDistanceTo(attacker)
            val selected = ctrl.target?.takeIf { ctrl.getDistanceTo(it) < dist } ?: attacker
            if (selected.isAlive) {
                ctrl.target = selected
                ctrl.switchToHostile(selected)
                if (!ctrl.hasErrands(ErrandType.FRAY)) {
                    ctrl.profession.hostileHandler(ctrl)
                }
                return
            }
        }

        if (ctrl.lockTarget) return

        if (!ctrl.hasErrands(ErrandType.FRAY)) {
            ctrl.nearbyHostile
                .minWithOrNull(compareBy({ ctrl.getDistanceTo(it) }))
                ?.let { hostile ->
                    ctrl.target = hostile
                    ctrl.profession.hostileHandler(ctrl)
                    return
                }
        }
    }
}

object NeutralHandlerProducer : Producer {
    override val interval: Int = 40
    override val maxNoise: Int = 20

    override fun exec(ctrl: EntityController) {
        if (ctrl.lockTarget ||
            !ctrl.nearbyHostile.isEmpty() ||
            ctrl.hasErrands(ErrandType.FRAY) ||
            ctrl.containsErrand(Action.Type.SLEEP)
        ) {
            return
        }

        if (!ctrl.hasErrands(ErrandType.IDLE) &&
            !ctrl.hasErrands(ErrandType.WORK)
        ) {
            // create ActionGroups tags
            if (!ctrl.containsErrands(Action.Type.FOLLOW, Action.Type.LOCK) && ctrl.random.nextFloat() > 0.45f) {
                ctrl.nearbyNeutral
                    .minWithOrNull(compareBy({ ctrl.getDistanceTo(it) }))
                    ?.let { nearby ->
                        ctrl.target = nearby
                        ctrl.profession.neutralHandler(ctrl)
                        return
                    }
            } else {
                if (!ctrl.containsErrand(Action.Type.SIT)) {
                    BlockUtils.findWalkableBlock(ctrl.entity)?.let { pos ->
                        ctrl.pushErrand(Action.Type.WANDER, pos)
                        return
                    }
                }
            }

            ctrl.nearbyItems
                .filter { it.itemAge > 40 && ctrl.entity.canAcceptStack(it.stack) }
                .minByOrNull { ctrl.getDistanceTo(it) }
                ?.let { item ->
                    ctrl.pushErrand(Action.Type.PICK, item.blockPos)
                }
        }

        if (ctrl.hasErrands(ErrandType.DUAL) && ctrl.random.nextInt(400) == 0) {
            ctrl.pushErrand(Action.Type.YIELD)
        }
    }
}

object StatsUpdateProducer : Producer {
    override val interval: Int = 20
    override val maxNoise: Int = 5

    // TODO: add profession yield method handler
    override fun exec(ctrl: EntityController) {
        val entity = ctrl.entity

        if (ctrl.entity.health < ctrl.entity.maxHealth && entity.age % 4 == 0 && ctrl.satiation >= 70.0f) {
            ctrl.addSatiation(-1.5f)
            ctrl.addTiredness(1.0f)
            entity.heal(1.0f)
        }

        if (ctrl.satiation <= 0.0f && entity.age % 4 == 0) {
            val world = ctrl.world
            entity.damage(ctrl.world as ServerWorld, world.damageSources.starve(), 1.0f)
            ctrl.addTiredness(0.5f)
        }

        if (!ctrl.hasErrands(ErrandType.DUAL) &&
            !ctrl.containsErrand(Action.Type.SLEEP)
        ) {
            if (ctrl.satiation <= 40) {
                ctrl.pushErrand(Action.Type.EAT)
            }
        }

        ctrl.addSatiation(-0.056f)
        ctrl.addTiredness(0.0012f)
    }
}

// TODO: put this in pathfinder
object PathObstructionProducer : Producer {
    override val interval: Int = 20
    override val maxNoise: Int = 5

    override fun exec(ctrl: EntityController) {
        val entity = ctrl.entity
        if (entity.isTouchingWater() && !ctrl.containsErrand(Action.Type.SWIM)) {
            ctrl.pushErrand(Action.Type.SWIM)
        } else {
            if (ctrl.navigation.isFollowingPath() && ctrl.isMoving) {
                val pos =
                    entity.blockPos.takeIf { BlockPredicate.BARRIER(ctrl.world, it) }
                        ?: (entity.blockPos.neighbours())
                            .find {
                                BlockPredicate.BARRIER(ctrl.world, it)
                            }
                pos?.let {
                    ctrl.pushErrand(Action.Type.OPEN, it)
                }
            }
        }
    }
}

data class ErrandProvider(
    val supplier: Producer,
    var nextTick: Long = 0L,
) {
    fun scheduleProducer(ctrl: EntityController) {
        nextTick = ctrl.globalTicker + supplier.interval + ctrl.world.random.nextInt(supplier.maxNoise)
    }

    fun pullErrand(ctrl: EntityController) {
        if (ctrl.globalTicker >= nextTick && supplier.test(ctrl)) {
            supplier.exec(ctrl)
            scheduleProducer(ctrl)
        }
    }
}

class EntityController(
    val entity: AbstractVillagerEntity,
) {
    var professionType: Profession.Type = Profession.Type.UNEMPLOYED

    val profession: Profession
        get() = professionType.instance

    var tiredness: Float = 0.0f
    var satiation: Float = 100.0f
    var happiness: Float = 0.0f
    var hydration: Float = 0.0f
    private var sdim: Byte = 0

    // TODO: merge keys <=> sources
    val sources = arrayOfNulls<((Int) -> List<Errand>?)>(SOURCE_COUNT)
    val keys = IntArray(KEY_COUNT)

    private val errandComparator =
        compareBy<Errand>(
            {
                it.type
                    .get()
                    .group.ordinal
            },
            { -it.priority.toInt() },
        )
    private val mainQueue = PriorityQueue(errandComparator)
    private val bodyQueue = PriorityQueue(errandComparator)
    private val dualQueue = PriorityQueue(errandComparator)

    private val hasErrand = BooleanArray(ACTION_COUNT)
    private val countsPerType = IntArray(ERRAND_COUNT)

    var globalTicker: Long = 0
        private set

    var mainTicker: Int = -1
    var bodyTicker: Int = -1
    var dualTicker: Int = -1

    var target: LivingEntity? = null
        get() = field?.takeIf { it.isAlive }
    var player: PlayerEntity? = null
    var stack: ItemStack? = null
    var path: Path? = null
    var notify: Boolean = false
    var lockTarget: Boolean = false
    var callback: (() -> Boolean)? = null
    var expectedState: State = State.NONE
    var state: State = State.NONE
        set(value) {
            entity.setState(value.ordinal)
            field = value
        }

    private val pulser = Pulser(40, 20)
    // use a MultiMap?
    val items = mutableMapOf<Action.Type, Int>()

    private val scratch = mutableListOf<Entity>()
    private val hostileEntities = mutableListOf<LivingEntity>()
    private val neutralEntities = mutableListOf<LivingEntity>()
    private val itemEntities = mutableListOf<ItemEntity>()

    private val providers =
        arrayOf(
            ErrandProvider(HostileHandlerProducer),
            ErrandProvider(NeutralHandlerProducer),
            ErrandProvider(StructureAccessProducer),
            ErrandProvider(StatsUpdateProducer),
            ErrandProvider(PathObstructionProducer),
        ).onEach { it.scheduleProducer(this) }

    private val executors =
        arrayOf(
            ErrandExecutor(PositionExecutor),
            ErrandExecutor(LinkableExecutor),
            ErrandExecutor(ParallelExecutor),
        )

    val visibleEntities: List<Entity> get() = scratch
    val nearbyHostile: List<LivingEntity> get() = hostileEntities
    val nearbyNeutral: List<LivingEntity> get() = neutralEntities
    val nearbyItems: List<ItemEntity> get() = itemEntities

    fun switchToHostile(e: LivingEntity) {
        if (!hostileEntities.contains(e)) hostileEntities += e
    }

    fun refreshVisibility() {
        scratch.clear()
        hostileEntities.clear()
        neutralEntities.clear()
        itemEntities.clear()
        entity.boundingBox
            .expand(16.0, 4.0, 16.0)
            .let { box -> entity.world.getOtherEntities(entity, box) }
            .filter { entity.visibilityCache.canSee(it) }
            .forEach { e ->
                scratch += e
                when (e) {
                    is HostileEntity -> hostileEntities += e
                    is LivingEntity -> neutralEntities += e
                    is ItemEntity -> itemEntities += e
                }
            }
    }

    fun updateTargets() {
        if (!lockTarget) refreshVisibility()
    }

    fun tickErrands() {
        globalTicker++
        providers.forEach { it.pullErrand(this) }
        executors.forEach { it.execErrand(this) }
    }

    fun tryEquip(
        type: Action.Type,
        predicate: (ItemStack) -> Boolean,
        slot: EquipmentSlot,
    ) = entity.tryItemLookup(type, predicate, slot)

    fun addSatiation(amount: Float) {
        satiation += amount
    }

    fun addTiredness(amount: Float) {
        tiredness += amount
    }

    fun addHappiness(amount: Float) {
        happiness += amount
    }

    fun decayMainTicker(): Int = --mainTicker

    fun decayBodyTicker(): Int = --bodyTicker

    fun decayDualTicker(): Int = --dualTicker

    val random get() = entity.world.random

    val world get() = entity.world

    val navigation get() = entity.navigation

    val isMoving get() = !(Math.abs(entity.velocity.x) < 2.0e-4 && Math.abs(entity.velocity.z) < 2.0e-4)

    val freeLooking get() = pulser.next()

    fun getDistanceTo(pos: BlockPos? = null): Double =
        when {
            pos == null && target?.isAlive == true -> entity.squaredDistanceTo(target)
            pos != null -> entity.squaredDistanceTo(pos.toCenterPos())
            else -> 0.0
        }

    fun getDistanceTo(e: Entity?) = e?.let { entity.squaredDistanceTo(it) } ?: Double.MAX_VALUE

    fun createPathTo(
        to: BlockPos,
        reach: Int,
    ) = navigation.findPathTo(to, reach)

    fun createPathTo(
        to: LivingEntity,
        reach: Int,
    ) = navigation.findPathTo(to, reach)

    fun stopNavigation() = navigation.stop()

    fun setSpeed(speed: Double) = navigation.setSpeed(speed)

    fun setBlock(
        pos: BlockPos,
        state: BlockState,
        sound: SoundEvent,
        volume: Float,
        pitch: Float,
    ) {
        entity.world.setBlockState(pos, state, Block.NOTIFY_LISTENERS)
        entity.world.playSound(entity, pos, sound, SoundCategory.BLOCKS, volume, pitch)
    }

    var stackInMainHand: ItemStack
        get() = entity.getStackInHand(Hand.MAIN_HAND)
        set(v) = entity.setStackInHand(Hand.MAIN_HAND, v)

    var stackInOffHand: ItemStack
        get() = entity.getStackInHand(Hand.OFF_HAND)
        set(v) = entity.setStackInHand(Hand.OFF_HAND, v)

    fun swingMainHand() = entity.swingHand(Hand.MAIN_HAND)

    fun swingOffHand() = entity.swingHand(Hand.OFF_HAND)

    fun decrementStackInMainHand(amount: Int) = stackInMainHand.decrement(amount)

    fun decrementStackInOffHand(amount: Int) = stackInOffHand.decrement(amount)

    fun damageStackInMainHand(amount: Int = 1) = stackInMainHand.takeIf { !it.isEmpty }?.damage(amount, entity, EquipmentSlot.MAINHAND)

    fun damageStackInOffHand(amount: Int = 1) = stackInOffHand.takeIf { !it.isEmpty }?.damage(amount, entity, EquipmentSlot.OFFHAND)

    fun pushErrand(
        type: Action.Type,
        pos: BlockPos? = null,
    ): Boolean {
        val action = type.get()
        val priority = action.scan(this, pos)
        if (priority <= 0) return false
        val queue = if (action.group == ErrandType.DUAL) dualQueue else mainQueue
        val errand = Errand(type, pos, priority.toByte())
        queue.offer(errand)
        hasErrand[type.ordinal] = true
        countsPerType[action.group.ordinal]++
        return true
    }

    // add method to sort work errands
    fun offerToBody() =
        mainQueue.peek()?.let {
            if (it.type.get().group == ErrandType.BODY) {
                bodyQueue.offer(it)
                mainQueue.pop()
            }
        }

    private fun <Q : PriorityQueue<Errand>> Q.pop(): Errand? =
        poll()?.also {
            hasErrand[it.type.ordinal] = false
            countsPerType[
                it.type
                    .get()
                    .group.ordinal,
            ]--
        }

    fun peekMain() = mainQueue.peek()

    fun popMain() = mainQueue.pop()

    fun peekBody() = bodyQueue.peek()

    fun popBody() = bodyQueue.pop()

    fun peekDual() = dualQueue.peek()

    fun popDual() = dualQueue.pop()

    fun containsErrand(type: Action.Type) = hasErrand[type.ordinal]

    fun containsErrands(vararg types: Action.Type) = types.any { hasErrand[it.ordinal] }

    fun hasErrands(type: ErrandType) = countsPerType[type.ordinal] > 0

    fun getProviderErrands(
        src: ErrandSource,
        key: Key,
    ): List<Errand>? {
        val id = keys[key.ordinal]
        val result = sources[src.ordinal]?.invoke(id)
        if (result == null) {
            sources[src.ordinal] = null
            keys[key.ordinal] = 0
        }
        return result
    }

    fun hasProvider(source: ErrandSource): Boolean = sources[source.ordinal] != null

    fun hasKey(key: Key): Boolean = keys[key.ordinal] != 0

    fun assignSettlement(
        id: Int,
        key: Int,
    ) {
        keys[Key.ALOC.ordinal] = id
        keys[Key.SELF.ordinal] = key
    }

    fun clear() {
        mainQueue.clear()
        bodyQueue.clear()
        dualQueue.clear()
        hasErrand.fill(false)
        countsPerType.fill(0)
    }

    fun debugData(): List<String> =
        buildList {
            add(items.toString())
            add(
                "ALOC:[${keys[Key.ALOC.ordinal]}] SELF:[${keys[Key.SELF.ordinal]}] HOME:[${keys[Key.HOME.ordinal]}] WORK:[${keys[Key.WORK.ordinal]}] FREE:[${keys[Key.WORK.ordinal]}]",
            )
            mainQueue.forEach { add("MAIN: ${it.type}@${it.pos}") }
            bodyQueue.forEach { add("BODY: ${it.type}@${it.pos}") }
            dualQueue.forEach { add("DUAL: ${it.type}@${it.pos}") }
            add("Tiredness=$tiredness Satiation=$satiation Happiness=$happiness")
            add(professionType.name)
        }

    class Pulser(
        private val baseHigh: Int,
        private val baseLow: Int,
        private val jitterHigh: Int = 1,
        private val jitterLow: Int = 2,
        private val random: Random = Random.create(),
    ) {
        private var remaining = nextDuration(baseHigh, jitterHigh)
        private var state = true

        fun next(): Boolean =
            state.also {
                if (--remaining <= 0) {
                    state = !state
                    remaining =
                        nextDuration(
                            if (state) baseHigh else baseLow,
                            if (state) jitterHigh else jitterLow,
                        )
                }
            }

        private fun nextDuration(
            base: Int,
            jitter: Int,
        ) = base + random.nextBetween(-jitter, jitter + 1)
    }

    fun loadNbt(other: EntityController) {
        this.professionType = other.professionType
        this.tiredness = other.tiredness
        this.satiation = other.satiation
        this.happiness = other.happiness
        this.sdim = other.sdim

        mainQueue.clear()
        mainQueue.addAll(other.mainQueue)
        bodyQueue.clear()
        bodyQueue.addAll(other.bodyQueue)
        dualQueue.clear()
        dualQueue.addAll(other.dualQueue)

        other.keys.forEachIndexed { i, v -> keys[i] = v }
        other.items.forEach { a, i -> items[a] = i }

        hasErrand.fill(false)
        countsPerType.fill(0)
        (mainQueue + bodyQueue + dualQueue).forEach {
            hasErrand[it.type.ordinal] = true
            countsPerType[
                it.type
                    .get()
                    .group.ordinal,
            ]++
        }
    }

    companion object {
        private val ACTION_COUNT = Action.Type.values().size
        private val SOURCE_COUNT = ErrandSource.values().size
        private val ERRAND_COUNT = ErrandType.values().size
        private val KEY_COUNT = Key.values().size

        private val ITEM_LOOKUP_CODEC: Codec<Int> =
            Codec.STRING.comapFlatMap(
                { str ->
                    str
                        .toIntOrNull()
                        ?.let { DataResult.success<Int>(it) }
                        ?: DataResult.error<Int>(Supplier { "Invalid Action.Type ordinal: $str" })
                },
                { ord -> ord.toString() },
            )

        fun readNbt(entity: AbstractVillagerEntity): Codec<EntityController> =
            RecordCodecBuilder.create { inst ->
                inst
                    .group(
                        Codec.INT.fieldOf("profession").forGetter { it.professionType.ordinal },
                        Codec.FLOAT.fieldOf("tiredness").forGetter { it.tiredness },
                        Codec.FLOAT.fieldOf("satiation").forGetter { it.satiation },
                        Codec.FLOAT.fieldOf("happiness").forGetter { it.happiness },
                        Codec.BYTE.fieldOf("sdim").forGetter { it.sdim },
                        Errand.CODEC
                            .listOf()
                            .fieldOf("main")
                            .forGetter { it.mainQueue.toList() },
                        Errand.CODEC
                            .listOf()
                            .fieldOf("body")
                            .forGetter { it.bodyQueue.toList() },
                        Errand.CODEC
                            .listOf()
                            .fieldOf("dual")
                            .forGetter { it.dualQueue.toList() },
                        Codec.INT
                            .listOf()
                            .fieldOf("keys")
                            .forGetter { it.keys.toList() },
                        Codec
                            .unboundedMap(ITEM_LOOKUP_CODEC, Codec.INT)
                            .fieldOf("item")
                            .forGetter { it.items.mapKeys { (t, _) -> t.ordinal } },
                    ).apply(inst) { type, tir, sat, hap, dim, mainList, bodyList, dualList, keysList, itemsMap ->
                        EntityController(entity).apply {
                            professionType = Profession.Type.values()[type]
                            tiredness = tir
                            satiation = sat
                            happiness = hap
                            sdim = dim

                            mainQueue.clear()
                            mainQueue.addAll(mainList)
                            bodyQueue.clear()
                            bodyQueue.addAll(bodyList)
                            dualQueue.clear()
                            dualQueue.addAll(dualList)

                            keysList.forEachIndexed { i, v -> keys[i] = v }

                            itemsMap.forEach { ord, v ->
                                Action.Type.values().getOrNull(ord)?.let { type ->
                                    items[type] = v
                                }
                            }

                            hasErrand.fill(false)
                            countsPerType.fill(0)
                            (mainQueue + bodyQueue + dualQueue).forEach {
                                hasErrand[it.type.ordinal] = true
                                countsPerType[
                                    it.type
                                        .get()
                                        .group.ordinal,
                                ]++
                            }
                        }
                    }
            }
    }
}
