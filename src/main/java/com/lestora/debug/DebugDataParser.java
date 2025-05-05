package com.lestora.debug;
import net.minecraft.ChatFormatting;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.*;

public class DebugDataParser {
    @FunctionalInterface
    public interface LineHandler {
        /**
         * @param lineKey       the logical key for this line (e.g. "LocationDetails.Light")
         * @param rawLine       the exact text from F3
         * @param datumEmitter  call datumEmitter.accept(datumKey, datumValue) for each piece you parse
         * @return a Function that, given a map of datumKey→datumValue, reconstructs the line
         */
        Function<Map<String,String>, String> handle(
                String lineKey,
                String rawLine,
                BiConsumer<String,String> datumEmitter
        );
    }
    private static final Map<String, LineHandler> lineHandlers = new LinkedHashMap<>();
    // at top of class
    private static final Map<String, Function<Map<String,String>,String>> rebuilderMap = new HashMap<>();
    // Single flat map for all parsed data.
    public static Map<String, String> data = new LinkedHashMap<>();
    // Static global blocklist for keys to exclude.
    public static Set<String> blocklist = new HashSet<>();

    public static final List<String> leftLines = new ArrayList<>(Arrays.asList(
            // Paragraph 1: game & perf
            "MinecraftData.VersionInfo",
            "MinecraftData.Renderer",
            "MinecraftData.Server",
            "MinecraftData.Chunks",
            "MinecraftData.Entities",
            "MinecraftData.Particles",
            "MinecraftData.ChunksClient",
            "MinecraftData.ChunksServer",
            "MinecraftData.Dimension",

            // blank line between paragraphs
            "<br>",

            // Paragraph 2: location & env
            "LocationDetails.Position",
            "LocationDetails.Block",
            "LocationDetails.Chunk",
            "LocationDetails.Facing",
            "LocationDetails.Light",
            "LocationDetails.LocalDifficulty",
            "LocationDetails.HeightmapClient",
            "LocationDetails.HeightmapServer",
            "LocationDetails.Biome",
            "LocationDetails.NoiseRouter",
            "LocationDetails.BiomeBuilder",
            "LocationDetails.MobCaps",
            "LocationDetails.Sounds"
    ));

    /** Ordered, line-level keys for the right column (with blank-line placeholders). */
    public static final List<String> rightLines = new ArrayList<>(Arrays.asList(
            // Java & Memory section
            "System.Java",                   // Java: 17.0.2 (64bit)
            "System.Memory",                 // Mem: 45% 512/1024MB
            "System.AllocationRate",  // Allocation rate: 5.0MB/s
            "System.Allocated",       // Allocated: 50% 512/1024MB

            "<br>",

            // CPU & Display section
            "System.CPU",                    // CPU: 8 Intel(R)…

            "<br>",

            "System.Display",                // Display: 1920x1080 (Dell Inc.)
            "System.Renderer",       // renderer: GeForce RTX 3080
            "System.OpenGLVersion",  // version: 4.6.0 NVIDIA…

            "<br>",

            // Target sections
            "TargetBlock.Coords",
            "TargetBlock.ResourceLocation",
            "TargetBlock.States",
            "TargetBlock.Tags",

            "<br>",

            "TargetFluid.Coords",
            "TargetFluid.ResourceLocation",
            "TargetFluid.States",
            "TargetFluid.Tags",

            "<br>",

            "TargetEntity.Coords",
            "TargetEntity.ResourceLocation",
            "TargetEntity.States",
            "TargetEntity.Tags"
    ));

    /**
     * Register a handler for a specific lineKey.
     * Any existing handler for that key will be replaced.
     *
     * @param lineKey   the logical key (e.g. "LocationDetails.Light")
     * @param handler   the parser/builder for that line
     */
    public static void registerHandler(String lineKey, LineHandler handler) {
        lineHandlers.put(lineKey, handler);
    }

    /**
     * Retrieve the rebuilder you registered for a given lineKey (or null).
     */
    public static Function<Map<String,String>,String> getRebuilder(String lineKey) {
        return rebuilderMap.get(lineKey);
    }

    public static void parse(List<String> lines) {
        if (lines == null || lines.isEmpty()) return;

        // decide which half we’re parsing by inspecting the very first line
        String first = lines.get(0).trim();
        if (first.startsWith("Minecraft")) {
            parseLeft(lines);
        } else {
            parseRight(lines);
        }
    }

