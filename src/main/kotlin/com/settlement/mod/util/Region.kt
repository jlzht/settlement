package com.settlement.mod.util

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.math.BlockPos

data class Region(
    var lower: BlockPos,
    var upper: BlockPos,
) {

    fun append(block: BlockPos) {
        lower =
            BlockPos(
                minOf(block.x, lower.x),
                minOf(block.y, lower.y),
                minOf(block.z, lower.z),
            )
        upper =
            BlockPos(
                maxOf(block.x, upper.x),
                maxOf(block.y, upper.y),
                maxOf(block.z, upper.z),
            )
    }

    fun shrink(): Region {
        val l = BlockPos(lower.x + 1, lower.y + 1, lower.z + 1)
        val u = BlockPos(upper.x - 1, upper.y - 1, upper.z - 1)
        return Region(l, u)
    }

    fun grow(): Region {
        val l = BlockPos(lower.x - 1, lower.y - 1, lower.z - 1)
        val u = BlockPos(upper.x + 1, upper.y + 1, upper.z + 1)
        return Region(l, u)
    }

    fun volume(): Int = (upper.x - lower.x + 1) * (upper.y - lower.y + 1) * (upper.z - lower.z + 1)

    fun center(): BlockPos {
        val middleX = (lower.x + upper.x) / 2
        val middleY = (lower.y + upper.y) / 2
        val middleZ = (lower.z + upper.z) / 2
        return BlockPos(middleX, middleY, middleZ)
    }

    fun contains(point: BlockPos): Boolean =
        point.x >= lower.x &&
            point.x <= upper.x &&
            point.y >= lower.y &&
            point.y <= upper.y &&
            point.z >= lower.z &&
            point.z <= upper.z

    companion object {
        val CODEC: Codec<Region> =
            RecordCodecBuilder.create { instance ->
                instance
                    .group(
                        BlockPos.CODEC.fieldOf("lower").forGetter { it.lower },
                        BlockPos.CODEC.fieldOf("upper").forGetter { it.upper },
                    ).apply(instance, ::Region)
            }
    }
}
