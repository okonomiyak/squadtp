package uk.iwaservice.squadtp.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import uk.iwaservice.squadtp.client.ClientReviveData;

/** HUD: "DOWNED" banner for the downed player and the revive channel gauge. */
public class DownedHudOverlay implements IGuiOverlay {

    public static final DownedHudOverlay INSTANCE = new DownedHudOverlay();

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        Font font = mc.font;

        int remaining = ClientReviveData.getDownedRemainingTicks();
        if (remaining >= 0) {
            graphics.pose().pushPose();
            graphics.pose().translate(width / 2f, height / 2f - 58, 0);
            graphics.pose().scale(2f, 2f, 1f);
            graphics.drawCenteredString(font, Component.translatable("squadtp.hud.downed"), 0, 0, 0xFF5555);
            graphics.pose().popPose();
            graphics.drawCenteredString(font,
                    Component.translatable("squadtp.hud.downed_hint", (remaining + 19) / 20),
                    width / 2, height / 2 - 34, 0xFFAAAA);
            graphics.drawCenteredString(font,
                    Component.translatable("squadtp.hud.giveup_hint",
                            uk.iwaservice.squadtp.client.ClientModEvents.GIVE_UP.getTranslatedKeyMessage()),
                    width / 2, height / 2 - 22, 0x999999);
            int hold = ClientReviveData.getGiveUpHoldTicks();
            if (hold > 0) {
                int barWidth = 60;
                int x = (width - barWidth) / 2;
                int y = height / 2 - 10;
                graphics.fill(x - 1, y - 1, x + barWidth + 1, y + 4, 0xA0000000);
                int fill = (int) (barWidth * Math.min(1f,
                        hold / (float) uk.iwaservice.squadtp.Config.GIVE_UP_HOLD_TICKS.get()));
                graphics.fill(x, y, x + fill, y + 3, 0xFFDF6F6F);
            }
        }

        int progress = ClientReviveData.getReviveProgressTicks();
        int total = ClientReviveData.getReviveTotalTicks();
        if (progress >= 0 && total > 0) {
            int barWidth = 120;
            int x = (width - barWidth) / 2;
            int y = height / 2 + 24;
            graphics.drawCenteredString(font, Component.translatable("squadtp.hud.reviving"),
                    width / 2, y - 11, 0xFFFFFF);
            graphics.fill(x - 1, y - 1, x + barWidth + 1, y + 6, 0xA0000000);
            int fill = (int) (barWidth * Math.min(1f, progress / (float) total));
            graphics.fill(x, y, x + fill, y + 5, 0xFF6FDF6F);
        }
    }
}
