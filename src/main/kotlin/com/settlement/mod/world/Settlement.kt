package com.settlement.mod.world

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import com.settlement.mod.entity.mob.AbstractVillagerEntity
import com.settlement.mod.network.SettlementDebugData
import com.settlement.mod.network.StructureDebugData
import com.settlement.mod.profession.ProfessionType
import com.settlement.mod.screen.Response
import com.settlement.mod.structure.Building
import com.settlement.mod.structure.Campfire
import com.settlement.mod.structure.Farm
import com.settlement.mod.structure.Tunnel
import com.settlement.mod.structure.Pen
import com.settlement.mod.structure.Pond
import com.settlement.mod.structure.Structure
import com.settlement.mod.structure.StructureType
import com.settlement.mod.structure.Tree
import it.unimi.dsi.fastutil.ints.IntArrayList
import net.minecraft.block.Blocks
import net.minecraft.block.Block
import net.minecraft.block.CampfireBlock
import net.minecraft.block.DoorBlock
import net.minecraft.block.FarmlandBlock
import net.minecraft.block.FenceGateBlock
import net.minecraft.block.FluidBlock
import net.minecraft.block.SaplingBlock
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.BlockPos

// TODO:
// - implement leveling system
// - make pos the median center point (relative to structure centers)
class Settlement(
    val id: Int,
    val name: String,
    val pos: BlockPos,
    val dim: Byte,
    val structures: MutableMap<Int, Structure> = mutableMapOf(),
    val residents: MutableList<Int> = mutableListOf(),
    // val allies: MutableMap<UUID, Int> = mutableMapOf(),
) {
    // TODO: make a levelManager
    var level: Int = 1

    fun createStructure(
        pos: BlockPos,
        player: PlayerEntity,
    ) {
        when (player.world.getBlockState(pos).getBlock()) {
            is FarmlandBlock -> {
                if (
                    player.world.getBlockState(pos.north().west()).isOf(Blocks.FARMLAND) ||
                    player.world.getBlockState(pos.north().east()).isOf(Blocks.FARMLAND) ||
                    player.world.getBlockState(pos.south().east()).isOf(Blocks.FARMLAND) ||
                    player.world.getBlockState(pos.south().west()).isOf(Blocks.FARMLAND)
                ) {
                    Farm.createStructure(pos, player)
                } else {
                    Response.ANOTHER_STRUCTURE_CLOSE.send(player).run { null }
                }
            }
            is FluidBlock -> {
                // make a flag for same type
                if (!isStructureInRange(pos, 8.0f)) {
                    Pond.createStructure(pos, player)
                } else {
                    Response.ANOTHER_STRUCTURE_CLOSE.send(player).run { null }
                }
            }
            is DoorBlock -> {
                if (!isStructureInRegion(pos)) {
                    Building.createStructure(pos, player)
                } else {
                    // TODO -> change to THIS IS ALREADY A BUILDING (get type)
                    Response.ANOTHER_STRUCTURE_INSIDE.send(player).run { null }
                }
            }
            is FenceGateBlock -> {
                if (!isStructureInRange(pos, 8.0f)) {
                    Pen.createStructure(pos, player)
                } else {
                    Response.ANOTHER_STRUCTURE_CLOSE.send(player).run { null }
                }
            }
            is CampfireBlock -> {
                if (!isStructureInRegion(pos)) {
                    Campfire.createStructure(pos, player)
                } else {
                    Response.ANOTHER_STRUCTURE_CLOSE.send(player).run { null }
                }
            }
            is SaplingBlock -> {
                if (!isStructureInRegion(pos)) {
                    Tree.createStructure(pos, player)
                } else {
                    Response.ANOTHER_STRUCTURE_CLOSE.send(player).run { null }
                }
            }
            is Block -> {
                if (
                    player.world.getBlockState(pos).isOf(Blocks.STONE)
                ) {
                    Tunnel.createStructure(pos, player)
                } else {
                    Response.INVALID_BLOCK.send(player, "NÃO É PEDRA KKKK").run { null }
                }
            }
            else -> Response.INVALID_BLOCK.send(player).run { null }
        }?.let {
            this.addStructure(it)
        }
    }

    fun addStructure(structure: Structure) {
        // TODO: create method that finds allies players by UUID and sendMessage to them notifying structure creating
        val key = Settlement.getAvailableKey(structures.map { it.key })
        structures.put(key, structure)
    }

    fun getStructure(id: Int): Structure? = structures[id]

    fun getStructureByType(type: StructureType): Pair<Int, Structure>? =
        structures
            .entries
            .filter { it.value.type == type }
            .randomOrNull()
            ?.toPair()

    fun removeStructure(id: Int) {
        structures.remove(id)
    }

    fun addVillager(entity: AbstractVillagerEntity) {
        val key = Settlement.getAvailableKey(residents.map { it })
        entity.errandManager.assignSettlement(id, key)
        residents.add(key)
    }

    fun removeVillager(id: Int) {
        residents.removeIf { it == id }
        structures.entries
            .filter { id in it.value.residents }
            .forEach { it.value.removeResident(id) }
    }

    fun getStructureInRegion(pos: BlockPos): StructureType? {
        for (structure in structures.values) {
            if (structure.region.contains(pos)) {
                return structure.type
            }
        }
        return null
    }

    fun isStructureInRegion(pos: BlockPos): Boolean {
        for (structure in structures.values) {
            if (structure.region.grow().contains(pos)) {
                return true
            }
        }
        return false
    }

    fun isStructureInRange(
        pos: BlockPos,
        range: Float,
    ): Boolean {
        for (structure in structures.values) {
            if (pos.getManhattanDistance(structure.region.center()) < range) {
                return true
            }
        }
        return false
    }

    fun getProfessionsBySettlementLevel(): List<ProfessionType> {
        val levelProfessionMap =
            mapOf(
                1 to
                    listOf(
                        ProfessionType.MERCHANT,
                        ProfessionType.MINER,
                        ProfessionType.GUARD,
                        ProfessionType.SHEPHERD,
                        ProfessionType.FARMER,
                        ProfessionType.FISHERMAN,
                    ),
            )
        val professions = levelProfessionMap[level] ?: listOf(ProfessionType.GATHERER, ProfessionType.HUNTER)
        return professions
    }

    fun getDebugData(): SettlementDebugData {
        val structures = mutableMapOf<Int, StructureDebugData>()
        this.structures.forEach { structure ->
            structures[structure.key] =
                StructureDebugData(
                    IntArrayList(structure.value.residents),
                    structure.value.capacity,
                    structure.value.currentCapacity,
                    structure.value.region.lower,
                    structure.value.region.upper,
                    structure.value.errands,
                )
        }
        val data = SettlementDebugData(id, structures)
        return data
    }

    companion object {
        val CODEC: Codec<Settlement> =
            RecordCodecBuilder.create { instance ->
                instance
                    .group(
                        Codec.INT.fieldOf("id").forGetter { it.id },
                        Codec.STRING.fieldOf("name").forGetter { it.name },
                        BlockPos.CODEC.fieldOf("pos").forGetter { it.pos },
                        Codec.BYTE.fieldOf("dim").forGetter { it.dim },
                        Codec
                            .unboundedMap(Codec.STRING, Structure.CODEC)
                            .fieldOf("structures")
                            .forGetter { it.structures.mapKeys { (k, _) -> k.toString() } },
                        Codec
                            .list(Codec.INT)
                            .fieldOf("residents")
                            .forGetter { it.residents },
                    ).apply(
                        instance,
                        {
                                id,
                                name,
                                pos,
                                dim,
                                structures,
                                residents,
                            ->
                            val map = structures.mapKeys { (k, _) -> k.toInt() }.toMutableMap()
                            Settlement(id, name, pos, dim, map, residents.toMutableList())
                        },
                    )
            }

        // TODO: find a better impl to get Map ids
        fun getAvailableKey(existingNumbers: List<Int>): Int {
            var idNumber: Int = 0
            do {
                idNumber = ++idNumber
            } while (idNumber in existingNumbers)
            return idNumber
        }
    }
}
