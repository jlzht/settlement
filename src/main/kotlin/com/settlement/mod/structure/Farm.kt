package com.settlement.mod.structure

import com.settlement.mod.action.Action
import com.settlement.mod.action.Errand
import com.settlement.mod.screen.Response
import com.settlement.mod.util.BlockIterator
import com.settlement.mod.util.Region
import net.minecraft.block.Blocks
import net.minecraft.block.CropBlock
import net.minecraft.block.FarmlandBlock
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import kotlin.collections.emptyList

class Farm(
    val lower: BlockPos,
    val upper: BlockPos,
) : Structure() {
    override val maxCapacity: Int = 4
    override val volumePerResident: Int = 25
    override var type: StructureType = StructureType.FARM
    override var region: Region = Region(lower, upper)
    override val residents: MutableList<Int> = MutableList(maxCapacity) { -1 }
    override var capacity: Int
        get() = getResidents().size
        set(value) {
        }

    override fun updateErrands(world: World) {
        BlockIterator.CUBOID(region.lower.add(-1, 0, -1), region.upper.add(1, 0, 1)).forEach { pos ->
            getFarmAction(pos, world)?.let { action ->
                if (!region.contains(pos)) {
                    region.append(pos)
                }
                val e = Errand(action, pos)
                if (!errands.contains(e)) {
                    errands.add(e)
                }
            }
        }
        updatedCapacity = region.volume() / volumePerResident
        updateCapacity()
    }

    override fun getErrands(vid: Int): List<Errand>? {
        if (!hasErrands()) return null
        if (!residents.contains(vid)) {
            emptyList<Errand>()
        }
        val amount = errands.size / capacity
        val taken = errands.take(amount)
        errands.removeAll(taken)
        return taken
    }

    private fun getFarmAction(
        pos: BlockPos,
        world: World,
    ): Action.Type? {
        val block = world.getBlockState(pos)
        if (block.isOf(Blocks.FARMLAND) && block.get(FarmlandBlock.MOISTURE) >= 5) {
            if (world.getBlockState(pos.up()).isOf(Blocks.AIR)) {
                return Action.Type.PLANT
            } else {
                val up = world.getBlockState(pos.up())
                val upBlock = up.getBlock()
                if (upBlock is CropBlock) {
                    if (upBlock.isMature(up)) {
                        return Action.Type.HARVEST
                    } else if (world.random.nextInt(20) == 0) {
                        return Action.Type.POWDER
                    }
                }
            }
        }
        if (block.isOf(Blocks.GRASS_BLOCK) || block.isOf(Blocks.DIRT)) {
            if (world.getBlockState(pos.up()).isOf(Blocks.AIR)) {
                val north = world.getBlockState(pos.north())
                val south = world.getBlockState(pos.south())
                val east = world.getBlockState(pos.east())
                val west = world.getBlockState(pos.west())
                if (
                    (
                        (north.isOf(Blocks.FARMLAND) && north.get(FarmlandBlock.MOISTURE) >= 5) ||
                            (south.isOf(Blocks.FARMLAND) && south.get(FarmlandBlock.MOISTURE) >= 5)
                    ) &&
                    (
                        (west.isOf(Blocks.FARMLAND) && west.get(FarmlandBlock.MOISTURE) >= 5) ||
                            (east.isOf(Blocks.FARMLAND) && east.get(FarmlandBlock.MOISTURE) >= 5)
                    )
                ) {
                    val northwest = world.getBlockState(pos.north().west())
                    val northeast = world.getBlockState(pos.north().east())
                    val southeast = world.getBlockState(pos.south().east())
                    val southwest = world.getBlockState(pos.south().west())
                    if (
                        northeast.isOf(Blocks.FARMLAND) ||
                        northwest.isOf(Blocks.FARMLAND) ||
                        southeast.isOf(Blocks.FARMLAND) ||
                        southwest.isOf(Blocks.FARMLAND)
                    ) {
                        return Action.Type.TILL
                    }
                }
            }
        }
        return null
    }

    companion object {
        fun createStructure(
            pos: BlockPos,
            player: PlayerEntity,
        ): Structure? {
            val world = player.world
            if (
                world.getBlockState(pos.north().west()).isOf(Blocks.FARMLAND) ||
                world.getBlockState(pos.north().east()).isOf(Blocks.FARMLAND) ||
                world.getBlockState(pos.south().east()).isOf(Blocks.FARMLAND) ||
                world.getBlockState(pos.south().west()).isOf(Blocks.FARMLAND)
            ) {
                val farm = Farm(pos, pos)
                Response.NEW_STRUCTURE.send(player, farm.type.name)
                return farm
            } else {
                Response.NOT_ENOUGHT_MOISTURE.send(player)
                return null
            }
        }
    }
}
