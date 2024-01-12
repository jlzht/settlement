package com.settlement.mod.entity.ai.pathing

import com.settlement.mod.entity.mob.AbstractVillagerEntity
import net.minecraft.entity.ai.pathing.MobNavigation
import net.minecraft.entity.ai.pathing.PathNodeNavigator
import net.minecraft.entity.ai.pathing.PathNodeType
import net.minecraft.world.World

class VillagerNavigation(
    entity: AbstractVillagerEntity,
    world: World,
) : MobNavigation(entity, world) {
    override fun createPathNodeNavigator(range: Int): PathNodeNavigator {
        this.nodeMaker =
            VillagerPathNodeMaker().apply {
                setCanEnterOpenDoors(true)
                entity.setPathfindingPenalty(PathNodeType.WATER, 32.0f)
                entity.setPathfindingPenalty(PathNodeType.DOOR_WOOD_CLOSED, 0.0f)
                entity.setPathfindingPenalty(PathNodeType.BREACH, 64.0f)
                entity.setPathfindingPenalty(PathNodeType.FENCE, -1.0f)
                entity.setPathfindingPenalty(PathNodeType.LAVA, -1.0f)
            }
        return PathNodeNavigator(this.nodeMaker, range)
    }

    override fun isAtValidPosition(): Boolean = this.entity.isOnGround || this.entity.isInFluid || this.entity.hasVehicle()
}
