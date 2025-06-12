package com.settlement.mod.network

import com.settlement.mod.MODID
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import java.util.UUID

data class VillagerDebugData(
    val uuid: UUID,
    val lines: List<String>,
)

data class VillagerDebugPacket(
    val data: VillagerDebugData,
) : CustomPayload {
    override fun getId(): CustomPayload.Id<out CustomPayload> = ID

    companion object {
        val ID = CustomPayload.Id<VillagerDebugPacket>(Identifier.of(MODID, "villager_debug"))

        val CODEC: PacketCodec<RegistryByteBuf, VillagerDebugPacket> =
            PacketCodec.of(
                { packet, buf -> encode(packet, buf) },
                { buf -> decode(buf) },
            )

        private fun encode(
            packet: VillagerDebugPacket,
            buf: RegistryByteBuf,
        ) {
            buf.writeUuid(packet.data.uuid)
            buf.writeInt(packet.data.lines.size)
            packet.data.lines.forEach { buf.writeString(it) }
        }

        private fun decode(buf: RegistryByteBuf): VillagerDebugPacket {
            val uuid = buf.readUuid()
            val size = buf.readInt()
            val lines = List(size) { buf.readString() }
            return VillagerDebugPacket(VillagerDebugData(uuid, lines))
        }

        fun sendToClient(
            player: ServerPlayerEntity,
            uuid: UUID,
            lines: List<String>,
        ) {
            ServerPlayNetworking.send(player, VillagerDebugPacket(VillagerDebugData(uuid, lines)))
        }
    }
}
