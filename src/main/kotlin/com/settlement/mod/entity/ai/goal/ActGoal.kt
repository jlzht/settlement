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
    var tickToEvalCount: Int = 0,
    var tickToGiveUp: Int = 0,
    val ticker: (ErrandExecutor, AbstractVillagerEntity) -> Unit,
) {
    fun reset() {
        tickToTestCount = 0
        tickToExecCount = 0
        tickToEvalCount = 0
        status = ErrandState.PENDING
        errand = null
    }

    fun next() {
        status = status.next()
    }
}

class ActGoal(
    private val entity: AbstractVillagerEntity,
) : Goal() {
    init {
        this.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.JUMP, Goal.Control.LOOK))
    }

    private val executors =
        arrayOf<ErrandExecutor>(
            ErrandExecutor(ticker = MAIN_TICKER),
            ErrandExecutor(ticker = DUAL_TICKER),
        )

    override fun canStart(): Boolean = true

    override fun shouldContinue(): Boolean = false

    override fun shouldRunEveryTick() = true

    override fun tick() {
        executors.forEachIndexed { index, executor ->
            val expected = selectErrandGroup(index)
            val current = executor.errand

            if (current != null) {
                if (current != expected) {
                    Action.get(current.cid).stop(entity, current.pos)
                    executor.reset()
                    executor.errand = expected
                }
            } else {
                executor.reset()
                executor.errand = expected
            }

            tickErrand(executor, entity)
        }
    }

    private fun selectErrandGroup(index: Int) =
        when (index) {
            0 -> entity.errandManager.peekMain()
            1 -> entity.errandManager.peekDual()
            else -> null
        }

    companion object {
        private val MAIN_TICKER: (ErrandExecutor, AbstractVillagerEntity) -> Unit = { executor, entity ->
            executor.errand?.let { errand ->
                val action = Action.get(errand.cid)
                val distance =
                    entity.target?.let { target ->
                        if (errand.pos == null && target.isAlive) {
                            val dist = entity.squaredDistanceTo(target)
                            if (action.shouldLook(dist)) entity.getLookControl().lookAt(target)
                            dist
                        } else {
                            null
                        }
                    } ?: run {
                        errand.pos?.let { point ->
                            val center = point.toCenterPos()
                            val dist = entity.squaredDistanceTo(center)
                            if (action.shouldLook(dist)) {
                                entity.getLookControl().lookAt(center)
                            } else {
                                entity.target?.let {
                                    if (entity.pulse.next()) entity.getLookControl().lookAt(it)
                                }
                            }
                            dist
                        } ?: 0.0
                    }

                if (action.shouldMove(distance)) {
                    if (entity.navigation.isIdle) {
                        entity.path?.let { path ->
                            if (entity.isSleeping()) {
                                entity.wakeUp()
                            } else if (entity.isSitting()) {
                                entity.getUp()
                            }
                            entity.setWorking(false) // quick fix for fisherman canceling
                            entity.navigation.startMovingAlong(path, 1.0)
                            val velocity = entity.getVelocity()
                            if (velocity.x == 0.0 && velocity.z == 0.0) {
                                if (--executor.tickToGiveUp <= 0) {
                                    entity.errandManager.popMain()
                                    executor.reset()
                                }
                            }
                        }
                        if (entity.path == null || entity.path?.isFinished() == true) {
                            if (errand.pos != null) {
                                val center = errand.pos.toCenterPos()
                                entity.path = entity.navigation.findPathTo(center.x, center.y, center.z, action.pathReach)
                            } else if (entity.target != null) {
                                entity.path = entity.navigation.findPathTo(entity.target, action.pathReach)
                            }
                        }
                    }
                } else {
                    executor.tickToGiveUp = 20
                    executor.tickToTestCount++
                }
            }
        }

        private val DUAL_TICKER: (ErrandExecutor, AbstractVillagerEntity) -> Unit = { executor, entity ->
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
                    executor.tickToGiveUp = 800
                    executor.tickToTestCount++
                } else if (--executor.tickToGiveUp <= 0) {
                    entity.errandManager.popMain()
                    executor.reset()
                }
            }
        }

        private fun tickErrand(
            executor: ErrandExecutor,
            entity: AbstractVillagerEntity,
        ) {
            val errand = executor.errand ?: return
            val action = Action.get(errand.cid)
            entity.getNavigation().setSpeed(
                if (entity.isUsingItem) {
                    0.5
                } else if (entity.errandManager.satiation >=
                    20
                ) {
                    action.speedModifier
                } else {
                    0.9
                },
            )

            executor.ticker(executor, entity)

            when (executor.status) {
                ErrandState.PENDING -> tickPending(executor, entity, errand, action)
                ErrandState.TESTED -> tickTested(executor, entity, errand, action)
                ErrandState.COMPLETED -> tickCompleted(executor, entity, errand, action)
            }
        }

        private fun tickPending(
            executor: ErrandExecutor,
            entity: AbstractVillagerEntity,
            errand: Errand,
            action: Action,
        ) {
            if (action.shouldExec(executor.tickToTestCount)) {
                when (action.test(entity, errand.pos)) {
                    1.toByte() -> executor.next()
                    2.toByte() -> completeErrand(entity, action, executor)
                    else -> executor.reset()
                }
            }
        }

        private fun tickTested(
            executor: ErrandExecutor,
            entity: AbstractVillagerEntity,
            errand: Errand,
            action: Action,
        ) {
            if (action.shouldExec(executor.tickToExecCount)) {
                action.exec(entity, errand.pos)
                entity.errandManager.tiredness += action.restCost.toFloat()
                executor.next()
            } else {
                executor.tickToExecCount++
            }
        }

        private fun tickCompleted(
            executor: ErrandExecutor,
            entity: AbstractVillagerEntity,
            errand: Errand,
            action: Action,
        ) {
            if (action.shouldEval(executor.tickToEvalCount)) {
                when (action.eval(entity, errand.pos)) {
                    1.toByte() -> completeErrand(entity, action, executor)
                    2.toByte() -> {
                        completeErrand(entity, action, executor)
                        action.redo(entity, errand.pos)
                    }
                }
            } else {
                executor.tickToEvalCount++
            }
        }

        private fun completeErrand(
            entity: AbstractVillagerEntity,
            action: Action,
            executor: ErrandExecutor,
        ) {
            if (action.type == ErrandType.DUAL) {
                entity.errandManager.popDual()
            } else {
                entity.navigation.stop()
                entity.path = null
                entity.errandManager.popMain()
            }
            executor.reset()
        }
    }
}
