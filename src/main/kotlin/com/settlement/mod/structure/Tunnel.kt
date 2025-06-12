package com.settlement.mod.structure

import com.settlement.mod.action.Action
import com.settlement.mod.action.Errand
import com.settlement.mod.screen.Response
import com.settlement.mod.util.BlockIterator
import com.settlement.mod.util.Region
import net.minecraft.block.BlockState
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.registry.tag.BlockTags
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

// TODO: add source for start tunnel search
class Tunnel(
    override val region: Region,
) : Structure() {
    override val maxCapacity: Int = 2
    override val volumePerResident: Int = 1
    override var type: StructureType = StructureType.TUNNEL
    override val residents: MutableList<Int> = MutableList(maxCapacity) { -1 }

    override fun getErrands(vid: Int): List<Errand>? {
        if (!hasErrands()) return null
        if (!residents.contains(vid)) {
            emptyList<Errand>()
        }

        val taken = errands.take(8)
        errands.removeAll(taken)
        return taken
    }

    override fun updateErrands(world: World) {
        BlockIterator.FLOOD_FILL(world, region.lower, BlockIterator.TUNNEL_AVAILABLE_SPACE, false, region)?.let { (_, edges) ->
            edges
                .sortedBy { it.getSquaredDistance(region.lower) }
                .forEach { pos ->
                    getAction(world.getBlockState(pos))?.let { action ->
                        errands.add(Errand(action, pos))
                    }
                }
        }
        this.updateCapacity(1)
    }

    private fun getAction(state: BlockState): Action.Type? =
        when {
            state.isIn(BlockTags.PICKAXE_MINEABLE) -> Action.Type.MINE
            state.isIn(BlockTags.SHOVEL_MINEABLE) -> Action.Type.DIG
            else -> null
        }

    companion object {
        fun createStructure(
            pos: BlockPos,
            player: PlayerEntity,
        ): Structure? {
            val list = listOf(BlockPos(1, 0, 0), BlockPos(0, 0, 1), BlockPos(-1, 0, 0), BlockPos(0, 0, -1))
            list.minByOrNull { player.squaredDistanceTo(pos.add(it).toCenterPos()) }?.let { p ->
                val s = pos.add(-p.getX() * 8, 0, -p.getZ() * 8)
                val z = pos.add(p.getZ(), -1, p.getX())
                val n = pos.add(-p.getZ(), 1, -p.getX())
                val region = Region(z, n)
                region.append(s)
                val tunnel = Tunnel(region)
                Response.NEW_STRUCTURE.send(player, tunnel.type.name)
                return tunnel
            }
            return null
        }
    }
}
