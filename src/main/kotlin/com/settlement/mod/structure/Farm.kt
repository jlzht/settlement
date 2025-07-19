package com.settlement.mod.structure

import com.settlement.mod.action.Action
import com.settlement.mod.action.Errand
import com.settlement.mod.block.BlockPredicate
import com.settlement.mod.screen.Response
import com.settlement.mod.util.BlockUtils
import com.settlement.mod.util.Region
import com.settlement.mod.util.diagonals
import com.settlement.mod.util.neighbours
import com.settlement.mod.world.Settlement
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.FarmlandBlock
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

class Farm(
    override val region: Region,
) : Structure() {
    override val maxCapacity: Int = 4
    override var type: Structure.Type = Structure.Type.FARM

    override fun updateErrands(world: World) {
        val pickedErrands = mutableListOf<Errand>()
        val expand = if (region.volume() < 128) 1 else 0
        BlockUtils.cuboid(region.lower.add(-expand, 0, -expand), region.upper.add(expand, 0, expand)).forEach { pos ->
            getAction(pos, world)?.let { action ->
                if (!region.contains(pos)) {
                    region.append(pos)
                }
                pickedErrands.add(Errand(action, pos))
            }
        }
        pickedErrands.sortBy { region.center().getSquaredDistance(it.pos) }
        extractErrands(pickedErrands, 8)
        updateCapacity(region.volume() / 32)
    }

    override fun getAction(
        pos: BlockPos,
        world: World,
    ): Action.Type? {
        if (BlockPredicate.OPEN_FARMLAND(world, pos)) {
            return Action.Type.PLANT
        } else {
            if (!BlockPredicate.FARMLAND(world, pos)) {
                if (
                    BlockPredicate.ARABLE(world, pos) &&
                    pos.neighbours().any { BlockPredicate.FARMLAND(world, it) } &&
                    pos.diagonals().any { BlockPredicate.FARMLAND(world, it) }
                ) {
                    return Action.Type.TILL
                }
            } else {
                if (BlockPredicate.MATURE_CROP(world, pos)) {
                    return Action.Type.HARVEST
                } else {
                    if (world.random.nextInt(40) == 0) {
                        return Action.Type.POWDER
                    }
                }
            }
        }
        return null
    }

    companion object Factory : Structure.Factory {
        override fun matches(state: BlockState): Boolean = state.block is FarmlandBlock

        override fun validate(
            settlement: Settlement,
            pos: BlockPos,
            player: PlayerEntity,
        ): Boolean =
            if (!settlement.hasStructureNear(pos, Structure.Type.FARM)) {
                true
            } else {
                Response.ANOTHER_STRUCTURE_INSIDE.send(player)
                false
            }

        override fun create(
            pos: BlockPos,
            player: PlayerEntity,
        ): Structure? {
            if (
                pos.diagonals().any { player.world.getBlockState(it).isOf(Blocks.FARMLAND) }
            ) {
                val region = Region(pos, pos)
                val farm = Farm(region)
                Response.NEW_STRUCTURE.send(player, farm.type.name)
                return farm
            } else {
                Response.NOT_ENOUGHT_MOISTURE.send(player)
                return null
            }
        }

        override fun load(region: Region): Structure = Farm(region)
    }
}
