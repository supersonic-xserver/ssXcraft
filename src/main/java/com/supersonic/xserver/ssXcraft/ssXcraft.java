package com.supersonic.xserver.ssXcraft;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.Mod.EventBusSubscriber;
import net.neoforged.fml.common.ModInitializer;
import net.neoforged.fml.common.event.FMLClientSetupEvent;
import net.neoforged.fml.common.event.FMLInitializationEvent;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ssXcraft - Pure Java 25 XCB-based window compositor for Minecraft
 * Replaces the Wayland/Rust implementation with zero-copy X11 shared memory pipeline.
 * Uses NeoForge for modern Java 25 FFM/Panama support.
 */
@Mod("ssxcraft")
@EventBusSubscriber(modid = ssXcraft.MOD_ID, bus = EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ssXcraft {
    public static final String MOD_ID = "ssxcraft";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
    private static X11Display display = null;
    
    public ssXcraft(FMLInitializationEvent event) {
        LOGGER.info("ssXcraft initializing - pure Java 25 XCB pipeline with NeoForge");
    }
    
    public static void onClientSetup(FMLClientSetupEvent event) {
        if (INITIALIZED.compareAndSet(false, true)) {
            LOGGER.info("Starting X11 display pipeline...");
            
            try {
                // Initialize the X11 display connection
                display = new X11Display();
                
                // Connect to headless X server (DISPLAY=:99)
                display.connect(System.getenv("DISPLAY"));
                
                LOGGER.info("ssXcraft display connected");
            } catch (Exception e) {
                LOGGER.error("Failed to initialize X11 display", e);
            }
        }
    }
    
    /**
     * Get the X11 display instance.
     */
    public static X11Display getDisplay() {
        return display;
    }
    
    /**
     * Check if ssXcraft is initialized.
     */
    public static boolean isInitialized() {
        return INITIALIZED.get();
    }
}