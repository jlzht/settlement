package com.settlement.mod.structure

import com.settlement.mod.action.Action
import com.settlement.mod.action.Errand
import com.settlement.mod.screen.Response
import com.settlement.mod.util.BlockIterator
import com.settlement.mod.util.Region
import net.minecraft.block.BlockState
import net.minecraft.block.SlabBlock
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

// TODO: give a purpose to this structure
class Campfire(
    override val region: Region,
) : Structure() {
    override val maxCapacity: Int = 4
    override val volumePerResident: Int = 4
    override var type: StructureType = StructureType.CAMPFIRE
    override val residents: MutableList<Int> = MutableList(maxCapacity) { -1 }

    override fun getErrands(vid: Int): List<Errand>? {
        if (!hasErrands()) return null
        if (!residents.contains(vid)) {
            emptyList<Errand>()
        }
        return listOf(errands.removeLast())
    }

    override fun updateErrands(world: World) {
        BlockIterator.CUBOID(region.lower, region.upper).forEach { pos ->
            getAction(world.getBlockState(pos))?.let { action ->
                errands.add(Errand(action, pos))
            }
        }
        this.updateCapacity(errands.count())
    }

    private fun getAction(state: BlockState): Action.Type? =
        when (state.block) {
            is SlabBlock -> Action.Type.SIT
            else -> null
        }

    companion object {
        fun createStructure(
            pos: BlockPos,
            player: PlayerEntity,
        ): Structure? {
            val world = player.world
            // TODO: think of fail cases
            val region = Region(pos.add(-2, 0, -2), pos.add(2, 2, 2))
            val campfire = Campfire(region)
            Response.NEW_STRUCTURE.send(player, campfire.type.name)
            return campfire
        }
    }
}
