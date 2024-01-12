package com.settlement.mod.client.render.debug

import com.mojang.blaze3d.systems.RenderSystem
import com.settlement.mod.entity.mob.AbstractVillagerEntity
import com.settlement.mod.network.VillagerDebugData
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.render.Camera
import net.minecraft.client.render.GameRenderer
import net.minecraft.client.render.Tessellator
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.debug.DebugRenderer
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.math.RotationAxis
import java.util.UUID

@Environment(value = EnvType.CLIENT)
class VillagerDebugRenderer(
    val client: MinecraftClient,
) : WorldRenderEvents.End {
    private val debugData = mutableMapOf<UUID, List<String>>()
    private var target: UUID? = null

    fun updateDebugData(data: VillagerDebugData) {
        debugData[data.uuid] = data.lines
    }

    fun render(
        matrixStack: MatrixStack,
        camera: Camera,
    ) {
        val entity = getDebugTarget() ?: return
        val lines = debugData[entity.uuid] ?: return

        val entityPos = entity.pos
        val textRenderer = client.textRenderer
        val vertexConsumerProvider = VertexConsumerProvider.immediate(Tessellator.getInstance().buffer)

        matrixStack.push()
        matrixStack.translate(
            entityPos.x - camera.pos.x,
            entityPos.y - camera.pos.y + 4.0,
            entityPos.z - camera.pos.z,
        )

        matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.yaw))
        matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.pitch))

        val scale = 0.025f
        matrixStack.scale(-scale, -scale, scale)

        var yOffset = 0f
        for (line in lines) {
            drawText(textRenderer, line, yOffset, matrixStack, vertexConsumerProvider)
            yOffset += 10
        }

        vertexConsumerProvider.draw()
        matrixStack.pop()
    }

    private fun drawText(
        textRenderer: TextRenderer,
        text: String,
        yOffset: Float,
        matrixStack: MatrixStack,
        vertexConsumerProvider: VertexConsumerProvider,
    ) {
        val textMatrix = matrixStack.peek().positionMatrix
        textRenderer.draw(
            text,
            -textRenderer.getWidth(text) / 2.0f,
            yOffset,
            0xFFFFFF,
            false,
            textMatrix,
            vertexConsumerProvider,
            TextRenderer.TextLayerType.SEE_THROUGH,
            0,
            15728880,
        )
    }

    override fun onEnd(context: WorldRenderContext) {
        if (debugData.isEmpty()) return
        val camera = context.camera()
        val matrixStack = context.matrixStack()
        RenderSystem.setShader(GameRenderer::getPositionColorProgram)
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
        RenderSystem.disableCull()
        RenderSystem.disableDepthTest()
        render(matrixStack, camera)
        RenderSystem.enableDepthTest()
        RenderSystem.enableCull()
    }

    private fun getDebugTarget(): AbstractVillagerEntity? {
        var target: AbstractVillagerEntity? = null
        DebugRenderer.getTargetedEntity(this.client.cameraEntity, 8).ifPresent { entity ->
            if (entity is AbstractVillagerEntity) {
                target = entity
            }
        }
        return target
    }
}
