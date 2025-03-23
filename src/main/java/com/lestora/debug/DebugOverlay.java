package com.lestora.debug;

import com.lestora.debug.models.DebugObject;
import com.lestora.debug.models.DebugSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DebugOverlay {
    private static boolean onMainMenu = true;
    private static Level lastLevel = null;
    private static boolean mcF3Showing = false;
    private static boolean showDebug = false;
    private static boolean hudEnabled = false;
    private static boolean showHud = false;
    private static boolean f3Enabled = false;
    private static final Map<String, DebugSupplier> registeredLogs = new LinkedHashMap<>();
    private static final Map<String, List<Function<Integer, Integer>>> conditions = new LinkedHashMap<>();

    public static List<String> getLogs() {
        return registeredLogs.values().stream()
                .sorted(Comparator.comparingInt(DebugSupplier::getPriority).reversed())
                .map(ds -> "(" + ds.getPriority() + ")" + ds.getKey())
                .collect(Collectors.toList());
    }

    public static void changePriority(String key, Function<Integer, Integer> condition) {
        if (key == null || condition == null) return;
        conditions.computeIfAbsent(key, k -> new ArrayList<>()).add(condition);
    }

    static {
        var coordsSupplier = new DebugSupplier("MC_Location", 99, () -> {
            Player player = Minecraft.getInstance().player;
            BlockPos playerPos = player.blockPosition();
            var dobj = new DebugObject("Coords", playerPos.getX() + " " + playerPos.getY() + " " + playerPos.getZ(), "Coordinates", "X: " + playerPos.getX() + ", " + "Y " + playerPos.getY() + ", " + "Z: " + playerPos.getZ());
            return dobj;
        });
        registerDebugLine(coordsSupplier.getKey(), coordsSupplier);

        var facingSupplier = new DebugSupplier("MC_Facing", 98, () -> {
            Player player = Minecraft.getInstance().player;
            float adjustedYaw = (player.getYRot() + 180) % 360;
            if (adjustedYaw < 0)
                adjustedYaw += 360;
            String[] directions = {
                    "North", "North East", "East", "South East",
                    "South", "South West", "West", "North West"
            };

            int index = (int)((adjustedYaw + 22.5) / 45) % 8;
            var dobj = new DebugObject("Dir", directions[index], "Facing Direction", directions[index]);
            return dobj;
        });
        registerDebugLine(facingSupplier.getKey(), facingSupplier);

        var blockLightSupplier = new DebugSupplier("MC_Block_Light", 97, () -> {
            Player player = Minecraft.getInstance().player;
            var playerPos = player.blockPosition();
            var ll = String.valueOf(player.level().getLightEngine().getLayerListener(LightLayer.BLOCK).getLightValue(playerPos));
            return new DebugObject("Light", ll, "Block Light Level", ll);
        });
        registerDebugLine(blockLightSupplier.getKey(), blockLightSupplier);

        changePriority("MC_Location", x -> f3Enabled && !hudEnabled ? 0 : x);
        changePriority("MC_Facing", x -> f3Enabled && !hudEnabled ? 0 : x);
        changePriority("MC_Block_Light", x -> f3Enabled && !hudEnabled ? 0 : x);
    }

    public static boolean getF3Enabled() { return f3Enabled; }

    public static void setF3Enabled(boolean value) {
        if (value == f3Enabled) return;

        if (mcF3Showing && value == false){
            Minecraft.getInstance().player.displayClientMessage(Component.literal("You cannot disable F3 while the F3 screen is showing."), true);
        }
        else {
            f3Enabled = !f3Enabled;
            showDebug = false;
        }
    }

    public static void setHudEnabled(boolean value) {
        hudEnabled = value;
        if (hudEnabled)
            showHud = !showDebug;
        else
            showHud = false;
    }

    public static void registerDebugLine(String key, DebugSupplier supplier) {
        registeredLogs.put(key, supplier);
    }



    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (event.getKey() == GLFW.GLFW_KEY_F3 && event.getAction() == GLFW.GLFW_PRESS) {
            showDebug = !showDebug;
            if (hudEnabled)
                showHud = !showDebug;
            if (f3Enabled){
                mcF3Showing = showDebug;
            }
        }
    }

    @SubscribeEvent
    public static void onCustomizeGuiOverlay(CustomizeGuiOverlayEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        List<DebugSupplier> lines = new ArrayList<>();
        if (showDebug || showHud) {
            registeredLogs.entrySet().stream()
                    .sorted(Comparator.comparingInt((Map.Entry<String, DebugSupplier> entry) -> entry.getValue().getPriority()).reversed())
                    .forEach((Map.Entry<String, DebugSupplier> entry) -> {
                        int pri = entry.getValue().getPriority();
                        var conds = conditions.get(entry.getKey());
                        if (conds != null)
                            for (Function<Integer, Integer> c : conds)
                                pri = c.apply(pri);

                        if (pri > 0) lines.add(entry.getValue());
                    });

            lines.sort(Comparator.comparingInt(DebugSupplier::getPriority).reversed());
        }

        if (showDebug) {
            if (f3Enabled)
                drawCenteredLines(event.getGuiGraphics(), mc, lines.toArray(new DebugSupplier[0]));
            else
                drawLeftAlignedLines(event.getGuiGraphics(), mc, lines.toArray(new DebugSupplier[0]));
        }
        else if (showHud) {
            drawTopLines(event.getGuiGraphics(), mc, lines.toArray(new DebugSupplier[0]));
        }
    }

    private static void drawTopLines(GuiGraphics guiGraphics, Minecraft mc, DebugSupplier... lines) {
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int leftMargin = 15;
        int rightMargin = 15;
        int effectiveWidth = screenWidth - leftMargin - rightMargin;

        // Build a maximum text string for measurement using max characters.
        String maxKey = "W".repeat(8);    // max 8 characters for key
        String maxValue = "W".repeat(20);   // max 20 characters for value
        String maxText = maxKey + ": " + maxValue;
        int maxCellPixelWidth = mc.font.width(maxText);

        int minGap = 10; // minimum gap in pixels between cells

        // Calculate maximum cells per line.
        int maxCellsPerLine = (effectiveWidth + minGap) / (maxCellPixelWidth + minGap);
        if (maxCellsPerLine <= 0) return;

        // Determine how many items for the first line and, if needed, a second line.
        int firstLineCount = Math.min(lines.length, maxCellsPerLine);
        int secondLineCount = 0;
        if (lines.length > maxCellsPerLine) {
            secondLineCount = Math.min(lines.length - maxCellsPerLine, maxCellsPerLine);
        }

        // Draw the first line at y = 5.
        int y1 = 5;
        drawTopLine(guiGraphics, mc, lines, 0, firstLineCount, leftMargin, effectiveWidth, maxCellPixelWidth, minGap, y1);

        // Draw the second line, if needed.
        if (secondLineCount > 0) {
            int lineHeight = mc.font.lineHeight; // or use a fixed value like 15
            int y2 = y1 + lineHeight + 5;
            drawTopLine(guiGraphics, mc, lines, firstLineCount, secondLineCount, leftMargin, effectiveWidth, maxCellPixelWidth, minGap, y2);
        }
    }

    private static void drawTopLine(GuiGraphics guiGraphics, Minecraft mc, DebugSupplier[] lines, int startIndex, int count,
                                    int leftMargin, int effectiveWidth, int cellWidth, int minGap, int y) {
        int gap;
        int cellStartX;
        if (count > 1) {
            // Distribute cells evenly.
            gap = (effectiveWidth - count * cellWidth) / (count - 1);
            if (gap < minGap) gap = minGap;
            cellStartX = leftMargin;
        } else {
            gap = 0;
            cellStartX = leftMargin + (effectiveWidth - cellWidth) / 2;
        }

        for (int i = 0; i < count; i++) {
            DebugObject dobj = lines[startIndex + i].getSupplier().get();
            String key = dobj.getShortKey();
            String value = dobj.getShortValue();
            String colon = ": ";
            int keyWidth = mc.font.width(key);
            int colonWidth = mc.font.width(colon);
            int valueWidth = mc.font.width(value);
            int totalWidth = keyWidth + colonWidth + valueWidth;

            int cellX = cellStartX + i * (cellWidth + gap);
            int textX = cellX + (cellWidth - totalWidth) / 2;

            // Draw key, colon, and value separately using their color and drop shadow.
            guiGraphics.drawString(mc.font, key, textX, y, dobj.getShortKeyColor(), dobj.isShortKeyDropShadow());
            guiGraphics.drawString(mc.font, colon, textX + keyWidth, y, 0xFFFFFF, false);
            guiGraphics.drawString(mc.font, value, textX + keyWidth + colonWidth, y, dobj.getShortValueColor(), dobj.isShortValueDropShadow());
        }
    }

    private static void drawCenteredLines(GuiGraphics guiGraphics, Minecraft mc, DebugSupplier... lines) {
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int halfScreenHeight = screenHeight / 2;
        int startY = 10;
        int lineHeight = 15;

        // How many centered lines fit in the top half.
        int maxCenteredLines = (halfScreenHeight - startY) / lineHeight;
        int centeredCount = Math.min(lines.length, maxCenteredLines);

        // Precompute DebugObjects.
        DebugObject[] centeredObjects = new DebugObject[centeredCount];
        for (int i = 0; i < centeredCount; i++) {
            centeredObjects[i] = lines[i].getSupplier().get();
        }

        int centerX = screenWidth / 2;
        int colonWidth = mc.font.width(":");
        int colonDrawX = centerX - colonWidth / 2;
        int y = startY;
        for (int i = 0; i < centeredCount; i++) {
            DebugObject dobj = centeredObjects[i];
            String leftText = dobj.getLongKey();
            String rightText = "  " + dobj.getLongValue();

            int leftTextWidth = mc.font.width(leftText);
            int rightTextWidth = mc.font.width(rightText);

            int leftDrawX = colonDrawX - leftTextWidth;
            guiGraphics.drawString(mc.font, leftText, leftDrawX, y, dobj.getLongKeyColor(), dobj.isLongKeyDropShadow());
            guiGraphics.drawString(mc.font, " : ", colonDrawX, y, 0xFFFFFF, false);
            int rightDrawX = colonDrawX + colonWidth;
            guiGraphics.drawString(mc.font, rightText, rightDrawX, y, dobj.getLongValueColor(), dobj.isLongValueDropShadow());

            y += lineHeight;
        }

        // If more items remain, draw them right-aligned.
        if (lines.length > centeredCount) {
            DebugSupplier[] remaining = Arrays.copyOfRange(lines, centeredCount, lines.length);
            drawRightAlignedLines(guiGraphics, mc, remaining);
        }
    }

    private static void drawLeftAlignedLines(GuiGraphics guiGraphics, Minecraft mc, DebugSupplier... lines) {
        int leftMargin = 15;
        int colonWidth = mc.font.width(":");
        int startY = 10;
        int lineHeight = 15;
        int halfScreenHeight = mc.getWindow().getGuiScaledHeight() / 2;

        // Determine how many lines fit.
        int maxLines = (halfScreenHeight - startY) / lineHeight;
        int leftCount = Math.min(lines.length, maxLines);

        // Precompute DebugObjects and find max width of left text.
        DebugObject[] debugObjects = new DebugObject[leftCount];
        int maxLeftWidth = 0;
        for (int i = 0; i < leftCount; i++) {
            debugObjects[i] = lines[i].getSupplier().get();
            String leftText = debugObjects[i].getLongKey();
            int width = mc.font.width(leftText);
            if (width > maxLeftWidth) {
                maxLeftWidth = width;
            }
        }

        int colonX = leftMargin + maxLeftWidth;
        int y = startY;
        for (int i = 0; i < leftCount; i++) {
            DebugObject dobj = debugObjects[i];
            String leftText = dobj.getLongKey();
            String rightText = "  " + dobj.getLongValue();

            int leftTextWidth = mc.font.width(leftText);
            int leftDrawX = colonX - leftTextWidth - colonWidth;
            guiGraphics.drawString(mc.font, leftText, leftDrawX, y, dobj.getLongKeyColor(), dobj.isLongKeyDropShadow());
            int colonDrawX = colonX - colonWidth;
            guiGraphics.drawString(mc.font, ":", colonDrawX, y, 0xFFFFFF, false);
            int rightDrawX = colonX + colonWidth * 2;
            guiGraphics.drawString(mc.font, rightText, rightDrawX, y, dobj.getLongValueColor(), dobj.isLongValueDropShadow());
            y += lineHeight;
        }

        if (lines.length > leftCount) {
            DebugSupplier[] remaining = Arrays.copyOfRange(lines, leftCount, lines.length);
            drawRightAlignedLines(guiGraphics, mc, remaining);
        }
    }

    private static void drawRightAlignedLines(GuiGraphics guiGraphics, Minecraft mc, DebugSupplier... lines) {
        if (mcF3Showing) return;
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int halfScreenHeight = screenHeight / 2;
        int startY = 10;
        int lineHeight = 15;

        int maxLines = (halfScreenHeight - startY) / lineHeight;
        int count = Math.min(lines.length, maxLines);

        // Precompute DebugObjects and determine max width for right text.
        DebugObject[] debugObjects = new DebugObject[count];
        int maxRightWidth = 0;
        for (int i = 0; i < count; i++) {
            debugObjects[i] = lines[i].getSupplier().get();
            String rightText = "  " + debugObjects[i].getLongValue();
            int width = mc.font.width(rightText);
            if (width > maxRightWidth) {
                maxRightWidth = width;
            }
        }

        int rightMargin = 15;
        int rightTextX = screenWidth - rightMargin - maxRightWidth;
        int colonWidth = mc.font.width(":");
        int colonX = rightTextX - colonWidth;

        int y = startY;
        for (int i = 0; i < count; i++) {
            DebugObject dobj = debugObjects[i];
            String leftText = dobj.getLongKey();
            String rightText = "  " + dobj.getLongValue();

            int leftTextWidth = mc.font.width(leftText);
            int leftDrawX = colonX - leftTextWidth - colonWidth;
            guiGraphics.drawString(mc.font, leftText, leftDrawX, y, dobj.getLongKeyColor(), dobj.isLongKeyDropShadow());
            guiGraphics.drawString(mc.font, ":", colonX, y, 0xFFFFFF, false);
            guiGraphics.drawString(mc.font, rightText, rightTextX, y, dobj.getLongValueColor(), dobj.isLongValueDropShadow());
            y += lineHeight;
        }
    }


    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Level currentLevel = Minecraft.getInstance().level;

        if (currentLevel != lastLevel) {
            if (currentLevel == null && lastLevel != null) {
                onMainMenu = true;

                showDebug = false;
                f3Enabled = false;
                mcF3Showing = false;
                hudEnabled = false;
                showHud = false;
            } else if (currentLevel != null && onMainMenu) {
                onMainMenu = false;

                showDebug = false;
                f3Enabled = false;
                mcF3Showing = false;
                hudEnabled = false;
                showHud = false;
            }
            lastLevel = currentLevel;
        }
    }

    public static String getShortOutput(String key) {
        if (key == null) { return "Invalid key (null)"; }

        var supplier = registeredLogs.get(key);
        if (supplier == null) { return "Key not registered: " + key; }

        var value = supplier.getSupplier().get();
        if (value == null) { return "No value provided for key: " + key; }

        try { return value.getShortKey() + ": " + value.getShortValue(); }
        catch (Exception e) { return "Error retrieving short output for key: " + key; }
    }

    public static String getLongOutput(String key) {
        if (key == null) { return "Invalid key (null)"; }

        var supplier = registeredLogs.get(key);
        if (supplier == null) { return "Key not registered: " + key; }

        var value = supplier.getSupplier().get();
        if (value == null) { return "No value provided for key: " + key; }

        try { return value.getLongKey() + ": " + value.getLongValue(); }
        catch (Exception e) { return "Error retrieving long output for key: " + key; }
    }
}