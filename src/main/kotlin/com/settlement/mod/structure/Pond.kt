package com.settlement.mod.structure

import com.settlement.mod.action.Action
import com.settlement.mod.action.Errand
import com.settlement.mod.block.BlockPredicate
import com.settlement.mod.screen.Response
import com.settlement.mod.util.BlockUtils
import com.settlement.mod.util.Region
import com.settlement.mod.util.neighbours
import com.settlement.mod.world.Settlement
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.FluidBlock
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.registry.tag.FluidTags
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

class Pond(
    override val region: Region,
) : Structure() {
    override val maxCapacity: Int = 1
    override var type: Structure.Type = Structure.Type.POND

    override fun updateErrands(world: World) {
        val expand = if (region.volume() < 32) 1 else 0
        val available =
            BlockUtils
                .cuboid(region.lower.add(-expand, 0, -expand), region.upper.add(expand, 0, expand))
                .shuffled()
                .take(3)

        slots.firstOrNull()?.let { slot ->
            for (pos in available) {
                getAction(pos, world)?.let {
                    slot.errands.add(Errand(it, pos))
                }
            }
        }
        updateCapacity(1)
    }

    override fun getAction(
        pos: BlockPos,
        world: World,
    ): Action.Type? {
        val state = world.getBlockState(pos)
        return if (state.isOf(Blocks.WATER)) Action.Type.FISH else null
    }

    companion object Factory : Structure.Factory {
        override fun matches(state: BlockState): Boolean = state.block is FluidBlock

        override fun validate(
            settlement: Settlement,
            pos: BlockPos,
            player: PlayerEntity,
        ): Boolean =
            if (!settlement.hasStructureInRange(pos, 8.0f, Structure.Type.POND)) {
                true
            } else {
                Response.ANOTHER_STRUCTURE_INSIDE.send(player)
                false
            }

        override fun create(
            pos: BlockPos,
            player: PlayerEntity,
        ): Structure? {
            val world = player.world
            val check =
                pos.neighbours().all { p ->
                    world.getFluidState(p).isIn(FluidTags.WATER) && world.getBlockState(p.up()).isAir
                }
            if (!check) {
                Response.BLOCKS_MUST_BE_WATER.send(player)
                return null
            }

            val points = BlockUtils.floodFill(world, pos, BlockPredicate.WATER, null)
            if (points.size < 32) {
                Response.NOT_ENOUGH_WATER.send(player)
                return null
            }
            val region = Region(pos.add(-1, 0, -1), pos.add(1, 0, 1))
            val pond = Pond(region)
            Response.NEW_STRUCTURE.send(player, pond.type.name)
            return pond
        }

        override fun load(region: Region): Structure = Pond(region)
    }
}
