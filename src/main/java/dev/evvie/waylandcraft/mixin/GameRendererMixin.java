package dev.evvie.waylandcraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.Window;
import dev.evvie.waylandcraft.Window.WindowHitResult;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
	
	@Shadow
	private HitResult pick(Entity entity, double d, double e, float f) {throw new AssertionError();}
	
	@Redirect(method = "pick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;pick(Lnet/minecraft/world/entity/Entity;DDF)Lnet/minecraft/world/phys/HitResult;"))
	public HitResult pick(GameRenderer renderer, Entity cameraEntity, double blockInteractRange, double entityInteractRange, float partialTicks) {
		HitResult result = pick(cameraEntity, blockInteractRange, entityInteractRange, partialTicks);
		
		WaylandCraft.instance.hitResult = null;
		
		Vec3 pos = cameraEntity.getEyePosition(partialTicks);
		Vec3 dir = cameraEntity.getViewVector(partialTicks);
		
		WindowHitResult windowHit = null;
		
		for(Window window : WaylandCraft.instance.windows) {
			WindowHitResult h = window.intersect(pos, dir);
			if(h == null) continue;
			
			if(windowHit == null || h.dist < windowHit.dist) {
				windowHit = h;
			}
		}
		
		if(windowHit == null) return result;
		
		// Only check window hit up until block range
		if(!windowHit.position.closerThan(pos, blockInteractRange)) return result;
		
		// Check if the window is closer than the normal result
		if(windowHit.position.distanceToSqr(pos) < result.getLocation().distanceToSqr(pos)) {
			WaylandCraft.instance.hitResult = windowHit;
			
			Vec3 diff = windowHit.position.subtract(pos);
			return BlockHitResult.miss(windowHit.position, Direction.getNearest(diff), BlockPos.containing(windowHit.position));
		}
		
		return result;
	}
	
}
