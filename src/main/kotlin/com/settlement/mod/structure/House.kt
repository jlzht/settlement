package com.settlement.mod.structure

import com.settlement.mod.action.Action
import com.settlement.mod.action.Errand
import com.settlement.mod.util.BlockUtils
import com.settlement.mod.util.Region
import net.minecraft.block.BedBlock
import net.minecraft.block.ChestBlock
import net.minecraft.block.SlabBlock
import net.minecraft.block.enums.BedPart
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

class House(
    region: Region,
) : Building(region) {
    override val type = Structure.Type.HOUSE

    override val maxCapacity: Int = 4

    override fun getErrands(vid: Int): List<Errand>? {
        val slot = slots.firstOrNull { it.id == vid } ?: return null
        return slot.errands
    }

    override fun updateErrands(world: World) {
        val pickedErrands = mutableListOf<Errand>()
        BlockUtils.cuboid(region.lower, region.upper).forEach { pos ->
            getAction(pos, world)?.let { action ->
                pickedErrands.add(Errand(action, pos))
            }
        }
        extractErrands(pickedErrands, Action.Type.SLEEP, setOf(Action.Type.SLEEP, Action.Type.SIT, Action.Type.STORE))
        this.updateCapacity(pickedErrands.count { it.type == Action.Type.SLEEP })
    }

    override fun getAction(
        pos: BlockPos,
        world: World,
    ): Action.Type? {
        val state = world.getBlockState(pos)
        return when (state.block) {
            is BedBlock -> {
                if (state.get(BedBlock.PART) == BedPart.HEAD) Action.Type.SLEEP else null
            }
            is ChestBlock -> Action.Type.STORE
            is SlabBlock -> Action.Type.SIT
            else -> null
        }
    }
}
