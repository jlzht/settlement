package com.settlement.mod.util

import com.settlement.mod.LOGGER
import com.settlement.mod.action.Action
import net.minecraft.block.FenceGateBlock
import com.settlement.mod.action.Errand
import com.settlement.mod.entity.mob.AbstractVillagerEntity
import net.minecraft.block.Blocks
import net.minecraft.block.DoorBlock
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.ai.NoPenaltyTargeting
import net.minecraft.entity.ai.pathing.Path
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

object Finder {
    fun findWaterBlock(
        pos: BlockPos,
        world: World,
    ): BlockPos? =
        BlockPos
            .findClosest(pos, 8, 5, { wpos ->
                BlockIterator.NEIGHBOURS(wpos).all { world.getBlockState(it).isOf(Blocks.WATER) && world.getBlockState(wpos.up()).isOf(Blocks.AIR) }
            })
            .orElse(null)

    fun findSurfaceBlock(
        pos: BlockPos,
        world: World,
    ): Errand? =
        BlockPos
            .findClosest(pos, 4, 4, { spos ->
                world.getBlockState(spos).isSolid && world.getBlockState(spos.up()).isAir && world.getBlockState(spos.up(2)).isAir
            })
            .orElse(null)
            ?.let {
                return Errand(Action.Type.MOVE, it)
            }

    fun findFleeBlock(
        entity: AbstractVillagerEntity,
        target: LivingEntity,
    ): Errand? {
        NoPenaltyTargeting.findFrom(entity, 12, 4, target.getPos())?.let { t ->
            return Errand(Action.Type.FLEE, BlockPos(t.x.toInt(), t.y.toInt(), t.z.toInt()))
        }
        return null
    }

    fun findWanderBlock(
        entity: AbstractVillagerEntity,
        target: LivingEntity,
    ): Errand? {
        NoPenaltyTargeting.findFrom(entity, 4 + target.world.random.nextInt(8), 4, target.getPos())?.let { t ->
            return Errand(Action.Type.WANDER, BlockPos(t.x.toInt(), t.y.toInt() + 1, t.z.toInt()))
        }
        return null
    }

    fun findSeekBlock(
        entity: AbstractVillagerEntity,
        target: LivingEntity,
    ): Errand? {
        NoPenaltyTargeting.findFrom(entity, 12, 4, target.getPos())?.let { t ->
            return Errand(Action.Type.SEEK, BlockPos(t.x.toInt(), t.y.toInt() + 1, t.z.toInt()))
        }
        return null
    }

    fun findEntranceBlock(
        world: World,
        path: Path,
    ): Errand? {
        for (i in 0 until minOf(path.currentNodeIndex + 2, path.length)) {
            val pathNode = path.getNode(i)
            val entrancePos = BlockPos(pathNode.x, pathNode.y, pathNode.z)
            val state = world.getBlockState(entrancePos)
            LOGGER.info("{}", state)
            //if (state.isAir) continue
            when (state.getBlock()) {
                is DoorBlock -> {
                    if (DoorBlock.canOpenByHand(world, entrancePos)) return Errand(Action.Type.OPEN, entrancePos)
                }
                is FenceGateBlock -> {
                    LOGGER.info("GOT HERE!")
                    if (!state.get(FenceGateBlock.OPEN)) return Errand(Action.Type.OPEN, entrancePos)
                }
                else -> continue
            }
        }
        return null
    }
}
