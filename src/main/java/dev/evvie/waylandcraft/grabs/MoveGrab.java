package dev.evvie.waylandcraft.grabs;

import dev.evvie.waylandcraft.WindowDisplay;
import dev.evvie.waylandcraft.WindowDisplay.DisplayHitResult;
import dev.evvie.waylandcraft.bridge.WLCAbstractWindow;
import dev.evvie.waylandcraft.bridge.WLCSurface;
import dev.evvie.waylandcraft.grabs.PointerGrabMap.ImplicitGrab;
import net.minecraft.world.phys.Vec3;

public class MoveGrab extends PointerGrab {
	
	private final WindowDisplay window;
	private Vec3 initialSurfaceLocal = null;
	
	public MoveGrab(ImplicitGrab implicit) {
		super(implicit.button());
		this.window = implicit.window();
		this.initialSurfaceLocal = implicit.startSurfaceLocal();
	}
	
	@Override
	public void init() throws GrabDroppedException {
	}
	
	@Override
	public void release() throws GrabDroppedException {
	}
	
	@Override
	public void moveWorld(Vec3 pos, Vec3 view, Vec3 up) throws GrabDroppedException {
		DisplayHitResult hitResult = window.intersect(pos, view);
		if(hitResult == null) return;
		
		Vec3 diff = hitResult.surfaceLocalOrigin.subtract(initialSurfaceLocal);
		window.pivot = window.pivot.add(window.localX().scale(diff.x).add(window.localY().scale(diff.y)));
	}
	
	@Override
	public void hover(WLCAbstractWindow window, WLCSurface surface, double x, double y) throws GrabDroppedException {
	}
	
}
