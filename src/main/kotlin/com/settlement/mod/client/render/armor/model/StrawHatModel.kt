package com.settlement.mod.client.render.armor.model

import com.settlement.mod.MODID
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.model.Dilation
import net.minecraft.client.model.ModelData
import net.minecraft.client.model.ModelPart
import net.minecraft.client.model.ModelPartBuilder
import net.minecraft.client.model.ModelTransform
import net.minecraft.client.model.TexturedModelData
import net.minecraft.client.render.entity.model.EntityModel
import net.minecraft.client.render.entity.model.EntityModelLayer
import net.minecraft.client.render.entity.state.EntityRenderState
import net.minecraft.util.Identifier

@Environment(EnvType.CLIENT)
class StrawHatModel<T : EntityRenderState>(
    root: ModelPart,
) : EntityModel<T>(root) {
    companion object {
        val LAYER = EntityModelLayer(Identifier.of(MODID, "straw_hat"), "main")

        fun getTexturedModelData(): TexturedModelData {
            val modelData = ModelData()
            val root = modelData.root

            root.addChild(
                "main",
                ModelPartBuilder
                    .create()
                    .uv(0, 12)
                    .cuboid(-16.0f, 0.0f, 0.0f, 16.0f, 0.0f, 16.0f, Dilation(0.04f))
                    .uv(-16, 12)
                    .cuboid(-16.0f, 0.0f, 0.0f, 16.0f, 0.0f, 16.0f, Dilation(0.04f))
                    .uv(0, 0)
                    .cuboid(-12.0f, -4.0f, 4.0f, 8.0f, 4.0f, 8.0f),
                ModelTransform.origin(8.0f, 24.0f, -8.0f),
            )
            return TexturedModelData.of(modelData, 32, 32)
        }
    }
}
