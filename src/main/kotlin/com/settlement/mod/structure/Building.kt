package com.settlement.mod.structure

import com.settlement.mod.action.Action
import com.settlement.mod.action.Errand
import com.settlement.mod.screen.Response
import com.settlement.mod.util.BlockIterator
import com.settlement.mod.util.Region
import net.minecraft.block.BedBlock
import net.minecraft.block.BlockState
import net.minecraft.block.ChestBlock
import net.minecraft.block.DoorBlock
import net.minecraft.block.SlabBlock
import net.minecraft.block.enums.BedPart
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

class Building(
    override var type: StructureType,
    override val region: Region,
) : Structure() {
    override val maxCapacity: Int = 4
    override val volumePerResident: Int = 32

    override val residents: MutableList<Int> = MutableList(maxCapacity) { -1 }

    override fun getErrands(vid: Int): List<Errand>? {
        if (!hasErrands()) return null
        if (!residents.contains(vid)) return emptyList<Errand>()
        val index = getResidentIndex(vid) ?: return null
        extractErrandsByIndex(index)?.let {
            return it
        }
        return null
    }

    override fun updateErrands(world: World) {
        val pickedErrands = mutableListOf<Errand>()
        BlockIterator.CUBOID(region.lower, region.upper).forEach { pos ->
            getAction(world.getBlockState(pos))?.let { action ->
                pickedErrands.add(Errand(action, pos))
            }
        }
        val sortedErrands = sortErrands(pickedErrands, Action.Type.SLEEP)
        errands.addAll(sortedErrands)
        this.updateCapacity(pickedErrands.count { it.cid == Action.Type.SLEEP })
    }

    private fun extractErrandsByIndex(index: Int): List<Errand>? {
        val indicesOfSleep =
            errands
                .withIndex()
                .filter { it.value.cid == Action.Type.SLEEP }
                .map { it.index }

        if (index !in indicesOfSleep.indices) return null

        val startIndex = if (index == 0) 0 else indicesOfSleep[index - 1] + 1
        val endIndex = indicesOfSleep[index]

        return errands.subList(startIndex, endIndex + 1)
    }

    companion object {
        enum class BuildingSet(
            val set: Set<Action.Type>,
        ) {
            HOUSE(setOf(Action.Type.SLEEP, Action.Type.STORE, Action.Type.SIT)),
            BARRACKS(setOf(Action.Type.SLEEP)),
        }

        private fun getAction(state: BlockState): Action.Type? =
            when (state.block) {
                is BedBlock -> {
                    if (state.get(BedBlock.PART) == BedPart.HEAD) Action.Type.SLEEP else null
                }
                is ChestBlock -> Action.Type.STORE
                is SlabBlock -> Action.Type.SIT
                else -> null
                // is FletchingTableBlock -> return Action.Type.YIELD
                // is SmokerBlock -> return Action.Type.COOK
                // is AnvilBlock -> return Action.Type.FORGE
                // is GrindstoneBlock -> return Action.Type.REPAIR
                // is AbstractCauldronBlock -> return Action.Type.FILL
                // is BrewingStandBlock -> return Action.Type.BREW
            }

        fun getBuildingType(
            region: Region,
            world: World,
        ): StructureType? {
            val set = mutableSetOf<Action.Type>()
            BlockIterator.CUBOID(region.lower, region.upper).forEach { pos ->
                getAction(world.getBlockState(pos))?.let { action ->
                    set.add(action)
                }
            }
            BuildingSet.values().forEach { building ->
                if (set.containsAll(building.set)) {
                    return StructureType.valueOf(building.name)
                }
            }
            return null
        }

        fun createStructure(
            pos: BlockPos,
            player: PlayerEntity,
        ): Structure? {
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

            BlockIterator.FLOOD_FILL(player.world, spos, BlockIterator.BUILDING_AVAILABLE_SPACE, true, null)?.let { (lightCount, edges) ->
                val region = Region(spos, spos)
                edges.forEach { edge ->
                    region.append(edge)
                }

                if (region.volume() >= lightCount) {
                    Response.NOT_ENOUGH_LIGHT.send(player)
                    return null
                }

                if (region.grow().volume() < 125) {
                    Response.NOT_ENOUGH_SPACE.send(player)
                    return null
                }
                getBuildingType(region, player.world)?.let { type ->
                    Response.NEW_STRUCTURE.send(player, type.name)
                    return Building(type, region)
                }
            }
            Response.NOT_ENOUGH_FURNITURE.send(player)
            return null
        }
    }
}
