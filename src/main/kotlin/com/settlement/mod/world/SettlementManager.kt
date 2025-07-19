package com.settlement.mod.world

import com.settlement.mod.LOGGER
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import com.settlement.mod.screen.Response
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.world.PersistentState
import net.minecraft.world.PersistentStateType
import net.minecraft.world.World
import net.minecraft.world.dimension.DimensionType
import net.minecraft.world.dimension.DimensionTypes

data class SettlementRef(
    val id: Int,
    val pos: BlockPos,
    val dim: Byte,
) {
    companion object {
        val CODEC: Codec<SettlementRef> =
            RecordCodecBuilder.create { instance ->
                instance
                    .group(
                        Codec.INT.fieldOf("id").forGetter(SettlementRef::id),
                        BlockPos.CODEC.fieldOf("pos").forGetter(SettlementRef::pos),
                        Codec.BYTE.fieldOf("dim").forGetter(SettlementRef::dim),
                    ).apply(instance, ::SettlementRef)
            }
    }
}

class SettlementManager : PersistentState() {
    private val settlements = mutableMapOf<SettlementRef, Settlement?>()

    fun createSettlement(
        name: String,
        pos: BlockPos,
        player: PlayerEntity,
    ): Settlement? {
        val dim = getDimensionString(player.world.dimensionEntry) ?: return null

        // val loadedSettlements = getLoadedSettlements()
        // if (loadedSettlements.any { it.name == name }) {
        //     Response.ANOTHER_SETTLEMENT_HAS_NAME.send(player)
        //     return null
        // }

        if (settlements.keys.any { it.dim == dim && it.pos.getSquaredDistance(pos.toCenterPos()) < 16384.0 }) {
            Response.ANOTHER_SETTLEMENT_NEARBY.send(player)
            return null
        }

        val id = Settlement.getAvailableKey(settlements.keys.map { it.id })
        val settlement =
            Settlement(id, name, pos, dim).apply {
                addAlly(player.uuid)
                adjustReputation(player.uuid, 20)
            }
        val ref = SettlementRef(id, pos, dim)

        settlements[ref] = settlement
        markDirty()

        Response.NEW_SETTLEMENT.send(player, name)
        return settlement
    }

    fun removeSettlement(ref: SettlementRef) {
        if (settlements.remove(ref) != null) {
            markDirty()
        }
    }

    fun getLoadedSettlements(): List<Settlement> = settlements.values.filterNotNull()

    fun findLoadedSettlement(id: Int): Settlement? = settlements.values.find { it?.id == id }

    fun findSettlementRef(id: Int): SettlementRef? = settlements.keys.find { it.id == id }

    fun clearSettlements() {
        settlements.clear()
        markDirty()
    }

    companion object {
        val CODEC: Codec<SettlementManager> =
            RecordCodecBuilder.create { instance ->
                instance
                    .group(
                        SettlementRef.CODEC
                            .listOf()
                            .fieldOf("settlement_refs")
                            .forGetter { it.settlements.keys.toList() },
                    ).apply(instance) { refs ->
                        SettlementManager().apply {
                            refs.forEach { ref -> this.settlements[ref] = null }
                        }
                    }
            }

        @JvmStatic
        fun getPersistentStateType(): PersistentStateType<SettlementManager> =
            PersistentStateType("settlement_manager", ::SettlementManager, CODEC, null)

        private lateinit var instance: SettlementManager

        fun setInstance(instance: SettlementManager) {
            this.instance = instance
        }

        fun getInstance() = instance

        private val worlds = mutableMapOf<Byte, ServerWorld>()

        fun getWorld(id: Byte) = worlds[id]

        fun getWorlds(): MutableMap<Byte, ServerWorld> = worlds

        fun setWorld(
            entry: RegistryEntry<DimensionType>,
            world: ServerWorld,
        ) {
            SettlementManager.getDimensionString(entry)?.let { string ->
                worlds[string] = world
            }
        }

        fun loadSettlement(settlement: Settlement) {
            LOGGER.info("Loading settlement: {}", settlement.name)
            val ref = instance.findSettlementRef(settlement.id)
            if (ref != null) {
                instance.settlements[ref] = settlement
            }
        }

        fun unloadSettlement(settlementId: Int) {
            val ref = instance.findSettlementRef(settlementId)
            if (ref != null) {
                LOGGER.info("Loading settlement: {}", instance.settlements[ref]?.name)
                instance.settlements[ref] = null
            }
        }

        fun findNearestLoadedSettlement(
            world: World,
            pos: BlockPos,
        ): Settlement? {
            val nearest = findNearestSettlementRef(world, pos)
            return nearest?.let { ref -> findLoadedSettlementById(ref.id) }
        }

        fun findNearestSettlementRef(
            world: World,
            pos: BlockPos,
        ): SettlementRef? {
            val dim = getDimensionString(world.dimensionEntry)
            return getInstance()
                .settlements.keys
                .filter { it.dim == dim }
                .filter { it.pos.getSquaredDistance(pos) < 16384.0 }
                .minByOrNull { it.pos.getSquaredDistance(pos) }
        }

        fun findLoadedSettlementById(sid: Int): Settlement? = getInstance().findLoadedSettlement(sid)

        fun createSettlement(
            name: String,
            pos: BlockPos,
            player: PlayerEntity,
        ): Settlement? = getInstance().createSettlement(name, pos, player)

        fun getDimensionString(entry: RegistryEntry<DimensionType>): Byte? =
            when {
                entry.matchesKey(DimensionTypes.OVERWORLD) -> 0
                entry.matchesKey(DimensionTypes.THE_NETHER) -> 1
                entry.matchesKey(DimensionTypes.THE_END) -> 2
                else -> null
            }
    }
}
