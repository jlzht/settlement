package com.settlement.mod.util

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import java.util.Optional

data class Region(
    var lower: BlockPos,
    var upper: BlockPos,
    var point: BlockPos? = null, // used as source for flood-fill of some structures
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

    fun getDirection(): Direction {
        val axis = getAxis()
        val c = center()
        val origin = point ?: c
        return when (axis) {
            Direction.Axis.X -> if (c.x >= origin.x) Direction.EAST else Direction.WEST
            Direction.Axis.Z -> if (c.z >= origin.z) Direction.SOUTH else Direction.NORTH
            else -> Direction.NORTH
        }
    }

    fun getAxis(): Direction.Axis {
        val dx = upper.x - lower.x
        val dz = upper.z - lower.z
        return if (dx >= dz) Direction.Axis.X else Direction.Axis.Z
    }

    companion object {
        val CODEC: Codec<Region> =
            RecordCodecBuilder.create { instance ->
                instance
                    .group(
                        BlockPos.CODEC.fieldOf("lower").forGetter { it.lower },
                        BlockPos.CODEC.fieldOf("upper").forGetter { it.upper },
                        BlockPos.CODEC.optionalFieldOf("point").forGetter { Optional.ofNullable(it.point) },
                    ).apply(instance) { lower, upper, pointOpt ->
                        Region(lower, upper, pointOpt.orElse(null))
                    }
            }
    }
}
