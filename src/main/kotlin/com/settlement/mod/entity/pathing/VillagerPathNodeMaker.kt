package com.settlement.mod.entity.ai.pathing

import net.minecraft.block.AbstractRailBlock
import net.minecraft.block.Blocks
import net.minecraft.block.DoorBlock
import net.minecraft.block.FenceGateBlock
import net.minecraft.block.LeavesBlock
import net.minecraft.entity.ai.pathing.LandPathNodeMaker
import net.minecraft.entity.ai.pathing.NavigationType
import net.minecraft.entity.ai.pathing.PathNodeType
import net.minecraft.registry.tag.BlockTags
import net.minecraft.registry.tag.FluidTags
import net.minecraft.util.math.BlockPos
import net.minecraft.world.BlockView

class VillagerPathNodeMaker : LandPathNodeMaker() {
    override fun getDefaultNodeType(
        world: BlockView,
        x: Int,
        y: Int,
        z: Int,
    ): PathNodeType = VillagerPathNodeMaker.getLandNodeType(world, BlockPos.Mutable(x, y, z))

    companion object {
        fun getLandNodeType(
            world: BlockView,
            pos: BlockPos.Mutable,
        ): PathNodeType {
            val i = pos.x
            val j = pos.y
            val k = pos.z
            var pathNodeType = VillagerPathNodeMaker.getCommonNodeType(world, pos)

            if (pathNodeType != PathNodeType.OPEN || j < world.bottomY + 1) {
                return pathNodeType
            }

            return when (VillagerPathNodeMaker.getCommonNodeType(world, pos.set(i, j - 1, k))) {
                PathNodeType.OPEN, PathNodeType.WATER, PathNodeType.LAVA, PathNodeType.WALKABLE -> PathNodeType.OPEN
                PathNodeType.FENCE -> PathNodeType.OPEN
                PathNodeType.DAMAGE_FIRE -> PathNodeType.DAMAGE_FIRE
                PathNodeType.DAMAGE_OTHER -> PathNodeType.DAMAGE_OTHER
                PathNodeType.STICKY_HONEY -> PathNodeType.STICKY_HONEY
                PathNodeType.POWDER_SNOW -> PathNodeType.DANGER_POWDER_SNOW
                PathNodeType.DAMAGE_CAUTIOUS -> PathNodeType.DAMAGE_CAUTIOUS
                PathNodeType.TRAPDOOR -> PathNodeType.DANGER_TRAPDOOR
                else -> VillagerPathNodeMaker.getNodeTypeFromNeighbors(world, pos.set(i, j, k), PathNodeType.WALKABLE)
            }
        }

        fun getNodeTypeFromNeighbors(
            world: BlockView,
            pos: BlockPos.Mutable,
            nodeType: PathNodeType,
        ): PathNodeType {
            val i = pos.x
            val j = pos.y
            val k = pos.z

            for (l in -1..1) {
                for (m in -1..1) {
                    for (n in -1..1) {
                        if (l == 0 && n == 0) continue
                        pos.set(i + l, j + m, k + n)
                        val blockState = world.getBlockState(pos)

                        if (blockState.isOf(Blocks.CACTUS) || blockState.isOf(Blocks.SWEET_BERRY_BUSH)) {
                            return PathNodeType.DANGER_OTHER
                        }
                        if (LandPathNodeMaker.inflictsFireDamage(blockState)) {
                            return PathNodeType.DANGER_FIRE
                        }
                        if (world.getFluidState(pos).isIn(FluidTags.WATER)) {
                            return PathNodeType.WATER_BORDER
                        }
                        if (blockState.isOf(Blocks.WITHER_ROSE) || blockState.isOf(Blocks.POINTED_DRIPSTONE)) {
                            return PathNodeType.DAMAGE_CAUTIOUS
                        }
                    }
                }
            }
            return nodeType
        }

        fun getCommonNodeType(
            world: BlockView,
            pos: BlockPos,
        ): PathNodeType {
            val blockState = world.getBlockState(pos)
            val block = blockState.getBlock()
            if (blockState.isAir()) {
                return PathNodeType.OPEN
            }
            if (blockState.isIn(BlockTags.TRAPDOORS) || blockState.isOf(Blocks.LILY_PAD) || blockState.isOf(Blocks.BIG_DRIPLEAF)) {
                return PathNodeType.TRAPDOOR
            }

            if (blockState.isOf(Blocks.POWDER_SNOW)) {
                return PathNodeType.POWDER_SNOW
            }
            if (blockState.isOf(Blocks.CACTUS) || blockState.isOf(Blocks.SWEET_BERRY_BUSH)) {
                return PathNodeType.DAMAGE_OTHER
            }
            if (blockState.isOf(Blocks.HONEY_BLOCK)) {
                return PathNodeType.STICKY_HONEY
            }
            if (blockState.isOf(Blocks.COCOA)) {
                return PathNodeType.COCOA
            }
            if (blockState.isOf(Blocks.WITHER_ROSE) || blockState.isOf(Blocks.POINTED_DRIPSTONE)) {
                return PathNodeType.DAMAGE_CAUTIOUS
            }
            val fluidState = world.getFluidState(pos)
            if (fluidState.isIn(FluidTags.LAVA)) {
                return PathNodeType.LAVA
            }
            if (LandPathNodeMaker.inflictsFireDamage(blockState)) {
                return PathNodeType.DAMAGE_FIRE
            }
            if (block is DoorBlock) {
                val doorBlock = block
                if (blockState.get(DoorBlock.OPEN) == true) {
                    return PathNodeType.DOOR_OPEN
                }
                return if (doorBlock.getBlockSetType().canOpenByHand()) PathNodeType.DOOR_WOOD_CLOSED else PathNodeType.DOOR_IRON_CLOSED
            }
            if (block is AbstractRailBlock) {
                return PathNodeType.RAIL
            }
            if (block is LeavesBlock) {
                return PathNodeType.LEAVES
            }

            if (block is FenceGateBlock) {
                if (blockState.get(DoorBlock.OPEN) == true) {
                    return PathNodeType.DOOR_OPEN
                }
                return PathNodeType.DOOR_WOOD_CLOSED
            }

            if (blockState.isIn(BlockTags.FENCES) || blockState.isIn(BlockTags.WALLS)) {
                return PathNodeType.FENCE
            }

            if (!blockState.canPathfindThrough(world, pos, NavigationType.LAND)) {
                return PathNodeType.BLOCKED
            }
            if (fluidState.isIn(FluidTags.WATER)) {
                return PathNodeType.WATER
            }
            return PathNodeType.OPEN
        }
    }
}
