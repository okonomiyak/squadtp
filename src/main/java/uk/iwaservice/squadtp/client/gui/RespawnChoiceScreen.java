package uk.iwaservice.squadtp.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import uk.iwaservice.squadtp.client.SquadColors;
import uk.iwaservice.squadtp.compat.JourneyMapCompat;
import uk.iwaservice.squadtp.network.RespawnChoicePacket;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Shown right after a squad member respawns: pick where to continue, with a
 * map preview (JourneyMap tile when available, simple radar otherwise).
 * The server only honors /squad respawn inside a short post-respawn window,
 * so this cannot be abused as a regular teleport.
 */
public class RespawnChoiceScreen extends Screen {

    private static final int HEADER_H = 24;
    private static final int PAD = 12;
    private static final int ROW_H = 26;
    private static final int LIST_WIDTH = 220;
    private static final int MAP_SIZE = 148;
    /** Selectable map radii (blocks); JourneyMap zoom level rises as the area shrinks. */
    private static final int[] MAP_RADII = {32, 64, 128, 256};
    private static final int[] JM_ZOOMS = {3, 2, 1, 0};
    private static final int DEFAULT_RADIUS_INDEX = 1;

    private final RespawnChoicePacket data;

    private int panelWidth;
    private int panelLeft;
    private int panelTop;
    private int panelHeight;
    private int mapX;
    private int mapY;

    // Chunk-aligned world bounds of the map area (must match JmMapTile's alignment).
    private int mapMinX;
    private int mapMinZ;
    private int mapMaxX;
    private int mapMaxZ;
    private ResourceLocation mapDim;
    private BlockPos mapCenter;

    @Nullable
    private DynamicTexture mapTexture;
    @Nullable
    private ResourceLocation mapTextureId;
    private int radiusIndex = DEFAULT_RADIUS_INDEX;
    /** Serial of the latest tile request; stale callbacks are discarded. */
    private int tileSerial;

    public RespawnChoiceScreen(RespawnChoicePacket data) {
        super(Component.translatable("squadtp.gui.respawn_title"));
        this.data = data;
    }

    @Override
    protected void init() {
        int rows = (data.hasRally() ? 1 : 0) + (data.hasBeacon() ? 1 : 0) + data.members().size();
        panelWidth = Math.min(PAD * 3 + LIST_WIDTH + MAP_SIZE, this.width - 12);
        panelHeight = Math.max(HEADER_H + 8 + 14 + rows * ROW_H + 8 + 24 + PAD,
                HEADER_H + 8 + MAP_SIZE + 12 + 24 + PAD);
        panelLeft = (this.width - panelWidth) / 2;
        panelTop = Math.max(12, (this.height - panelHeight) / 2 - 8);
        mapX = panelLeft + panelWidth - PAD - MAP_SIZE;
        mapY = panelTop + HEADER_H + 22;

        if (minecraft != null && minecraft.player != null) {
            mapDim = minecraft.player.level().dimension().location();
            mapCenter = minecraft.player.blockPosition();
            requestTile();

            addRenderableWidget(Button.builder(Component.literal("+"),
                            b -> changeRadius(-1))
                    .bounds(mapX + MAP_SIZE - 30, mapY + MAP_SIZE - 15, 14, 14).build());
            addRenderableWidget(Button.builder(Component.literal("-"),
                            b -> changeRadius(1))
                    .bounds(mapX + MAP_SIZE - 15, mapY + MAP_SIZE - 15, 14, 14).build());
        }

        int y = panelTop + HEADER_H + 22;
        int buttonX = panelLeft + PAD + LIST_WIDTH - 60;
        if (data.hasRally()) {
            addRenderableWidget(Button.builder(Component.translatable("squadtp.gui.respawn_go"),
                            b -> { command("squad respawn rally"); onClose(); })
                    .bounds(buttonX, y, 60, 20).build());
            y += ROW_H;
        }
        if (data.hasBeacon()) {
            addRenderableWidget(Button.builder(Component.translatable("squadtp.gui.respawn_go"),
                            b -> { command("squad respawn beacon"); onClose(); })
                    .bounds(buttonX, y, 60, 20).build());
            y += ROW_H;
        }
        for (RespawnChoicePacket.Entry member : data.members()) {
            String name = member.name();
            addRenderableWidget(Button.builder(Component.translatable("squadtp.gui.respawn_go"),
                            b -> { command("squad respawn member " + name); onClose(); })
                    .bounds(buttonX, y, 60, 20).build());
            y += ROW_H;
        }
        y += 8;
        addRenderableWidget(Button.builder(Component.translatable("squadtp.gui.respawn_stay"),
                        b -> onClose())
                .bounds(panelLeft + PAD, y, LIST_WIDTH, 20).build());
    }

