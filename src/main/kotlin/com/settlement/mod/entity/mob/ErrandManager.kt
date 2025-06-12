package com.settlement.mod.entity.mob

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import com.settlement.mod.action.Action
import com.settlement.mod.action.Errand
import net.minecraft.util.math.BlockPos
import java.util.Optional
import java.util.PriorityQueue

enum class ErrandType {
    IDLE,
    DUAL,
    WORK,
    FRAY,
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

// TODO:
// - return a list of existing stats, keys and errands
class ErrandManager {
    var tiredness = 0.0f
    var satiation = 100.0f
    var happiness = 0.0f

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

    private var fray: Errand? = null
    private var idle: Errand? = null
    private var dual: Errand? = null

    private var pendingFray: Errand? = null
    private var pendingIdle: Errand? = null
    private var pendingDual: Errand? = null

    private val work: PriorityQueue<Errand> = PriorityQueue()

    fun pushErrand(
        type: ErrandType,
        cid: Action.Type,
        pos: BlockPos?,
        priority: Byte,
        isUnique: Boolean,
    ) {
        when (type) {
            ErrandType.FRAY -> {
                if (fray == null) {
                    fray = Errand(cid, pos, priority)
                } else {
                    pendingFray = Errand(cid, pos, priority)
                }
                // -----
                idle = null
                pendingIdle = null
                dual = null
                pendingDual = null
                work.clear()
            }
            ErrandType.IDLE -> {
                if (idle == null) {
                    idle = Errand(cid, pos, priority)
                } else {
                    pendingIdle = Errand(cid, pos, priority)
                }
            }
            ErrandType.DUAL -> {
                if (dual == null) {
                    dual = Errand(cid, pos, priority)
                } else {
                    pendingDual = Errand(cid, pos, priority)
                }
            }
            ErrandType.WORK -> {
                if (isUnique) {
                    work.removeIf { it.cid == cid }
                }
                work.offer(Errand(cid, pos, priority))
            }
        }
    }

    fun peekMain(): Errand? = fray ?: idle ?: work.peek()

    fun popMain(): Errand? =
        when {
            fray != null ->
                fray.also {
                    fray = pendingFray
                    pendingFray = null
                }
            idle != null ->
                idle.also {
                    idle = pendingIdle
                    pendingIdle = null
                }
            !work.isEmpty() -> work.poll()
            else -> null
        }

    fun peekDual(): Errand? = dual

    fun popDual(): Errand? =
        dual.also {
            dual = pendingDual
            pendingDual = null
        }

    fun clear() {
        fray = null
        idle = null
        dual = null
        pendingFray = null
        pendingIdle = null
        pendingDual = null
        work.clear()
    }

    fun containsErrand(cid: Action.Type): Boolean {
        val action = Action.get(cid)
        return when (action.type) {
            ErrandType.FRAY -> fray != null && fray!!.cid == cid
            ErrandType.IDLE -> idle != null && idle!!.cid == cid
            ErrandType.DUAL -> dual != null && dual!!.cid == cid
            ErrandType.WORK -> work.any { it.cid == cid }
        }
    }

    fun hasErrands(type: ErrandType): Boolean =
        when (type) {
            ErrandType.FRAY -> fray != null
            ErrandType.IDLE -> idle != null
            ErrandType.DUAL -> dual != null
            ErrandType.WORK -> !work.isEmpty()
        }

    fun getErrand(type: ErrandType): Errand? =
        when (type) {
            ErrandType.FRAY -> fray
            ErrandType.IDLE -> idle
            ErrandType.DUAL -> dual
            ErrandType.WORK -> work.peek()
        }

    fun getProviderErrands(
        errandSource: ErrandSource,
        key: Key,
    ): List<Errand>? {
        lookup[key]?.let { k ->
            sources[errandSource]?.invoke(k)?.let { errands ->
                if (errands.isEmpty()) {
                    // detaches from structure
                    this.setKey(key, 0)
                    this.attachProvider(errandSource, null)
                    return null
                }
                return errands
            }
        }
        return null
    }

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

