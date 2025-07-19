package com.settlement.mod.block

import com.settlement.mod.registry.tag.ModBlockTags
import net.minecraft.block.BedBlock
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.block.CropBlock
import net.minecraft.block.FarmlandBlock
import net.minecraft.registry.tag.BlockTags
import net.minecraft.registry.tag.TagKey
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

object BlockPredicate {
    val ARABLE = isIn(ModBlockTags.ARABLE)
    val SEDIMENT = isIn(ModBlockTags.SEDIMENT)
    val STONE = isIn(ModBlockTags.STONES)
    val LOG = isIn(BlockTags.LOGS)
    val CROP = isBlockType<CropBlock>()
    val WATER = isBlock(Blocks.WATER)
    val SEAT = isIn(BlockTags.SLABS)
    val STORAGE = isIn(ModBlockTags.STORAGE)
    val BED = isBlockType<BedBlock>()
    val SMELTER = isIn(ModBlockTags.SMELTERS)
    val COOK = isIn(ModBlockTags.COOKS)
    val BARRIER = isIn(ModBlockTags.BARRIERS)
    val HARVEST = isIn(ModBlockTags.BARRIERS)
    val FENCE = isIn(ModBlockTags.FENCES)

    val BUILDING: (World, BlockPos) -> Boolean = { world, pos ->
        world.getBlockState(pos).let { state ->
            (state.isOf(Blocks.AIR) || state.isOf(Blocks.TORCH))
        }
    }

    val EXCAVATION: (World, BlockPos) -> Boolean = { world, pos ->
        world.getBlockState(pos).let { state ->
            state.isOf(Blocks.AIR) || state.isIn(ModBlockTags.SEDIMENT) || state.isIn(ModBlockTags.STONES)
        }
    }

    val OPEN_FARMLAND: (World, BlockPos) -> Boolean = { world, pos ->
        world.getBlockState(pos).let { state ->
            (state.block as? FarmlandBlock)?.let {
                world.getBlockState(pos.up()).isAir && state.get(FarmlandBlock.MOISTURE) >= 5
            } ?: false
        }
    }

    val FARMLAND: (World, BlockPos) -> Boolean = { world, pos ->
        world.getBlockState(pos).block is FarmlandBlock
    }

    val MATURE_CROP: (World, BlockPos) -> Boolean = { world, pos ->
        val state = world.getBlockState(pos.up())
        val block = state.block
        block is CropBlock &&
            block.isMature(state) &&
            world.getBlockState(pos).block is FarmlandBlock
    }

    private fun isIn(tag: TagKey<Block>): (World, BlockPos) -> Boolean =
        { world, pos ->
            world.getBlockState(pos).isIn(tag)
        }

    private inline fun <reified T : Block> isBlockType(): (World, BlockPos) -> Boolean =
        { world, pos ->
            world.getBlockState(pos).block is T
        }

    private fun isBlock(target: Block): (World, BlockPos) -> Boolean =
        { world, pos ->
            world.getBlockState(pos).block == target
        }
}