    private void changeRadius(int delta) {
        int next = Math.max(0, Math.min(MAP_RADII.length - 1, radiusIndex + delta));
        if (next != radiusIndex) {
            radiusIndex = next;
            requestTile();
        }
    }

    /** (Re)computes the chunk-aligned map bounds and requests a fresh JourneyMap tile. */
    private void requestTile() {
        if (mapDim == null || mapCenter == null) {
            return;
        }
        int radius = MAP_RADII[radiusIndex];
        mapMinX = ((mapCenter.getX() - radius) >> 4) << 4;
        mapMinZ = ((mapCenter.getZ() - radius) >> 4) << 4;
        mapMaxX = (((mapCenter.getX() + radius) >> 4) << 4) + 16;
        mapMaxZ = (((mapCenter.getZ() + radius) >> 4) << 4) + 16;

        releaseTexture();
        int serial = ++tileSerial;
        JourneyMapCompat.requestMapTile(mapDim, mapCenter, radius, JM_ZOOMS[radiusIndex],
                image -> acceptTile(serial, image));
    }

    /** JourneyMap tile callback; may arrive on a background thread. */
    private void acceptTile(int serial, @Nullable NativeImage image) {
        if (image == null) {
            return;
        }
        Minecraft.getInstance().execute(() -> {
            if (serial != tileSerial || mapTexture != null) {
                image.close(); // stale request or duplicate callback
                return;
            }
            DynamicTexture texture = new DynamicTexture(image);
            mapTexture = texture;
            mapTextureId = Minecraft.getInstance().getTextureManager().register("squadtp_respawn_map", texture);
        });
    }

    private void releaseTexture() {
        if (mapTextureId != null) {
            Minecraft.getInstance().getTextureManager().release(mapTextureId);
            mapTextureId = null;
            mapTexture = null;
        }
    }

    @Override
    public void removed() {
        tileSerial++;
        releaseTexture();
        super.removed();
    }

