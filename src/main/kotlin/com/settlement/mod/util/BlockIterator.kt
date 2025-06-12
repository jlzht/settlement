package com.settlement.mod.util

import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.HorizontalFacingBlock
import net.minecraft.block.Waterloggable
import net.minecraft.registry.tag.BlockTags
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import java.util.ArrayDeque
import java.util.Queue
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

typealias CellPredicate = (BlockPos, BlockState, HashSet<BlockPos>) -> Boolean

object BlockIterator {
    private val neighboursOffsets =
        setOf(
            BlockPos(1, 0, 0),
            BlockPos(-1, 0, 0),
            BlockPos(0, 0, 1),
            BlockPos(0, 0, -1),
        )

    val NEIGHBOURS: (BlockPos) -> List<BlockPos> = { b ->
        neighboursOffsets.map { offset -> b.add(offset) }
    }
    private val touchingOffsets =
        setOf(
            BlockPos(0, 0, -1),
            BlockPos(0, 0, 1),
            BlockPos(1, 0, 0),
            BlockPos(-1, 0, 0),
            BlockPos(0, 1, 0),
            BlockPos(0, -1, 0),
        )

    val TOUCHING: (BlockPos) -> List<BlockPos> = { b ->
        touchingOffsets.map { offset -> b.add(offset) }
    }

    private val bottomOffsets =
        setOf(
            BlockPos(0, -1, 0),
            BlockPos(0, -1, -1),
            BlockPos(0, -1, 1),
            BlockPos(1, -1, 0),
            BlockPos(-1, -1, 0),
            BlockPos(1, -1, -1),
            BlockPos(-1, -1, -1),
            BlockPos(1, -1, 1),
            BlockPos(-1, -1, 1),
        )

    val BOTTOM: (BlockPos) -> List<BlockPos> = { b ->
        bottomOffsets.map { offset -> b.add(offset) }
    }

    val CUBOID: (BlockPos, BlockPos) -> Iterable<BlockPos> = { lower, upper ->
        val minX = minOf(lower.x, upper.x)
        val minY = minOf(lower.y, upper.y)
        val minZ = minOf(lower.z, upper.z)
        val maxX = maxOf(lower.x, upper.x)
        val maxY = maxOf(lower.y, upper.y)
        val maxZ = maxOf(lower.z, upper.z)

        Iterable {
            object : AbstractIterator<BlockPos>() {
                private var x = minX
                private var y = minY
                private var z = minZ

                override fun computeNext() {
                    if (y > maxY) {
                        done()
                        return
                    }

                    val pos = BlockPos(x, y, z)
                    setNext(pos)

                    x++
                    if (x > maxX) {
                        x = minX
                        z++
                        if (z > maxZ) {
                            z = minZ
                            y++
                        }
                    }
                }
            }
        }
    }

    val CIRCUMFERENCE: (BlockPos, Int) -> Iterable<BlockPos> = { center, radius ->
        Iterable {
            object : AbstractIterator<BlockPos>() {
                private var angle = 0.0
                private val angleIncrement = 360.0 / (30 * radius)

                override fun computeNext() {
                    if (angle >= 360) {
                        done()
                        return
                    }
                    val xOffset = (Math.cos(Math.toRadians(angle)) * radius).roundToInt()
                    val zOffset = (Math.sin(Math.toRadians(angle)) * radius).roundToInt()
                    setNext(center.add(xOffset, 0, zOffset))
                    angle += angleIncrement
                }
            }
        }
    }

    val TILLABLE_BLOCKS = setOf(Blocks.DIRT_PATH, Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT, Blocks.GRASS_BLOCK)

    val BUILDING_AVAILABLE_SPACE: CellPredicate = { pos, state, cached ->
        (
            state.isOf(Blocks.AIR) ||
                (
                    state.getBlock() is HorizontalFacingBlock &&
                        !state.isIn(BlockTags.TRAPDOORS) &&
                        !state.isIn(BlockTags.FENCE_GATES)
                ) ||
                (state.getBlock() is Waterloggable && cached.contains(pos.up()))
        )
    }

    val RIVER_AVAILABLE_SPACE: CellPredicate = { _, state, _ ->
        state.isOf(Blocks.WATER) // add more blocks
    }

