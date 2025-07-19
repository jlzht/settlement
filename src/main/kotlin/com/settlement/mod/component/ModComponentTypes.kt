package com.settlement.mod.component

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import com.settlement.mod.MODID
import net.minecraft.component.ComponentType
import net.minecraft.network.codec.PacketCodecs
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.util.Identifier

data class BoundInfo(
    var id: Int = -1,
    var name: String = "",
) {
    companion object {
        val CODEC: Codec<BoundInfo> =
            RecordCodecBuilder.create { inst ->
                inst
                    .group(
                        Codec.INT.fieldOf("id").forGetter(BoundInfo::id),
                        Codec.STRING.fieldOf("name").forGetter(BoundInfo::name),
                    ).apply(inst, ::BoundInfo)
            }
    }
}

object ModComponentTypes {
    val BOUND_INFO: ComponentType<BoundInfo> =
        register("bound_info") { builder ->
            builder
                .codec(BoundInfo.CODEC)
                .packetCodec(PacketCodecs.codec(BoundInfo.CODEC))
        }

    private fun <T> register(
        path: String,
        builderOperator: (ComponentType.Builder<T>) -> ComponentType.Builder<T>,
    ): ComponentType<T> =
        Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of(MODID, path),
            builderOperator(ComponentType.builder()).build(),
        )

    fun initialize() {
        BOUND_INFO
    }
}
