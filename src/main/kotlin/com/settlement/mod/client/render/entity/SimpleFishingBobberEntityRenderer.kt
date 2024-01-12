package com.settlement.mod.client.render.entity

import com.settlement.mod.entity.projectile.SimpleFishingBobberEntity
import com.settlement.mod.entity.mob.AbstractVillagerEntity
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.render.OverlayTexture
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.entity.EntityRenderer
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.util.Arm
import net.minecraft.util.Identifier
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.RotationAxis
import org.joml.Matrix3f
import org.joml.Matrix4f

@Environment(EnvType.CLIENT)
class SimpleFishingBobberEntityRenderer(
    context: EntityRendererFactory.Context,
) : EntityRenderer<SimpleFishingBobberEntity>(context) {
    companion object {
        private val TEXTURE = Identifier("textures/entity/fishing_hook.png")
        private val LAYER = RenderLayer.getEntityCutout(TEXTURE)
    }

    override fun render(
        simpleFishingBobberEntity: SimpleFishingBobberEntity,
        f: Float,
        g: Float,
        matrixStack: MatrixStack,
        vertexConsumerProvider: VertexConsumerProvider,
        i: Int,
    ) {
        val entity = simpleFishingBobberEntity.getOwner()
        if (entity == null) return
        entity as AbstractVillagerEntity
        matrixStack.push()
        matrixStack.push()
        matrixStack.scale(0.5f, 0.5f, 0.5f)
        matrixStack.multiply(dispatcher.rotation)
        matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f))
        val entry: MatrixStack.Entry = matrixStack.peek()
        val matrix4f: Matrix4f = entry.positionMatrix
        val matrix3f: Matrix3f = entry.normalMatrix
        val vertexConsumer: VertexConsumer = vertexConsumerProvider.getBuffer(LAYER)
        vertex(vertexConsumer, matrix4f, matrix3f, i, 0.0f, 0, 0, 1)
        vertex(vertexConsumer, matrix4f, matrix3f, i, 1.0f, 0, 1, 1)
        vertex(vertexConsumer, matrix4f, matrix3f, i, 1.0f, 1, 1, 0)
        vertex(vertexConsumer, matrix4f, matrix3f, i, 0.0f, 1, 0, 0)
        matrixStack.pop()
        val j = if (entity.mainArm == Arm.RIGHT) 1 else -1
        var itemStack: ItemStack = entity.mainHandStack
        if (!itemStack.isOf(Items.FISHING_ROD)) {
            itemStack = entity.offHandStack
            if (!itemStack.isOf(Items.FISHING_ROD)) {
                return
            }
        }
        val l = MathHelper.lerp(g, entity.prevBodyYaw, entity.bodyYaw) * (Math.PI / 180).toFloat()
        val d = MathHelper.sin(l)
        val e = MathHelper.cos(l)
        val m = (j * 0.35).toDouble()
        val o: Double = MathHelper.lerp(g.toDouble(), entity.prevX, entity.x) - e * m - d * 0.8
        val p: Double = entity.prevY + entity.standingEyeHeight + (entity.y - entity.prevY) * g.toDouble() - 0.45
        val q: Double = MathHelper.lerp(g.toDouble(), entity.prevZ, entity.z) - d * m + e * 0.8
        val r: Float = if (entity.isInSneakingPose) -0.1875f else 0.0f
        val s = MathHelper.lerp(g.toDouble(), simpleFishingBobberEntity.prevX, simpleFishingBobberEntity.x)
        val t = MathHelper.lerp(g.toDouble(), simpleFishingBobberEntity.prevY, simpleFishingBobberEntity.y) + 0.25
        val u = MathHelper.lerp(g.toDouble(), simpleFishingBobberEntity.prevZ, simpleFishingBobberEntity.z)
        val v = (o - s).toFloat()
        val w = (p - t + r).toFloat()
        val x = (q - u).toFloat()
        val vertexConsumer2: VertexConsumer = vertexConsumerProvider.getBuffer(RenderLayer.getLineStrip())
        val entry2: MatrixStack.Entry = matrixStack.peek()
        for (z in 0..16) {
            renderFishingLine(v, w, x, vertexConsumer2, entry2, percentage(z, 16), percentage(z + 1, 16))
        }
        matrixStack.pop()
        super.render(simpleFishingBobberEntity, f, g, matrixStack, vertexConsumerProvider, i)
    }

    override fun getTexture(simpleFishingBobberEntity: SimpleFishingBobberEntity): Identifier = TEXTURE

    private fun percentage(
        value: Int,
        max: Int,
    ): Float = value.toFloat() / max.toFloat()

    private fun vertex(
        buffer: VertexConsumer,
        matrix: Matrix4f,
        normalMatrix: Matrix3f,
        light: Int,
        x: Float,
        y: Int,
        u: Int,
        v: Int,
    ) {
        buffer
            .vertex(
                matrix,
                x - 0.5f,
                y - 0.5f,
                0.0f,
            ).color(
                255,
                255,
                255,
                255,
            ).texture(u.toFloat(), v.toFloat())
            .overlay(OverlayTexture.DEFAULT_UV)
            .light(light)
            .normal(normalMatrix, 0.0f, 1.0f, 0.0f)
            .next()
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
        val i = x * segmentEnd - f
        val j = y * (segmentEnd * segmentEnd + segmentEnd) * 0.5f + 0.25f - g
        val k = z * segmentEnd - h
        val l = MathHelper.sqrt(i * i + j * j + k * k)
        buffer
            .vertex(matrices.positionMatrix, f, g, h)
            .color(0, 0, 0, 255)
            .normal(matrices.normalMatrix, i / l, j / l, k / l)
            .next()
    }
}
