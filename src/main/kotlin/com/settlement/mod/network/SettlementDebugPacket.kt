package com.settlement.mod.network

import com.settlement.mod.MODID
import com.settlement.mod.action.Action
import com.settlement.mod.action.Errand
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.PacketByteBuf
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos

data class StructureDebugData(
    val capacity: Int,
    val maxCapacity: Int,
    val lower: BlockPos,
    val upper: BlockPos,
    val errands: List<Errand>,
)

data class SettlementDebugData(
    val id: Int,
    // val data -> name, pos, settlers
    val structures: MutableMap<Int, StructureDebugData>,
)

data class SettlementDebugDataPacket(
    val data: SettlementDebugData,
) {
    companion object {
        val ID = Identifier(MODID, "settlement_debug")

        fun encode(
            packet: SettlementDebugDataPacket,
            buf: PacketByteBuf,
        ) {
            buf.writeInt(packet.data.id)
            packet.data.structures.forEach { structure ->
                buf.writeInt(structure.key)
                buf.writeInt(structure.value.capacity)
                buf.writeInt(structure.value.maxCapacity)
                buf.writeBlockPos(structure.value.lower)
                buf.writeBlockPos(structure.value.upper)
                buf.writeInt(structure.value.errands.size)
                structure.value.errands.forEach { errand ->
                    buf.writeInt(errand.cid.ordinal)
                    buf.writeBlockPos(errand.pos)
                }
            }
        }

        fun decode(buf: PacketByteBuf): SettlementDebugDataPacket {
            val structures = mutableMapOf<Int, StructureDebugData>()
            val types = Action.Type.values()

            val id = buf.readInt()
            while (buf.isReadable) {
                val sid = buf.readInt()
                val capacity = buf.readInt()
                val maxCapacity = buf.readInt()
                val lower = buf.readBlockPos()
                val upper = buf.readBlockPos()
                val size = buf.readInt()
                val errands = ArrayList<Errand>()
                repeat(size) {
                    val cid = buf.readInt()
                    val pos = buf.readBlockPos()
                    errands.add(Errand(types[cid], pos))
                }
                structures[sid] = StructureDebugData(capacity, maxCapacity, lower, upper, errands)
            }
            val settlementDebugData = SettlementDebugData(id, structures)
            return SettlementDebugDataPacket(settlementDebugData)
        }

        fun sendToClient(
            player: ServerPlayerEntity,
            data: SettlementDebugData,
        ) {
            val buf = PacketByteBufs.create()
            encode(SettlementDebugDataPacket(data), buf)
            ServerPlayNetworking.send(player, ID, buf)
        }
    }
}
