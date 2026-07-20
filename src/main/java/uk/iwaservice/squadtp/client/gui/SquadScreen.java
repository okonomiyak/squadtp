package uk.iwaservice.squadtp.client.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import uk.iwaservice.squadtp.client.SquadClientData;
import uk.iwaservice.squadtp.client.SquadColors;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Squad management window. Every action simply sends the corresponding
 * /squad command, so server-side permission checks remain the single source
 * of truth and the GUI adds no new network surface.
 */
public class SquadScreen extends Screen {

    private static final int HEADER_H = 24;
    private static final int PAD = 12;
    private static final int ROW_H = 26;
    private static final int MAX_LIST_ROWS = 5;

    private static final int COLOR_PANEL_BG = 0xF4141420;
    private static final int COLOR_HEADER_BG = 0xFF1F2333;
    private static final int COLOR_ACCENT = 0xFF4A5A8A;
    private static final int COLOR_OUTLINE = 0xFF454A66;
    private static final int COLOR_TEXT = 0xFFFFFF;
    private static final int COLOR_TEXT_DIM = 0xA0A8C0;
    private static final int COLOR_TEXT_FAINT = 0x6A7188;
    private static final int COLOR_SEPARATOR = 0x28FFFFFF;
    private static final int COLOR_ONLINE = 0xFF55FF55;
    private static final int COLOR_OFFLINE = 0xFF666B80;

    private record TextLine(int x, int y, Component text, int color, boolean centered) {}
    private record Rect(int x, int y, int w, int h, int color) {}
    private record Face(int x, int y, UUID uuid) {}
    private interface Placement { void place(int top); }

    private final List<TextLine> textLines = new ArrayList<>();
    private final List<Rect> rects = new ArrayList<>();
    private final List<Face> faces = new ArrayList<>();
    private final List<Placement> pending = new ArrayList<>();

    private int panelWidth = 360;
    private int panelLeft;
    private int panelTop;
    private int panelHeight;
    private int cursor;
    private int dataRevision = -1;
    private Component headerRight = Component.empty();

    public SquadScreen() {
        super(Component.translatable("squadtp.gui.title"));
    }

    @Override
    protected void init() {
        rebuild();
    }

    @Override
    public void tick() {
        // Sync/pos packets arrive while the screen is open; rebuild on change.
        if (dataRevision != SquadClientData.getRevision()) {
            rebuild();
        }
    }

    // --- layout helpers (phase A records placements, phase B assigns absolute y) ---

    private void text(int relX, int relY, Component t, int color) {
        pending.add(top -> textLines.add(new TextLine(panelLeft + relX, top + relY, t, color, false)));
    }

    private void centeredText(int relY, Component t, int color) {
        pending.add(top -> textLines.add(new TextLine(panelLeft + panelWidth / 2, top + relY, t, color, true)));
    }

    private void rect(int relX, int relY, int w, int h, int color) {
        pending.add(top -> rects.add(new Rect(panelLeft + relX, top + relY, w, h, color)));
    }

    private void face(int relX, int relY, UUID uuid) {
        pending.add(top -> faces.add(new Face(panelLeft + relX, top + relY, uuid)));
    }

    private void button(int relX, int relY, int w, Component label, @Nullable Component tooltip, Runnable action) {
        pending.add(top -> {
            Button b = Button.builder(label, btn -> action.run())
                    .bounds(panelLeft + relX, top + relY, w, 20).build();
            if (tooltip != null) {
                b.setTooltip(Tooltip.create(tooltip));
            }
            addRenderableWidget(b);
        });
    }

    private void section(String key) {
        cursor += 4;
        text(PAD, cursor, Component.translatable(key), COLOR_TEXT_DIM);
        rect(PAD, cursor + 11, panelWidth - 2 * PAD, 1, COLOR_SEPARATOR);
        cursor += 17;
    }

    // --- content ---

    private void rebuild() {
        clearWidgets();
        textLines.clear();
        rects.clear();
        faces.clear();
        pending.clear();
        dataRevision = SquadClientData.getRevision();
        headerRight = Component.empty();

        if (minecraft == null || minecraft.player == null) {
            return;
        }
        panelWidth = Math.min(360, this.width - 16);
        cursor = HEADER_H + 8;

        if (SquadClientData.isInSquad()) {
            buildSquadView();
        } else {
            buildNoSquadView();
        }

        buildSettingsSection();

        cursor += PAD - 4;
        panelHeight = cursor;
        panelLeft = (this.width - panelWidth) / 2;
        panelTop = Math.max(12, (this.height - panelHeight) / 2 - 8);

        for (Placement p : pending) {
            p.place(panelTop);
        }
    }

