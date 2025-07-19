package com.settlement.mod.structure

import com.settlement.mod.action.Action
import com.settlement.mod.action.Errand
import com.settlement.mod.block.BlockPredicate
import com.settlement.mod.screen.Response
import com.settlement.mod.util.BlockUtils
import com.settlement.mod.util.Region
import com.settlement.mod.world.Settlement
import net.minecraft.block.BlockState
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.registry.tag.BlockTags
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

// TODO: add pathing logic to break leaves in the way
class Tree(
    override val region: Region,
) : Structure() {
    override val maxCapacity: Int = 1
    override var type: Structure.Type = Structure.Type.TREE

    override fun updateErrands(world: World) {
        val pickedErrands = mutableListOf<Errand>()

        BlockUtils.cuboid(region.lower, region.upper).forEach { pos ->
            getAction(pos, world)?.let { action ->
                pickedErrands += Errand(action, pos)

                val breakErrands =
                    generateSequence(1) { it + 1 }
                        .takeWhile { i ->
                            world.getBlockState(pos.add(i, 1, 0)).isIn(BlockTags.LEAVES) &&
                                !world.getBlockState(pos.add(i, -1, 0)).isSolid
                        }.map { i -> Errand(Action.Type.BREAK, pos.add(i, 0, 0)) }
                        .toList()
                        .asReversed()

                pickedErrands += breakErrands
            }
        }

        if (pickedErrands.isEmpty() || pickedErrands.count() >= 8) {
            markRemoval = true
            return
        }

        slots.firstNotNullOf {
            pickedErrands.forEach { errand ->
                it.errands.add(errand)
            }
        }

        this.updateCapacity(1)
    }

    override fun getAction(
        pos: BlockPos,
        world: World,
    ): Action.Type? {
        val state = world.getBlockState(pos)
        return when {
            state.isIn(BlockTags.LOGS_THAT_BURN) -> Action.Type.CHOP
            else -> null
        }
    }

    companion object Factory : Structure.Factory {
        override fun matches(state: BlockState): Boolean = state.isIn(BlockTags.LOGS_THAT_BURN)

        override fun validate(
            settlement: Settlement,
            pos: BlockPos,
            player: PlayerEntity,
        ): Boolean =
            if (!settlement.hasStructureNear(pos)) {
                true
            } else {
                Response.ANOTHER_STRUCTURE_INSIDE.send(player)
                false
            }

        override fun create(
            pos: BlockPos,
            player: PlayerEntity,
        ): Structure? {
            val points = BlockUtils.floodFill(player.world, pos, BlockPredicate.LOG, null)
            if (points.size >= 6) {
                Response.TREE_IS_TOO_BIG.send(player)
                return null
            }
            val region = Region(pos, pos)
            points.forEach { point ->
                region.append(point)
            }
            val tree = Tree(region)
            Response.NEW_STRUCTURE.send(player, tree.type.name)
            return tree
        }

        override fun load(region: Region): Structure = Tree(region)
    }
}
