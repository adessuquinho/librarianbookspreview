package com.example.showenchantments.mixin;

import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.entity.passive.VillagerEntity;

/**
 * Mixin placeholder para VillagerEntity.
 * O processamento principal de renderização é feito no ShowEnchantmentsClient,
 * que intercepta o evento HudRender e acessa diretamente as ofertas de trade.
 */
@Mixin(VillagerEntity.class)
public class VillagerTradeMixin {
	// Placeholder - trades são acessíveis publicamente
}
