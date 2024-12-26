package com.settlement.mod.world

import com.settlement.mod.LOGGER
import com.settlement.mod.action.Action
import com.settlement.mod.entity.mob.AbstractVillagerEntity
import com.settlement.mod.profession.ProfessionType
import com.settlement.mod.structure.StructureType

object SettlementAccessor {
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
        SettlementManager.findSettlementById(entity.errandProvider.alocKey)?.let { settlement ->
            val key = entity.errandProvider.selfKey
            LOGGER.info("REMOVING:", key)
            settlement.removeVillager(key)
        }
    }

    fun getStructureToAttach(
        entity: AbstractVillagerEntity,
        id: Int,
        selector: Boolean?,
    ) {
        SettlementManager.findSettlementById(entity.errandProvider.alocKey)?.let { settlement ->
            settlement.getStructure(id)?.let { structure ->
                LOGGER.info(structure.type.toString())
                LOGGER.info(structure.getResidents().toString())
                if (structure.getResidents().contains(entity.errandProvider.selfKey)) {
                    entity.errandProvider.attachProvider(
                        { key -> structure.getErrands(key) },
                        structure.type == StructureType.HOUSE,
                    )
                }
            } ?: run {
                selector?.let {
                    if (selector) {
                        entity.errandProvider.homeKey = 0
                    } else {
                        entity.errandProvider.workKey = 0
                    }
                } ?: run {
                    entity.errandProvider.freeKey = 0
                }
            }
        }
    }

    fun findStructureToAttach(
        entity: AbstractVillagerEntity,
        type: StructureType,
    ) {
        SettlementManager.findSettlementById(entity.errandProvider.alocKey)?.let { settlement ->
            // TODO: if not structure is available, force a delay of villager request
            settlement.getStructureByType(type)?.let { (id, structure) ->
                // put this somewhere else
                if (structure.isAvailable()) {
                    structure.addResident(entity.errandProvider.selfKey)
                    val selector = if (structure.type == StructureType.HOUSE) true else if (structure.type == StructureType.CAMPFIRE) null else false
                    entity.errandProvider.assignProvider(
                        id,
                        { i -> structure.getErrands(i) },
                        selector
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
