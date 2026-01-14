package com.example.showenchantments;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShowEnchantmlentsMain implements ModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("showenchantments");

	@Override
	public void onInitialize() {
		LOGGER.info("Show Enchantments mod loaded!");
	}
}
