package com.settlement.mod.structure

import com.settlement.mod.LOGGER
import com.settlement.mod.action.Action
import com.settlement.mod.block.BlockPredicate
import com.settlement.mod.screen.Response
import com.settlement.mod.util.BlockUtils
import com.settlement.mod.util.Region
import com.settlement.mod.world.Settlement
import net.minecraft.block.BedBlock
import net.minecraft.block.BlockState
import net.minecraft.block.ChestBlock
import net.minecraft.block.DoorBlock
import net.minecraft.block.SlabBlock
import net.minecraft.block.SmokerBlock
import net.minecraft.block.enums.BedPart
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

abstract class Building(
    override val region: Region,
) : Structure() {
    companion object Factory : Structure.Factory {
        fun getStructure(set: Set<Action.Type>): Structure.Type? =
            when (set) {
                setOf(Action.Type.STORE, Action.Type.SIT, Action.Type.SLEEP) -> Structure.Type.HOUSE
                setOf(Action.Type.COOK, Action.Type.STORE) -> Structure.Type.KITCHEN
                setOf(Action.Type.SLEEP) -> Structure.Type.BARRACK
                else -> null
            }

        fun getAction(
            pos: BlockPos,
            world: World,
        ): Action.Type? {
            val state = world.getBlockState(pos)
            return when (state.block) {
                is BedBlock -> {
                    if (state.get(BedBlock.PART) == BedPart.HEAD) Action.Type.SLEEP else null
                }
                is SmokerBlock -> Action.Type.COOK
                is ChestBlock -> Action.Type.STORE
                is SlabBlock -> Action.Type.SIT
                else -> null
                // is FletchingTableBlock -> return Action.Type.YIELD
                // is AnvilBlock -> return Action.Type.FORGE
                // is GrindstoneBlock -> return Action.Type.REPAIR
                // is AbstractCauldronBlock -> return Action.Type.FILL
                // is BrewingStandBlock -> return Action.Type.BREW
            }
        }

        fun getRegion(
            pos: BlockPos,
            player: PlayerEntity,
        ): Region? {
            // used to find relative to door where to check for enclosed region
            val d = player.world.getBlockState(pos).get(DoorBlock.FACING)
            val r = player.blockPos.getSquaredDistance(pos.offset(d, 1).toCenterPos())
            val l = player.blockPos.getSquaredDistance(pos.offset(d.getOpposite(), 1).toCenterPos())
            val spos =
                if (r > l) {
                    pos.offset(d, 1)
                } else {
                    pos.offset(d.getOpposite())
                }

            val points = BlockUtils.floodFill(player.world, spos, BlockPredicate.BUILDING, null)
            val region = Region(spos, spos)

            points.forEach { edge ->
                region.append(edge)
            }

            if (region.grow().volume() < 125) {
                Response.NOT_ENOUGH_SPACE.send(player)
                return null
            }
            return region
        }

        override fun matches(state: BlockState): Boolean = state.block is DoorBlock

        // TODO: add other exclusions
        override fun validate(
            settlement: Settlement,
            pos: BlockPos,
            player: PlayerEntity,
        ): Boolean =
            if (!settlement.hasStructureInRange(pos, 8.0f, Structure.Type.HOUSE, Structure.Type.KITCHEN, Structure.Type.BARRACK)) {
                true
            } else {
                Response.ANOTHER_STRUCTURE_INSIDE.send(player)
                false
            }

        override fun create(
            pos: BlockPos,
            player: PlayerEntity,
        ): Structure? {
            val region = Building.getRegion(pos, player) ?: return null

            val actionSet: Set<Action.Type> =
                BlockUtils
                    .cuboid(region.lower, region.upper)
                    .mapNotNull { Building.getAction(it, player.world) }
                    .toSet()

            val chosen = getStructure(actionSet)
            if (chosen == null) {
                Response.NOT_ENOUGH_FURNITURE.send(player)
                return null
            }

            val structure =
                when (chosen) {
                    Structure.Type.KITCHEN -> Kitchen(region)
                    Structure.Type.BARRACK -> Barrack(region)
                    Structure.Type.FOUNDRY -> Foundry(region)
                    Structure.Type.HOUSE -> House(region)
                    else -> null
                }

            Response.NEW_STRUCTURE.send(player, chosen.name)
            return structure
        }

        override fun load(region: Region): Structure = House(region)
    }
}
