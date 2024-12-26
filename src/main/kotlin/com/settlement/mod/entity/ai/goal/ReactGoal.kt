package com.settlement.mod.entity.ai.goal

import com.settlement.mod.action.Action
import com.settlement.mod.entity.mob.AbstractVillagerEntity
import net.minecraft.entity.ai.goal.Goal
import java.util.EnumSet

class ReactGoal(
    private val entity: AbstractVillagerEntity,
) : Goal() {
    init {
        this.setControls(EnumSet.of(Goal.Control.TARGET))
    }

    override fun canStart(): Boolean = !entity.isSleeping()

    override fun shouldContinue(): Boolean = false

    override fun shouldRunEveryTick(): Boolean = true

    override fun tick() {
        entity.target?.let { target ->
            if (!target.isAlive) {
                entity.target = null
                return
            }
            val peek = entity.errandManager.peek()
            if (peek == null || peek.cid !in combatSet) {
                if (entity.isFighting()) {
                    entity.profession.COMFLICT_ERRAND_PROVIDER(entity, target)
                } else if (peek == null) {
                    entity.profession.PEACEFUL_ERRAND_PROVIDER(entity, target)
                }
            }
        }
    }

    companion object {
        val combatSet = setOf(Action.Type.ATTACK, Action.Type.CHARGE, Action.Type.AIM, Action.Type.FLEE, Action.Type.DEFEND)
        val InteractionSet = setOf(Action.Type.LOOK)
    }
}
