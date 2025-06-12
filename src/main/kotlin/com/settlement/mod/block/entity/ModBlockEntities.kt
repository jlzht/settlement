package com.settlement.mod.block.entity

import com.settlement.mod.MODID
import com.settlement.mod.block.ModBlocks
import com.settlement.mod.client.render.block.entity.EnchantedBellBlockEntityRenderer
import net.fabricmc.fabric.api.`object`.builder.v1.block.entity.FabricBlockEntityTypeBuilder
import net.minecraft.block.Block
import net.minecraft.block.entity.BlockEntity
import net.minecraft.block.entity.BlockEntityType
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.util.Identifier

object ModBlockEntities {
    val ENCHANTED_BELL_BLOCK_ENTITY: BlockEntityType<EnchantedBellBlockEntity> =
        register("charged_bell", ::EnchantedBellBlockEntity, ModBlocks.ENCHANTED_BELL)

    val CONTRACT_TABLE_BLOCK_ENTITY: BlockEntityType<ContractTableBlockEntity> =
        register("contract_table", ::ContractTableBlockEntity, ModBlocks.CONTRACT_TABLE)

    fun initialize() {
        ENCHANTED_BELL_BLOCK_ENTITY
    }

    fun initializeClient() {
        BlockEntityRendererFactories.register(ENCHANTED_BELL_BLOCK_ENTITY, ::EnchantedBellBlockEntityRenderer)
    }

    private fun <T : BlockEntity> register(
        name: String,
        entityFactory: FabricBlockEntityTypeBuilder.Factory<out T>,
        vararg blocks: Block,
    ): BlockEntityType<T> {
        val id = Identifier.of(MODID, name)
        return Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            id,
            FabricBlockEntityTypeBuilder.create(entityFactory, *blocks).build(),
        )
    }
}
