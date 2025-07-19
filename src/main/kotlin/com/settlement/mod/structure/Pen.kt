package com.settlement.mod.structure

import com.settlement.mod.action.Action
import com.settlement.mod.action.Errand
import com.settlement.mod.block.BlockPredicate
import com.settlement.mod.screen.Response
import com.settlement.mod.util.BlockUtils
import com.settlement.mod.util.Region
import com.settlement.mod.world.Settlement
import net.minecraft.block.BlockState
import net.minecraft.block.FenceGateBlock
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

// TODO: give a purpose to this structure
class Pen(
    override val region: Region,
) : Structure() {
    override val maxCapacity: Int = 1
    override var type: Structure.Type = Structure.Type.PEN

    override fun updateErrands(world: World) {
        if (world.random.nextFloat() > 0.9f) {
            val center = region.center()
            slots.firstOrNull {
                it.errands.add(Errand(Action.Type.REACH, center))
            }
        }
    }

    override fun getAction(
        pos: BlockPos,
        world: World,
    ): Action.Type? = null

    companion object Factory : Structure.Factory {
        override fun matches(state: BlockState): Boolean = state.block is FenceGateBlock

        override fun validate(
            settlement: Settlement,
            pos: BlockPos,
            player: PlayerEntity,
        ): Boolean =
            if (!settlement.hasStructureInRange(pos, 16.0f, Structure.Type.PEN)) {
                true
            } else {
                Response.ANOTHER_STRUCTURE_INSIDE.send(player)
                false
            }

        override fun create(
            pos: BlockPos,
            player: PlayerEntity,
        ): Structure? {
            val region = Region(pos, pos)
            val points = BlockUtils.floodFill(player.world, pos, BlockPredicate.FENCE, null)

            points.forEach { edge ->
                region.append(edge)
            }

            if (region.volume() >= 96) {
                Response.STRUCTURE_IS_TOO_BIG.send(player)
                return null
            }

            val pen = Pen(region)
            Response.NEW_STRUCTURE.send(player, pen.type.name)
            return pen
        }

        override fun load(region: Region): Structure = Pen(region)
    }
}
