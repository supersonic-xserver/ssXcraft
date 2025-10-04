package dev.evvie.waylandcraft;

import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.systems.RenderSystem;

import dev.evvie.waylandcraft.Window.WindowHitResult;
import dev.evvie.waylandcraft.bridge.WLCToplevel;
import dev.evvie.waylandcraft.bridge.WaylandCraftBridge;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

public class WaylandCraft implements ModInitializer, ClientModInitializer {
	public static final String MOD_ID = "waylandcraft";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	
	public static WaylandCraft instance;
	
	private WaylandCraftBridge bridge = null;
	public ArrayList<Window> windows = new ArrayList<Window>();
	public WindowHitResult hitResult = null;
	
	@Override
	public void onInitialize() {
	}
	
	@Override
	public void onInitializeClient() {
		LOGGER.info("Initializing WaylandCraft");
		
		instance = this;
		
		WorldRenderEvents.AFTER_ENTITIES.register(context -> {
			if(bridge == null) {
				bridge = WaylandCraftBridge.start();
				String socket = bridge.getSocket();
				Minecraft.getInstance().gui.getChat().addMessage(Component.literal("Server started on " + socket));
			}
			bridge.update();
			
			for(WLCToplevel toplevel : bridge.getToplevels()) {
				if(!windows.stream().anyMatch((w) -> w.toplevel == toplevel)) {
					windows.add(new Window(toplevel));
				}
			}
			windows.removeIf((w) -> !w.isAlive());
			
			RenderSystem.enableDepthTest();
			windows.forEach((w) -> w.render(context));
		});
		
		WorldRenderEvents.END.register(context -> {
			Camera camera = context.camera();
			if(hitResult != null) {
				Vec3 coords = hitResult.surfaceLocal;
				Window w = hitResult.target;
				
				LOGGER.info(coords.x + ", " + coords.y + " (" + hitResult.dist + ")");
				
				Vec3 hitPos = hitResult.target.origin().add(w.localX().scale(coords.x)).add(w.localY().scale(coords.y));
				RenderUtils.drawMarker(camera, hitPos, 0.2, 1.0f, 0.0f, 1.0f);
			}
		});
	}
}