package com.settlement.mod.network

import com.settlement.mod.MODID
import com.settlement.mod.action.Action
import com.settlement.mod.action.Errand
import it.unimi.dsi.fastutil.ints.IntList
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos

data class StructureDebugData(
    val residents: IntList,
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
) : CustomPayload {
    override fun getId(): CustomPayload.Id<out CustomPayload> = ID

    companion object {
        val ID = CustomPayload.Id<SettlementDebugDataPacket>(Identifier.of(MODID, "settlement_debug"))

        val CODEC: PacketCodec<RegistryByteBuf, SettlementDebugDataPacket> =
            PacketCodec.of(
                { packet, buf -> encode(packet, buf) },
                { buf -> decode(buf) },
            )

        private fun encode(
            packet: SettlementDebugDataPacket,
            buf: RegistryByteBuf,
        ) {
            val data = packet.data
            buf.writeInt(data.id)
            data.structures.forEach { (key, structure) ->
                buf.writeInt(key)
                buf.writeIntList(structure.residents)
                buf.writeInt(structure.capacity)
                buf.writeInt(structure.maxCapacity)
                buf.writeBlockPos(structure.lower)
                buf.writeBlockPos(structure.upper)
                buf.writeInt(structure.errands.size)
                structure.errands.forEach { errand ->
                    buf.writeInt(errand.cid.ordinal)
                    buf.writeBlockPos(errand.pos)
                }
            }
        }

        private fun decode(buf: RegistryByteBuf): SettlementDebugDataPacket {
            val types = Action.Type.values()
            val structures = mutableMapOf<Int, StructureDebugData>()

            val id = buf.readInt()
            while (buf.isReadable) {
                val sid = buf.readInt()
                val residents = buf.readIntList()
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
                structures[sid] = StructureDebugData(residents, capacity, maxCapacity, lower, upper, errands)
            }
            return SettlementDebugDataPacket(SettlementDebugData(id, structures))
        }

        fun sendToClient(
            player: ServerPlayerEntity,
            data: SettlementDebugData,
        ) {
            ServerPlayNetworking.send(player, SettlementDebugDataPacket(data))
        }
    }
}
