package com.settlement.mod.network

import com.settlement.mod.MODID
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.PacketByteBuf
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import java.util.UUID

data class VillagerDebugData(
    val uuid: UUID,
    val lines: List<String>,
)

data class VillagerDebugPacket(
    val data: VillagerDebugData,
) {
    companion object {
        val ID = Identifier(MODID, "villager_debug")

        fun encode(
            packet: VillagerDebugPacket,
            buf: PacketByteBuf,
        ) {
            buf.writeUuid(packet.data.uuid)
            buf.writeInt(packet.data.lines.size)
            packet.data.lines.forEach { line ->
                buf.writeString(line)
            }
        }

        fun decode(buf: PacketByteBuf): VillagerDebugPacket {
            val lines = mutableListOf<String>()
            val uuid = buf.readUuid()
            val size = buf.readInt()
            repeat(size) {
                lines.add(buf.readString())
            }
            return VillagerDebugPacket(VillagerDebugData(uuid, lines))
        }

        fun sendToClient(
            player: ServerPlayerEntity,
            uuid: UUID,
            lines: List<String>,
        ) {
            val buf = PacketByteBufs.create()
            encode(VillagerDebugPacket(VillagerDebugData(uuid, lines)), buf)
            ServerPlayNetworking.send(player, ID, buf)
        }
    }
}
