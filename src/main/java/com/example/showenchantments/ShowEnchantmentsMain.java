package com.example.showenchantments;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.TradedItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ShowEnchantmentsMain implements ModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("showenchantments");

	@Override
	public void onInitialize() {
		ShowEnchantmentsPackets.registerPayloads();

		ServerPlayNetworking.registerGlobalReceiver(ShowEnchantmentsPackets.RequestTradesPayload.ID, (payload, context) -> {
			int entityId = payload.entityId;
			context.server().execute(() -> {
				Entity entity = null;
				for (ServerWorld world : context.server().getWorlds()) {
					entity = world.getEntityById(entityId);
					if (entity != null) break;
				}
				if (!(entity instanceof VillagerEntity villager)) {
					ServerPlayNetworking.send(context.player(), new ShowEnchantmentsPackets.TradesResponsePayload(entityId, List.of()));
					return;
				}

				List<String> lines = extractTradeLines(villager.getOffers());
				ServerPlayNetworking.send(context.player(), new ShowEnchantmentsPackets.TradesResponsePayload(entityId, lines));
			});
		});

		LOGGER.info("Show Enchantments mod loaded!");
	}

	private static List<String> extractTradeLines(TradeOfferList offers) {
		if (offers == null || offers.isEmpty()) return List.of();

		List<String> result = new ArrayList<>();

		for (TradeOffer offer : offers) {
			ItemStack sell = offer.getSellItem();
			if (sell == null || sell.isEmpty()) continue;
			if (!sell.isOf(Items.ENCHANTED_BOOK)) continue;

			ItemEnchantmentsComponent stored = sell.get(DataComponentTypes.STORED_ENCHANTMENTS);
			if (stored == null) continue;

			List<String> enchants = new ArrayList<>();
			for (RegistryEntry<Enchantment> enchantment : stored.getEnchantments()) {
				int lvl = stored.getLevel(enchantment);
				String name = Enchantment.getName(enchantment, lvl).getString();
				enchants.add(name);
			}

			if (enchants.isEmpty()) continue;

			int emeraldCost = 0;
			ItemStack displayedFirst = offer.getDisplayedFirstBuyItem();
			if (!displayedFirst.isEmpty() && displayedFirst.isOf(Items.EMERALD)) emeraldCost += displayedFirst.getCount();
			ItemStack displayedSecond = offer.getDisplayedSecondBuyItem();
			if (!displayedSecond.isEmpty() && displayedSecond.isOf(Items.EMERALD)) emeraldCost += displayedSecond.getCount();

			// Enviar a translation key do item para o cliente renderizar no idioma do jogador
			result.add(String.join(", ", enchants) + " - " + emeraldCost + " @@" + Items.EMERALD.getTranslationKey() + "@@");
			if (result.size() >= 10) break;
		}

		return result;
	}
}
