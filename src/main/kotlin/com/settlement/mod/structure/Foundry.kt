package com.settlement.mod.structure

import com.settlement.mod.action.Action
import com.settlement.mod.action.Errand
import com.settlement.mod.util.BlockUtils
import com.settlement.mod.util.Region
import net.minecraft.block.AnvilBlock
import net.minecraft.block.BlastFurnaceBlock
import net.minecraft.block.SmokerBlock
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

class Foundry(
    region: Region,
) : Building(region) {
    override val type = Structure.Type.FOUNDRY

    override val maxCapacity: Int = 2

    override fun getErrands(vid: Int): List<Errand>? {
        val slot = slots.firstOrNull { it.id == vid } ?: return null
        return slot.errands
    }

    override fun updateErrands(world: World) {
        val pickedErrands = mutableListOf<Errand>()
        BlockUtils.cuboid(region.lower, region.upper).forEach { pos ->
            getAction(pos, world)?.let { action ->
                if (action == Action.Type.COOK) {
                    pickedErrands.add(Errand(Action.Type.REFILL, pos))
                    pickedErrands.add(Errand(Action.Type.COLLECT, pos))
                }
                pickedErrands.add(Errand(action, pos))
            }
        }
        extractErrands(pickedErrands, Action.Type.COOK, setOf(Action.Type.COOK, Action.Type.REFILL, Action.Type.STORE))
        this.updateCapacity(pickedErrands.count { it.type == Action.Type.COOK })
    }

    override fun getAction(
        pos: BlockPos,
        world: World,
    ): Action.Type? {
        val state = world.getBlockState(pos)
        return when (state.block) {
            is AnvilBlock -> Action.Type.REPAIR
            is BlastFurnaceBlock -> Action.Type.SMELT
            else -> null
        }
    }
}
