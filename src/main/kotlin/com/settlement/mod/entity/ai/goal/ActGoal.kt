package com.settlement.mod.entity.ai.goal

import com.settlement.mod.LOGGER
import com.settlement.mod.action.Action
import com.settlement.mod.action.Errand
import com.settlement.mod.action.Parallel
import com.settlement.mod.action.Position
import com.settlement.mod.entity.mob.AbstractVillagerEntity
import com.settlement.mod.util.Finder
import net.minecraft.entity.ai.goal.Goal
import net.minecraft.entity.ai.pathing.Path
import net.minecraft.util.math.BlockPos
import net.minecraft.block.LadderBlock
import java.util.EnumSet

class ActGoal(
    private val entity: AbstractVillagerEntity,
) : Goal() {
    private val world = entity.world

    private var tickToTestCount = 0
    private var tickToExecCount = 0
    private var pathChanged = true

    private var path: Path? = null
    private var errand: Errand? = null
    private var parallel: Errand? = null

    private var primaryState = ErrandState.PENDING
    private var parallelState = ErrandState.PENDING

    init {
        this.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.JUMP, Goal.Control.LOOK))
    }

    override fun canStart(): Boolean {
        if (entity.errandManager.isEmpty()) return false
        
        entity.errandManager.peek()?.let { peek ->
            if (errand != peek) {
                resetErrand()
                errand = peek
                parallel = selectParallel(peek)
            }
        }

        return true
    }

    override fun shouldContinue(): Boolean = false

    override fun shouldRunEveryTick() = true

    override fun tick() {
        errand?.let { processErrand(it, primaryState) }
        parallel?.let { processErrand(it, parallelState) }
    }

    private fun processErrand(
        errand: Errand,
        current: ErrandState
    ) {
        val action = Action.get(errand.cid)
        when (action) {
            is Position -> {
                val distance = calculateDistanceAndPath(errand.cid, errand.pos, action)

                if (action.shouldMove(distance)) {
                    tickMovement(action)
                } else {
                    tickToTestCount++
                }
            }
            else -> { }
        }
        when (current) {
            ErrandState.PENDING -> tickPending(action, errand)
            ErrandState.TESTED -> tickTested(action, errand)
            ErrandState.COMPLETED -> tickCompleted(action, errand)
        }
    }

    private fun tickPending(
        action: Action,
        errand: Errand
    ) {
        if (action.shouldTest(tickToTestCount)) {
            when (action) {
                is Position -> {
                    if (action.test(entity, errand.pos) >= 1) {
                        LOGGER.info("> Test passed")
                        setPrimaryState(ErrandState.TESTED)
                    } else {
                        LOGGER.info("> Test failed")
                        resetPrimaryState()
                    }
                }
                is Parallel -> {
                    if (action.test(entity) >= 1) {
                        LOGGER.info("> Test passed")
                        setParallelState(ErrandState.TESTED)
                    } else {
                        LOGGER.info("> Test failed")
                        resetParallelState()
                    }
                }
            }
        }
    }

    private fun tickTested(
        action: Action,
        errand: Errand
    ) {
        if (action.shouldExec(tickToExecCount)) {
            LOGGER.info("> Action executed")
            when (action) {
                is Position -> {
                    entity.energy -= action.energyCost
                    action.exec(entity, errand.pos)
                    setPrimaryState(ErrandState.COMPLETED)
                }
                is Parallel -> {
                    action.exec(entity)
                    setParallelState(ErrandState.COMPLETED)
                }
            }
        } else {
            tickToExecCount++
        }
    }

    private fun tickCompleted(
        action: Action,
        errand: Errand
    ) { 
        // add others codes to handle other numbers
        when (action) {
            is Position -> {
                when (action.eval(entity, errand.pos)) {
                    1.toByte() -> {
                        LOGGER.info("> Errand concluded")
                        resetPrimaryState()
                    }
                }
            }
            is Parallel -> {
                when (action.eval(entity)) {
                    1.toByte() -> {
                        LOGGER.info("> Errand concluded")
                        resetParallelState()
                    }
                }
            }
        }
    }

    private fun calculateDistanceAndPath(
        cid: Action.Type,
        pos: BlockPos?,
        action: Position,
    ): Double =
        entity.target?.let { target ->
            if (cid in repelSet && target.isAlive) {
                val dist = entity.squaredDistanceTo(target)
                if (action.shouldLook(dist)) entity.getLookControl().lookAt(target)
                if (!entity.navigation.isFollowingPath) {
                    path = entity.navigation.findPathTo(target, 1)
                    pathChanged = true
                }
                dist
            } else {
                null
            }
        } ?: run {
            pos?.let { point ->
                val center = point.toCenterPos()
                val dist = entity.squaredDistanceTo(center)
                if (action.shouldLook(dist)) entity.getLookControl().lookAt(center)
                if (!entity.navigation.isFollowingPath) {
                    path = entity.navigation.findPathTo(center.x, center.y, center.z, 0)
                    pathChanged = true
                }
                dist
            } ?: 0.0
        }

    private fun tickMovement(
        action: Position,
    ) {
        path?.let {
            // If door is in path, closes or open it
            if (pathChanged && !entity.errandManager.has(Action.Type.OPEN) && !entity.errandManager.has(Action.Type.CLOSE)) {
                Finder.findEntranceBlock(entity.world, path!!)?.let { (cid, pos, priority) ->
                    entity.pushErrand(cid, pos)
                }
            }
            pathChanged = false

            if (entity.navigation.isIdle) {
                entity.navigation.startMovingAlong(path, 1.0)
            }
        } ?: run {
            LOGGER.info("<<<")
            pathChanged = true
            entity.navigation.stop()
        }

        entity.getNavigation().setSpeed(if (entity.isUsingItem) 0.5 else action.speedModifier)
    }

    private fun setParallelState(state: ErrandState) {
        parallelState = state

    }

    private fun setPrimaryState(state: ErrandState) {
        primaryState = state
    }

    private fun resetParallelState() {
        parallelState = ErrandState.PENDING
        parallel = null
    }

    private fun resetPrimaryState() {
        entity.errandManager.pop()?.let { (cid, pos) ->
            val action = Action.get(cid) as Position
            action.stop(entity, pos)
            entity.navigation.stop()
        }
        resetErrand()
    }

    private fun resetErrand() {
        tickToTestCount = 0
        tickToExecCount = 0
        primaryState = ErrandState.PENDING
        errand = null
        path = null
    }

    private fun selectParallel(peek: Errand): Errand? {
        return PARALLEL_MAP[peek.cid]?.firstOrNull { (pid, chance) ->
            entity.random.nextInt(chance) == 0 && (Action.get(pid) as Parallel).scan(entity) > peek.priority
        }?.let { (pid, _) -> Errand(pid) }
    }

    private enum class ErrandState {
        PENDING,
        TESTED,
        COMPLETED,
    }

    companion object {
        val PARALLEL_MAP =
            mapOf(
                Action.Type.FLEE to setOf(Pair(Action.Type.EAT, 7)),
                Action.Type.TALK to setOf(Pair(Action.Type.DISAGREE, 8), Pair(Action.Type.AGREE, 8)),
            )
        val repelSet = setOf(Action.Type.ATTACK, Action.Type.AIM, Action.Type.CHARGE, Action.Type.DEFEND, Action.Type.LOOK, Action.Type.SHEAR)
    }
}
