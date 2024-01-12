package com.settlement.mod.structure

import com.settlement.mod.action.Action
import com.settlement.mod.action.Errand
import com.settlement.mod.screen.Response
import com.settlement.mod.util.BlockIterator
import com.settlement.mod.util.Finder
import com.settlement.mod.util.Region
import net.minecraft.block.Blocks
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

class Pond(
    lower: BlockPos,
    upper: BlockPos,
) : Structure() {
    override val maxCapacity: Int = 4
    override val volumePerResident: Int = 15
    override var type: StructureType = StructureType.POND

    override var region: Region = Region(lower, upper)
    override val residents: MutableList<Int> = MutableList(maxCapacity) { -1 }
    override var capacity: Int
        get() = getResidents().size
        set(value) {
        }

    override fun updateErrands(world: World) {
        // add method to get Y height of region
        BlockIterator.CUBOID(region.lower.add(-1, 0, -1), region.upper.add(1, 0, 1)).let { entries ->
            entries.shuffled().take(5).forEach { taken ->
                if (world.getBlockState(taken).isOf(Blocks.WATER)) {
                    errands.add(Errand(Action.Type.FISH, taken))
                    if (region.volume() < 60 && !region.contains(taken)) {
                        region.append(taken)
                    }
                }
            }
        }
        updatedCapacity = region.volume() / volumePerResident
        updateCapacity()
    }

    override fun getErrands(vid: Int): List<Errand>? {
        if (!hasErrands()) return null
        if (!residents.contains(vid)) {
            emptyList<Errand>()
        }
        val taken = errands.take(2)
        errands.removeAll(taken)
        return taken
    }

    companion object {
        fun createStructure(
            pos: BlockPos,
            player: PlayerEntity,
        ): Structure? {
            val world = player.world
            // This check will be removed in the future
            val check = BlockIterator.BOTTOM(pos).all { world.getBlockState(it).isSolid } && world.getBlockState(pos.up()).isAir
            if (!check) {
                Response.BLOCKS_MUST_BE_SOLID.send(player)
                return null
            }

            Finder.findWaterBlock(pos, world)?.let { wpos ->
                BlockIterator.FLOOD_FILL(world, wpos, BlockIterator.RIVER_AVAILABLE_SPACE)?.let { (waterCount, _) ->
                    if (waterCount < 32) {
                        Response.SMALL_BODY_WATER.send(player)
                        return null
                    }
                    val pond = Pond(wpos, wpos)
                    Response.NEW_STRUCTURE.send(player, pond.type.name)
                    return pond
                } ?: run {
                    // flood fill fails and returns null if to many iteration occurs, in this case it means a river was found!
                    val pond = Pond(wpos, wpos)
                    Response.NEW_STRUCTURE.send(player, pond.type.name)
                    return pond
                }
            } ?: run {
                Response.NOT_ENOUGH_WATER.send(player)
                return null
            }
        }
    }
}
