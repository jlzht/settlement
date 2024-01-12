package com.settlement.mod.structure

import com.settlement.mod.action.Action
import com.settlement.mod.action.Errand
import com.settlement.mod.util.BlockIterator
import com.settlement.mod.util.Region
import net.minecraft.block.BlockState
import net.minecraft.block.ChestBlock
import net.minecraft.block.SlabBlock
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

// TODO: give a purpose to this structure
class Campfire(
    val lower: BlockPos,
    val upper: BlockPos,
) : Structure() {
    override val maxCapacity: Int = 4
    override val volumePerResident: Int = 8
    override var type: StructureType = StructureType.CAMPFIRE
    override var region: Region = Region(lower, upper)
    override val residents: MutableList<Int> = MutableList(maxCapacity) { -1 }
    override var capacity: Int
        get() = getResidents().size
        set(value) {
        }

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
        updatedCapacity = errands.count()
        this.updateCapacity()
    }

    private fun getAction(state: BlockState): Action.Type? =
        when (state.block) {
            is SlabBlock -> {
                Action.Type.SIT
            }
            is ChestBlock -> {
                Action.Type.STORE
            }
            else -> null
        }

    companion object {
        fun createStructure(
            pos: BlockPos,
            player: PlayerEntity,
        ): Structure? {
            val world = player.world
            val campfire = Campfire(pos.south(3).west(3).down(), pos.north(3).east(3).up())
            return campfire
        }
    }
}
