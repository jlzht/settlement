package com.settlement.mod.component

import com.mojang.serialization.Codec
import com.settlement.mod.MODID
import net.minecraft.component.ComponentType
import net.minecraft.network.codec.PacketCodecs
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.util.Identifier

object ModComponentTypes {
    val BOUND_NAME: ComponentType<String> =
        register("name") { builder ->
            builder.codec(Codec.STRING).packetCodec(PacketCodecs.STRING)
        }
    // ContractType enum
    // type of contract
    // list of maximum 16 blockPos

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
        BOUND_NAME
    }
}
