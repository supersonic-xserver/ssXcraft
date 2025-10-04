package dev.evvie.waylandcraft;

import java.util.Random;

import org.joml.Matrix3d;
import org.joml.Matrix4f;
import org.joml.Vector3d;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import dev.evvie.waylandcraft.bridge.WLCSurface;
import dev.evvie.waylandcraft.bridge.WLCToplevel;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;

public class Window {
	
	private static final float PIXEL_SCALE = 1.0f / 500;
	
	public final WLCToplevel toplevel;
	
	// World position of window
	public Vec3 pivot = new Vec3(-250, 65, -500);
	
	// Window facing direction normal
	private Vec3 normal = new Vec3(0, 0, 1);
	
	// Window orientation downwards vector, has to be orthogonal to `normal` and normalized
	private Vec3 down = new Vec3(0, -1, 0);
	
	private int width;
	private int height;
	
	public Window(WLCToplevel toplevel) {
		this.toplevel = toplevel;
	}
	
	public boolean isAlive() {
		return toplevel.isAlive();
	}
	
	public Vec3 normal() {
		return normal;
	}
	
	public Vec3 down() {
		return down;
	}
	
	public Vec3 right() {
		return normal.cross(down);
	}
	
	public Vec3 localX() {
		return right().scale(PIXEL_SCALE);
	}
	
	public Vec3 localY() {
		return down.scale(PIXEL_SCALE);
	}
	
	public Vec3 origin() {
		return pivot.add(localX().scale(-width/2)).add(localY().scale(-height/2));
	}
	
	private void updateGeometry() {
		BufferTexture buf = toplevel.getSurfaceTree().getBuffer();
		if(buf == null) {
			width = 0;
			height = 0;
		}
		else {
			width = buf.width;
			height = buf.height;
		}
	}
	
	private long last = System.currentTimeMillis();
	private Random rand = new Random();
	
	public void render(WorldRenderContext ctx) {
		updateGeometry();
		
//		normal = new Vec3(ctx.camera().getLookVector()).reverse();
//		down = new Vec3(ctx.camera().getUpVector()).reverse();
		
		if(System.currentTimeMillis() - last > 10000) {
			normal = Vec3.directionFromRotation(rand.nextFloat(-180, 180), rand.nextFloat(-180, 180));
			down = new Vec3(rand.nextDouble(), rand.nextDouble(), rand.nextDouble()).cross(normal).normalize();
			last = System.currentTimeMillis();
		}
		
		int depth = 0;
		for(WLCSurface surface = toplevel.getSurfaceTree(); surface != null; surface = surface.getNextChild()) {
			renderSurface(ctx, surface, depth);
			depth++;
		}
	}
	
	private void renderSurface(WorldRenderContext ctx, WLCSurface surface, int depth) {
		Vec3 origin = origin();
		Vec3 localX = localX();
		Vec3 localY = localY();
		origin = origin.add(localX.scale(surface.xSubpos)).add(localY.scale(surface.ySubpos));
		origin = origin.add(normal.scale(depth * 0.0001));
		
		BufferTexture buf = surface.getBuffer();
		if(buf == null) return;
		
		Vec3 tl = origin;
		Vec3 bl = origin.add(localY.scale(buf.height));
		Vec3 br = bl.add(localX.scale(buf.width));
		Vec3 tr = tl.add(localX.scale(buf.width));
		
		Camera camera = ctx.camera();
		PoseStack matrixStack = new PoseStack();
		matrixStack.translate(-camera.getPosition().x, -camera.getPosition().y, -camera.getPosition().z);
		Matrix4f mat = matrixStack.last().pose();
		
		Tesselator tesselator = Tesselator.getInstance();
		
		/* Surface contents */
		BufferBuilder buffer = tesselator.getBuilder();
		buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
		buffer.vertex(mat, (float) tl.x, (float) tl.y, (float) tl.z).color(1.0f, 1.0f, 1.0f, 1.0f).uv(0, 0).endVertex();
		buffer.vertex(mat, (float) bl.x, (float) bl.y, (float) bl.z).color(1.0f, 1.0f, 1.0f, 1.0f).uv(0, 1).endVertex();
		buffer.vertex(mat, (float) br.x, (float) br.y, (float) br.z).color(1.0f, 1.0f, 1.0f, 1.0f).uv(1, 1).endVertex();
		buffer.vertex(mat, (float) tr.x, (float) tr.y, (float) tr.z).color(1.0f, 1.0f, 1.0f, 1.0f).uv(1, 0).endVertex();
		
		RenderSystem.setShader(GameRenderer::getPositionColorTexShader);
		RenderSystem.setShaderTexture(0, buf.getId());
		RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
		tesselator.end();
		
		/* Surface backside */
		buffer = tesselator.getBuilder();
		buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
		buffer.vertex(mat, (float) tl.x, (float) tl.y, (float) tl.z).color(0.0f, 0.0f, 0.0f, 1.0f).endVertex();
		buffer.vertex(mat, (float) tr.x, (float) tr.y, (float) tr.z).color(0.0f, 0.0f, 0.0f, 1.0f).endVertex();
		buffer.vertex(mat, (float) br.x, (float) br.y, (float) br.z).color(0.0f, 0.0f, 0.0f, 1.0f).endVertex();
		buffer.vertex(mat, (float) bl.x, (float) bl.y, (float) bl.z).color(0.0f, 0.0f, 0.0f, 1.0f).endVertex();
		
		RenderSystem.setShader(GameRenderer::getPositionColorShader);
		RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
		tesselator.end();
	}
	
