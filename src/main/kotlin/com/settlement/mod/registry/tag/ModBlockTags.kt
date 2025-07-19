package com.settlement.mod.registry.tag

import com.settlement.mod.MODID
import net.minecraft.block.Block
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.tag.TagKey
import net.minecraft.util.Identifier

object ModBlockTags {
    val ARABLE = tagOf("arable")
    val STORAGE = tagOf("storage")
    val SEDIMENT = tagOf("sediment")
    val STONES = tagOf("stones")
    val COOKS = tagOf("cooks")
    val SMELTERS = tagOf("smelters")
    val SEATINGS = tagOf("seatings")
    val BARRIERS = tagOf("barriers")
    val FENCES = tagOf("fences")
    val HARVESTS = tagOf("harvests")
    val WINDOWS = tagOf("windows")

    private fun tagOf(id: String): TagKey<Block> = TagKey.of(RegistryKeys.BLOCK, Identifier.of(MODID, id))
}
