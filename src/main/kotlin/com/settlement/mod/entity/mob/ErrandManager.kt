package com.settlement.mod.entity.mob

import com.settlement.mod.LOGGER
import com.settlement.mod.action.Action
import com.settlement.mod.action.Errand
import net.minecraft.nbt.NbtCompound
import net.minecraft.util.math.BlockPos
import net.minecraft.nbt.NbtElement
import net.minecraft.nbt.NbtList
import java.util.PriorityQueue

// TODO: add errand serialization
class ErrandManager {
    private val queue = PriorityQueue<Errand>()

    fun peek(): Errand? = queue.peek()

    fun has(type: Action.Type): Boolean = queue.any { it.cid == type }

    fun isEmpty(): Boolean = queue.isEmpty()

    fun pop(): Errand? {
        return queue.poll()
    }

    fun push(
        action: Action.Type,
        pos: BlockPos?,
        priority: Byte,
    ): Boolean {
        if (priority > 0) {
            val errand = Errand(action, pos, priority)
            queue.offer(errand)
            return true
        }
        return false
    }

    fun clear() {
        queue.clear()
    }

    fun toNbt(): NbtCompound =
        NbtCompound().apply {
            val nbtList = NbtList()
            for (element in queue) {
                val data = element.toNbt()
                nbtList.add(data)
            }
            put("ErrandList", nbtList) 
        }

    companion object {
        fun fromNbt(nbt: NbtCompound): ErrandManager {
            val errandManager = ErrandManager()
            nbt.getList("ErrandList", NbtElement.COMPOUND_TYPE.toInt()).let { nbtList ->
                for (i in 0 until nbtList.size) {
                    val nbtCompound = nbtList.getCompound(i)
                    Errand.fromNbt(nbtCompound).let { (action, pos, priority) ->
                      errandManager.push(action, pos, priority)
                    }
                }
            }

            return errandManager
        }
    }
}