    val TREE_AVAILABLE_SPACE: CellPredicate = { _, state, _ ->
        state.isIn(BlockTags.LOGS_THAT_BURN)
    }

    val PEN_AVAILABLE_SPACE: CellPredicate = { _, state, _ ->
        state.isIn(BlockTags.FENCES) || state.isIn(BlockTags.FENCE_GATES)
    }
    
    val TUNNEL_AVAILABLE_SPACE: CellPredicate = { _, state, _ ->
        state.isIn(BlockTags.PICKAXE_MINEABLE) || state.isIn(BlockTags.SHOVEL_MINEABLE) || state.isAir
    }

    val FLOOD_FILL: (World, BlockPos, CellPredicate, Boolean, Region?) -> Pair<Int, Iterable<BlockPos>>? =
        { world, spos, check, limitEdges, region ->

            val queue: Queue<BlockPos> = ArrayDeque()
            val visited = HashSet<BlockPos>()
            var iterations = 0
            var edgesCount = 0
            var totalCount = 0
            val edges = mutableListOf<BlockPos>()

            queue.add(spos)

            while (queue.isNotEmpty()) {
                val current = queue.poll()

                // skip if already visited or out of region
                if (!visited.add(current)) continue
                if (region != null && !region.contains(current)) continue

                iterations++
                if (edgesCount >= 32 || iterations >= 512) break

                var blockedCount = 0
                var freeCount = 0

                BlockIterator.TOUCHING(current).forEach { pos ->
                    if (!visited.contains(pos) && (region == null || region.contains(pos))) {
                        val state = world.getBlockState(pos)
                        if (check(pos, state, visited)) {
                            queue.add(pos)
                            totalCount++
                            freeCount++
                        }
                    } else {
                        if (blockedCount <= 3) blockedCount++
                    }
                }

                if (limitEdges) {
                    if ((blockedCount == 3 && freeCount == 0) ||
                        (blockedCount == 1 && freeCount == 2) ||
                        (blockedCount == 2 && freeCount == 1)
                    ) {
                        edgesCount++
                        edges.add(current)
                    }
                } else {
                    edgesCount++
                    edges.add(current)
                }
            }

            queue.clear()

            if (edgesCount <= 32 && iterations <= 512) {
                Pair(totalCount, edges)
            } else {
                null
            }
        }

    // val FLOOD_FILL: (World, BlockPos, CellPredicate, Boolean) -> Pair<Int, Iterable<BlockPos>>? = { world, spos, check, limitEdges ->
    //    val queue: Queue<BlockPos> = ArrayDeque()
    //    val visited = HashSet<BlockPos>()
    //    var iterations = 0
    //    var edgesCount = 0
    //    var totalCount = 0
    //    queue.add(spos)
    //    val edges = mutableListOf<BlockPos>()
    //    while (queue.isNotEmpty()) {
    //        val current = queue.poll()
    //        if (!visited.contains(current)) {
    //            iterations++
    //            if (edgesCount >= 32 || iterations >= 512) {
    //                break
    //            }
    //            var blockedCount = 0
    //            var freeCount = 0
    //            BlockIterator.TOUCHING(current).forEach { pos ->
    //                if (!visited.contains(pos)) {
    //                    val state = world.getBlockState(pos)
    //                    if (check(pos, state, visited)) {
    //                        queue.add(pos)
    //                        totalCount++
    //                        freeCount++
    //                    }
    //                } else {
    //                    if (blockedCount <= 3) {
    //                        blockedCount++
    //                    }
    //                }
    //            }
    //            // TODO: transform this into a lambda
    //            if (limitEdges) {
    //                // counts possible edges
    //                if ((blockedCount == 3 && freeCount == 0) ||
    //                    (blockedCount == 1 && freeCount == 2) ||
    //                    (blockedCount == 2 && freeCount == 1)
    //                ) {
    //                    edgesCount++
    //                    edges.add(current)
    //                }
    //            } else {
    //                edgesCount++
    //                edges.add(current)
    //            }
    //        }
    //        visited.add(current)
    //    }
    //    queue.clear()
    //    if (edgesCount <= 32 && iterations <= 512) Pair(totalCount, edges) else null
    // }
}
