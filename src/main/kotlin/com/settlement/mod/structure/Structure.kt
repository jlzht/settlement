package com.settlement.mod.structure

import com.settlement.mod.action.Errand
import com.settlement.mod.util.Region
import net.minecraft.nbt.NbtCompound
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

sealed class Structure {
    abstract val maxCapacity: Int
    abstract val volumePerResident: Int
    abstract val type: StructureType
    abstract var region: Region
    abstract val residents: MutableList<Int>
    abstract var capacity: Int
    var updatedCapacity: Int = 0 // gets new capacity in structure logic
    var currentCapacity: Int = 0 // debugData needs this var
    val errands = mutableListOf<Errand>()

    fun updateCapacity() {
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

    abstract fun updateErrands(world: World)

    abstract fun getErrands(vid: Int): List<Errand>?

    fun isAvailable(): Boolean = capacity < currentCapacity && currentCapacity < maxCapacity

    fun addResident(vid: Int) {
        residents.set(residents.indexOfFirst { it == -1 }, vid)
    }

    fun removeResident(vid: Int) {
        residents.set(residents.indexOf(vid), -1)
    }

    fun getResidentIndex(vid: Int): Int = residents.indexOf(vid) + 1

    @JvmName("filteredResidents")
    fun getResidents(): List<Int> = residents.filter { it != -1 }

    fun toNbt(): NbtCompound =
        NbtCompound().apply {
            putInt("StructureType", this@Structure.type.ordinal)
            putInt("StructureCapacity", capacity) // useless
            putIntArray("StructureSettlers", getResidents())
            putInt("StructureAreaLowerX", region.lower.x)
            putInt("StructureAreaLowerY", region.lower.y)
            putInt("StructureAreaLowerZ", region.lower.z)
            putInt("StructureAreaUpperX", region.upper.x)
            putInt("StructureAreaUpperY", region.upper.y)
            putInt("StructureAreaUpperZ", region.upper.z)
        }

    companion object {
        fun fromNbt(nbt: NbtCompound): Pair<Int, Structure>? {
            val lower =
                BlockPos(
                    nbt.getInt("StructureAreaLowerX"),
                    nbt.getInt("StructureAreaLowerY"),
                    nbt.getInt("StructureAreaLowerZ"),
                )
            val upper =
                BlockPos(
                    nbt.getInt("StructureAreaUpperX"),
                    nbt.getInt("StructureAreaUpperY"),
                    nbt.getInt("StructureAreaUpperZ"),
                )
            val structure = getStructure(nbt.getInt("StructureType"), lower, upper)

            structure?.let {
                val key = nbt.getInt("StructureKey")
                nbt.getIntArray("StructureSettlers").forEach { vid -> it.addResident(vid) }
                return Pair(key, it)
            }
            return null
        }

        private fun getStructure(
            structureType: Int,
            lower: BlockPos,
            upper: BlockPos,
        ): Structure? =
            when (structureType) {
                StructureType.KITCHEN.ordinal -> Building(StructureType.KITCHEN, lower, upper)
                StructureType.HOUSE.ordinal -> Building(StructureType.HOUSE, lower, upper)
                StructureType.CAMPFIRE.ordinal -> Campfire(lower, upper)
                StructureType.FARM.ordinal -> Farm(lower, upper)
                StructureType.POND.ordinal -> Pond(lower, upper)
                else -> null
            }
    }
}
