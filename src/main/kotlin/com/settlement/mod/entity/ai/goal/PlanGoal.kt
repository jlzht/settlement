package com.settlement.mod.entity.ai.goal

import com.settlement.mod.LOGGER
import com.settlement.mod.entity.mob.AbstractVillagerEntity
import com.settlement.mod.structure.StructureType
import com.settlement.mod.world.SettlementAccessor
import net.minecraft.entity.ai.goal.Goal
import java.util.EnumSet

class PlanGoal(
    private val entity: AbstractVillagerEntity,
) : Goal() {
    private val world = entity.world

    init {
        this.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.JUMP, Goal.Control.LOOK))
    }

    override fun canStart(): Boolean {
        if (entity.random.nextInt(20) != 0) return false
        return entity.errandManager.isEmpty()
    }

    override fun shouldContinue() = false

    override fun start() {
        if (entity.world.isClient) return
        val provider = entity.errandProvider
        if (provider.alocKey == 0) {
            SettlementAccessor.visitSettlement(entity)
            SettlementAccessor.findSettlementToAttach(entity)
        } else {
            if (provider.homeKey == 0) {
                SettlementAccessor.findStructureToAttach(entity, StructureType.HOUSE)
            } else if (!provider.hasHomeProvider()) {
                SettlementAccessor.getStructureToAttach(entity, provider.homeKey, true)
            }

            if (provider.workKey == 0) {
                SettlementAccessor.findStructureToAttach(entity, entity.profession.structureInterest)
            } else if (!provider.hasWorkProvider()) {
                SettlementAccessor.getStructureToAttach(entity, provider.workKey, false)
            }
            // I need to create a public settlement errand provider
            // if (provider.freeKey == 0) {
            //     LOGGER.info("WILL LOOK FOR AVAILABLE TASKS")
            //     SettlementAccessor.findStructureToAttach(entity, StructureType.CAMPFIRE)
            // } else if (!provider.hasFreeProvider()) {
            //     LOGGER.info("WILL ATTACH TO FREE - {}", provider.workKey)
            //     SettlementAccessor.getStructureToAttach(entity, provider.freeKey, null)
            // }

            provider.pull().forEach { (cid, pos) ->
                entity.pushErrand(cid, pos)
            }
        }
    }
}