    /** Client-only preferences; shown regardless of squad membership. */
    private void buildSettingsSection() {
        section("squadtp.gui.section_settings");
        boolean bellOn = uk.iwaservice.squadtp.client.ClientConfig.BELL_SOUND_ENABLED.get();
        text(PAD, cursor + 6, Component.translatable("squadtp.gui.bell_sound"), COLOR_TEXT);
        button(panelWidth - PAD - 70, cursor, 70,
                Component.translatable(bellOn ? "squadtp.gui.bell_on" : "squadtp.gui.bell_off"),
                Component.translatable("squadtp.gui.tooltip.bell_toggle"),
                () -> {
                    uk.iwaservice.squadtp.client.ClientConfig.BELL_SOUND_ENABLED.set(!bellOn);
                    uk.iwaservice.squadtp.client.ClientConfig.BELL_SOUND_ENABLED.save();
                    rebuild();
                });
        cursor += ROW_H;
    }

    private void buildNoSquadView() {
        centeredText(cursor + 2, Component.translatable("squadtp.gui.no_squad_hint"), COLOR_TEXT_DIM);
        cursor += 16;

        String invitedBy = SquadClientData.getInvitedBy();
        if (invitedBy != null) {
            centeredText(cursor + 2, Component.translatable("squadtp.gui.invited_by", invitedBy)
                    .withStyle(ChatFormatting.YELLOW), COLOR_TEXT);
            cursor += 15;
            int half = (panelWidth - 2 * PAD - 4) / 2;
            button(PAD, cursor, half, Component.translatable("squadtp.gui.accept"), null,
                    () -> command("squad accept"));
            button(PAD + half + 4, cursor, half, Component.translatable("squadtp.gui.deny"), null,
                    () -> command("squad deny"));
            cursor += 24;
        }

        button(PAD, cursor, panelWidth - 2 * PAD, Component.translatable("squadtp.gui.create"), null,
                () -> command("squad create"));
        cursor += 24;

        buildJoinRequestList(false);
    }

    /**
     * "Request to join" rows for every online player (except self), one
     * "request" button each. Available whether or not the local player is
     * already in a squad - requesting a different squad while in one switches
     * squads on approval (the server re-validates and performs the switch).
     */
    private void buildJoinRequestList(boolean excludeOwnSquad) {
        List<PlayerInfo> candidates = new ArrayList<>();
        for (PlayerInfo info : onlinePlayersExcept(minecraft.player.getUUID())) {
            if (excludeOwnSquad && SquadClientData.getMembers().containsKey(info.getProfile().getId())) {
                continue;
            }
            candidates.add(info);
        }
        section("squadtp.gui.join_section");
        if (candidates.isEmpty()) {
            text(PAD, cursor + 2, Component.translatable("squadtp.gui.no_players"), COLOR_TEXT_FAINT);
            cursor += 16;
        } else {
            int shown = 0;
            for (PlayerInfo info : candidates) {
                if (shown++ >= MAX_LIST_ROWS) {
                    text(PAD, cursor + 4, Component.literal("…"), COLOR_TEXT_FAINT);
                    cursor += 14;
                    break;
                }
                UUID uuid = info.getProfile().getId();
                String name = info.getProfile().getName();
                face(PAD, cursor + 4, uuid);
                text(PAD + 17, cursor + 6, Component.literal(name), COLOR_TEXT);
                button(panelWidth - PAD - 80, cursor, 80, Component.translatable("squadtp.gui.request_join"),
                        Component.translatable("squadtp.gui.tooltip.request_join", name),
                        () -> command("squad join " + name));
                cursor += ROW_H;
            }
        }
    }