    private static void parseLeft(List<String> lines) {
        Set<String> missing = new HashSet<>();
        for (String lineKey : leftLines) {
            if ("<br>".equals(lineKey)) continue;
            String prefix = lineKey + ".";
            for (String fullKey : data.keySet()) {
                if (fullKey.startsWith(prefix)) {
                    missing.add(fullKey);
                }
            }
        }

        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;

            // ─── first paragraph (MinecraftData) ───
            if (line.startsWith("Minecraft ")) {
                UseHandler("MinecraftData.VersionInfo", line, missing);
                continue;
            }

            if (line.contains(" fps ")) {
                try {
                    String[] tok = line.split("\\s+");
                    Integer.parseInt(tok[0]); // Make sure this is the actual fps line.
                    UseHandler("MinecraftData.Renderer", line, missing);
                    continue;
                } catch (NumberFormatException ignored) { }
            }

            if (line.startsWith("Integrated server @")) {
                UseHandler("MinecraftData.Server", line, missing);
                continue;
            }

            if (line.startsWith("C: ")) {
                UseHandler("MinecraftData.Chunks", line, missing);
                continue;
            }

            if (line.startsWith("E: ")) {
                UseHandler("MinecraftData.Entities", line, missing);
                continue;
            }

            if (line.startsWith("P: ")) {
                UseHandler("MinecraftData.Particles", line, missing);
                continue;
            }

            if (line.startsWith("Chunks[C]")) {
                UseHandler("MinecraftData.ChunksClient", line, missing);
                continue;
            }

            if (line.startsWith("Chunks[S]")) {
                UseHandler("MinecraftData.ChunksServer", line, missing);
                continue;
            }

            if (line.startsWith("minecraft:") && line.contains("FC:")) {
                UseHandler("MinecraftData.Dimension", line, missing);
                continue;
            }

            // ─── second paragraph (LocationDetails) ───
            if (line.startsWith("XYZ:")) {
                UseHandler("LocationDetails.Position", line, missing);
                continue;
            }

            if (line.startsWith("Block:")) {
                UseHandler("LocationDetails.Block", line, missing);
                continue;
            }

            if (line.startsWith("Chunk:")) {
                UseHandler("LocationDetails.Chunk", line, missing);
                continue;
            }

            if (line.startsWith("Facing:")) {
                UseHandler("LocationDetails.Facing", line, missing);
                continue;
            }

            if (line.startsWith("Client Light:")) {
                UseHandler("LocationDetails.Light", line, missing);
                continue;
            }

            if (line.startsWith("Local Difficulty:")) {
                UseHandler("LocationDetails.LocalDifficulty", line, missing);
                continue;
            }

            if (line.startsWith("CH ")) {
                UseHandler("LocationDetails.HeightmapClient", line, missing);
                continue;
            }

            if (line.startsWith("SH ")) {
                UseHandler("LocationDetails.HeightmapServer", line, missing);
                continue;
            }

            if (line.startsWith("Biome:")) {
                UseHandler("LocationDetails.Entities", line, missing);
                continue;
            }

            if (line.startsWith("NoiseRouter")) {
                UseHandler("LocationDetails.NoiseRouter", line, missing);
                continue;
            }

            if (line.startsWith("Biome builder")) {
                UseHandler("LocationDetails.BiomeBuilder", line, missing);
                continue;
            }

            if (line.startsWith("SC:")) {
                UseHandler("LocationDetails.MobCaps", line, missing);
                continue;
            }

            if (line.startsWith("Sounds:")) {
                UseHandler("LocationDetails.Sounds", line, missing);
                continue;
            }

            System.err.println("Lestora Debug. New line found? Couldn't find line parsing logic for: " + line);
        }

