package com.settlement.mod.structure

import com.settlement.mod.action.Action
import com.settlement.mod.action.Errand
import com.settlement.mod.block.BlockPredicate
import com.settlement.mod.screen.Response
import com.settlement.mod.util.BlockUtils
import com.settlement.mod.util.Region
import com.settlement.mod.world.Settlement
import net.minecraft.block.BlockState
import net.minecraft.block.CampfireBlock
import net.minecraft.block.SlabBlock
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

// TODO: give a purpose to this structure
class Campfire(
    override val region: Region,
) : Structure() {
    override val maxCapacity: Int = 4
    override var type: Structure.Type = Structure.Type.CAMPFIRE

    override fun updateErrands(world: World) {
        val pickedErrands = mutableListOf<Errand>()
        BlockUtils.cuboid(region.lower, region.upper).forEach { pos ->
            getAction(pos, world)?.let { action ->
                pickedErrands.add(Errand(action, pos))
            }
        }

        extractErrands(pickedErrands, Action.Type.SIT, setOf(Action.Type.SIT))
        this.updateCapacity(pickedErrands.count { it.type == Action.Type.SIT })
    }

    override fun getAction(
        pos: BlockPos,
        world: World,
    ): Action.Type? {
        val state = world.getBlockState(pos)
        return when (state.block) {
            is SlabBlock -> Action.Type.SIT
            else -> null
        }
    }

    companion object Factory : Structure.Factory {
        override fun matches(state: BlockState): Boolean = state.block is CampfireBlock

        override fun validate(
            settlement: Settlement,
            pos: BlockPos,
            player: PlayerEntity,
        ): Boolean =
            if (!settlement.hasStructureInRange(pos, 8.0f, Structure.Type.CAMPFIRE)) {
                true
            } else {
                Response.ANOTHER_STRUCTURE_INSIDE.send(player)
                false
            }

        override fun create(
            pos: BlockPos,
            player: PlayerEntity,
        ): Structure? {
            val region = Region(pos.add(-2, 0, -2), pos.add(2, 2, 2))
            if (!BlockUtils.cuboid(region.lower, region.upper).any { BlockPredicate.SEAT(player.world, it) }) {
                Response.NOWHERE_TO_SIT.send(player)
                return null
            }
            val campfire = Campfire(region)
            Response.NEW_STRUCTURE.send(player, campfire.type.name)
            return campfire
        }

        override fun load(region: Region): Structure = Campfire(region)
    }
}