        fray?.let { errand ->
            debugList.add("FRAY: ACTION:[%s] -> POS:[X:%d Y:%d Z:%d]".format(errand.cid.name, errand.pos?.x, errand.pos?.y, errand.pos?.z))
        }
        idle?.let { errand ->
            debugList.add("IDLE: ACTION:[%s] -> POS:[X:%d Y:%d Z:%d]".format(errand.cid.name, errand.pos?.x, errand.pos?.y, errand.pos?.z))
        }
        dual?.let { errand ->
            debugList.add("DUAL: ACTION:[%s] -> POS:[X:%d Y:%d Z:%d]".format(errand.cid.name, errand.pos?.x, errand.pos?.y, errand.pos?.z))
        }
        work.forEach { errand ->
            debugList.add("WORK: ACTION:[%s] -> POS:[X:%d Y:%d Z:%d]".format(errand.cid.name, errand.pos?.x, errand.pos?.y, errand.pos?.z))
        }

        debugList.add("T:[%f] - S:[%f] - H[%f]".format(tiredness, satiation, happiness))

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

    companion object {
        val CODEC: Codec<ErrandManager> =
            RecordCodecBuilder.create { instance ->
                instance
                    .group(
                        Codec.FLOAT.fieldOf("tiredness").forGetter { it.tiredness },
                        Codec.FLOAT.fieldOf("satiation").forGetter { it.satiation },
                        Codec.FLOAT.fieldOf("happiness").forGetter { it.happiness },
                        Errand.CODEC.optionalFieldOf("fray").forGetter { Optional.ofNullable(it.fray) },
                        Errand.CODEC.optionalFieldOf("idle").forGetter { Optional.ofNullable(it.idle) },
                        Errand.CODEC.optionalFieldOf("dual").forGetter { Optional.ofNullable(it.dual) },
                        Errand.CODEC
                            .listOf()
                            .fieldOf("work")
                            .forGetter { it.work.toList() },
                        Codec.BYTE.fieldOf("sdim").forGetter { it.sdim },
                        Codec.INT.optionalFieldOf("aloc").forGetter { Optional.ofNullable(it.lookup[Key.ALOC]) },
                        Codec.INT.optionalFieldOf("self").forGetter { Optional.ofNullable(it.lookup[Key.SELF]) },
                        Codec.INT.optionalFieldOf("home").forGetter { Optional.ofNullable(it.lookup[Key.HOME]) },
                        Codec.INT.optionalFieldOf("workKey").forGetter { Optional.ofNullable(it.lookup[Key.WORK]) },
                        Codec.INT.optionalFieldOf("free").forGetter { Optional.ofNullable(it.lookup[Key.FREE]) },
                    ).apply(instance) {
                            tiredness,
                            satiation,
                            happiness,
                            frayOpt,
                            idleOpt,
                            dualOpt,
                            workList,
                            sdim,
                            alocOpt,
                            selfOpt,
                            homeOpt,
                            workOpt,
                            freeOpt,
                        ->

                        ErrandManager().apply {
                            this.tiredness = tiredness
                            this.satiation = satiation
                            this.happiness = happiness
                            // forgets pending
                            this.fray = frayOpt.orElse(null)
                            this.idle = idleOpt.orElse(null)
                            this.dual = dualOpt.orElse(null)
                            this.work.addAll(workList)

                            this.sdim = sdim

                            alocOpt.ifPresent { this.setKey(Key.ALOC, it) }
                            selfOpt.ifPresent { this.setKey(Key.SELF, it) }
                            homeOpt.ifPresent { this.setKey(Key.HOME, it) }
                            workOpt.ifPresent { this.setKey(Key.WORK, it) }
                            freeOpt.ifPresent { this.setKey(Key.FREE, it) }
                        }
                    }
            }
    }
}
