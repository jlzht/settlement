package com.settlement.mod.world

import com.settlement.mod.screen.Response
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtElement
import net.minecraft.nbt.NbtList
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.world.PersistentState
import net.minecraft.world.dimension.DimensionType
import net.minecraft.world.dimension.DimensionTypes

// I want to structure Settlements in any dimension into a graph
class SettlementManager : PersistentState() {
    private val settlements = mutableListOf<Settlement>()

    init {
        this.markDirty()
    }

    fun addSettlement(
        name: String,
        pos: BlockPos,
        player: PlayerEntity,
    ): Settlement? {
        val id = Settlement.getAvailableKey(settlements.map { it.id })
        val entry = player.world.getDimensionEntry()

        getDimensionString(entry)?.let { dim ->
            for (settlement in settlements) {
                if (settlement.name == name) {
                    Response.ANOTHER_SETTLEMENT_HAS_NAME.send(player).also { return null }
                }
                if (settlement.dim == dim) {
                    if (settlement.pos == pos) {
                        Response.PLACE_IS_SETTLEMENT_ALREADY.send(player).also { return null }
                    }
                    if (settlement.pos.getSquaredDistance(pos.toCenterPos()) < 16384.0f) {
                        Response.ANOTHER_SETTLEMENT_NEARBY.send(player).also { return null }
                    }
                }
            }

            Settlement(id, name, pos, dim).let {
                settlements.add(it)
                it.allies[player.getUuid()] = 50
                Response.NEW_SETTLEMENT.send(player, name)
                return it
            }
        } ?: run { return null } // notifies player about invalid dimension
    }

    fun removeSettlement(settlement: Settlement) {
        settlements.remove(settlement)
    }

    fun getSettlements(): MutableList<Settlement> = settlements

    fun findSettlement(id: Int): Settlement? = settlements.find { it.id == id }

    fun clearSettlements() {
        settlements.clear()
    }

    override fun writeNbt(nbt: NbtCompound): NbtCompound {
        nbt.put("Settlements", settlementSerialize())
        return nbt
    }

    fun settlementSerialize(): NbtList {
        val nbtList = NbtList()
        for (settlement in settlements) {
            nbtList.add(settlement.toNbt())
        }
        return nbtList
    }

    var ticksToUpdate: Int = 300

    // TODO: find strategies for skiping settlement ticking
    fun tick() {
        settlements.forEach { settlement ->
            SettlementManager.getWorld(settlement.dim)?.let { world ->
                if (world.isChunkLoaded(settlement.pos)) {
                    settlement.structures.forEach { structure ->
                        if (!structure.value.hasErrands()) {
                            structure.value.updateErrands(world)
                            this.markDirty()
                        }
                    }
                }
            }
        }
    }

    companion object {
        fun createFromNbt(tag: NbtCompound): SettlementManager {
            val state = SettlementManager()
            val settlement = tag.getList("Settlements", NbtElement.COMPOUND_TYPE.toInt())
            for (i in 0 until settlement.size) {
                val data = settlement.getCompound(i)
                val set = Settlement.fromNbt(data)
                state.settlements.add(set)
            }
            return state
        }

        @JvmStatic
        fun getPersistentStateType(): Type<SettlementManager> =
            Type({ SettlementManager() }, { nbt -> SettlementManager.createFromNbt(nbt) }, null)

        private lateinit var instance: SettlementManager

        fun setInstance(instance: SettlementManager) {
            this.instance = instance
        }

        fun getInstance() = instance

        // easy access to dimensions
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

        fun tick() {
            instance.tick()
        }

        fun getDimensionString(entry: RegistryEntry<DimensionType>): Byte? {
            return when {
                entry.matchesKey(DimensionTypes.OVERWORLD) -> 0
                entry.matchesKey(DimensionTypes.THE_NETHER) -> 1
                entry.matchesKey(DimensionTypes.THE_END) -> 2
                else -> return null
            }
        }

        fun findNearestSettlement(entity: LivingEntity): Settlement? {
            val dim = getDimensionString(entity.world.getDimensionEntry())
            return getInstance()
                .getSettlements()
                .filter { it.dim == dim }
                .filter { it.pos.getSquaredDistance(entity.pos) < 16384.0 }
                .minByOrNull { it.pos.getSquaredDistance(entity.pos) }
        }

        fun findSettlementById(sid: Int): Settlement? = getInstance().findSettlement(sid)
    }
}
