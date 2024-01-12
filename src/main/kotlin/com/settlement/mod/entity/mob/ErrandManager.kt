package com.settlement.mod.entity.mob

import com.settlement.mod.action.Action
import com.settlement.mod.action.Errand
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtElement
import net.minecraft.nbt.NbtList
import net.minecraft.util.math.BlockPos
import java.util.PriorityQueue

enum class ErrandType {
    POSITION,
    PARALLEL,
}

enum class ErrandSource {
    HOME,
    WORK,
    FREE,
}

enum class Key {
    ALOC, // Key used by settlement
    SELF, // Key used by villager to be identified in settlement logic
    HOME, // Key used by villager to identify its house
    WORK, // Key used by villager to identify its workplace
    FREE, // Key used by villager to identify usage of public spaces
}

// TODO: making a single list containing references to available errands, make a FSM that will filter with errands selectable
// for example if in COMFLICT, it should not look for places to sleep, or if sleeping it should not look for work
class ErrandManager {
    var tiredness = 0.0f
    var satiation = 100.0f
    var happiness = 0.0f

    private val queues =
        mapOf(
            ErrandType.POSITION to PriorityQueue<Errand>(),
            ErrandType.PARALLEL to PriorityQueue<Errand>(),
        )

    private val sources =
        mutableMapOf<ErrandSource, ((Int) -> List<Errand>?)?>(
            ErrandSource.HOME to null,
            ErrandSource.WORK to null,
            ErrandSource.FREE to null,
        )
    private val lookup =
        mutableMapOf(
            Key.ALOC to 0,
            Key.SELF to 0,
            Key.HOME to 0,
            Key.WORK to 0,
            Key.FREE to 0,
        )

    var sdim: Byte = 0 // Will be discarded later

    fun peek(errandType: ErrandType): Errand? = queues[errandType]?.peek()

    fun pop(errand: Errand): Boolean {
        val queue = queues.values.find { it.peek() == errand }
        return queue?.poll() != null
    }

    fun has(actionType: Action.Type): Boolean = queues.any { it.component2().any { it.cid == actionType } == true }

    fun remove(set: Set<Action.Type>): Boolean = queues.any { it.component2().removeIf { it.cid in set } == true }

    fun isEmpty(errandType: ErrandType): Boolean = queues[errandType]?.isEmpty() ?: true

    fun push(
        errandType: ErrandType,
        cid: Action.Type,
        pos: BlockPos?,
        priority: Byte,
        isUnique: Boolean,
    ) {
        val errand = Errand(cid, pos, priority)
        if (isUnique) {
            queues[errandType]?.removeIf { it.cid == cid }
        }
        queues[errandType]?.offer(errand)
    }

    fun clear(errandType: ErrandType) {
        queues[errandType]?.clear()
    }

    fun getProviderErrands(
        errandType: ErrandSource,
        key: Key,
    ): List<Errand>? = sources[errandType]?.invoke(lookup[key]!!)

    fun assignSettlement(
        id: Int,
        key: Int,
    ) {
        lookup[Key.ALOC] = id
        lookup[Key.SELF] = key
    }

    fun assignProvider(
        key: Key,
        value: Int,
        errandSource: ErrandSource,
        provider: ((Int) -> List<Errand>?)?,
    ) {
        lookup[key] = value
        attachProvider(errandSource, provider)
    }

    fun attachProvider(
        errandSource: ErrandSource,
        provider: ((Int) -> List<Errand>?)?,
    ) {
        sources[errandSource] = provider
    }

    fun getKey(key: Key): Int = lookup[key]!!

    fun setKey(
        key: Key,
        value: Int,
    ) {
        lookup[key] = value
    }

    fun hasKey(key: Key): Boolean = lookup[key] != 0

    fun hasProvider(errandSource: ErrandSource): Boolean = sources[errandSource] != null

    fun getDebugData(): List<String> {
        val debugList = mutableListOf<String>()

        debugList.add("T:[%f] - S:[%f] - H[%f]".format(tiredness, satiation, happiness))
        queues.forEach { queue ->
            debugList.add("%s:".format(queue.key.name))
            queue.value.forEach { errand ->
                debugList.add(
                    "ACTION:[%s] -> POS:[X:%d Y:%d Z:%d]".format(errand.cid.name, errand?.pos?.x, errand?.pos?.y, errand?.pos?.z),
                )
            }
        }

        debugList.add(
            "ALOC:[%d] SELF:[%d] HOME:[%d] WORK:[%d] FREE:[%d]".format(
                lookup[Key.ALOC],
                lookup[Key.SELF],
                lookup[Key.HOME],
                lookup[Key.WORK],
                lookup[Key.FREE],
            ),
        )

        return debugList
    }

    fun writeNbt(): NbtCompound =
        NbtCompound().apply {
            putFloat("Tiredness", tiredness)
            putFloat("Satiation", satiation)
            putFloat("Happiness", satiation)
            queues.forEach { (type, queue) ->
                val nbtList = NbtList()
                queue.forEach { nbtList.add(it.toNbt()) }
                put(type.name, nbtList)
            }
            if (hasKey(Key.ALOC)) {
                putInt("AlocKey", lookup[Key.ALOC]!!)
            }
            if (hasKey(Key.SELF)) {
                putInt("SelfKey", lookup[Key.SELF]!!)
            }
            if (hasKey(Key.HOME)) {
                putInt("HomeKey", lookup[Key.HOME]!!)
            }
            if (hasKey(Key.WORK)) {
                putInt("WorkKey", lookup[Key.WORK]!!)
            }
            if (hasKey(Key.FREE)) {
                putInt("FreeKey", lookup[Key.FREE]!!)
            }
            putByte("SDIM", sdim)
        }

    fun readNbt(nbt: NbtCompound) {
        tiredness = nbt.getFloat("Tiredness")
        satiation = nbt.getFloat("Satiation")
        happiness = nbt.getFloat("Happiness")

        ErrandType.values().forEach { type ->
            nbt.getList(type.name, NbtElement.COMPOUND_TYPE.toInt())?.let { nbtList ->
                for (i in 0 until nbtList.size) {
                    val nbtCompound = nbtList.getCompound(i)
                    Errand.fromNbt(nbtCompound).let { queues[type]?.offer(it) }
                }
            }
        }

        lookup[Key.ALOC] = nbt.getInt("AlocKey")
        lookup[Key.SELF] = nbt.getInt("SelfKey")
        lookup[Key.HOME] = nbt.getInt("HomeKey")
        lookup[Key.WORK] = nbt.getInt("WorkKey")
        lookup[Key.FREE] = nbt.getInt("FreeKey")
        sdim = nbt.getByte("SDIM")
    }
}
