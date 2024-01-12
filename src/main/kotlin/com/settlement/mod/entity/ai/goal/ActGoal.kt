package com.settlement.mod.entity.ai.goal

import com.settlement.mod.action.Action
import com.settlement.mod.action.Errand
import com.settlement.mod.entity.mob.AbstractVillagerEntity
import com.settlement.mod.entity.mob.ErrandType
import net.minecraft.entity.ai.goal.Goal
import java.util.EnumSet

enum class ErrandState {
    PENDING,
    TESTED,
    COMPLETED,
    ;

    fun next(): ErrandState = values()[(ordinal + 1) % values().size]
}

data class ErrandExecutor(
    var errand: Errand? = null,
    var status: ErrandState = ErrandState.PENDING,
    var tickToTestCount: Int = 0,
    var tickToExecCount: Int = 0,
    val tick: (ErrandExecutor, AbstractVillagerEntity) -> Unit,
) {
    fun reset() {
        tickToTestCount = 0
        tickToExecCount = 0
        status = ErrandState.PENDING
        errand = null
    }

    fun status(): ErrandState = status

    fun next() {
        status = status.next()
    }
}

class ActGoal(
    private val entity: AbstractVillagerEntity,
) : Goal() {
    private val world = entity.world

    private val POSITION_TICK: (ErrandExecutor, AbstractVillagerEntity) -> Unit = { executor, entity ->
        executor.errand?.let { errand ->
            val action = Action.get(errand.cid)

            val distance =
                entity.target?.let { target ->
                    if (errand.pos == null && target.isAlive) {
                        val dist = entity.squaredDistanceTo(target)
                        if (!entity.navigation.isFollowingPath) {
                            entity.path = entity.navigation.findPathTo(target, action.pathReach)
                        }
                        if (action.shouldLook(dist)) entity.getLookControl().lookAt(target)
                        dist
                    } else {
                        null
                    }
                } ?: run {
                    errand.pos?.let { point ->
                        val center = point.toCenterPos()
                        val dist = entity.squaredDistanceTo(center)
                        if (!entity.navigation.isFollowingPath) {
                            entity.path = entity.navigation.findPathTo(center.x, center.y, center.z, action.pathReach)
                        }
                        if (action.shouldLook(dist)) entity.getLookControl().lookAt(center)
                        dist
                    } ?: 0.0
                }

            if (action.shouldMove(distance)) {
                entity.path?.let { path ->
                    if (entity.navigation.isIdle) {
                        entity.navigation.startMovingAlong(path, 1.0)
                    }
                } ?: run {
                    entity.navigation.stop()
                }

                entity.getNavigation().setSpeed(if (entity.isUsingItem) 0.5 else action.speedModifier)
            } else {
                executor.tickToTestCount++
            }
        }
    }

    private val PARALLEL_TICK: (ErrandExecutor, AbstractVillagerEntity) -> Unit = { executor, entity ->
        executor.errand?.let { errand ->
            val action = Action.get(errand.cid)

            val distance =
                entity.target?.let { target ->
                    if (errand.pos == null && target.isAlive) {
                        entity.squaredDistanceTo(target)
                    } else {
                        null
                    }
                } ?: run {
                    errand.pos?.let { point ->
                        val center = point.toCenterPos()
                        entity.squaredDistanceTo(center)
                    } ?: 0.0
                }

            if (!action.shouldMove(distance)) {
                executor.tickToTestCount++
            }
        }
    }

    private val executors =
        mutableMapOf<ErrandType, ErrandExecutor>(
            ErrandType.POSITION to ErrandExecutor(tick = POSITION_TICK),
            ErrandType.PARALLEL to ErrandExecutor(tick = PARALLEL_TICK),
        )

    init {
        this.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.JUMP, Goal.Control.LOOK))
    }

    override fun canStart(): Boolean {
        ErrandType.values().forEach { type ->
            executors[type]!!.errand?.let { errand ->
                if (errand != entity.errandManager.peek(type)) {
                    executors[type]?.component1()?.let { cid ->
                        val action = Action.get(cid.component1())
                        action.stop(entity, executors[type]!!.component1()!!.component2())
                    }
                    executors[type]!!.reset()
                }
            } ?: run {
                executors[type]!!.errand = entity.errandManager.peek(type)
            }
        }

        return executors.any { it.value.errand != null }
    }

    override fun shouldContinue(): Boolean = false

    override fun shouldRunEveryTick() = true

    override fun tick() {
        executors.forEach { executor ->
            tickErrand(executor.value)
        }
    }

    private fun tickErrand(executor: ErrandExecutor) {
        executor.errand?.let { errand ->
            executor.tick(executor, entity)
            when (executor.status()) {
                ErrandState.PENDING -> tickPending(executor)
                ErrandState.TESTED -> tickTested(executor)
                ErrandState.COMPLETED -> tickCompleted(executor)
            }
        }
    }

    private fun tickPending(executor: ErrandExecutor) {
        executor.errand?.let { errand ->
            val action = Action.get(errand.cid)
            if (action.shouldExec(executor.tickToTestCount)) {
                when (action.test(entity, errand.pos)) {
                    1.toByte() -> {
                        executor.next()
                    }
                    2.toByte() -> {
                        entity.errandManager.pop(errand)
                        executor.reset()
                    } else -> {
                        executor.reset()
                    }
                }
            }
        }
    }

    private fun tickTested(executor: ErrandExecutor) {
        executor.errand?.let { errand ->
            val action = Action.get(errand.cid)
            if (action.shouldExec(executor.tickToExecCount)) {
                action.exec(entity, errand.pos)
                entity.errandManager.tiredness += action.restCost.toFloat()
                executor.next()
            } else {
                executor.tickToExecCount++
            }
        }
    }

    private fun tickCompleted(executor: ErrandExecutor) {
        executor.errand?.let { errand ->
            val action = Action.get(errand.cid)
            when (action.eval(entity, errand.pos)) {
                1.toByte() -> {
                    entity.errandManager.pop(errand)
                    executor.reset()
                }
                else -> {}
            }
        }
    }
}
