package com.settlement.mod.structure

import com.settlement.mod.action.Errand
import com.settlement.mod.util.BlockIterator
import com.settlement.mod.util.Region
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

// TODO: give a purpose to this structure
class Pen(
    val lower: BlockPos,
    val upper: BlockPos,
) : Structure() {
    override val maxCapacity: Int = 2
    override val volumePerResident: Int = 48
    override var type: StructureType = StructureType.PEN
    override var region: Region = Region(lower, upper)
    override val residents: MutableList<Int> = MutableList(maxCapacity) { -1 }
    override var capacity: Int
        get() = getResidents().size
        set(value) {
        }

    override fun getErrands(vid: Int): List<Errand>? = null

    override fun updateErrands(world: World) {}

    companion object {
        fun createStructure(
            pos: BlockPos,
            player: PlayerEntity,
        ): Structure? {
            BlockIterator.FLOOD_FILL(player.world, pos, BlockIterator.BUILDING_AVAILABLE_SPACE)?.let { (fenceCount, edges) ->
                val region = Region(pos, pos)
                edges.forEach { edge ->
                    region.append(edge)
                }
                return Pen(region.lower, region.upper)
            }
            return null
        }
    }
}