    private void command(String command) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.connection.sendCommand(command);
        }
    }

    // --- rendering ---

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        int l = panelLeft;
        int t = panelTop;
        int r = l + panelWidth;
        int b = t + panelHeight;
        graphics.fill(l - 1, t - 1, r + 1, b + 1, 0x90000000);
        graphics.fill(l, t, r, b, 0xF4141420);
        graphics.fill(l, t, r, t + HEADER_H, 0xFF1F2333);
        graphics.fill(l, t + HEADER_H, r, t + HEADER_H + 1, 0xFF4A5A8A);
        graphics.renderOutline(l - 1, t - 1, panelWidth + 2, panelHeight + 2, 0xFF454A66);

        graphics.drawString(this.font, this.title, l + PAD, t + 8, 0xFFFFFF);
        graphics.drawString(this.font, Component.translatable("squadtp.gui.respawn_hint", data.windowSeconds())
                .withStyle(ChatFormatting.GRAY), l + PAD, t + HEADER_H + 7, 0x6A7188);

        renderList(graphics);
        renderMap(graphics);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderList(GuiGraphics graphics) {
        int y = panelTop + HEADER_H + 22;
        int x = panelLeft + PAD;
        if (data.hasRally()) {
            graphics.fill(x, y + 6, x + 8, y + 14, 0xFF000000 | SquadColors.RALLY_COLOR);
            graphics.drawString(this.font, Component.translatable("squadtp.gui.respawn_rally"), x + 14, y + 2, 0xFFFFFF);
            graphics.drawString(this.font, locationInfo(data.rallyDim(), data.rallyPos()), x + 14, y + 13, 0x6A7188);
            y += ROW_H;
        }
        if (data.hasBeacon()) {
            graphics.fill(x, y + 6, x + 8, y + 14, 0xFF000000 | SquadColors.BEACON_COLOR);
            graphics.drawString(this.font, Component.translatable("squadtp.gui.respawn_beacon"), x + 14, y + 2, 0xFFFFFF);
            Component beaconInfo = locationInfo(data.beaconDim(), data.beaconPos())
                    .copy().append(" (" + data.beaconUsesRemaining() + ")");
            graphics.drawString(this.font, beaconInfo, x + 14, y + 13, 0x6A7188);
            y += ROW_H;
        }
        int slot = 0;
        for (RespawnChoicePacket.Entry member : data.members()) {
            int color = SquadColors.memberColor(slot++);
            graphics.fill(x, y + 1, x + 3, y + ROW_H - 3, 0xFF000000 | color);
            PlayerFaceRenderer.draw(graphics, skinFor(member.uuid()), x + 7, y + 5, 12);
            graphics.drawString(this.font, member.name(), x + 24, y + 2, 0xFFFFFF);
            graphics.drawString(this.font, locationInfo(member.dimension(), member.pos()), x + 24, y + 13, 0x6A7188);
            y += ROW_H;
        }
    }

    private Component locationInfo(@Nullable ResourceLocation dim, @Nullable BlockPos pos) {
        if (dim == null || pos == null) {
            return Component.empty();
        }
        if (mapDim != null && !mapDim.equals(dim)) {
            return Component.translatable("squadtp.gui.respawn_other_dim", shortDim(dim));
        }
        if (minecraft != null && minecraft.player != null) {
            int dist = (int) Math.sqrt(pos.distToCenterSqr(minecraft.player.position()));
            return Component.literal(dist + "m");
        }
        return Component.empty();
    }

    private void renderMap(GuiGraphics graphics) {
        int x = mapX;
        int y = mapY;
        graphics.renderOutline(x - 1, y - 1, MAP_SIZE + 2, MAP_SIZE + 2, 0xFF454A66);

        if (mapTextureId != null && mapTexture != null && mapTexture.getPixels() != null) {
            int texW = mapTexture.getPixels().getWidth();
            int texH = mapTexture.getPixels().getHeight();
            graphics.blit(mapTextureId, x, y, MAP_SIZE, MAP_SIZE, 0, 0, texW, texH, texW, texH);
        } else {
            // Radar fallback: dark field with a subtle cross.
            graphics.fill(x, y, x + MAP_SIZE, y + MAP_SIZE, 0xFF0B0B12);
            graphics.fill(x, y + MAP_SIZE / 2, x + MAP_SIZE, y + MAP_SIZE / 2 + 1, 0x22FFFFFF);
            graphics.fill(x + MAP_SIZE / 2, y, x + MAP_SIZE / 2 + 1, y + MAP_SIZE, 0x22FFFFFF);
        }

        // Current range indicator, e.g. "±64".
        graphics.fill(x + 2, y + 2, x + 34, y + 13, 0x90000000);
        graphics.drawString(this.font, "±" + MAP_RADII[radiusIndex], x + 5, y + 4, 0xC0C8DC);

        if (mapDim == null || mapCenter == null) {
            return;
        }
        // Markers: rally, members (same dimension only), self.
        if (data.hasRally() && mapDim.equals(data.rallyDim())) {
            drawMarker(graphics, data.rallyPos(), 0xFF000000 | SquadColors.RALLY_COLOR, 3);
        }
        if (data.hasBeacon() && mapDim.equals(data.beaconDim())) {
            drawMarker(graphics, data.beaconPos(), 0xFF000000 | SquadColors.BEACON_COLOR, 3);
        }
        int slot = 0;
        for (RespawnChoicePacket.Entry member : data.members()) {
            int color = SquadColors.memberColor(slot++);
            if (mapDim.equals(member.dimension())) {
                drawMarker(graphics, member.pos(), 0xFF000000 | color, 2);
            }
        }
        drawMarker(graphics, mapCenter, 0xFFFFFFFF, 2);
    }

    private void drawMarker(GuiGraphics graphics, BlockPos pos, int argb, int halfSize) {
        double fx = (pos.getX() - mapMinX) / (double) (mapMaxX - mapMinX);
        double fz = (pos.getZ() - mapMinZ) / (double) (mapMaxZ - mapMinZ);
        int px = mapX + (int) Math.round(clamp01(fx) * MAP_SIZE);
        int py = mapY + (int) Math.round(clamp01(fz) * MAP_SIZE);
        px = Math.max(mapX + halfSize, Math.min(mapX + MAP_SIZE - halfSize, px));
        py = Math.max(mapY + halfSize, Math.min(mapY + MAP_SIZE - halfSize, py));
        graphics.fill(px - halfSize, py - halfSize, px + halfSize, py + halfSize, argb);
        graphics.renderOutline(px - halfSize - 1, py - halfSize - 1, halfSize * 2 + 2, halfSize * 2 + 2, 0x80000000);
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private ResourceLocation skinFor(UUID uuid) {
        if (minecraft != null && minecraft.getConnection() != null) {
            PlayerInfo info = minecraft.getConnection().getPlayerInfo(uuid);
            if (info != null) {
                return info.getSkinLocation();
            }
        }
        return DefaultPlayerSkin.getDefaultSkin(uuid);
    }

    private static String shortDim(ResourceLocation dim) {
        return "minecraft".equals(dim.getNamespace()) ? dim.getPath() : dim.toString();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
