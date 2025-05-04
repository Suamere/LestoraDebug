package com.lestora.debug;

import com.lestora.debug.models.DebugDataParser;
import com.lestora.debug.models.DebugObject;
import com.lestora.debug.models.DebugSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DebugOverlay {
    private static final Map<String, DebugSupplier> registeredLines = new LinkedHashMap<>();
    private static final Map<String, List<Function<Integer, Integer>>> conditions = new LinkedHashMap<>();

    public static void changePriority(String key, Function<Integer, Integer> condition) {
        if (key == null || condition == null) return;
        conditions.computeIfAbsent(key, k -> new ArrayList<>()).add(condition);
    }

//    static {
//        var coordsSupplier = new DebugSupplier("MC_Location", 99, () -> {
//            Player player = Minecraft.getInstance().player;
//            BlockPos playerPos = player.blockPosition();
//            var dobj = new DebugObject("Coordinates", "X: " + playerPos.getX() + ", " + "Y " + playerPos.getY() + ", " + "Z: " + playerPos.getZ());
//            return dobj;
//        });
//        registerDebugLine(coordsSupplier.key(), coordsSupplier);
//
//        var facingSupplier = new DebugSupplier("MC_Facing", 98, () -> {
//            Player player = Minecraft.getInstance().player;
//            float adjustedYaw = (player.getYRot() + 180) % 360;
//            if (adjustedYaw < 0)
//                adjustedYaw += 360;
//            String[] directions = {
//                    "North", "North East", "East", "South East",
//                    "South", "South West", "West", "North West"
//            };
//
//            int index = (int)((adjustedYaw + 22.5) / 45) % 8;
//            var dobj = new DebugObject("Facing Direction", directions[index]);
//            return dobj;
//        });
//        registerDebugLine(facingSupplier.key(), facingSupplier);
//
//        var blockLightSupplier = new DebugSupplier("MC_Block_Light", 97, () -> {
//            Player player = Minecraft.getInstance().player;
//            var playerPos = player.blockPosition();
//            var ll = String.valueOf(player.level().getLightEngine().getLayerListener(LightLayer.BLOCK).getLightValue(playerPos));
//            return new DebugObject("Block Light Level", ll);
//        });
//        registerDebugLine(blockLightSupplier.key(), blockLightSupplier);
//    }

    public static void registerDebugLine(String key, DebugSupplier supplier) {
        registeredLines.put(key, supplier);
    }

    private static int lol = 0;
    public static List<String> getLines(List<String> defaultLines) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return new ArrayList<>();
        List<DebugSupplier> lines = new ArrayList<>();

        lol++;
        if (lol > 100) {
            lol = 0;
            DebugDataParser.parse(defaultLines);
            var lol = DebugDataParser.getAllKeys();
            System.err.println(lol.size());
        }

        registeredLines.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, DebugSupplier> entry) -> entry.getValue().priority()).reversed())
                .forEach((Map.Entry<String, DebugSupplier> entry) -> {
                    int pri = entry.getValue().priority();
                    var conds = conditions.get(entry.getKey());
                    if (conds != null)
                        for (Function<Integer, Integer> c : conds)
                            pri = c.apply(pri);

                    if (pri > 0) lines.add(entry.getValue());
                });

        lines.sort(Comparator.comparingInt(DebugSupplier::priority).reversed());

        // ToDo: Take in the current game-provided lines, give them keys, priorities, and allow consumers to enable/disable/alter them

        return lines.stream()
                .map(x -> {
                    DebugObject dobj = x.supplier().get();
                    String leftText  = dobj.longKey();
                    String rightText = "  " + dobj.longValue();
                    return leftText + ": " + rightText;
                })
                .collect(Collectors.toList());
    }

    public static String getValue(String key) {
        if (key == null) { return "Invalid key (null)"; }

        var supplier = registeredLines.get(key);
        if (supplier == null) { return "Key not registered: " + key; }

        var value = supplier.supplier().get();
        if (value == null) { return "No value provided for key: " + key; }

        try { return value.longKey() + ": " + value.longValue(); }
        catch (Exception e) { return "Error retrieving long output for key: " + key; }
    }
}