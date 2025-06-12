package com.settlement.mod.structure

import com.settlement.mod.action.Action
import com.settlement.mod.action.Errand
import com.settlement.mod.screen.Response
import com.settlement.mod.util.BlockIterator
import com.settlement.mod.util.Region
import net.minecraft.block.Blocks
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.registry.tag.FluidTags
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

class Pond(
    override val region: Region,
) : Structure() {
    override val maxCapacity: Int = 1
    override val volumePerResident: Int = 16
    override var type: StructureType = StructureType.POND

    override val residents: MutableList<Int> = MutableList(maxCapacity) { -1 }
    
    override fun updateErrands(world: World) {
        BlockIterator.CUBOID(region.lower, region.upper).let { entries ->
            entries.shuffled().take(3).forEach { taken ->
                if (world.getBlockState(taken).isOf(Blocks.WATER)) {
                    errands.add(Errand(Action.Type.FISH, taken))
                }
            }
        }
        updateCapacity(region.volume() / volumePerResident)
    }

    override fun getErrands(vid: Int): List<Errand>? {
        if (!hasErrands()) return null
        if (!residents.contains(vid)) {
            emptyList<Errand>()
        }
        val taken = errands.take(1)
        errands.removeAll(taken)
        return taken
    }

    companion object {
        fun createStructure(
            pos: BlockPos,
            player: PlayerEntity,
        ): Structure? {
            val world = player.world
            val check =
                BlockIterator.NEIGHBOURS(pos).all { p ->
                    world.getFluidState(p).isIn(FluidTags.WATER) && world.getBlockState(p.up()).isAir
                }
            if (!check) {
                Response.BLOCKS_MUST_BE_SOLID.send(player)
                return null
            }

            BlockIterator.FLOOD_FILL(world, pos, BlockIterator.RIVER_AVAILABLE_SPACE, true, null)?.let { (waterCount, _) ->
                if (waterCount < 32) {
                    Response.SMALL_BODY_WATER.send(player)
                    return null
                }
                val region = Region(pos.add(-1, 0, -1), pos.add(1, 0, 1))
                val pond = Pond(region)
                Response.NEW_STRUCTURE.send(player, pond.type.name)
                return pond
            } ?: run {
                // flood fill returns null if to many iteration occurs, in this case it means a river was found!
                val region = Region(pos.add(-1, 0, -1), pos.add(1, 0, 1))
                val pond = Pond(region)
                Response.NEW_STRUCTURE.send(player, pond.type.name)
                return pond
            }
            Response.NOT_ENOUGH_WATER.send(player)
            return null
        }
    }
}
