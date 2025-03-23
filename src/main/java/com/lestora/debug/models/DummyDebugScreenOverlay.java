package com.lestora.debug.models;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.DebugScreenOverlay;

public class DummyDebugScreenOverlay extends DebugScreenOverlay {
    public DummyDebugScreenOverlay(Minecraft minecraft) {
        super(minecraft);
    }

    @Override
    public void render(GuiGraphics guiGraphics) {
        // Do nothing.
    }
}