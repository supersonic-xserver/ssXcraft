package dev.evvie.waylandcraft.render;

import java.awt.Color;

import org.joml.Vector2f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.serialization.MapCodec;

import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.bridge.WLCToplevel;
import dev.evvie.waylandcraft.desktop.DesktopEntry;
import dev.evvie.waylandcraft.item.WindowItem;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemModels;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState.LayerRenderState;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class WindowItemModel implements ItemModel {
	
	private static final WindowItemModel INSTANCE = new WindowItemModel();
	private static final ResourceLocation WINDOW_MODEL = ResourceLocation.fromNamespaceAndPath(WaylandCraft.MOD_ID, "item/window");
	private static final ResourceLocation BROKEN_WINDOW_MODEL = ResourceLocation.fromNamespaceAndPath(WaylandCraft.MOD_ID, "item/broken_window");
	
	private static WindowSpecialRenderer renderer;
	
	public static void register() {
		ItemModels.ID_MAPPER.put(ResourceLocation.fromNamespaceAndPath(WaylandCraft.MOD_ID, "window"), WindowItemModel.Unbaked.MAP_CODEC);
		ModelLoadingPlugin.register((ctx) -> ctx.addModels(WINDOW_MODEL, BROKEN_WINDOW_MODEL));
	}
	
	@Override
	public void update(ItemStackRenderState state, ItemStack item, ItemModelResolver modelResolver, ItemDisplayContext displayContext, ClientLevel level, LivingEntity entity, int i) {
		BakedModel windowModel = Minecraft.getInstance().getModelManager().getModel(WINDOW_MODEL);
		BakedModel brokenWindowModel = Minecraft.getInstance().getModelManager().getModel(BROKEN_WINDOW_MODEL);
		
		WLCToplevel toplevel = WindowItem.getToplevel(item);
		if(toplevel == null) {
			state.newLayer().setupBlockModel(brokenWindowModel, Sheets.cutoutBlockSheet());
			return;
		}
		
		DesktopEntry entry = WaylandCraft.instance.xdgManager.forAppId(toplevel.appID);
		ResourceLocation icon = null;
		if(entry == null || (icon = entry.getIcon()) == null) {
			state.newLayer().setupBlockModel(windowModel, Sheets.cutoutBlockSheet());
			return;
		}
		
		if(renderer == null) renderer = new WindowSpecialRenderer();
		LayerRenderState layer = state.newLayer();
		layer.setupSpecialModel(renderer, icon, windowModel);
	}
	
	public static class WindowSpecialRenderer implements SpecialModelRenderer<ResourceLocation> {
		
		@Override
		public void render(ResourceLocation icon, ItemDisplayContext itemDisplayContext, PoseStack poseStack, MultiBufferSource multiBufferSource, int light, int overlayCoords, boolean foil) {
			poseStack.pushPose();
			poseStack.translate(0, 0, 0.5);
			renderIconItem(poseStack.last(), multiBufferSource, icon, light, overlayCoords);
			poseStack.popPose();
		}
		
		@Override
		public ResourceLocation extractArgument(ItemStack itemStack) {
			return null;
		}
		
		private void renderIconItem(Pose pose, MultiBufferSource source, ResourceLocation tex, int light, int overlayCoords) {
			VertexConsumer buffer = source.getBuffer(RenderType.itemEntityTranslucentCull(tex));
			Vector3f pos1 = pose.pose().transformPosition(0, 1, 0, new Vector3f());
			Vector3f pos2 = pose.pose().transformPosition(0, 0, 0, new Vector3f());
			Vector3f pos3 = pose.pose().transformPosition(1, 0, 0, new Vector3f());
			Vector3f pos4 = pose.pose().transformPosition(1, 1, 0, new Vector3f());
			
			Vector2f uv1 = new Vector2f(0, 0);
			Vector2f uv2 = new Vector2f(0, 1);
			Vector2f uv3 = new Vector2f(1, 1);
			Vector2f uv4 = new Vector2f(1, 0);
			
			Vector3f normal = pose.transformNormal(0, 0, 1, new Vector3f());
			
			// Front quad
			buffer.addVertex(/* pos */ pos1.x, pos1.y, pos1.z, /* color */ Color.white.getRGB(), /* uv */ uv1.x, uv1.y, /* overlay */ overlayCoords, /* uv2 */ light, /* normal */ normal.x, normal.y, normal.z);
			buffer.addVertex(/* pos */ pos2.x, pos2.y, pos2.z, /* color */ Color.white.getRGB(), /* uv */ uv2.x, uv2.y, /* overlay */ overlayCoords, /* uv2 */ light, /* normal */ normal.x, normal.y, normal.z);
			buffer.addVertex(/* pos */ pos3.x, pos3.y, pos3.z, /* color */ Color.white.getRGB(), /* uv */ uv3.x, uv3.y, /* overlay */ overlayCoords, /* uv2 */ light, /* normal */ normal.x, normal.y, normal.z);
			buffer.addVertex(/* pos */ pos4.x, pos4.y, pos4.z, /* color */ Color.white.getRGB(), /* uv */ uv4.x, uv4.y, /* overlay */ overlayCoords, /* uv2 */ light, /* normal */ normal.x, normal.y, normal.z);
			
			// Back quad
			buffer.addVertex(/* pos */ pos1.x, pos1.y, pos1.z, /* color */ Color.white.getRGB(), /* uv */ uv1.x, uv1.y, /* overlay */ overlayCoords, /* uv2 */ light, /* normal */ normal.x, normal.y, normal.z);
			buffer.addVertex(/* pos */ pos4.x, pos4.y, pos4.z, /* color */ Color.white.getRGB(), /* uv */ uv4.x, uv4.y, /* overlay */ overlayCoords, /* uv2 */ light, /* normal */ normal.x, normal.y, normal.z);
			buffer.addVertex(/* pos */ pos3.x, pos3.y, pos3.z, /* color */ Color.white.getRGB(), /* uv */ uv3.x, uv3.y, /* overlay */ overlayCoords, /* uv2 */ light, /* normal */ normal.x, normal.y, normal.z);
			buffer.addVertex(/* pos */ pos2.x, pos2.y, pos2.z, /* color */ Color.white.getRGB(), /* uv */ uv2.x, uv2.y, /* overlay */ overlayCoords, /* uv2 */ light, /* normal */ normal.x, normal.y, normal.z);
		}
		
	}
	
	public static record Unbaked() implements ItemModel.Unbaked {
		
		public static final MapCodec<WindowItemModel.Unbaked> MAP_CODEC = MapCodec.unit(new WindowItemModel.Unbaked());
		
		@Override
		public void resolveDependencies(Resolver resolver) {
		}
		
		@Override
		public MapCodec<? extends ItemModel.Unbaked> type() {
			return MAP_CODEC;
		}
		
		@Override
		public ItemModel bake(BakingContext bakingContext) {
			return INSTANCE;
		}
		
	}
	
}
