package com.settlement.mod.structure

import com.settlement.mod.action.Action
import com.settlement.mod.action.Errand
import com.settlement.mod.screen.Response
import com.settlement.mod.util.Region
import com.settlement.mod.world.Settlement
import net.minecraft.block.BlockState
import net.minecraft.block.CauldronBlock
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

// TODO: give a purpose to this structure
class Hall(
    override val region: Region,
) : Structure() {
    override val maxCapacity: Int = 4
    override var type: Structure.Type = Structure.Type.HALL

    override fun getErrands(vid: Int): List<Errand>? = null

    override fun updateErrands(world: World) {}

    override fun getAction(
        pos: BlockPos,
        world: World,
    ): Action.Type? = null

    companion object Factory : Structure.Factory {
        override fun matches(state: BlockState): Boolean = state.block is CauldronBlock

        override fun validate(
            settlement: Settlement,
            pos: BlockPos,
            player: PlayerEntity,
        ): Boolean =
            if (!settlement.hasStructureInRange(pos, 32.0f, Structure.Type.HALL)) {
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
            val region = Region(pos, pos)
            val hall = Hall(region)
            return hall
        }

        override fun load(region: Region): Structure = Hall(region)

        init {
            Structure.register(Structure.Type.HALL, this)
        }
    }
}
