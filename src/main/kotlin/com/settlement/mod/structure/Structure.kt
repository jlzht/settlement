package com.settlement.mod.structure

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import com.settlement.mod.LOGGER
import net.minecraft.block.BlockState
import com.settlement.mod.action.Action
import com.settlement.mod.action.Errand
import com.settlement.mod.util.Region
import net.minecraft.world.World

// FIXME: using /kill makes entities keep registered in structure
sealed class Structure {
    abstract val maxCapacity: Int
    abstract val volumePerResident: Int
    abstract val type: StructureType
    abstract val region: Region

    // residents should have a size limited by max capacity
    abstract val residents: MutableList<Int>
    //abstract var capacity: Int
    // var updatedCapacity: Int = 0 // gets new capacity in structure logic
    var currentCapacity: Int = 0 // debugData needs this var
    val errands = mutableListOf<Errand>()

    val capacity: Int
        get() = getResidents().size

    // called inside impl of getErrands in inherited classes
    fun updateCapacity(updatedCapacity: Int) {
        val newCapacity = updatedCapacity
        currentCapacity =
            when {
                newCapacity < 1 -> 1
                newCapacity > maxCapacity -> maxCapacity
                else -> newCapacity
            }
        while (capacity > currentCapacity) {
            val lastIndex = residents.indexOfLast { it != -1 }
            if (lastIndex != -1) {
                residents.removeAt(lastIndex)
            }
        }
    }

    fun hasErrands(): Boolean = errands.isNotEmpty()

    // called periodically by settlement in order to update structure status
    abstract fun updateErrands(world: World)

    // a settler takes the reference of this function and passes its ID to get errands dedicated to itself
    abstract fun getErrands(vid: Int): List<Errand>?
    
    // abstract fun getAction(state: BlockState): Action.Type?

    // a new logic to see if currentCapacity is bellow capacity that is below or equal maxCapacity
    fun isAvailable(): Boolean = capacity < currentCapacity && currentCapacity <= maxCapacity

    // can this be done better
    fun addResident(vid: Int) {
        residents.set(residents.indexOfFirst { it == -1 }, vid)
    }

    fun removeResident(vid: Int) {
        residents.set(residents.indexOf(vid), -1)
    }

    fun getResidentIndex(vid: Int): Int? {
        val index = residents.indexOf(vid)
        return if (index != -1) index else null
    }

    fun sortErrands(
        found: List<Errand>,
        mainType: Action.Type,
    ): List<Errand> {
        val main = found.filter { it.cid == mainType }
        val others = found.filter { it.cid != mainType }

        val sorted = main.toMutableList()

        fun ranges(): List<Pair<Int, Int>> {
            val out = mutableListOf<Pair<Int, Int>>()
            var start = 0
            for ((i, errand) in sorted.withIndex()) {
                if (errand.cid == mainType) {
                    out.add(start to i)
                    start = i + 1
                }
            }
            return out
        }

        for (errand in others) {
            val nearest = main.minByOrNull { it.pos!!.getSquaredDistance(errand.pos) } ?: continue
            for ((start, end) in ranges()) {
                if (!sorted.subList(start, end).any { it.cid == errand.cid }) {
                    sorted.add(start, errand)
                    break
                }
            }
        }

        return sorted
    }

    @JvmName("filteredResidents")
    fun getResidents(): List<Int> = residents.filter { it != -1 }

    // generic serialization
    companion object {
        val CODEC: Codec<Structure> =
            RecordCodecBuilder.create { instance ->
                instance
                    .group(
                        Codec.INT.fieldOf("type").forGetter({ it.type.ordinal }),
                        Region.CODEC.fieldOf("region").forGetter { it.region },
                        Codec.list(Codec.INT).fieldOf("residents").forGetter { it.residents.toList() },
                    ).apply(instance, { type, region, residents ->
                        val structure =
                            getStructure(type, region)
                                ?: throw IllegalStateException("Could not create Structure with type=$type and region=$region")
                        structure.residents.clear()
                        structure.residents.addAll(residents)
                        structure
                    })
            }

        private fun getStructure(
            type: Int,
            region: Region,
        ): Structure? =
            when (StructureType.values().getOrNull(type)) {
                StructureType.BARRACKS -> Building(StructureType.BARRACKS, region)
                StructureType.PEN -> Pen(region)
                StructureType.KITCHEN -> Building(StructureType.KITCHEN, region)
                StructureType.HOUSE -> Building(StructureType.HOUSE, region)
                StructureType.CAMPFIRE -> Campfire(region)
                StructureType.FARM -> Farm(region)
                StructureType.POND -> Pond(region)
                StructureType.TUNNEL -> Tunnel(region)
                else -> {
                    LOGGER.info("Error serializing")
                    null
                }
            }
    }
}
