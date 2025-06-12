package com.settlement.mod.structure

import com.settlement.mod.LOGGER
import com.settlement.mod.action.Action
import com.settlement.mod.action.Errand
import com.settlement.mod.util.BlockIterator
import com.settlement.mod.util.Region
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

// TODO: give a purpose to this structure
class Pen(
    override val region: Region,
) : Structure() {
    override val maxCapacity: Int = 1
    override val volumePerResident: Int = 48
    override var type: StructureType = StructureType.PEN
    override val residents: MutableList<Int> = MutableList(maxCapacity) { -1 }

    override fun getErrands(vid: Int): List<Errand>? = null

    override fun updateErrands(world: World) {
        if (world.random.nextFloat() > 0.7f) {
            val center = region.center()
            errands.add(Errand(Action.Type.REACH, center))
        }
    }

    companion object {
        fun createStructure(
            pos: BlockPos,
            player: PlayerEntity,
        ): Structure? {
            BlockIterator.FLOOD_FILL(player.world, pos, BlockIterator.PEN_AVAILABLE_SPACE, false, null)?.let { (fenceCount, edges) ->
                val region = Region(pos, pos)
                edges.forEach { edge ->
                    region.append(edge)
                }
                return Pen(region)
            }
            return null
        }
    }
}
