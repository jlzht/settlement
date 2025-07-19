package com.settlement.mod.world

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import com.settlement.mod.entity.mob.EntityController
import com.settlement.mod.network.SettlementDebugData
import com.settlement.mod.network.StructureDebugData
import com.settlement.mod.profession.Profession
import com.settlement.mod.structure.Structure
import it.unimi.dsi.fastutil.ints.IntArrayList
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.BlockPos
import java.util.UUID

// TODO:
// - implement leveling system
class Settlement(
    val id: Int,
    val name: String,
    val pos: BlockPos,
    val dim: Byte,
    val structures: MutableMap<Int, Structure> = mutableMapOf(),
    val residents: MutableList<Int> = mutableListOf(),
    val allies: MutableMap<UUID, Int> = mutableMapOf(),
) {
    var level: Int = 1

    fun createStructure(
        pos: BlockPos,
        player: PlayerEntity,
    ) {
        Structure.create(this, pos, player)?.let(::addStructure)
    }

    fun addStructure(structure: Structure) {
        val key = getAvailableKey(structures.keys)
        structures[key] = structure
    }

    fun getStructureBy(predicate: (Structure) -> Boolean): Pair<Int, Structure>? =
        structures.entries
            .firstOrNull {
                predicate(it.value)
            }?.toPair()

    fun addResident(controller: EntityController) {
        val key = getAvailableKey(residents)
        controller.assignSettlement(id, key)
        residents += key
    }

    fun removeResident(vid: Int) {
        residents.remove(vid)
        structures.values.forEach { it.removeResident(vid) }
    }

    fun addAlly(uuid: UUID): Boolean = allies.putIfAbsent(uuid, 0) == null

    fun adjustReputation(
        uuid: UUID,
        amount: Int,
    ) {
        allies[uuid] = allies.getOrDefault(uuid, 0) + amount
    }

    fun structureAt(pos: BlockPos): Structure.Type? = structures.values.firstOrNull { it.region.contains(pos) }?.type

    fun hasStructureNear(pos: BlockPos): Boolean = structures.values.any { it.region.grow().contains(pos) }

    fun hasStructureNear(
        pos: BlockPos,
        type: Structure.Type,
    ): Boolean = structures.values.any { it.region.grow().contains(pos) && it.type == type }

    fun hasStructureInRange(
        pos: BlockPos,
        range: Float,
    ): Boolean = structures.values.any { pos.getManhattanDistance(it.region.center()) < range }

    fun hasStructureInRange(
        pos: BlockPos,
        range: Float,
        vararg type: Structure.Type,
    ): Boolean =
        structures.values.any {
            pos.getManhattanDistance(it.region.center()) < range && it.type in type
        }

    fun availableProfessions(): List<Profession.Type> =
        when (level) {
            1 -> listOf(Profession.Type.RECRUIT, Profession.Type.LUMBERJACK, Profession.Type.FARMER, Profession.Type.FISHERMAN)
            else -> listOf(Profession.Type.GATHERER, Profession.Type.HUNTER)
        }

    fun getDebugData(): SettlementDebugData =
        SettlementDebugData(
            id,
            structures
                .mapValues { (_, structure) ->
                    StructureDebugData(
                        structure.type.ordinal,
                        IntArrayList(structure.getResidents()),
                        structure.capacity,
                        structure.currentCapacity,
                        structure.region.lower,
                        structure.region.upper,
                        structure.slots.flatMap { it.errands },
                    )
                }.toMutableMap(),
        )

    companion object {
        val CODEC: Codec<Settlement> =
            RecordCodecBuilder.create { instance ->
                instance
                    .group(
                        Codec.INT.fieldOf("id").forGetter(Settlement::id),
                        Codec.STRING.fieldOf("name").forGetter(Settlement::name),
                        BlockPos.CODEC.fieldOf("pos").forGetter(Settlement::pos),
                        Codec.BYTE.fieldOf("dim").forGetter(Settlement::dim),
                        Codec
                            .unboundedMap(Codec.STRING, Structure.CODEC)
                            .fieldOf("structures")
                            .forGetter { it.structures.mapKeys { (k, _) -> k.toString() } },
                        Codec.list(Codec.INT).fieldOf("residents").forGetter(Settlement::residents),
                        Codec
                            .unboundedMap(Codec.STRING, Codec.INT)
                            .fieldOf("allies")
                            .forGetter { it.allies.mapKeys { (uuid, _) -> uuid.toString() } },
                    ).apply(instance) { id, name, pos, dim, structures, residents, allies ->
                        Settlement(
                            id,
                            name,
                            pos,
                            dim,
                            structures.mapKeys { it.key.toInt() }.toMutableMap(),
                            residents.toMutableList(),
                            allies.mapKeys { UUID.fromString(it.key) }.toMutableMap(),
                        )
                    }
            }

        fun getAvailableKey(existing: Collection<Int>): Int {
            var id = 1
            while (id in existing) id++
            return id
        }
    }
}
