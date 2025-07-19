package com.settlement.mod.structure

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import com.settlement.mod.action.Action
import com.settlement.mod.action.Errand
import com.settlement.mod.util.Region
import com.settlement.mod.world.Settlement
import net.minecraft.block.BlockState
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
// TODO: structures should updates only when entities asks for actions
sealed class Structure {
    abstract val type: Type
    abstract val region: Region
    abstract val maxCapacity: Int

    class Slot(
        var id: Int?,
        val errands: MutableList<Errand> = mutableListOf(),
    )

    val slots: MutableList<Slot> = mutableListOf()

    var currentCapacity: Int = 0

    val capacity: Int
        get() = slots.count { it.id != null }

    protected var markRemoval: Boolean = false

    fun updateCapacity(updatedCapacity: Int) {
        currentCapacity = updatedCapacity.coerceIn(1, maxCapacity)
        while (slots.size < currentCapacity) {
            slots.add(Slot(null))
        }
        while (capacity > currentCapacity) {
            slots.removeLast()
        }
    }

    open fun shouldUpdate(): Boolean = slots.all { it.errands.isEmpty() }

    fun shouldRemove(): Boolean = markRemoval

    abstract fun updateErrands(world: World)

    open fun getErrands(vid: Int): List<Errand>? {
        val slot = slots.firstOrNull { it.id == vid } ?: return null
        // tries to take errands from other residents
        if (slot.errands.isEmpty()) {
            slots.firstOrNull { it.id != vid && it.errands.isEmpty() }?.let {
                val errands = it.errands.toList()
                it.errands.clear()
                return errands
            }
        }
        val errands = slot.errands.toList()
        slot.errands.clear()
        return errands
    }

    fun isAvailable(): Boolean = slots.any { it.id == null }

    fun addResident(vid: Int) {
        if (slots.any { it.id == vid }) return
        slots.firstOrNull { it.id == null }?.id = vid
    }

    fun removeResident(vid: Int) {
        slots.firstOrNull { it.id == vid }?.id = null
    }

    fun hasResident(vid: Int): Boolean = slots.any { it.id == vid }

    fun getResidents(): Set<Int> = slots.mapNotNull { it.id }.toSet()

    fun clearErrands() {
        slots.forEach { slot ->
            slot.errands.clear()
        }
    }

    fun extractErrands(
        found: List<Errand>,
        mainType: Action.Type,
        expectedType: Set<Action.Type>,
    ) {
        val main = found.filter { it.type == mainType }
        val others = found.filter { it.type != mainType }.toMutableList()

        val usedPositions = mutableSetOf<BlockPos>()

        for (slot in slots) {
            for (mainErrand in main) {
                if (mainErrand.pos in usedPositions) continue

                val selected = mutableListOf<Errand>()
                selected.add(mainErrand)

                val available =
                    others
                        .filter { it.type in expectedType && it.pos !in usedPositions }
                        .distinctBy { it.type }

                selected.addAll(available)

                if (expectedType.all { cid -> selected.any { it.type == cid } }) {
                    slot.errands.clear()
                    slot.errands.addAll(selected)

                    usedPositions.addAll(selected.map { it.pos!! })
                    others.removeAll(selected)

                    break
                }
            }
        }
    }

    fun extractErrands(
        found: List<Errand>,
        expectedSize: Int,
    ) {
        val chunks = found.chunked(expectedSize)

        for ((chunk, slot) in chunks.zip(slots)) {
            slot.errands.clear()
            slot.errands.addAll(chunk.asReversed())
        }
    }

    abstract fun getAction(
        pos: BlockPos,
        world: World,
    ): Action.Type?

    enum class Type {
        NONE,
        HOUSE,
        KITCHEN,
        BARRACK,
        BUILDING,
        HALL,
        FARM,
        POND,
        CAMPFIRE,
        TUNNEL,
        FOUNDRY,
        TREE,
        PEN,
    }

    interface Factory {
        fun matches(state: BlockState): Boolean

        fun validate(
            settlement: Settlement,
            pos: BlockPos,
            player: PlayerEntity,
        ): Boolean

        fun create(
            pos: BlockPos,
            player: PlayerEntity,
        ): Structure?

        fun load(region: Region): Structure
    }

    companion object {
        private val registry = mutableMapOf<Type, Factory>()

        fun initialize() {
            register(Structure.Type.FARM, Farm.Factory)
            register(Structure.Type.TUNNEL, Tunnel.Factory)
            register(Structure.Type.TREE, Tree.Factory)
            register(Structure.Type.CAMPFIRE, Campfire.Factory)
            register(Structure.Type.POND, Pond.Factory)
            register(Structure.Type.HALL, Hall.Factory)
            register(Structure.Type.PEN, Pen.Factory)
            register(Structure.Type.BUILDING, Building.Factory)
        }

        fun register(
            type: Type,
            factory: Factory,
        ) {
            registry[type] = factory
        }

        fun create(
            settlement: Settlement,
            pos: BlockPos,
            player: PlayerEntity,
        ): Structure? {
            val state = player.world.getBlockState(pos)
            val matches =
                registry.values
                    .filter { it.matches(state) }

            if (matches.isEmpty()) return null
            val factory =
                matches.find { it.validate(settlement, pos, player) }
                    ?: return null
            return factory.create(pos, player)
        }

        // buildings bypass registry lookup
        fun load(
            type: Type,
            region: Region,
        ): Structure =
            when (type) {
                Structure.Type.BARRACK -> Barrack(region)
                Structure.Type.KITCHEN -> Kitchen(region)
                Structure.Type.HOUSE -> House(region)
                else -> registry[type]!!.load(region)
            }

        val CODEC: Codec<Structure> =
            RecordCodecBuilder.create { instance ->
                instance
                    .group(
                        Codec.INT.fieldOf("type").forGetter { it.type.ordinal },
                        Region.CODEC.fieldOf("region").forGetter { it.region },
                        Codec.list(Codec.INT).fieldOf("residents").forGetter {
                            it.slots.map { slot -> slot.id ?: -1 }
                        },
                    ).apply(instance) { type, region, residents ->
                        Structure.Type.values().getOrNull(type)?.let {
                            Structure.load(it, region).also { structure ->
                                structure.slots.clear()
                                residents.forEach { id ->
                                    structure.slots.add(
                                        Slot(if (id == -1) null else id),
                                    )
                                }
                            }
                        }
                    }
            }
    }
}
