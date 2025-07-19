package com.settlement.mod.client.render.block.entity

import com.settlement.mod.block.entity.EnchantedBellBlockEntity
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.block.BlockModelRenderer
import net.minecraft.client.render.block.entity.BlockEntityRenderer
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.Identifier
import net.minecraft.util.math.Vec3d

@Environment(EnvType.CLIENT)
class EnchantedBellBlockEntityRenderer(
    context: BlockEntityRendererFactory.Context,
) : BlockEntityRenderer<EnchantedBellBlockEntity> {
    override fun render(
        blockEntity: EnchantedBellBlockEntity,
        tickDelta: Float,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
        light: Int,
        overlay: Int,
        cameraPos: Vec3d,
    ) {
        val world = blockEntity.world ?: return
        val state = blockEntity.cachedState
        val model =
            MinecraftClient
                .getInstance()
                .blockRenderManager
                .getModel(state)

        val xScroll = (world.time % 1000) / 1000.0f
        val yScroll = ((world.time + tickDelta) * 0.002f) % 1.0f

        val energySwirl =
            vertexConsumers.getBuffer(
                RenderLayer.getEnergySwirl(
                    Identifier.ofVanilla("textures/block/soul_sand.png"),
                    xScroll,
                    yScroll,
                ),
            )

        matrices.push()

        matrices.translate(0.5, 0.5, 0.5)
        matrices.scale(1.2f, 1.2f, 1.2f)
        matrices.translate(-0.5, -0.5, -0.5)

        BlockModelRenderer.render(
            matrices.peek(),
            energySwirl,
            model,
            1f,
            1f,
            1f,
            light,
            overlay,
        )

        matrices.pop()
    }
}
