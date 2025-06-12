package com.settlement.mod.entity.ai.goal

import com.settlement.mod.LOGGER
import com.settlement.mod.action.Action
import com.settlement.mod.entity.mob.AbstractVillagerEntity
import com.settlement.mod.entity.mob.ErrandSource
import com.settlement.mod.entity.mob.ErrandType
import com.settlement.mod.entity.mob.Key
import com.settlement.mod.structure.StructureType
import com.settlement.mod.util.BlockIterator
import com.settlement.mod.world.SettlementAccessor
import net.minecraft.block.DoorBlock
import net.minecraft.block.FenceGateBlock
import net.minecraft.entity.Entity
import net.minecraft.entity.ItemEntity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.ai.goal.Goal
import net.minecraft.entity.mob.HostileEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.world.World
import java.util.EnumSet

data class TickContext(
    val entity: AbstractVillagerEntity,
) {
    val world: ServerWorld = entity.world as ServerWorld

    val visibleEntities: List<Entity> by lazy {
        world
            .getOtherEntities(entity, entity.boundingBox.expand(16.0, 4.0, 16.0))
            .filter { entity.getVisibilityCache().canSee(it) }
    }

    val nearbyLiving: List<LivingEntity> by lazy {
        visibleEntities.filterIsInstance<LivingEntity>()
    }

    val itemEntities: List<ItemEntity> by lazy {
        visibleEntities.filterIsInstance<ItemEntity>()
    }
    var updated: Boolean = false
}