        for (String orphan : missing) {
            data.remove(orphan);
        }
    }

    private static void parseRight(List<String> lines) {
        Set<String> missing = new HashSet<>();
        for (String lineKey : rightLines) {
            if ("<br>".equals(lineKey)) continue;
            String prefix = lineKey + ".";
            for (String fullKey : data.keySet()) {
                if (fullKey.startsWith(prefix)) {
                    missing.add(fullKey);
                }
            }
        }

        for (int i = 0; i < lines.size(); i++) {
            String raw = lines.get(i);
            String line = raw.trim();
            if (line.isEmpty()) continue;

            if (line.startsWith("Java:")) {
                UseHandler("System.Java", line, missing);
                continue;
            }

            if (line.startsWith("Mem:")) {
                UseHandler("System.Memory", line, missing);
                continue;
            }

            if (line.startsWith("Allocation rate:")) {
                UseHandler("System.AllocationRate", line, missing);
                continue;
            }

            if (line.startsWith("Allocated:")) {
                UseHandler("System.Allocated", line, missing);
                continue;
            }

            if (line.startsWith("CPU:")) {
                UseHandler("System.CPU", line, missing);
                continue;
            }

            if (line.startsWith("Display:")) {
                UseHandler("System.Display", line, missing);

                // 2) the very next line is the GPU renderer
                if (i+1 < lines.size()) {
                    UseHandler("System.Renderer", lines.get(++i).trim(), missing);
                }

                // 3) and the line after that is the OpenGL version
                if (i+1 < lines.size()) {
                    UseHandler("System.OpenGLVersion", lines.get(++i).trim(), missing);
                }

                continue;
            }

            line = line.replace("\u00A7n", "");
            if (line.startsWith("Targeted Block:")) {
                i = parseTargetSection(lines, i, "TargetBlock", missing);
            }
            else if (line.startsWith("Targeted Fluid:")) {
                i = parseTargetSection(lines, i, "TargetFluid", missing);
            }
            else if (line.startsWith("Targeted Entity")) {
                i = parseTargetSection(lines, i, "TargetEntity", missing);
            }
        }

        for (String orphan : missing) {
            data.remove(orphan);
        }
    }

    private static void UseHandler(String lineKey, String line, Set<String> missing) {
        LineHandler handler = lineHandlers.get(lineKey);
        Function<Map<String,String>,String> handlerResult = x -> line;
        if (handler != null){
            try {
                handlerResult = handler.handle(lineKey, line, (datumKey, datumValue) -> {
                    putIfNotBlocked(lineKey + "." + datumKey, datumValue, missing);
                });
            } catch (Exception e) {
                System.err.println("Error in handler for " + lineKey + ": " + e.getMessage());
            }
        }
        rebuilderMap.put(lineKey, handlerResult);
    }

    private static int parseTargetSection(
            List<String> lines,
            int i,
            String type,
            Set<String> missing
    ) {
        // 1) header “Targeted X: coords…”
        String header = lines.get(i).trim();
        String coords = header.substring(header.indexOf(':') + 1).trim();
        UseHandler(type + ".Coords", coords, missing);

        int idx = i;

        // 2) next line is the resource‐location (if present)
        if (idx + 1 < lines.size()) {
            String resource = lines.get(++idx).trim();
            UseHandler(type + ".ResourceLocation", resource, missing);
        }

        // 3) collect states & tags
        List<String> states = new ArrayList<>();
        List<String> tags   = new ArrayList<>();
        while (idx + 1 < lines.size()) {
            String next = lines.get(idx + 1).trim();
            if (next.isEmpty() || next.startsWith("Targeted")) break;
            idx++;
            if (next.startsWith("#")) {
                tags.add(next);            // “#foo”
            } else if (next.contains(":")) {
                String[] p = next.split(":", 2);
                states.add(p[0] + "=" + p[1].trim());
            }
        }

        // 4) write states/tags if any
        if (!states.isEmpty()) {
            UseHandler(type + ".States", String.join(";", states), missing);
        }
        if (!tags.isEmpty()) {
            UseHandler(type + ".Tags", String.join(";", tags), missing);
        }

        return idx;
    }



    /** Helper to put a key/value if not blocked; else remove it. */
    private static void putIfNotBlocked(String key, String value, Set<String> missing) {
        if (StringUtils.isBlank(key) || StringUtils.isBlank(value)) return;

        if (isBlocked(key)) {
            data.remove(key);
        } else {
            data.put(key, value);
        }

        missing.remove(key);
    }

    private static boolean isBlocked(String key) {
        // direct match
        if (blocklist.contains(key)) return true;

        // walk up the hierarchy
        int idx = key.lastIndexOf('.');
        while (idx != -1) {
            key = key.substring(0, idx);
            if (blocklist.contains(key)) {
                return true;
            }
            idx = key.lastIndexOf('.');
        }
        return false;
    }

    public static List<String> getAllKeys() {
        return new ArrayList<>(data.keySet());
    }

    public static List<String> getLeftValues() {
        List<String> output = new ArrayList<>();

        boolean firstIsBreak = leftLines.get(0).equals("<br>");
        for (String key : leftLines) {
            if (key.equals("<br>")) {
                if (!output.isEmpty() || firstIsBreak) {
                    output.add("§n");
                }
            }
            RebuildLine(key, output);
        }

        return output;
    }

    private static void RebuildLine(String key, List<String> output) {
        var rebuilder = getRebuilder(key);
        if (rebuilder == null) return;
        Map<String,String> valuesMap = new LinkedHashMap<>();
        for (var entry : data.entrySet()) {
            String fullKey = entry.getKey();
            if (fullKey.startsWith(key + ".")) {
                String datumKey = fullKey.substring(key.length() + 1);
                valuesMap.put(datumKey, entry.getValue());
            }
        }
        try {
            String out = rebuilder.apply(valuesMap);
            if (out != null) output.add(out);
        } catch (Exception e) {
            System.err.println("Error rebuilding " + key + ": " + e.getMessage());
        }
    }

    public static List<String> getRightValues() {
        List<String> output = new ArrayList<>();

        boolean firstIsBreak = rightLines.get(0).equals("<br>");
        for (String key : rightLines) {
            if (key.equals("<br>")) {
                if (!output.isEmpty() || firstIsBreak) {
                    output.add("§n");
                }
            }
            RebuildLine(key, output);
        }

        return output;
    }
}