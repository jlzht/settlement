package com.settlement.mod.structure

import com.settlement.mod.action.Errand
import com.settlement.mod.util.Region
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

// TODO: give a purpose to this structure
class Hall(
    override val region: Region
) : Structure() {
    override val maxCapacity: Int = 4
    override val volumePerResident: Int = 32
    override var type: StructureType = StructureType.HALL
    override val residents: MutableList<Int> = MutableList(maxCapacity) { -1 }

    override fun getErrands(vid: Int): List<Errand>? = null

    override fun updateErrands(world: World) {}

    companion object {
        fun createStructure(
            pos: BlockPos,
            player: PlayerEntity,
        ): Structure? {
            val world = player.world
            val region = Region(pos, pos)
            val hall = Hall(region)
            return hall
        }
    }
}