	public WindowBounds calculateBounds() {
		WindowBounds bounds = new WindowBounds();
		WLCSurface surface;
		
		for(surface = toplevel.getSurfaceTree(); surface != null; surface = surface.getNextChild()) {
			BufferTexture buf = surface.getBuffer();
			if(buf == null) continue;
			
			int minX = surface.xSubpos;
			int minY = surface.ySubpos;
			int maxX = minX + buf.width;
			int maxY = minY + buf.height;
			
			if(minX < bounds.minX) bounds.minX = minX;
			if(minY < bounds.minY) bounds.minY = minY;
			if(maxX > bounds.maxX) bounds.maxX = maxX;
			if(maxY > bounds.maxY) bounds.maxY = maxY;
		}
		
		return bounds;
	}
	
	/* Transform absolute world coordinates to surface-local pixel coordinates relative to toplevel (0, 0)
	 * 
	 * The resulting vector is the (x, y) pixel location and the z value is the block distance normal to the plane.
	 */
	public Vec3 worldToLocal(Vec3 in) {
		Vec3 origin = origin();
		Vec3 localX = localX();
		Vec3 localY = localY();
		
		// World coordinates relative to the origin of this window
		Vec3 world = in.subtract(origin);
		
		Matrix3d matrix = new Matrix3d(
			localX.x, localX.y, localX.z, // Column 0
			localY.x, localY.y, localY.z, // Column 1
			normal.x, normal.y, normal.z  // Column 2
		);
		matrix.invert();
		
		Vector3d result = matrix.transform(new Vector3d(world.x, world.y, world.z));
		return new Vec3(result.x, result.y, result.z);
	}
	
	/* Perform ray-window intersection
	 * `dir` must be normalized.
	 */
	public WindowHitResult intersect(Vec3 pos, Vec3 dir) {
		double p1 = pivot.subtract(pos).dot(normal);
		double p2 = dir.dot(normal);
		
		// Avoid division by zero
		if(p2 == 0) return null;
		
		double t = p1 / p2;
		
		// Intersection happens behind the camera
		if(t < 0) return null;
		
		Vec3 hitPos = pos.add(dir.scale(t));
		Vec3 localCoords = worldToLocal(hitPos);
		
		WindowBounds bounds = calculateBounds();
		
		// Completely outside of window extent
		if(!bounds.contains((int) localCoords.x, (int) localCoords.y)) return null;
		
		// Flip z-coordinate when on the window backside
		double dist = t;
		if(p2 > 0) dist *= -1;
		
		return new WindowHitResult(this, hitPos, localCoords, dist);
	}
	
	public static class WindowBounds {
		
		public int minX;
		public int minY;
		public int maxX;
		public int maxY;
		
		public WindowBounds(int minX, int minY, int maxX, int maxY) {
			this.minX = minX;
			this.minY = minY;
			this.maxX = maxX;
			this.maxY = maxY;
		}
		
		public WindowBounds() {
			this(0, 0, 0, 0);
		}
		
		public boolean contains(int x, int y) {
			return x >= minX && x <= maxX && y >= minY && y <= maxY;
		}
		
	}
	
	public static class WindowHitResult {
		
		public final Window target;
		public final Vec3 position;
		public final Vec3 surfaceLocal;
		public final double dist;
		
		public WindowHitResult(Window target, Vec3 position, Vec3 surfaceLocal, double dist) {
			this.target = target;
			this.position = position;
			this.surfaceLocal = surfaceLocal;
			this.dist = dist;
		}
		
	}
	
}
