package com.seafucker;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SeaFucker implements ModInitializer {
    public static final String MOD_ID = "kedagay";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        // Questo metodo viene eseguito all'avvio, per ora non ci serve nulla qui
        LOGGER.info("SeaFucker Mod Initialized");
    }
}