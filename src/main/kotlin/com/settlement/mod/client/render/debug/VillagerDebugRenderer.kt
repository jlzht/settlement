package com.settlement.mod.client.render.debug

import com.settlement.mod.entity.mob.AbstractVillagerEntity
import com.settlement.mod.network.VillagerDebugData
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.Camera
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.debug.DebugRenderer
import net.minecraft.client.util.math.MatrixStack
import java.util.UUID

@Environment(value = EnvType.CLIENT)
class VillagerDebugRenderer(
    val client: MinecraftClient,
) : WorldRenderEvents.DebugRender {
    private val debugData = mutableMapOf<UUID, List<String>>()
    private var target: UUID? = null

    fun updateDebugData(data: VillagerDebugData) {
        debugData[data.uuid] = data.lines
    }

    fun render(
        matrixStack: MatrixStack,
        vertexConsumer: VertexConsumerProvider
    ) {
        val entity = getDebugTarget() ?: return
        val lines = debugData[entity.uuid] ?: return
        val pos = entity.pos
        var yOffset = 2.0f
        for (line in lines) {
            DebugRenderer.drawString(
                matrixStack,
                vertexConsumer,
                line,
                pos.x.toDouble(),
                pos.y.toDouble() + yOffset,
                pos.z.toDouble(),
                16777215,
                0.018f,
                true,
                0.0f,
                true,
            )
            yOffset += 0.15f
        }
    }

    override fun beforeDebugRender(context: WorldRenderContext) {
        if (debugData.isEmpty()) return
        context.matrixStack()?.let { matrixStack ->
            context.consumers()?.let { vertexConsumer ->
                render(matrixStack, vertexConsumer)
            }
        }
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
