package com.settlement.mod.client.render.debug

import com.settlement.mod.network.SettlementDebugData
import com.settlement.mod.network.StructureDebugData
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.Camera
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.debug.DebugRenderer
import net.minecraft.client.util.math.MatrixStack

// TODO:
//  - show residents ID
//  - color structures by type
@Environment(value = EnvType.CLIENT)
class SettlementDebugRenderer(
    val client: MinecraftClient,
) : WorldRenderEvents.DebugRender {
    // transform this into a map
    private val debugData = mutableMapOf<Int, MutableMap<Int, StructureDebugData>>()

    fun updateDebugData(data: SettlementDebugData) {
        debugData[data.id] = data.structures
    }

    fun render(
        matrixStack: MatrixStack,
        vertexConsumer: VertexConsumerProvider
    ) {
        debugData.forEach { data ->
            data.value.forEach { structure ->
                val errands = structure.value.errands
                val lower = structure.value.lower
                val upper = structure.value.upper
                val text = structure.value.capacity.toString() + "/" + structure.value.maxCapacity.toString()
                val usage = structure.value.residents.toString()
                DebugRenderer.drawBox(matrixStack, vertexConsumer, lower, upper, 0.2f, 0.2f, 0.2f, 0.3f)
                val xMin = lower.x.toFloat()
                val yMin = lower.y.toFloat()
                val zMin = lower.z.toFloat()
                val xMax = upper.x.toFloat()
                val yMax = upper.y.toFloat()
                val zMax = upper.z.toFloat()

                val centerX = ((xMin + xMax) / 2.0) + 0.5
                val centerY = yMax + 1.0
                val centerZ = ((zMin + zMax) / 2.0) + 0.5

                val scale = 0.03f
                for (errand in errands) {
                    val errandText = "${errand.cid.name}"
                    DebugRenderer.drawString(
                        matrixStack,
                        vertexConsumer,
                        errandText,
                        errand.pos!!.x.toDouble() + 0.5,
                        errand.pos!!.y.toDouble() + 0.5,
                        errand.pos!!.z.toDouble() + 0.5,
                        16777215,
                        0.03f,
                        true,
                        0.0f,
                        true,
                    )
                }
                DebugRenderer.drawString(
                    matrixStack,
                    vertexConsumer,
                    structure.key.toString(),
                    centerX,
                    centerY + 1,
                    centerZ,
                    16777215,
                    0.02f,
                    true,
                    0.0f,
                    true,
                )
                DebugRenderer.drawString(
                    matrixStack,
                    vertexConsumer,
                    usage,
                    centerX,
                    centerY + 0.5,
                    centerZ,
                    16777215,
                    0.02f,
                    true,
                    0.0f,
                    true,
                )
                DebugRenderer.drawString(matrixStack, vertexConsumer, text, centerX, centerY, centerZ, 16777215, 0.02f, true, 0.0f, true)
            }
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
}