    private void buildSquadView() {
        UUID self = minecraft.player.getUUID();
        boolean isLeader = self.equals(SquadClientData.getLeader());
        Map<UUID, String> members = SquadClientData.getMembers();
        Map<UUID, SquadClientData.MemberPos> positions = SquadClientData.getPositions();
        headerRight = Component.translatable("squadtp.gui.members", members.size());

        section("squadtp.gui.section_members");
        int slot = 0;
        for (Map.Entry<UUID, String> entry : members.entrySet()) {
            UUID uuid = entry.getKey();
            String name = entry.getValue();
            int color = SquadColors.memberColor(slot++);
            boolean memberIsLeader = uuid.equals(SquadClientData.getLeader());
            boolean isSelf = uuid.equals(self);
            SquadClientData.MemberPos pos = positions.get(uuid);
            boolean online = pos != null || isSelf;

            rect(PAD, cursor + 1, 3, ROW_H - 4, 0xFF000000 | color);
            face(PAD + 7, cursor + 5, uuid);
            rect(PAD + 16, cursor + 12, 4, 4, online ? COLOR_ONLINE : COLOR_OFFLINE);

            var nameText = Component.literal(name);
            if (isSelf) {
                nameText.withStyle(ChatFormatting.AQUA);
            }
            if (memberIsLeader) {
                nameText.append(Component.literal(" ★").withStyle(ChatFormatting.GOLD));
            }
            text(PAD + 24, cursor + 2, nameText, COLOR_TEXT);
            text(PAD + 24, cursor + 13, subInfo(pos, isSelf), COLOR_TEXT_FAINT);

            int bx = panelWidth - PAD;
            if (isLeader && !isSelf) {
                bx -= 46;
                button(bx, cursor + 1, 46, Component.translatable("squadtp.gui.promote"),
                        Component.translatable("squadtp.gui.tooltip.promote"), () -> command("squad promote " + name));
                bx -= 50;
                button(bx, cursor + 1, 46, Component.translatable("squadtp.gui.kick"),
                        Component.translatable("squadtp.gui.tooltip.kick"), () -> command("squad kick " + name));
            }
            if (!isSelf && pos != null) {
                bx -= 44;
                button(bx, cursor + 1, 40, Component.translatable("squadtp.gui.tp"),
                        Component.translatable("squadtp.gui.tooltip.tp"),
                        () -> { command("squad tp " + name); onClose(); });
            }
            cursor += ROW_H;
        }

        section("squadtp.gui.section_rally");
        ResourceLocation rallyDim = SquadClientData.getRallyDimension();
        BlockPos rallyPos = SquadClientData.getRallyPos();
        if (rallyDim != null && rallyPos != null) {
            rect(PAD, cursor + 3, 8, 8, 0xFF000000 | SquadColors.RALLY_COLOR);
            text(PAD + 14, cursor + 3, Component.literal("%d, %d, %d".formatted(
                    rallyPos.getX(), rallyPos.getY(), rallyPos.getZ())), COLOR_TEXT);
            text(PAD + 14, cursor + 14, Component.literal(shortDim(rallyDim)), COLOR_TEXT_FAINT);
            button(panelWidth - PAD - 60, cursor, 60, Component.translatable("squadtp.gui.rally"),
                    Component.translatable("squadtp.gui.tooltip.rally"),
                    () -> { command("squad rally"); onClose(); });
            if (isLeader) {
                button(panelWidth - PAD - 60 - 80, cursor, 76, Component.translatable("squadtp.gui.setrally"), null,
                        () -> command("squad setrally"));
            }
        } else {
            text(PAD, cursor + 6, Component.translatable("squadtp.gui.rally_none"), COLOR_TEXT_FAINT);
            if (isLeader) {
                button(panelWidth - PAD - 76, cursor, 76, Component.translatable("squadtp.gui.setrally"), null,
                        () -> command("squad setrally"));
            }
        }
        cursor += ROW_H;

        if (isLeader) {
            List<String> requests = SquadClientData.getJoinRequests();
            if (!requests.isEmpty()) {
                section("squadtp.gui.requests_section");
                for (String name : requests) {
                    text(PAD, cursor + 6, Component.literal(name).withStyle(ChatFormatting.YELLOW), COLOR_TEXT);
                    button(panelWidth - PAD - 96, cursor, 46, Component.translatable("squadtp.gui.approve"), null,
                            () -> command("squad approve " + name));
                    button(panelWidth - PAD - 46, cursor, 46, Component.translatable("squadtp.gui.reject"), null,
                            () -> command("squad reject " + name));
                    cursor += ROW_H;
                }
            }

            List<PlayerInfo> invitable = new ArrayList<>();
            for (PlayerInfo info : onlinePlayersExcept(self)) {
                if (!SquadClientData.getMembers().containsKey(info.getProfile().getId())) {
                    invitable.add(info);
                }
            }
            section("squadtp.gui.invite_section");
            if (invitable.isEmpty()) {
                text(PAD, cursor + 2, Component.translatable("squadtp.gui.no_invitable"), COLOR_TEXT_FAINT);
                cursor += 16;
            } else {
                int shown = 0;
                for (PlayerInfo info : invitable) {
                    if (shown++ >= MAX_LIST_ROWS) {
                        text(PAD, cursor + 4, Component.literal("…"), COLOR_TEXT_FAINT);
                        cursor += 14;
                        break;
                    }
                    UUID uuid = info.getProfile().getId();
                    String name = info.getProfile().getName();
                    face(PAD, cursor + 4, uuid);
                    text(PAD + 17, cursor + 6, Component.literal(name), COLOR_TEXT);
                    button(panelWidth - PAD - 60, cursor, 60, Component.translatable("squadtp.gui.invite"), null,
                            () -> command("squad invite " + name));
                    cursor += ROW_H;
                }
            }
        }

        buildJoinRequestList(true);

        cursor += 4;
        button(PAD, cursor, 76, Component.translatable("squadtp.gui.leave"), null,
                () -> command("squad leave"));
        if (isLeader) {
            button(PAD + 80, cursor, 76, Component.translatable("squadtp.gui.disband"),
                    Component.translatable("squadtp.gui.tooltip.disband"), () -> command("squad disband"));
        }
        cursor += 24;
    }

