package com.example.showenchantments;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.util.Identifier;
import net.minecraft.network.packet.CustomPayload;

import java.util.ArrayList;
import java.util.List;

public final class ShowEnchantmentsPackets {
	private static boolean registered = false;

	public static final class RequestTradesPayload implements CustomPayload {
		public static final Id<RequestTradesPayload> ID = new Id<>(Identifier.of("showenchantments", "request_trades"));
		public static final PacketCodec<RegistryByteBuf, RequestTradesPayload> CODEC = PacketCodec.of(
				(RequestTradesPayload payload, RegistryByteBuf buf) -> buf.writeVarInt(payload.entityId),
				(RegistryByteBuf buf) -> new RequestTradesPayload(buf.readVarInt())
		);

		public final int entityId;

		public RequestTradesPayload(int entityId) {
			this.entityId = entityId;
		}

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public static final class TradesResponsePayload implements CustomPayload {
		public static final Id<TradesResponsePayload> ID = new Id<>(Identifier.of("showenchantments", "trades_response"));
		public static final PacketCodec<RegistryByteBuf, TradesResponsePayload> CODEC = PacketCodec.of(
				(TradesResponsePayload payload, RegistryByteBuf buf) -> {
					buf.writeVarInt(payload.entityId);
					buf.writeVarInt(payload.lines.size());
					for (String line : payload.lines) {
						buf.writeString(line);
					}
				},
				(RegistryByteBuf buf) -> {
					int entityId = buf.readVarInt();
					int size = buf.readVarInt();
					List<String> lines = new ArrayList<>(size);
					for (int i = 0; i < size; i++) {
						lines.add(buf.readString());
					}
					return new TradesResponsePayload(entityId, lines);
				}
		);

		public final int entityId;
		public final List<String> lines;

		public TradesResponsePayload(int entityId, List<String> lines) {
			this.entityId = entityId;
			this.lines = List.copyOf(lines);
		}

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public static void registerPayloads() {
		if (registered) return;
		registered = true;
		PayloadTypeRegistry.playC2S().register(RequestTradesPayload.ID, RequestTradesPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(TradesResponsePayload.ID, TradesResponsePayload.CODEC);
	}

	private ShowEnchantmentsPackets() {
	}
}
