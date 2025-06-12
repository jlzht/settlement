package com.settlement.mod.client.render.block.entity

import net.minecraft.client.render.OverlayTexture
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.block.entity.BlockEntityRenderer
import com.settlement.mod.block.entity.EnchantedBellBlockEntity
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory
import net.minecraft.client.render.block.entity.model.BellBlockModel
import net.minecraft.client.render.entity.model.EntityModelLayers
import net.minecraft.client.texture.SpriteAtlasTexture
import net.minecraft.client.util.SpriteIdentifier
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.Identifier
import net.minecraft.util.math.Vec3d

class EnchantedBellBlockEntityRenderer(
    context: BlockEntityRendererFactory.Context,
) : BlockEntityRenderer<EnchantedBellBlockEntity> {
    private val BASE_TEXTURE =
        SpriteIdentifier(
            SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE,
            Identifier.ofVanilla("entity/bell/bell_body"),
        )
    private val model = BellBlockModel(context.getLayerModelPart(EntityModelLayers.BELL))

    override fun render(
        blockEntity: EnchantedBellBlockEntity,
        tickDelta: Float,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
        light: Int,
        overlay: Int,
        camera: Vec3d,
    ) {
        val fullbright = 0xF000F0

        val solid = BASE_TEXTURE.getVertexConsumer(vertexConsumers, RenderLayer::getEntityTranslucent)
        model.render(matrices, solid, fullbright, overlay)

        val overlayConsumer =
            vertexConsumers.getBuffer(
                RenderLayer.getEnergySwirl(
                    Identifier.ofVanilla("textures/block/soul_sand.png"),
                    (blockEntity.world!!.time % 100) / 100.0f,
                    (tickDelta * 0.01f) % 1.0f,
                ),
            )

        matrices.push()
        matrices.translate(0.5, 0.5, 0.5)
        matrices.scale(1.1f, 1.1f, 1.1f)
        matrices.translate(-0.5, -0.5, -0.5)
        matrices.translate(0.0, 0.015, -0.0)
        model.render(matrices, overlayConsumer, fullbright, OverlayTexture.DEFAULT_UV)
        matrices.pop()
    }
}
