package com.settlement.mod.entity.ai.goal

import com.settlement.mod.action.Action
import com.settlement.mod.entity.mob.AbstractVillagerEntity
import com.settlement.mod.entity.mob.ErrandSource
import com.settlement.mod.entity.mob.Key
import com.settlement.mod.structure.StructureType
import com.settlement.mod.util.Finder
import com.settlement.mod.world.SettlementAccessor
import net.minecraft.entity.ItemEntity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.ai.goal.Goal
import net.minecraft.entity.mob.HostileEntity
import java.util.EnumSet

data class ErrandProvider(
    val trigger: (AbstractVillagerEntity) -> Unit,
    val tickInterval: Int,
    var ticksToRun: Int = 0,
) {
    fun reset() {
        ticksToRun = tickInterval
    }

    fun pull(entity: AbstractVillagerEntity): Boolean {
        ticksToRun--
        if (ticksToRun > 0) return false

        trigger(entity)
        reset()
        return true
    }
}

class ReactGoal(
    private val entity: AbstractVillagerEntity,
) : Goal() {
    init {
        this.setControls(EnumSet.of(Goal.Control.TARGET))
    }
    // TODO: recycle calculations
    val STRUCTURE_ACCESS_PROVIDER: (AbstractVillagerEntity) -> Unit = { e ->
        if (!e.isSleeping()) {
            val manager = e.errandManager
            if (!manager.hasKey(Key.ALOC)) {
                SettlementAccessor.visitSettlement(entity)
                SettlementAccessor.findSettlementToAttach(entity)
            } else {
                if (entity.errandManager.tiredness >= 60 && entity.errandManager.satiation >= 0) {
                    if (!manager.hasKey(Key.HOME)) {
                        SettlementAccessor.findStructureToAttach(entity, StructureType.HOUSE)
                    } else if (!manager.hasProvider(ErrandSource.HOME)) {
                        SettlementAccessor.getStructureToAttach(entity, ErrandSource.HOME)
                    } else {
                        val errands = entity.errandManager.getProviderErrands(ErrandSource.HOME, Key.SELF)
                        errands?.find { errand -> errand.cid == Action.Type.SLEEP }?.let {
                            e.pushErrand(it.cid, it.pos)
                        }
                    }
                } else if (entity.errandManager.tiredness <= 40) {
                    val workSet = setOf(Action.Type.TILL, Action.Type.HARVEST, Action.Type.POWDER, Action.Type.PLANT, Action.Type.FISH)
                    if (!workSet.any { e.errandManager.has(it) }) {
                        if (!manager.hasKey(Key.WORK)) {
                            SettlementAccessor.findStructureToAttach(entity, entity.profession.structureInterest)
                        } else if (!manager.hasProvider(ErrandSource.WORK)) {
                            SettlementAccessor.getStructureToAttach(entity, ErrandSource.WORK)
                        } else {
                            entity.errandManager.getProviderErrands(ErrandSource.WORK, Key.SELF)?.forEach { errand ->
                                e.pushErrand(errand.cid, errand.pos)
                            }
                        }
                    }
                } else {
                    // IMPLEMENT FREE PROVIDER
                }
            }
        }
    }

    val STATS_UPDATE_PROVIDER: (AbstractVillagerEntity) -> Unit = { e ->
        val manager = e.errandManager

        val MAX_HEALTH = 20.0f
        val HUNGER_THRESHOLD_LOW = 40.0f
        val HUNGER_THRESHOLD_HEAL = 70.0f
        val STARVATION_THRESHOLD = 15.0f

        val isSleeping = e.isSleeping()
        val isSitting = e.isSitting()
        val isFighting = e.isFighting()

        if (manager.satiation >= HUNGER_THRESHOLD_HEAL && e.health < MAX_HEALTH) {
            e.heal(1.0f)
            manager.satiation -= 1.5f
            manager.tiredness += 1.0f
        }

        if (manager.satiation <= 0.0f) {
            e.damage(e.damageSources.starve(), 1.0f)
            manager.tiredness += 0.07f
        }

        if (manager.satiation <= HUNGER_THRESHOLD_LOW) {
            e.pushErrand(Action.Type.EAT)
        }

        val hungerBonus = if (isSleeping) 1.2f else 1.0f

        manager.satiation = (manager.satiation - 0.1f * hungerBonus).coerceAtLeast(0.0f)

        if (isSleeping) manager.tiredness -= 0.8f
        if (isSitting) manager.tiredness -= 0.1f

        if (e.isSleeping() && (manager.tiredness <= 0.0f || manager.satiation <= 0.0f || isFighting)) {
            e.pushErrand(Action.Type.WAKE)
        }
    }

    val IDLE_PROVIDER: (AbstractVillagerEntity) -> Unit = { e ->
        if (!e.isSleeping() && !e.isFighting() && !e.isSitting()) {
            val options =
                listOf(
                    0.5f to { Finder.findSeekBlock(e)?.let { (cid, pos) -> e.pushErrand(cid, pos) } },
                    0.4f to { Finder.findWanderBlock(e)?.let { (cid, pos) -> e.pushErrand(cid, pos) } },
                )

            val selected =
                options
                    .filter { pair -> entity.world.random.nextFloat() < pair.first }
                    .map { it.second }

            selected.randomOrNull()?.invoke()
        }
    }

    val PATH_OBSTRUCTION_PROVIDER: (AbstractVillagerEntity) -> Unit = { e ->
        if (e.isTouchingWater() && !e.errandManager.has(Action.Type.SWIM)) {
            entity.pushErrand(Action.Type.SWIM)
        } else {
            e.path?.let { path ->
                Finder.findEntranceBlock(entity.world, path)?.let { (cid, pos, priority) ->
                    entity.pushErrand(cid, pos)
                }
            }
        }
    }

    val TARGET_UPDATE_PROVIDER: (AbstractVillagerEntity) -> Unit = { e ->
        e
            .getRecentDamageSource()
            ?.getAttacker()
            ?.takeIf { it is LivingEntity }
            ?.let { attacker ->
                e.profession.TRACK_HANDLER(entity, attacker as LivingEntity, true)
            }

        e.target?.let { target ->
            if (!target.isAlive || e.squaredDistanceTo(target) > 512.0) {
                e.target = null
            }
        }
        if (!e.isSleeping()) {
            val world = e.getWorld()
            val visibility = e.getVisibilityCache()
            val nearbyEntities = world.getOtherEntities(e, entity.boundingBox.expand(16.0, 4.0, 16.0)).filter { visibility.canSee(it) }
            if (entity.target == null || e.random.nextInt(5) == 0) {
                nearbyEntities
                    .filterIsInstance<LivingEntity>()
                    .sortedWith(
                        compareBy(
                            {
                                when (it) {
                                    is HostileEntity -> 0
                                    is AbstractVillagerEntity -> 1
                                    else -> 2
                                }
                            },
                            { entity.squaredDistanceTo(it) },
                        ),
                    ).firstOrNull { it.isAlive }
                    ?.let { entity.profession.TRACK_HANDLER(entity, it, false) }
            }

            if (e.random.nextInt(5) == 0 && !entity.isFighting() && !e.errandManager.has(Action.Type.PICK)) {
                nearbyEntities
                    .filterIsInstance<ItemEntity>()
                    .filter { it.getItemAge() > 20 }
                    .filter { entity.canGather(it.stack) }
                    .minByOrNull { entity.squaredDistanceTo(it) }
                    ?.let { item ->
                        entity.pushErrand(Action.Type.PICK, item.blockPos)
                    }
            }
        }
    }

    private val providers =
        listOf<ErrandProvider>(
            ErrandProvider(
                TARGET_UPDATE_PROVIDER,
                5,
            ),
            ErrandProvider(
                STATS_UPDATE_PROVIDER,
                40,
            ),
            ErrandProvider(
                IDLE_PROVIDER,
                100,
            ),
            ErrandProvider(
                STRUCTURE_ACCESS_PROVIDER,
                160,
            ),
            ErrandProvider(
                PATH_OBSTRUCTION_PROVIDER,
                5,
            ),
        )

    override fun canStart(): Boolean = true

    override fun shouldContinue(): Boolean = false

    override fun shouldRunEveryTick(): Boolean = true

    override fun tick() {
        for (provider in providers) {
            if (provider.pull(entity)) break
        }
    }
}
