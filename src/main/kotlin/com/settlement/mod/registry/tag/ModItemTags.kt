package com.settlement.mod.registry.tag

import com.settlement.mod.MODID
import net.minecraft.item.Item
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.tag.TagKey
import net.minecraft.util.Identifier

object ModItemTags {
    val COOKABLES = tagOf("cookables")
    val SMELTABLES = tagOf("smeltables")
    val CURRENCIES = tagOf("currencies")
    val FERTILIZERS = tagOf("fertilizers")
    val SEEDS = tagOf("seeds")
    val FISHES = tagOf("fishes")
    val RODS = tagOf("rods")
    val FOODS = tagOf("food")
    val ORES = tagOf("ores")
    val SHEARS = tagOf("shears")
    val FUELS = tagOf("fuels")
    val PICKAXES = tagOf("pickaxes")
    val SHOVELS = tagOf("shovels")
    val AXES = tagOf("axes")
    val HOES = tagOf("hoes")

    val ARMORS = tagOf("armors")

    val BASIC_COMBAT = tagOf("basic_combat")
    val INTERMEDIATE_COMBAT = tagOf("intermediate_combat")
    val ADVANCED_COMBAT = tagOf("advanced_combat") // include netherite gear

    private fun tagOf(id: String): TagKey<Item> = TagKey.of(RegistryKeys.ITEM, Identifier.of(MODID, id))
}
