package com.settlement.mod.client.render.entity

import com.settlement.mod.entity.projectile.SimpleFishingBobberEntity
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.render.Frustum
import net.minecraft.client.render.OverlayTexture
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.entity.EntityRenderer
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.render.entity.state.FishingBobberEntityState
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.entity.LivingEntity
import net.minecraft.item.FishingRodItem
import net.minecraft.util.Arm
import net.minecraft.util.Colors
import net.minecraft.util.Identifier
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d

@Environment(EnvType.CLIENT)
class SimpleFishingBobberEntityRenderer(
    context: EntityRendererFactory.Context,
) : EntityRenderer<SimpleFishingBobberEntity, FishingBobberEntityState>(context) {
    companion object {
        private val TEXTURE = Identifier.ofVanilla("textures/entity/fishing_hook.png")
        private val LAYER = RenderLayer.getEntityCutout(TEXTURE)
    }

    override fun createRenderState(): FishingBobberEntityState = FishingBobberEntityState()

    override fun updateRenderState(
        simpleFishingBobberEntity: SimpleFishingBobberEntity,
        state: FishingBobberEntityState,
        f: Float,
    ) {
        super.updateRenderState(simpleFishingBobberEntity, state, f)
        val entity = simpleFishingBobberEntity.getOwner()
        if (entity == null) {
            state.pos = Vec3d.ZERO
        } else {
            entity as LivingEntity
            val o = entity.getHandSwingProgress(f)
            val h = MathHelper.sin(MathHelper.sqrt(o) * Math.PI.toFloat())
            val arm =
                if (entity.mainHandStack.item is FishingRodItem) {
                    entity.mainArm
                } else {
                    entity.mainArm.opposite
                }

            val i = if (arm == Arm.RIGHT) 1 else -1
            val g = MathHelper.lerp(f, entity.lastBodyYaw, entity.bodyYaw) * (Math.PI / 180.0).toFloat()
            val sin = MathHelper.sin(g)
            val cos = MathHelper.cos(g)
            val scale = entity.scale
            val j = i * 0.35 * scale
            val k = 0.8 * scale
            val yOffset = if (entity.isInSneakingPose) -0.1875 else 0.0

            val vec3d =
                entity.getCameraPosVec(f).add(
                    -cos * j - sin * k,
                    yOffset - 0.45 * scale,
                    -sin * j + cos * k,
                )

            val vec3d2 = simpleFishingBobberEntity.getLerpedPos(f).add(0.0, 0.25, 0.0)
            state.pos = vec3d.subtract(vec3d2)
        }
    }

    override fun shouldRender(
        simpleFishingBobberEntity: SimpleFishingBobberEntity,
        frustum: Frustum,
        d: Double,
        e: Double,
        f: Double,
    ): Boolean = super.shouldRender(simpleFishingBobberEntity, frustum, d, e, f) && simpleFishingBobberEntity.getOwner() != null

    override fun render(
        state: FishingBobberEntityState,
        matrixStack: MatrixStack,
        vertexConsumerProvider: VertexConsumerProvider,
        i: Int,
    ) {
        matrixStack.push()
        matrixStack.push()
        matrixStack.scale(0.5F, 0.5F, 0.5F)
        matrixStack.multiply(this.dispatcher.getRotation())
        val entry = matrixStack.peek()
        val vertexConsumer = vertexConsumerProvider.getBuffer(LAYER)
        vertex(vertexConsumer, entry, i, 0.0F, 0, 0, 1)
        vertex(vertexConsumer, entry, i, 1.0F, 0, 1, 1)
        vertex(vertexConsumer, entry, i, 1.0F, 1, 1, 0)
        vertex(vertexConsumer, entry, i, 0.0F, 1, 0, 0)

        matrixStack.pop()
        val f = state.pos.x.toFloat()
        val g = state.pos.y.toFloat()
        val h = state.pos.z.toFloat()
        val vertexConsumer2 = vertexConsumerProvider.getBuffer(RenderLayer.getLineStrip())
        val entry2 = matrixStack.peek()
        val j = 16

        for (z in 0..16) {
            renderFishingLine(f, g, h, vertexConsumer2, entry2, percentage(z, 16), percentage(z + 1, 16))
        }

        matrixStack.pop()
        super.render(state, matrixStack, vertexConsumerProvider, i)
    }

    private fun percentage(
        value: Int,
        max: Int,
    ): Float = value.toFloat() / max.toFloat()

    private fun vertex(
        buffer: VertexConsumer,
        matrix: MatrixStack.Entry,
        light: Int,
        x: Float,
        y: Int,
        u: Int,
        v: Int,
    ) {
        buffer
            .vertex(matrix, x - 0.5f, y - 0.5f, 0.0f)
            .color(Colors.WHITE)
            .texture(u.toFloat(), v.toFloat())
            .overlay(OverlayTexture.DEFAULT_UV)
            .light(light)
            .normal(matrix, 0.0f, 1.0f, 0.0f)
    }

    private fun renderFishingLine(
        x: Float,
        y: Float,
        z: Float,
        buffer: VertexConsumer,
        matrices: MatrixStack.Entry,
        segmentStart: Float,
        segmentEnd: Float,
    ) {
        val f = x * segmentStart
        val g = y * (segmentStart * segmentStart + segmentStart) * 0.5f + 0.25f
        val h = z * segmentStart

        var i = x * segmentEnd - f
        var j = y * (segmentEnd * segmentEnd + segmentEnd) * 0.5f + 0.25f - g
        var k = z * segmentEnd - h

        val length = MathHelper.sqrt(i * i + j * j + k * k)
        i /= length
        j /= length
        k /= length

        buffer
            .vertex(matrices, f, g, h)
            .color(Colors.BLACK)
            .normal(matrices, i, j, k)
    }
}
