package com.settlement.mod.structure

import com.settlement.mod.action.Action
import com.settlement.mod.action.Errand
import com.settlement.mod.screen.Response
import com.settlement.mod.util.Region
import com.settlement.mod.util.around
import com.settlement.mod.util.neighbours
import com.settlement.mod.world.Settlement
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.registry.tag.BlockTags
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

class Tunnel(
    override val region: Region,
) : Structure() {
    override val maxCapacity: Int = 1
    override var type: Structure.Type = Structure.Type.TUNNEL

    override fun updateErrands(world: World) {
        val pickedErrands = mutableListOf<Errand>()
        region.point?.let { point ->
            val axis = region.getAxis()
            val direction = region.getDirection()
            outer@ for (i in 0..8) {
                val base = point.offset(direction, i).around(axis, true)
                for (pos in base) {
                    getAction(pos, world)?.let { action ->
                        pickedErrands.add(Errand(action, pos))
                    }
                    if (pickedErrands.size >= 16) break@outer
                }
            }
            pickedErrands.sortBy { point.getSquaredDistance(it.pos!!) }
            extractErrands(pickedErrands, 16)
            this.updateCapacity(1)
        } ?: {
            // mark for removal
        }
    }

    override fun getAction(
        pos: BlockPos,
        world: World,
    ): Action.Type? {
        val state = world.getBlockState(pos)
        return when {
            state.isIn(BlockTags.PICKAXE_MINEABLE) -> Action.Type.MINE
            state.isIn(BlockTags.SHOVEL_MINEABLE) -> Action.Type.DIG
            else -> null
        }
    }

    companion object Factory : Structure.Factory {
        override fun matches(state: BlockState): Boolean = state.isOf(Blocks.STONE)

        override fun validate(
            settlement: Settlement,
            pos: BlockPos,
            player: PlayerEntity,
        ): Boolean =
            if (!settlement.hasStructureNear(pos)) {
                true
            } else {
                Response.ANOTHER_STRUCTURE_INSIDE.send(player)
                false
            }

        override fun create(
            pos: BlockPos,
            player: PlayerEntity,
        ): Structure? {
            pos.neighbours().minBy { player.squaredDistanceTo(pos.add(it).toCenterPos()) }.let { p ->
                val s = pos.add(-p.getX() * 8, 0, -p.getZ() * 8)
                val l = pos.add(p.getZ(), -1, p.getX())
                val u = pos.add(-p.getZ(), 1, -p.getX())
                val region = Region(l, u, pos)
                region.append(s)
                val tunnel = Tunnel(region)
                Response.NEW_STRUCTURE.send(player, tunnel.type.name)
                return tunnel
            }
        }

        override fun load(region: Region): Structure = Tunnel(region)
    }
}
