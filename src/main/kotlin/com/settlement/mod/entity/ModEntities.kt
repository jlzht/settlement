package com.settlement.mod.entity

import com.settlement.mod.MODID
import com.settlement.mod.client.render.armor.model.StrawHatModel
import com.settlement.mod.client.render.entity.AbstractVillagerEntityRenderer
import com.settlement.mod.client.render.entity.SimpleFishingBobberEntityRenderer
import com.settlement.mod.client.render.entity.model.AbstractVillagerEntityModel
import com.settlement.mod.entity.mob.AbstractVillagerEntity
import com.settlement.mod.entity.projectile.SimpleFishingBobberEntity
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricDefaultAttributeRegistry
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.entity.SpawnGroup
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.util.Identifier

object ModEntities {
    val VILLAGER =
        register(
            "villager",
            EntityType.Builder
                .create(::AbstractVillagerEntity, SpawnGroup.CREATURE)
                .eyeHeight(1.62f)
                .dimensions(0.6f, 1.95f)
                .maxTrackingRange(10),
        )

    val SIMPLE_FISHING_BOBBER =
        register(
            "simple_fishing_bobber",
            EntityType.Builder
                .create({ type, world -> SimpleFishingBobberEntity(type, world, 0, 0) }, SpawnGroup.MISC)
                .dropsNothing()
                .disableSaving()
                .disableSummon()
                .dimensions(0.025f, 0.25f)
                .maxTrackingRange(4)
                .trackingTickInterval(5),
        )

    fun initialize() {
        FabricDefaultAttributeRegistry.register(VILLAGER, AbstractVillagerEntity.createAttributes())
    }

    fun initializeClient() {
        EntityRendererRegistry.register(SIMPLE_FISHING_BOBBER, ::SimpleFishingBobberEntityRenderer)
        EntityRendererRegistry.register(VILLAGER, ::AbstractVillagerEntityRenderer)

        EntityModelLayerRegistry.registerModelLayer(
            AbstractVillagerEntityModel.LAYER,
            AbstractVillagerEntityModel.Companion::getTexturedModelData,
        )
        EntityModelLayerRegistry.registerModelLayer(
            AbstractVillagerEntityModel.ARMOR_INNER,
            AbstractVillagerEntityModel.Companion::getInnerArmorLayer,
        )
        EntityModelLayerRegistry.registerModelLayer(
            AbstractVillagerEntityModel.ARMOR_OUTER,
            AbstractVillagerEntityModel.Companion::getOuterArmorLayer,
        )

        EntityModelLayerRegistry.registerModelLayer(StrawHatModel.LAYER, StrawHatModel.Companion::getTexturedModelData)
    }

    private fun keyOf(id: String): RegistryKey<EntityType<*>> = RegistryKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(MODID, id))

    private fun <T : Entity> register(
        key: RegistryKey<EntityType<*>>,
        builder: EntityType.Builder<T>,
    ): EntityType<T> = Registry.register(Registries.ENTITY_TYPE, key, builder.build(key))

    private fun <T : Entity> register(
        id: String,
        builder: EntityType.Builder<T>,
    ): EntityType<T> = register(keyOf(id), builder)
}
