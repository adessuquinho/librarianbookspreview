package com.example.showenchantments;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.Environment;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Environment(EnvType.CLIENT)
public class ShowEnchantmentsClient implements ClientModInitializer {
	private static VillagerEntity lastTargetedVillager = null;
	private static final long REQUEST_COOLDOWN_MS = 800;
	private static long lastRequestMs = 0L;
	private static int lastRequestedEntityId = -1;
	private static final Pattern TRAILING_INT = Pattern.compile("^(.*?)(\\d+)\\s*$");
	private static final ItemStack ICON_ENCHANTED_BOOK = new ItemStack(Items.ENCHANTED_BOOK);
	private static final ItemStack ICON_EMERALD = new ItemStack(Items.EMERALD);
	private static final Pattern FIRST_INT = Pattern.compile("\\b(\\d+)\\b");

	@Override
	public void onInitializeClient() {
		ShowEnchantmentsPackets.registerPayloads();

		ClientPlayNetworking.registerGlobalReceiver(ShowEnchantmentsPackets.TradesResponsePayload.ID, (payload, context) -> {
			context.client().execute(() -> VillagerTradeHelper.updateCachedLines(payload.entityId, payload.lines));
		});

		HudRenderCallback.EVENT.register(this::onHudRender);
	}

	private void onHudRender(DrawContext drawContext, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) return;

		if (client.targetedEntity instanceof VillagerEntity villager) {
			lastTargetedVillager = villager;
			maybeRequestTrades(client, villager);
			renderProjectedTrades(drawContext, client, villager);
		} else {
			lastTargetedVillager = null;
		}
	}

	private static void renderProjectedTrades(DrawContext drawContext, MinecraftClient client, VillagerEntity villager) {
		var lines = VillagerTradeHelper.getCachedLines(villager.getId());
		if (lines.isEmpty()) return;

		int maxLines = Math.min(3, lines.size());
		Vec3d worldPos = villager.getEntityPos().add(0.0, villager.getHeight() + 0.6, 0.0);
		Vec3d ndc = client.gameRenderer.project(worldPos);

		// ndc: x/y em -1..1, z normalmente em 0..1. Se estiver fora, não está visível.
		if (ndc.z < 0.0 || ndc.z > 1.0) return;
		if (ndc.x < -1.2 || ndc.x > 1.2 || ndc.y < -1.2 || ndc.y > 1.2) return;

		int sw = client.getWindow().getScaledWidth();
		int sh = client.getWindow().getScaledHeight();
		int screenX = (int) ((ndc.x * 0.5 + 0.5) * sw);
		int screenY = (int) ((-ndc.y * 0.5 + 0.5) * sh);

		int iconSize = 16;
		int padY = 2;
		int rowHeight = iconSize + (padY * 2);
		int yBase = screenY - (maxLines * rowHeight);

		for (int i = 0; i < maxLines; i++) {
			RenderLineParts parts = toRenderParts(lines.get(i));
			int pad = 3;
			int gap = 4;
			int rowY = yBase + (i * rowHeight);
			int iconY = rowY + padY;
			int textY = rowY + padY + Math.max(0, (iconSize - client.textRenderer.fontHeight) / 2);

			int widthLeft = client.textRenderer.getWidth(parts.left);
			int widthSep = client.textRenderer.getWidth(parts.separator);
			int widthRight = parts.right == null ? 0 : client.textRenderer.getWidth(parts.right);

			int totalWidth = iconSize + gap + widthLeft + widthSep;
			if (parts.right != null) totalWidth += gap + iconSize + gap + widthRight;
			totalWidth += pad * 2;

			int xStart = screenX - (totalWidth / 2);
			int yStart = rowY;
			int xEnd = xStart + totalWidth;
			int yEnd = rowY + rowHeight;

			// Fundo único pra cobrir ícones + texto
			drawContext.fill(xStart, yStart, xEnd, yEnd, 0x66000000);

			int x = xStart + pad;
			drawContext.drawItem(ICON_ENCHANTED_BOOK, x, iconY);
			x += iconSize + gap;
			drawContext.drawTextWithShadow(client.textRenderer, parts.left, x, textY, 0xFFFFFFFF);
			x += widthLeft;
			drawContext.drawTextWithShadow(client.textRenderer, parts.separator, x, textY, 0xFFFFFFFF);
			x += widthSep;

			if (parts.right != null) {
				x += gap;
				drawContext.drawItem(ICON_EMERALD, x, iconY);
				x += iconSize + gap;
				drawContext.drawTextWithShadow(client.textRenderer, parts.right, x, textY, 0xFFFFFFFF);
			}
		}
	}

	private record RenderLineParts(Text left, Text separator, Text right) {}

	private static RenderLineParts toRenderParts(String raw) {
		// Formato esperado: "<encantamento> - <n> @@item.minecraft.emerald@@"
		String emeraldMarker = "@@" + Items.EMERALD.getTranslationKey() + "@@";
		String cleaned = raw.replace(emeraldMarker, "").trim();

		String[] split = cleaned.split("\\s-\\s", 2);
		Text left = toDisplayText(split[0].trim());
		Text separator = Text.literal(" - ");
		Text right = null;

		if (raw.contains(emeraldMarker) && split.length == 2) {
			Matcher m = FIRST_INT.matcher(split[1]);
			int count = 0;
			if (m.find()) {
				try {
					count = Integer.parseInt(m.group(1));
				} catch (NumberFormatException ignored) {
					count = 0;
				}
			}
			String pluralKey = (count == 1) ? "showenchantments.emerald.one" : "showenchantments.emerald.many";
			right = Text.translatable(pluralKey, count);
		}

		return new RenderLineParts(left, separator, right);
	}

	private static Text toDisplayText(String raw) {
		final String marker = "@@";
		int start = raw.indexOf(marker);
		if (start < 0) return Text.literal(raw);
		int end = raw.indexOf(marker, start + marker.length());
		if (end < 0) return Text.literal(raw);

		String before = raw.substring(0, start);
		String key = raw.substring(start + marker.length(), end);
		String after = raw.substring(end + marker.length());

		// Caso especial: pluralização de esmeralda (o nome do item em si não pluraliza)
		if (key.equals(Items.EMERALD.getTranslationKey())) {
			Matcher m = TRAILING_INT.matcher(before);
			if (m.matches()) {
				String prefix = m.group(1);
				int count;
				try {
					count = Integer.parseInt(m.group(2));
				} catch (NumberFormatException e) {
					count = 0;
				}
				String pluralKey = (count == 1) ? "showenchantments.emerald.one" : "showenchantments.emerald.many";
				return Text.empty()
					.append(Text.literal(prefix))
					.append(Text.translatable(pluralKey, count))
					.append(Text.literal(after));
			}
		}

		return Text.empty()
			.append(Text.literal(before))
			.append(Text.translatable(key))
			.append(Text.literal(after));
	}

	private static void maybeRequestTrades(MinecraftClient client, VillagerEntity villager) {
		if (!ClientPlayNetworking.canSend(ShowEnchantmentsPackets.RequestTradesPayload.ID)) return;
		long now = System.currentTimeMillis();
		int entityId = villager.getId();
		if (entityId == lastRequestedEntityId && (now - lastRequestMs) < REQUEST_COOLDOWN_MS) return;
		if (VillagerTradeHelper.isCacheFresh(entityId, now)) return;

		ClientPlayNetworking.send(new ShowEnchantmentsPackets.RequestTradesPayload(entityId));
		lastRequestMs = now;
		lastRequestedEntityId = entityId;
	}
}