    // --- data helpers ---

    private Component subInfo(@Nullable SquadClientData.MemberPos pos, boolean isSelf) {
        if (isSelf) {
            return Component.translatable("squadtp.gui.you");
        }
        if (pos == null) {
            return Component.translatable("squadtp.msg.info_offline");
        }
        StringBuilder sb = new StringBuilder("%d, %d, %d · %s".formatted(
                pos.pos().getX(), pos.pos().getY(), pos.pos().getZ(), shortDim(pos.dimension())));
        if (minecraft != null && minecraft.player != null
                && minecraft.player.level().dimension().location().equals(pos.dimension())) {
            double dist = Math.sqrt(pos.pos().distToCenterSqr(minecraft.player.position()));
            sb.append(" · ").append((int) dist).append("m");
        }
        return Component.literal(sb.toString());
    }

    private List<PlayerInfo> onlinePlayersExcept(UUID excluded) {
        List<PlayerInfo> result = new ArrayList<>();
        if (minecraft != null && minecraft.getConnection() != null) {
            for (PlayerInfo info : minecraft.getConnection().getOnlinePlayers()) {
                if (!info.getProfile().getId().equals(excluded)) {
                    result.add(info);
                }
            }
        }
        return result;
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
        graphics.fill(l, t, r, b, COLOR_PANEL_BG);
        graphics.fill(l, t, r, t + HEADER_H, COLOR_HEADER_BG);
        graphics.fill(l, t + HEADER_H, r, t + HEADER_H + 1, COLOR_ACCENT);
        graphics.renderOutline(l - 1, t - 1, panelWidth + 2, panelHeight + 2, COLOR_OUTLINE);

        graphics.drawString(this.font, this.title, l + PAD, t + 8, COLOR_TEXT);
        int rightWidth = this.font.width(headerRight);
        graphics.drawString(this.font, headerRight, r - PAD - rightWidth, t + 8, COLOR_TEXT_DIM);

        for (Rect rect : rects) {
            graphics.fill(rect.x(), rect.y(), rect.x() + rect.w(), rect.y() + rect.h(), rect.color());
        }
        for (Face faceEntry : faces) {
            PlayerFaceRenderer.draw(graphics, skinFor(faceEntry.uuid()), faceEntry.x(), faceEntry.y(), 12);
        }
        for (TextLine line : textLines) {
            if (line.centered()) {
                graphics.drawCenteredString(this.font, line.text(), line.x(), line.y(), line.color());
            } else {
                graphics.drawString(this.font, line.text(), line.x(), line.y(), line.color());
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