class ReactGoal(
    private val entity: AbstractVillagerEntity,
) : Goal() {
    init {
        this.setControls(EnumSet.of(Goal.Control.TARGET))
    }

    private val providers =
        listOf(
            TimedProvider.start(IDLE_PROVIDER, 40, entity.world),
            TimedProvider.start(STATS_UPDATE_PROVIDER, 20, entity.world),
            TimedProvider.start(TARGET_UPDATE_PROVIDER, 2, entity.world),
            TimedProvider.start(TARGET_HANDLING_PROVIDER, 2, entity.world),
            TimedProvider.start(SURROUND_BLOCK_PROVIDER, 240, entity.world),
            TimedProvider.start(PATH_OBSTRUCTION_PROVIDER, 10, entity.world),
            TimedProvider.start(STRUCTURE_ACCESS_PROVIDER, 160, entity.world),
        )

    override fun canStart() = true

    override fun shouldContinue() = false

    override fun shouldRunEveryTick() = true

    override fun tick() {
        if (!entity.world.isClient) {
            val context = TickContext(entity)
            for (provider in providers) {
                provider.pull(context)
                if (context.updated) break
            }
        }
    }

    data class TimedProvider(
        val logic: (TickContext) -> Unit,
        val baseInterval: Int,
        var ticks: Int = 0,
    ) {
        fun pull(ctx: TickContext) {
            ticks--
            if (ticks <= 0) {
                logic(ctx)
                ticks = baseInterval + ctx.world.random.nextInt(5)
            }
        }

        companion object {
            fun start(
                logic: (TickContext) -> Unit,
                baseInterval: Int,
                world: World,
            ): TimedProvider {
                val initialTicks = world.random.nextInt(baseInterval)
                return TimedProvider(logic, baseInterval, initialTicks)
            }
        }
    }

    companion object {
        val TARGET_UPDATE_PROVIDER: (TickContext) -> Unit = { ctx ->
            val e = ctx.entity
            val m = e.errandManager

            val attacker = e.getRecentDamageSource()?.attacker as? LivingEntity

            fun LivingEntity.priority(): Int =
                when (this) {
                    is HostileEntity -> 0
                    is AbstractVillagerEntity -> 1
                    else -> 2
                }

            if (attacker != null) {
                val nearest =
                    ctx.nearbyLiving
                        .filter { it !== attacker }
                        .minWithOrNull(compareBy({ it.priority() }, { e.squaredDistanceTo(it) }))

                val target = nearest?.takeIf { e.squaredDistanceTo(it) < e.squaredDistanceTo(attacker) } ?: attacker
                e.profession.TRACK_HANDLER(e, target, target === attacker)
            } else {
                val findNewTarget =
                    e.target?.let {
                        !it.isAlive || e.squaredDistanceTo(it) > 1024.0 || e.random.nextInt(5) == 0
                    } ?: false

                if (findNewTarget) {
                    e.profession.TRACK_HANDLER(e, null, false)
                }

                if (e.target == null) {
                    ctx.nearbyLiving
                        .minWithOrNull(compareBy({ it.priority() }, { e.squaredDistanceTo(it) }))
                        ?.let { e.profession.TRACK_HANDLER(e, it, false) }
                }

                if (!m.hasErrands(ErrandType.IDLE) &&
                    !m.hasErrands(ErrandType.WORK) &&
                    !e.isFighting() &&
                    !e.isPicking()
                ) {
                    ctx.itemEntities
                        .filter { it.itemAge > 80 && e.canGather(ctx.world, it.stack) }
                        .minByOrNull { e.squaredDistanceTo(it) }
                        ?.let {
                            e.pushErrand(Action.Type.PICK, it.blockPos)
                            ctx.updated = true
                        }
                }
            }
        }

        val STRUCTURE_ACCESS_PROVIDER: (TickContext) -> Unit = { ctx ->
            val e = ctx.entity
            val m = e.errandManager
            // if entity is not fighting nor sleeping
            if (!e.isSleeping() && !e.isFighting()) {
                // if it has no ALOC key (settlement to live in, tries to visit and attach)
                if (!m.hasKey(Key.ALOC)) {
                    SettlementAccessor.visitSettlement(e)
                    SettlementAccessor.findSettlementToAttach(e)
                } else {
                    // if the entity has a settlement, it tries to do actions based on its energy level
                    if (m.tiredness >= 70 || e.canSleep()) {
                        if (!m.hasKey(Key.HOME)) {
                            SettlementAccessor.findStructureToAttach(e, StructureType.HOUSE)
                        } else if (!m.hasProvider(ErrandSource.HOME)) {
                            SettlementAccessor.getStructureToAttach(e, ErrandSource.HOME)
                        } else {
                            val errands = e.errandManager.getProviderErrands(ErrandSource.HOME, Key.SELF)
                            if (e.shouldStoreItems()) {
                                errands?.find { errand -> errand.cid == Action.Type.STORE }?.let {
                                    e.pushErrand(it.cid, it.pos)
                                    ctx.updated = true
                                }
                            }
                            errands?.find { errand -> errand.cid == Action.Type.SIT }?.let {
                                e.pushErrand(it.cid, it.pos)
                                ctx.updated = true
                            }
                            if (m.satiation >= 20 && e.canSleep()) {
                                errands?.find { errand -> errand.cid == Action.Type.SLEEP }?.let {
                                    e.pushErrand(it.cid, it.pos)
                                    ctx.updated = true
                                }
                            }
                        }
                    } else if (m.tiredness <= 45 && !m.hasErrands(ErrandType.WORK)) {
                        if (!m.hasKey(Key.WORK)) {
                            LOGGER.info("{}", e.profession.structureInterest)
                            SettlementAccessor.findStructureToAttach(e, e.profession.structureInterest)
                        } else if (!m.hasProvider(ErrandSource.WORK)) {
                            LOGGER.info(">>>>>>")
                            SettlementAccessor.getStructureToAttach(e, ErrandSource.WORK)
                        } else {
                            m.getProviderErrands(ErrandSource.WORK, Key.SELF)?.forEach { errand ->
                                LOGGER.info("|>>>>>>")
                                e.pushErrand(errand.cid, errand.pos)
                                ctx.updated = true
                            }
                        }
                    } else {
                        if (!m.hasKey(Key.FREE)) {
                            SettlementAccessor.findStructureToAttach(e, StructureType.CAMPFIRE)
                        } else if (!m.hasProvider(ErrandSource.FREE)) {
                            SettlementAccessor.getStructureToAttach(e, ErrandSource.FREE)
                        } else {
                            m
                                .getProviderErrands(ErrandSource.FREE, Key.SELF)
                                ?.firstOrNull { !e.isSitting() && !m.hasErrands(ErrandType.WORK) } // this is problematic
                                ?.let {
                                    e.pushErrand(it.cid, it.pos)
                                    ctx.updated = true
                                }
                        }
                    }
                }
            }
        }

        val IDLE_PROVIDER: (TickContext) -> Unit = { ctx ->
            val e = ctx.entity
            val m = e.errandManager
            if (!m.hasErrands(ErrandType.WORK) &&
                !m.hasErrands(ErrandType.IDLE) &&
                !e.isSleeping() &&
                !e.isFighting()
            ) {
                e.profession.IDLE_HANDLER(e)
            }
        }

        val TARGET_HANDLING_PROVIDER: (TickContext) -> Unit = { ctx ->
            val e = ctx.entity
            if (e.target != null) {
                e.profession.TARGET_HANDLER(e)
                ctx.updated = true
            }
        }

        val SURROUND_BLOCK_PROVIDER: (TickContext) -> Unit = { ctx ->
            ctx.entity.profession.BLOCK_HANDLER(ctx.entity)
        }

        val STATS_UPDATE_PROVIDER: (TickContext) -> Unit = { ctx ->
            val e = ctx.entity
            val m = e.errandManager

            val heal = e.health < e.maxHealth && m.satiation >= 70.0f
            if (heal && e.age % 4 == 0) {
                e.heal(1.0f)
                m.satiation -= 1.5f
                m.tiredness += 1.0f
            }
            if (m.satiation <= 0.0f && e.age % 4 == 0) {
                e.damage(ctx.world, ctx.world.damageSources.starve(), 1.0f)
                m.tiredness += 0.07f
            }
            // Only push errand to eat if satiation is low and not already eating/fighting.
            if (m.satiation <= 40.0f && !m.hasErrands(ErrandType.DUAL) && !e.isFighting()) {
                e.pushErrand(Action.Type.EAT)
            }

            m.satiation = (m.satiation - 0.1f * if (e.isSleeping()) 1.2f else 1.0f).coerceAtLeast(0f)
            if (e.isSleeping()) m.tiredness -= 0.8f
            else if (e.isSitting()) { m.tiredness -= 0.002f }
            else m.tiredness += 0.0008f

            if (m.tiredness <= 0f || m.satiation <= 0f || e.isFighting()) {
                if (e.isSleeping()) {
                    e.wakeUp()
                } else if (e.isSitting()) {
                    e.getUp()
                }
            }
        }

        val PATH_OBSTRUCTION_PROVIDER: (TickContext) -> Unit = { ctx ->
            val e = ctx.entity
            val m = e.errandManager
            if (e.isTouchingWater() && !m.containsErrand(Action.Type.SWIM)) {
                e.pushErrand(Action.Type.SWIM)
            } else {
                // refine this approach for looking for door is path
                val vel = e.getVelocity()
                if (e.navigation.isFollowingPath && vel.x == 0.0 && vel.z == 0.0) {
                    // TODO: make it consider only the block it is facing
                    (BlockIterator.NEIGHBOURS(e.blockPos) + e.blockPos).forEach {
                        val state = e.world.getBlockState(it)
                        val errand =
                            when (state.getBlock()) {
                                is DoorBlock -> {
                                    if (DoorBlock.canOpenByHand(e.world, it)) Pair(Action.Type.OPEN, it) else null
                                }
                                is FenceGateBlock -> {
                                    if (!state.get(FenceGateBlock.OPEN)) Pair(Action.Type.OPEN, it) else null
                                }
                                else -> null
                            }?.let { (cid, pos) ->
                                e.pushErrand(cid, pos)
                            }
                    }
                }
            }
        }
    }
}
