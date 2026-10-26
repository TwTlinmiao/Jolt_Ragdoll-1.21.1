package com.ragdoll.client;

import com.ragdoll.server.CorpseLootMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.ChatFormatting;

public final class CorpseLootScreen extends AbstractContainerScreen<CorpseLootMenu> {
    public CorpseLootScreen(
            CorpseLootMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 236;
    }

    @Override
    protected void renderBg(
            GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int left = leftPos;
        int top = topPos;
        graphics.fill(left, top, left + imageWidth, top + imageHeight, 0xFFC6C6C6);
        graphics.fill(left + 154, top + 2, left + 170, top + 18, 0xFF686868);
        graphics.fill(left + 155, top + 3, left + 169, top + 17, 0xFF8A3737);
        for (int i = 0; i < 36; ++i) {
            int x = left + 7 + (i % 9) * 18;
            int row = i / 9;
            int y = top + 141 + row * 18 + (row == 3 ? 4 : 0);
            graphics.fill(x, y, x + 18, y + 18, 0xFF3E3E3E);
            graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
        }
        for (int i = 0; i < 54; ++i) {
            int x;
            int y;
            if (menu.page() == 1) {
                x = left + 7 + (i % 9) * 18;
                y = top + 17 + (i / 9) * 18;
            } else if (i < 36) {
                x = left + 7 + (i % 9) * 18;
                y = top + 17 + (i / 9) * 18;
            } else if (i < 40) {
                x = left + 7 + (i - 36) * 18;
                y = top + 97;
            } else if (i == 40) {
                x = left + 79;
                y = top + 97;
            } else {
                continue;
            }
            graphics.fill(x, y, x + 18, y + 18, 0xFF3E3E3E);
            graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
        }
        if (menu.page() == 0) {
            graphics.fill(left + 100, top + 97, left + 169, top + 115, 0xFF686868);
            graphics.fill(left + 100, top + 117, left + 169, top + 135, 0xFF686868);
        } else {
            graphics.fill(left + 112, top + 2, left + 152, top + 18, 0xFF686868);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        Component header = menu.page() == 0
                ? title : Component.translatable("container.ragdoll.other_title");
        graphics.drawString(font, header, 8, 5, 0x404040, false);
        graphics.drawString(font, "X", 158, 6, 0xFFFFFF, false);
        if (menu.page() == 0) {
            drawCentered(graphics,
                    Component.translatable("container.ragdoll.other"), 100, 97, 69, 18);
            drawCentered(graphics,
                    Component.translatable("container.ragdoll.pickup"), 100, 117, 69, 18);
        } else {
            drawCentered(graphics,
                    Component.translatable("container.ragdoll.back"), 112, 2, 40, 16);
        }
        graphics.drawString(font,
                Component.translatable("container.inventory"),
                8, 122, 0x404040, false);
    }

    private void drawCentered(
            GuiGraphics graphics, Component text, int x, int y, int width, int height) {
        Component fittingText = font.width(text) <= width - 4
                ? text : Component.literal("拾取").withStyle(ChatFormatting.WHITE);
        graphics.drawString(font, fittingText,
                x + (width - font.width(fittingText)) / 2,
                y + (height - 8) / 2, 0xFFFFFF, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int x = (int) mouseX - leftPos;
            int y = (int) mouseY - topPos;
            if (x >= 154 && x < 170 && y >= 2 && y < 18) {
                minecraft.gameMode.handleInventoryButtonClick(
                        menu.containerId, 3);
                return true;
            }
            if (menu.page() == 0 && x >= 100 && x < 170 && y >= 97 && y < 116) {
                menu.clickMenuButton(minecraft.player, 0);
                minecraft.gameMode.handleInventoryButtonClick(
                        menu.containerId, 0);
                return true;
            }
            if (menu.page() == 0 && x >= 100 && x < 170 && y >= 117 && y < 136) {
                minecraft.gameMode.handleInventoryButtonClick(
                        menu.containerId, 2);
                return true;
            }
            if (menu.page() == 1 && x >= 112 && x < 152 && y >= 2 && y < 18) {
                menu.clickMenuButton(minecraft.player, 1);
                minecraft.gameMode.handleInventoryButtonClick(
                        menu.containerId, 1);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (mouseX >= leftPos + 154 && mouseX < leftPos + 170
                && mouseY >= topPos + 2 && mouseY < topPos + 18) {
            graphics.renderTooltip(font,
                    Component.translatable("container.ragdoll.clear"),
                    mouseX, mouseY);
        }
    }
}
