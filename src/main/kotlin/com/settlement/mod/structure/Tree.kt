package com.settlement.mod.structure

import com.settlement.mod.LOGGER
import com.settlement.mod.action.Action
import com.settlement.mod.action.Errand
import com.settlement.mod.screen.Response
import com.settlement.mod.util.Region
import net.minecraft.block.BlockState
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.registry.tag.BlockTags
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

// TODO: give a purpose to this structure
class Tree(
    val lower: BlockPos,
    val upper: BlockPos,
) : Structure() {
    override val maxCapacity: Int = 1
    override val volumePerResident: Int = 1
    override var type: StructureType = StructureType.TREE
    override var region: Region = Region(lower, upper)
    override val residents: MutableList<Int> = MutableList(maxCapacity) { -1 }
 
    override fun getErrands(vid: Int): List<Errand>? {
        if (!hasErrands()) return null
        if (!residents.contains(vid)) return emptyList<Errand>()
        return errands
    }

    // if size is bigger than N, indicates that tree is not uniform, so tree should not be chopped
    override fun updateErrands(world: World) {
        var pos = region.center()
        LOGGER.info("{}", pos)
        var hasNextLog = true
        while (hasNextLog) {
            getAction(world.getBlockState(pos))?.let { action ->
                errands.add(Errand(action, pos))
                if (!region.contains(pos)) {
                    region.append(pos)
                    var i = 1

                    val temp = mutableListOf<Errand>()
                    while (world.getBlockState(pos.add(i, 0, 0)).isIn(BlockTags.LEAVES) &&
                        !world.getBlockState(pos.add(i, -1, 0)).isSolid
                    ) {
                        temp.add(Errand(Action.Type.BREAK, pos.add(i, 0, 0)))
                        i++
                    }
                    for (errand in temp.asReversed()) {
                        errands.add(errand)
                    }
                }
                pos = pos.add(0, 1, 0)
            } ?: run {
                hasNextLog = false
            }
        }
        this.updateCapacity(1)
    }

    private fun getAction(state: BlockState): Action.Type? =
        when {
            state.isIn(BlockTags.LOGS_THAT_BURN) -> Action.Type.CHOP
            else -> null
        }

    companion object {
        fun createStructure(
            pos: BlockPos,
            player: PlayerEntity,
        ): Structure? {
            val world = player.world
            // TODO: think of fail cases
            val tree = Tree(pos, pos)
            Response.NEW_STRUCTURE.send(player, tree.type.name)
            return tree
        }
    }
}
