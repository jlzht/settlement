package com.settlement.mod.util

import com.settlement.mod.entity.mob.AbstractVillagerEntity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.ai.NoPenaltyTargeting
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.world.World
import java.util.ArrayDeque
import java.util.Queue

object BlockUtils {
    fun findWalkableBlock(
        entity: AbstractVillagerEntity,
        range: Int = 12,
    ): BlockPos? =
        NoPenaltyTargeting.find(entity, range, 4)?.let { vec ->
            BlockPos(vec.x.toInt(), vec.y.toInt(), vec.z.toInt())
        }

    fun findFleeBlock(
        entity: AbstractVillagerEntity,
        target: LivingEntity,
        range: Int = 8,
    ): BlockPos? =
        NoPenaltyTargeting.findFrom(entity, range, range / 2, target.getPos())?.let { vec ->
            BlockPos(vec.x.toInt(), vec.y.toInt(), vec.z.toInt())
        }

    val NEIGHBOUR_OFFSETS =
        listOf(
            BlockPos(1, 0, 0),
            BlockPos(-1, 0, 0),
            BlockPos(0, 0, 1),
            BlockPos(0, 0, -1),
        )

    val Y_OFFSETS =
        listOf(
            BlockPos(0, 1, 0),
            BlockPos(0, -1, 0),
        )

    val DIAGONAL_OFFSETS =
        listOf(
            BlockPos(1, 0, 1),
            BlockPos(1, 0, -1),
            BlockPos(-1, 0, 1),
            BlockPos(-1, 0, -1),
        )

    val BOTTOM_OFFSETS =
        sequence {
            for (dx in -1..1) {
                for (dz in -1..1) {
                    yield(BlockPos(dx, -1, dz))
                }
            }
        }.toList()

    val TOUCHING_OFFSETS = NEIGHBOUR_OFFSETS + Y_OFFSETS

    val DIRECTION_OFFSETS =
        mapOf(
            Direction.Axis.X to
                listOf(
                    BlockPos(0, -1, -1),
                    BlockPos(0, -1, 0),
                    BlockPos(0, -1, 1),
                    BlockPos(0, 0, -1),
                    BlockPos(0, 0, 1),
                    BlockPos(0, 1, -1),
                    BlockPos(0, 1, 0),
                    BlockPos(0, 1, 1),
                ),
            Direction.Axis.Y to
                listOf(
                    BlockPos(-1, 0, -1),
                    BlockPos(-1, 0, 0),
                    BlockPos(-1, 0, 1),
                    BlockPos(0, 0, -1),
                    BlockPos(0, 0, 1),
                    BlockPos(1, 0, -1),
                    BlockPos(1, 0, 0),
                    BlockPos(1, 0, 1),
                ),
            Direction.Axis.Z to
                listOf(
                    BlockPos(-1, -1, 0),
                    BlockPos(-1, 0, 0),
                    BlockPos(-1, 1, 0),
                    BlockPos(0, -1, 0),
                    BlockPos(0, 1, 0),
                    BlockPos(1, -1, 0),
                    BlockPos(1, 0, 0),
                    BlockPos(1, 1, 0),
                ),
        )

    fun cuboid(
        lower: BlockPos,
        upper: BlockPos,
    ): Sequence<BlockPos> =
        sequence {
            val xRange = minOf(lower.x, upper.x)..maxOf(lower.x, upper.x)
            val yRange = minOf(lower.y, upper.y)..maxOf(lower.y, upper.y)
            val zRange = minOf(lower.z, upper.z)..maxOf(lower.z, upper.z)
            for (x in xRange) {
                for (y in yRange) {
                    for (z in zRange) {
                        yield(BlockPos(x, y, z))
                    }
                }
            }
        }

    fun circumference(
        center: BlockPos,
        radius: Int,
    ): Sequence<BlockPos> =
        sequence {
            val step = 360.0 / (30 * radius) // tweak resolution here
            var angle = 0.0
            while (angle < 360.0) {
                val rad = Math.toRadians(angle)
                val dx = Math.round((Math.cos(rad) * radius)).toInt()
                val dz = Math.round((Math.sin(rad) * radius)).toInt()
                yield(center.add(dx, 0, dz))
                angle += step
            }
        }

    fun floodFill(
        world: World,
        start: BlockPos,
        check: (World, BlockPos) -> Boolean,
        region: Region? = null,
        maxBlocks: Int = 512,
    ): Set<BlockPos> {
        if (!check(world, start)) {
            return emptySet()
        }

        val queue: Queue<BlockPos> = ArrayDeque()
        val filledBlocks = mutableSetOf<BlockPos>()

        queue.add(start)
        filledBlocks.add(start)

        while (queue.isNotEmpty()) {
            if (filledBlocks.size >= maxBlocks) break

            val current = queue.poll()

            current.touching().forEach { neighbor ->
                if (region != null && !region.contains(neighbor)) {
                    return@forEach
                }

                if (check(world, neighbor)) {
                    if (filledBlocks.add(neighbor)) {
                        queue.add(neighbor)
                    }
                }
            }
        }

        return filledBlocks
    }
}

fun BlockPos.neighbours(): List<BlockPos> = BlockUtils.NEIGHBOUR_OFFSETS.map(::add)

fun BlockPos.touching(): List<BlockPos> = BlockUtils.TOUCHING_OFFSETS.map(::add)

fun BlockPos.diagonals(): List<BlockPos> = BlockUtils.DIAGONAL_OFFSETS.map(::add)

fun BlockPos.bottom(): List<BlockPos> = BlockUtils.BOTTOM_OFFSETS.map(::add)

fun BlockPos.around(
    axis: Direction.Axis,
    self: Boolean = false,
): List<BlockPos> {
    val offsets = BlockUtils.DIRECTION_OFFSETS[axis] ?: emptyList()
    val positions = offsets.map { this.add(it) }
    return if (self) positions + this else positions
}
