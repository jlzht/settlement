package com.settlement.mod.world

import com.settlement.mod.LOGGER
import com.settlement.mod.action.Action
import com.settlement.mod.entity.mob.AbstractVillagerEntity
import com.settlement.mod.entity.mob.ErrandSource
import com.settlement.mod.entity.mob.Key
import com.settlement.mod.profession.ProfessionType
import com.settlement.mod.structure.StructureType

object SettlementAccessor {
    val HOUSING = setOf(StructureType.HOUSE)
    val FREEING = setOf(StructureType.CAMPFIRE)

    val SOURCE_TO_KEY =
        mapOf(
            ErrandSource.HOME to Key.HOME,
            ErrandSource.WORK to Key.WORK,
            ErrandSource.FREE to Key.FREE,
        )

    fun visitSettlement(entity: AbstractVillagerEntity) {
        SettlementManager.findNearestSettlement(entity)?.let { settlement ->
            entity.pushErrand(Action.Type.REACH, settlement.pos)
        }
    }

    fun findSettlementToAttach(entity: AbstractVillagerEntity) {
        SettlementManager.findNearestSettlement(entity)?.let { settlement ->
            settlement.addVillager(entity)
        }
    }

    fun leaveSettlement(entity: AbstractVillagerEntity) {
        SettlementManager.findSettlementById(entity.errandManager.getKey(Key.ALOC))?.let { settlement ->
            val key = entity.errandManager.getKey(Key.SELF)
            settlement.removeVillager(key)
        }
    }

    fun getStructureToAttach(
        entity: AbstractVillagerEntity,
        errandSource: ErrandSource,
    ) {
        SettlementManager.findSettlementById(entity.errandManager.getKey(Key.ALOC))?.let { settlement ->
            val key = SOURCE_TO_KEY[errandSource]!!
            settlement.getStructure(entity.errandManager.getKey(key))?.let { structure ->
                if (structure.getResidents().contains(entity.errandManager.getKey(Key.SELF))) {
                    entity.errandManager.attachProvider(
                        errandSource,
                        { key -> structure.getErrands(key) },
                    )
                } else {
                    // dettaches if structure updates not keeping villager Key
                    entity.errandManager.setKey(key, 0)
                    entity.errandManager.attachProvider(errandSource, null)
                }
            } ?: run {
                // dettaches if structure is deleted 
                entity.errandManager.setKey(key, 0)
                entity.errandManager.attachProvider(errandSource, null)
            }
        }
    }

    fun findStructureToAttach(
        entity: AbstractVillagerEntity,
        type: StructureType,
    ) {
        SettlementManager.findSettlementById(entity.errandManager.getKey(Key.ALOC))?.let { settlement ->
            // TODO: if not structure is available, force a delay of villager request
            settlement.getStructureByType(type)?.let { (id, structure) ->
                // put this somewhere else
                if (structure.isAvailable()) {
                    structure.addResident(entity.errandManager.getKey(Key.SELF))
                    val source =
                        if (HOUSING.contains(type)) {
                            ErrandSource.HOME
                        } else if (FREEING.contains(type)) {
                            ErrandSource.FREE
                        } else {
                            ErrandSource.WORK
                        }

                    val key = SOURCE_TO_KEY[source]!!

                    entity.errandManager.assignProvider(
                        key,
                        id,
                        source,
                        { i -> structure.getErrands(i) },
                    )
                }
            }
        }
    }

    // defines villager profession based on level of nearest settlement (called once on spawn)
    fun setProfession(entity: AbstractVillagerEntity) {
        SettlementManager.findNearestSettlement(entity)?.let { settlement ->
            val professions = settlement.getProfessionsBySettlementLevel()
            val profession = professions[entity.random.nextInt(professions.size)]
            entity.setProfession(profession)
        } ?: run {
            val base = listOf(ProfessionType.GATHERER, ProfessionType.HUNTER).shuffled()
            entity.setProfession(base[0])
        }
    }
}
