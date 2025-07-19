package com.settlement.mod.world

import com.settlement.mod.action.Action
import com.settlement.mod.entity.mob.EntityController
import com.settlement.mod.entity.mob.ErrandSource
import com.settlement.mod.entity.mob.Key
import com.settlement.mod.profession.Profession
import com.settlement.mod.structure.Structure
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

// TODO: add group field for structures
object SettlementAccessor {
    val BUILDING = setOf(Structure.Type.HOUSE, Structure.Type.KITCHEN, Structure.Type.BARRACK)
    val FREEING = setOf(Structure.Type.CAMPFIRE)

    val SOURCE_TO_KEY =
        mapOf(
            ErrandSource.HOME to Key.HOME,
            ErrandSource.WORK to Key.WORK,
            ErrandSource.FREE to Key.FREE,
        )

    @JvmStatic
    fun createSettlement(
        name: String,
        pos: BlockPos,
        player: PlayerEntity,
    ) = SettlementManager.createSettlement(name, pos, player)

    fun findSettlementById(id: Int) = SettlementManager.findLoadedSettlementById(id)

    fun findNearestSettlementRef(
        world: World,
        pos: BlockPos,
    ) = SettlementManager.findNearestSettlementRef(world, pos)

    fun findNearestSettlement(
        world: World,
        pos: BlockPos,
    ) = SettlementManager.findNearestLoadedSettlement(world, pos)

    fun visitSettlement(ctrl: EntityController) {
        SettlementManager.findNearestSettlementRef(ctrl.entity.world, ctrl.entity.blockPos)?.let { ref ->
            ctrl.pushErrand(Action.Type.REACH, ref.pos)
        }
    }

    fun leaveSettlement(ctrl: EntityController) {
        SettlementManager.findLoadedSettlementById(ctrl.keys[Key.ALOC.ordinal])?.let { settlement ->
            settlement.removeResident(ctrl.keys[Key.SELF.ordinal])
        }
    }

    fun findSettlementToAttach(ctrl: EntityController) {
        SettlementManager.findNearestLoadedSettlement(ctrl.entity.world, ctrl.entity.blockPos)?.let { settlement ->
            settlement.addResident(ctrl)
        }
    }

    fun getStructureToAttach(
        ctrl: EntityController,
        source: ErrandSource,
    ) {
        SettlementManager.findLoadedSettlementById(ctrl.keys[Key.ALOC.ordinal])?.let { settlement ->
            val key = SOURCE_TO_KEY[source]!!
            settlement.structures[ctrl.keys[key.ordinal]]?.let { structure ->
                if (structure.slots.any { it.id == ctrl.keys[Key.SELF.ordinal] }) {
                    ctrl.sources[source.ordinal] = { key -> structure.getErrands(key) }
                } else {
                    // dettaches if structure updates not keeping villager Key
                    ctrl.keys[key.ordinal] = 0
                    ctrl.sources[source.ordinal] = null
                }
            } ?: run {
                // dettaches if structure is deleted
                ctrl.keys[key.ordinal] = 0
                ctrl.sources[source.ordinal] = null
            }
        }
    }

    // TODO: if not structure is available, force a delay of villager request
    fun findStructureToAttach(
        ctrl: EntityController,
        type: Structure.Type,
    ) {
        SettlementManager.findLoadedSettlementById(ctrl.keys[Key.ALOC.ordinal])?.let { settlement ->
            settlement.getStructureBy({ it.type == type && it.isAvailable() })?.let { (id, structure) ->
                structure.addResident(ctrl.keys[Key.SELF.ordinal])
                val source =
                    if (BUILDING.contains(type)) {
                        ErrandSource.HOME
                    } else if (FREEING.contains(type)) {
                        ErrandSource.FREE
                    } else {
                        ErrandSource.WORK
                    }

                val key = SOURCE_TO_KEY[source]!!

                ctrl.keys[key.ordinal] = id
                ctrl.sources[source.ordinal] = { i -> structure.getErrands(i) }
            }
            // TODO: increase interval in Structure Producer
        }
        // TODO: increase interval in Structure Producer
    }

    // defines villager profession based on level of nearest settlement (called once on spawn)
    fun setProfession(ctrl: EntityController) {
        SettlementManager.findNearestLoadedSettlement(ctrl.world, ctrl.entity.blockPos)?.let { settlement ->
            ctrl.professionType =
                settlement
                    .availableProfessions()
                    .shuffled()
                    .random()
        } ?: run {
            val base = listOf(Profession.Type.GATHERER, Profession.Type.HUNTER).shuffled()
            ctrl.professionType = base.random()
        }
    }
}
