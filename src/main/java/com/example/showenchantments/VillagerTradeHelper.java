package com.example.showenchantments;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.text.Text;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.TradedItem;

import net.minecraft.registry.entry.RegistryEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VillagerTradeHelper {
	private static final long CACHE_TTL_MS = 1500;
	private static final Map<Integer, CacheEntry> CACHE = new ConcurrentHashMap<>();

	private record CacheEntry(long updatedAtMs, List<String> lines) {}

	public static void updateCachedLines(int entityId, List<String> lines) {
		CACHE.put(entityId, new CacheEntry(System.currentTimeMillis(), List.copyOf(lines)));
	}

	public static boolean isCacheFresh(int entityId, long nowMs) {
		CacheEntry entry = CACHE.get(entityId);
		return entry != null && (nowMs - entry.updatedAtMs) <= CACHE_TTL_MS;
	}

	public static List<String> getCachedLines(int entityId) {
		CacheEntry entry = CACHE.get(entityId);
		if (entry == null || entry.lines == null) return List.of();
		return entry.lines;
	}

	public static void renderCachedTrades(DrawContext drawContext, MinecraftClient client, int entityId) {
		CacheEntry entry = CACHE.get(entityId);
		if (entry == null || entry.lines == null || entry.lines.isEmpty()) return;
		renderText(drawContext, client, entry.lines);
	}

	private static void renderText(DrawContext drawContext, MinecraftClient client, List<String> lines) {
		if (client.getWindow() == null) return;

		TextRenderer textRenderer = client.textRenderer;
		int screenWidth = client.getWindow().getScaledWidth();
		int screenHeight = client.getWindow().getScaledHeight();

		int x = 10;
		int y = 10;
		int lineHeight = textRenderer.fontHeight + 2;

		// Header
		drawContext.drawText(textRenderer, Text.literal("Livros Encantados:"), x, y, 0xFFD700, true);
		y += lineHeight;

		for (String line : lines) {
			drawContext.drawText(textRenderer, Text.literal(line), x, y, 0xFFFFFF, true);
			y += lineHeight;
		}
	}

	private static String getRomanNumeral(int num) {
		String[] roman = {"I","II","III","IV","V","VI","VII","VIII","IX","X"};
		if (num > 0 && num <= roman.length) return roman[num-1];
		return String.valueOf(num);
	}
}
