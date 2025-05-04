package com.lestora.debug.models;
import net.minecraft.ChatFormatting;

import java.util.*;
import java.util.regex.*;
import java.util.stream.Stream;

public class DebugDataParser {
    // Single flat map for all parsed data.
    private static Map<String, String> data = new LinkedHashMap<>();
    // Static global blocklist for keys to exclude.
    private static Set<String> blocklist = new HashSet<>();

    public static final List<String> leftLines = Collections.unmodifiableList(List.of(
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
            "LocationDetails.Difficulty",
            "LocationDetails.NoiseRouter",
            "LocationDetails.BiomeBuilder",
            "LocationDetails.MobCaps",
            "LocationDetails.Sounds"
    ));

    /** Ordered, line-level keys for the right column (with blank-line placeholders). */
    public static final List<String> rightLines = Collections.unmodifiableList(List.of(
            // Java & Memory section
            "System.Java",                   // Java: 17.0.2 (64bit)
            "System.Memory",                 // Mem: 45% 512/1024MB
            "System.Memory.AllocationRate",  // Allocation rate: 5.0MB/s
            "System.Memory.Allocated",       // Allocated: 50% 512/1024MB

            "<br>",

            // CPU & Display section
            "System.CPU",                    // CPU: 8 Intel(R)…

            "<br>",

            "System.Display",                // Display: 1920x1080 (Dell Inc.)
            "System.Display.Renderer",       // renderer: GeForce RTX 3080
            "System.Display.OpenGLVersion",  // version: 4.6.0 NVIDIA…

            "<br>",

            // Target sections
            "Targets.TargetBlock",

            "<br>",

            "Targets.TargetFluid",

            "<br>",

            "Targets.TargetEntity"
    ));

    /** Add a key to the blocklist (it will be omitted or removed). */
    public static void addToBlocklist(String key) {
        blocklist.add(key);
        data.remove(key);
    }
    /** Remove a key from the blocklist (allow it to be parsed). */
    public static void removeFromBlocklist(String key) {
        blocklist.remove(key);
    }

    public static List<String> getBlockedKeys() {
        return Collections.unmodifiableList(new ArrayList<>(blocklist));
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
                // "Minecraft 1.21.4 (MOD_DEV/forge)"
                // Use a regex to pull out version and optional mod name
                Matcher m = Pattern.compile("^Minecraft\\s+(\\S+)(?:\\s+\\(([^)]+)\\))?").matcher(line);
                if (m.find()) {
                    String version = m.group(1);
                    putIfNotBlocked("MinecraftData.VersionInfo.VersionNumber", version, missing);

                    String modName = m.group(2);
                    if (modName != null && !modName.isEmpty()) {
                        putIfNotBlocked("MinecraftData.VersionInfo.ModName", modName, missing);
                    }
                }
                continue;
            }

            if (line.contains(" fps ") && line.contains("GPU:")) {
                // "60 fps T: 120 vsync fancy clouds B: 2 GPU: 20%"
                String[] tok = line.split("\\s+");
                putIfNotBlocked("MinecraftData.Renderer.FPS", tok[0], missing);
                for (int j = 1; j < tok.length; j++) {
                    switch (tok[j]) {
                        case "T:" -> putIfNotBlocked("MinecraftData.Renderer.TickTime", tok[j+1], missing);
                        case "vsync" -> putIfNotBlocked("MinecraftData.Renderer.VSync", "on", missing);
                        case "fast","fancy","fabulous" -> putIfNotBlocked("MinecraftData.Renderer.Graphics", tok[j], missing);
                        case "fancy-clouds","fast-clouds" -> putIfNotBlocked("MinecraftData.Renderer.Clouds", tok[j], missing);
                        case "B:" -> putIfNotBlocked("MinecraftData.Renderer.BiomeBlend", tok[j+1], missing);
                        case "GPU:" -> putIfNotBlocked("MinecraftData.Renderer.GPU", tok[j+1].replace("%",""), missing);
                    }
                }
                continue;
            }

            if (line.startsWith("Integrated server @")) {
                // "Integrated server @ 3.1/50.0 ms, 22 tx, 1053 rx"
                String[] at = line.split("@");
                putIfNotBlocked("MinecraftData.Server.Brand", at[0].replace("Integrated server","").trim(), missing);
                String[] parts = at[1].split(",");
                String timing = parts[0].replace("ms","").trim();
                String[] times = timing.split("/");
                putIfNotBlocked("MinecraftData.Server.TickTimeMs", times[0], missing);
                putIfNotBlocked("MinecraftData.Server.TicksPerSecond", times[1], missing);
                putIfNotBlocked("MinecraftData.Server.PacketsSent", parts[1].trim().split(" ")[0], missing);
                putIfNotBlocked("MinecraftData.Server.PacketsReceived", parts[2].trim().split(" ")[0], missing);
                continue;
            }

            if (line.startsWith("C: ")) {
                // "C: 305/15000 (s) D: 12, pC: 000, pU: 00, aB: 16"
                String[] p = line.split("\\s+");
                String[] ct = p[1].split("/");
                putIfNotBlocked("MinecraftData.Chunks.SectionsRendered", ct[0], missing);
                putIfNotBlocked("MinecraftData.Chunks.SectionsTotal",    ct[1], missing);
                for (int j = 2; j < p.length; j++) {
                    switch(p[j]) {
                        case "D:"  -> putIfNotBlocked("MinecraftData.Chunks.RenderDistance", p[j+1].replace(",",""), missing);
                        case "pC:" -> putIfNotBlocked("MinecraftData.Chunks.PendingBatch",    p[j+1].replace(",",""), missing);
                        case "pU:" -> putIfNotBlocked("MinecraftData.Chunks.PendingUploads",  p[j+1].replace(",",""), missing);
                        case "aB:" -> putIfNotBlocked("MinecraftData.Chunks.AvailableBuffers", p[j+1], missing);
                    }
                }
                continue;
            }

            if (line.startsWith("E: ")) {
                // "E: 3/127, SD: 12"
                String entityPart = line.substring(0, line.indexOf(',')).trim();       // "E: 3/127"
                String[] counts     = entityPart.substring(entityPart.indexOf(' ')+1).split("/");
                putIfNotBlocked("MinecraftData.Entities.Rendered", counts[0], missing);
                putIfNotBlocked("MinecraftData.Entities.Total",    counts[1], missing);
                int sdIdx = line.indexOf("SD:");
                putIfNotBlocked("MinecraftData.Entities.SimulationDistance", line.substring(sdIdx+3).trim(), missing);
                continue;
            }

            if (line.startsWith("P: ")) {
                // "P: 1270. T: 127"
                String[] parts = line.split("\\s+");
                putIfNotBlocked("MinecraftData.Particles.Count",     parts[1].replace(".",""), missing);
                putIfNotBlocked("MinecraftData.Particles.TickValue", parts[3], missing);
                continue;
            }

            if (line.startsWith("Chunks[C]")) {
                // "Chunks[C] W: 961, 637 E: 127,76,637"
                String wPart = line.substring(line.indexOf("W:")+2, line.indexOf("E:")).trim();
                String ePart = line.substring(line.indexOf("E:")+2).trim();
                String[] wc = wPart.split(",");
                String[] ec = ePart.split(",");
                putIfNotBlocked("MinecraftData.ChunksClient.Cached",        wc[0].trim(), missing);
                putIfNotBlocked("MinecraftData.ChunksClient.Loaded",        wc[1].trim(), missing);
                if (ec.length >= 3) {
                    putIfNotBlocked("MinecraftData.ChunksClient.Entities",       ec[0].trim(), missing);
                    putIfNotBlocked("MinecraftData.ChunksClient.EntitySections", ec[1].trim(), missing);
                    putIfNotBlocked("MinecraftData.ChunksClient.Ticking",        ec[2].trim(), missing);
                }
                continue;
            }

            if (line.startsWith("Chunks[S]")) {
                // "Chunks[S] W: 3338 E: 173,103,890,890,0,0"
                String wVal = line.substring(line.indexOf("W:")+2, line.indexOf("E:")).trim();
                putIfNotBlocked("MinecraftData.ChunksServer.World", wVal, missing);
                String[] es = line.substring(line.indexOf("E:")+2).split(",");
                if (es.length >= 6) {
                    putIfNotBlocked("MinecraftData.ChunksServer.Entities", es[0].trim(), missing);
                    putIfNotBlocked("MinecraftData.ChunksServer.Visible",  es[1].trim(), missing);
                    putIfNotBlocked("MinecraftData.ChunksServer.Sections", es[2].trim(), missing);
                    putIfNotBlocked("MinecraftData.ChunksServer.Loaded",   es[3].trim(), missing);
                    putIfNotBlocked("MinecraftData.ChunksServer.Ticking",  es[4].trim(), missing);
                    putIfNotBlocked("MinecraftData.ChunksServer.ToLoad",   es[5].trim(), missing);
                    if (es.length > 6) putIfNotBlocked("MinecraftData.ChunksServer.ToUnload", es[6].trim(), missing);
                }
                continue;
            }

            if (line.startsWith("minecraft:") && line.contains("FC:")) {
                // "minecraft:overworld FC: 0"
                String[] parts = line.split("\\s+");
                putIfNotBlocked("MinecraftData.Dimension.ID", parts[0], missing);
                putIfNotBlocked("MinecraftData.Dimension.ForceLoadedChunks", parts[2], missing);
                continue;
            }

            // ─── second paragraph (LocationDetails) ───
            if (line.startsWith("XYZ:")) {
                // "XYZ: -123.000 / 64.000 / -123.000"
                String[] xyz = line.substring(5).split("/");
                putIfNotBlocked("LocationDetails.Position.X", xyz[0].trim(), missing);
                putIfNotBlocked("LocationDetails.Position.Y", xyz[1].trim(), missing);
                putIfNotBlocked("LocationDetails.Position.Z", xyz[2].trim(), missing);
                continue;
            }

            if (line.startsWith("Block:")) {
                // "Block: -124 64 -124 [1 2 3]"
                String world = line.substring(7, line.indexOf("[")).trim();
                String rel   = line.substring(line.indexOf("[")+1, line.indexOf("]")).trim();
                String[] w   = world.split(" ");
                String[] r   = rel.split(" ");
                putIfNotBlocked("LocationDetails.Block.WorldX", w[0], missing);
                putIfNotBlocked("LocationDetails.Block.WorldY", w[1], missing);
                putIfNotBlocked("LocationDetails.Block.WorldZ", w[2], missing);
                putIfNotBlocked("LocationDetails.Block.RelativeX", r[0], missing);
                putIfNotBlocked("LocationDetails.Block.RelativeY", r[1], missing);
                putIfNotBlocked("LocationDetails.Block.RelativeZ", r[2], missing);
                continue;
            }

            if (line.startsWith("Chunk:")) {
                // "Chunk: -9 4 -9 [14 20 in r.-1.-1.mca]"
                String coord = line.substring(7, line.indexOf("[")).trim();
                String detail= line.substring(line.indexOf("[")+1, line.indexOf("]")).trim();
                String[] c    = coord.split(" ");
                putIfNotBlocked("LocationDetails.Chunk.WorldX", c[0], missing);
                putIfNotBlocked("LocationDetails.Chunk.WorldY", c[1], missing);
                putIfNotBlocked("LocationDetails.Chunk.WorldZ", c[2], missing);
                String[] dt   = detail.split(" in ");
                String[] rc   = dt[0].split(" ");
                putIfNotBlocked("LocationDetails.Chunk.RelativeX", rc[0], missing);
                putIfNotBlocked("LocationDetails.Chunk.RelativeZ", rc[1], missing);
                if (dt.length>1) putIfNotBlocked("LocationDetails.Chunk.RegionFile", dt[1], missing);
                continue;
            }

            if (line.startsWith("Facing:")) {
                // "Facing: south (Towards positive Z) (1.5 / 66.8)"
                String[] seg = line.split("\\(");
                putIfNotBlocked("LocationDetails.Facing.Compass", seg[0].split(":")[1].trim(), missing);
                putIfNotBlocked("LocationDetails.Facing.Toward", seg[1].replace(")","").replace("Towards","").trim(), missing);
                putIfNotBlocked("LocationDetails.Facing.HeadYaw", seg[2].replace(")","").trim(), missing);
                continue;
            }

            if (line.startsWith("Client Light:")) {
                // "Client Light: 15 (15 sky, 9 block)"
                String tot = line.substring(13,line.indexOf("(")).trim();
                putIfNotBlocked("LocationDetails.Light.Total", tot, missing);
                String sub = line.substring(line.indexOf("(")+1, line.indexOf(")"));
                String[] sp = sub.split(",");
                putIfNotBlocked("LocationDetails.Light.Sky",   sp[0].replace("sky","").trim(), missing);
                putIfNotBlocked("LocationDetails.Light.Block", sp[1].replace("block","").trim(), missing);
                continue;
            }

            if (line.startsWith("CH ")) {
                // "CH S: 63 M: 63"
                String[] p = line.split("\\s+");
                putIfNotBlocked("LocationDetails.HeightmapClient.WorldSurface",    p[2], missing);
                putIfNotBlocked("LocationDetails.HeightmapClient.MotionBlocking", p[4], missing);
                continue;
            }

            if (line.startsWith("SH ")) {
                // "SH S: 63 O: 63 M: 63 ML: 63"
                String[] p = line.split("\\s+");
                putIfNotBlocked("LocationDetails.HeightmapServer.WorldSurface",    p[2], missing);
                putIfNotBlocked("LocationDetails.HeightmapServer.OceanFloor",      p[4], missing);
                putIfNotBlocked("LocationDetails.HeightmapServer.MotionBlocking",  p[6], missing);
                putIfNotBlocked("LocationDetails.HeightmapServer.MotionBlockingNoLeaves", p[8], missing);
                continue;
            }

            if (line.startsWith("Biome:")) {
                putIfNotBlocked("LocationDetails.Biome", line.substring(7).trim(), missing);
                continue;
            }

            if (line.startsWith("Local Difficulty:")) {
                // drop the prefix
                String rest = line.substring("Local Difficulty:".length()).trim();
                // handle the "??" case early
                if (rest.equals("??")) {
                    putIfNotBlocked("LocationDetails.LocalDifficulty.Numerator", "??", missing);
                } else {
                    // rest should look like "0.75 // 0.00 (Day 0)"
                    String[] halves = rest.split("//", 2);
                    // left of "//" = raw local diff
                    String local = halves[0].trim();
                    putIfNotBlocked("LocationDetails.LocalDifficulty.Numerator", local, missing);

                    if (halves.length > 1) {
                        // right of "//" = clamped + day
                        String right = halves[1].trim();            // e.g. "0.00 (Day 0)"
                        // clamped is the first token
                        String[] tokens = right.split("\\s+", 2);
                        putIfNotBlocked("LocationDetails.LocalDifficulty.Denominator", tokens[0], missing);

                        if (tokens.length > 1) {
                            // tokens[1] should be "(Day 0)" or similar
                            String dayPart = tokens[1].trim();
                            if (dayPart.startsWith("(Day") && dayPart.endsWith(")")) {
                                // extract the number between "Day" and ")"
                                String dayNum = dayPart.substring(4, dayPart.length() - 1).trim();
                                putIfNotBlocked("LocationDetails.LocalDifficulty.Day", dayNum, missing);
                            }
                        }
                    }
                }
                continue;
            }

            if (line.startsWith("NoiseRouter")) {
                String[] tok = line.split("\\s+");
                for (int k = 1; k < tok.length; k+=2) {
                    String key = tok[k].replace(":", "");
                    String val = tok[k+1];
                    switch (key) {
                        case "T"  -> putIfNotBlocked("LocationDetails.NoiseRouter.Temperature", val, missing);
                        case "V"  -> putIfNotBlocked("LocationDetails.NoiseRouter.Vegetation", val, missing);
                        case "C"  -> putIfNotBlocked("LocationDetails.NoiseRouter.Continents", val, missing);
                        case "E"  -> putIfNotBlocked("LocationDetails.NoiseRouter.Erosion", val, missing);
                        case "D"  -> putIfNotBlocked("LocationDetails.NoiseRouter.Depth", val, missing);
                        case "W"  -> putIfNotBlocked("LocationDetails.NoiseRouter.Ridges", val, missing);
                        case "PV" -> putIfNotBlocked("LocationDetails.NoiseRouter.PeaksValleys", val, missing);
                        case "AS" -> putIfNotBlocked("LocationDetails.NoiseRouter.InitialDensity", val, missing);
                        case "N"  -> putIfNotBlocked("LocationDetails.NoiseRouter.FinalDensity", val, missing);
                    }
                }
                continue;
            }

            if (line.startsWith("Biome builder")) {
                // strip off the leading text
                String rest = line.substring("Biome builder".length()).trim();
                // the labels, in order:
                String[] labels = {"PV:", "C:", "E:", "T:", "H:"};

                for (int idx = 0; idx < labels.length; idx++) {
                    String label = labels[idx];
                    int start = rest.indexOf(label);
                    if (start < 0) continue;

                    // compute end as the next label’s index, or end of string
                    int valueStart = start + label.length();
                    int end = rest.length();
                    for (int j = idx + 1; j < labels.length; j++) {
                        int next = rest.indexOf(labels[j], valueStart);
                        if (next >= 0) {
                            end = next;
                            break;
                        }
                    }

                    // extract & trim the multi-word value
                    String val = rest.substring(valueStart, end).trim();
                    // map label → PascalCase key
                    switch (label) {
                        case "PV:" -> putIfNotBlocked("LocationDetails.BiomeBuilder.PeaksValleys", val, missing);
                        case "C:"  -> putIfNotBlocked("LocationDetails.BiomeBuilder.Continentalness", val, missing);
                        case "E:"  -> putIfNotBlocked("LocationDetails.BiomeBuilder.Erosion", val, missing);
                        case "T:"  -> putIfNotBlocked("LocationDetails.BiomeBuilder.Temperature", val, missing);
                        case "H:"  -> putIfNotBlocked("LocationDetails.BiomeBuilder.Humidity", val, missing);
                    }
                }
                continue;
            }

            if (line.startsWith("SC:")) {
                String[] tok = line.replace(",", "").split("\\s+");
                int aCount = 0, wCount = 0, mCount = 0;
                for (int k = 0; k < tok.length; k+=2) {
                    String key = tok[k].replace(":", "");
                    String val = tok[k+1];
                    switch (key) {
                        case "SC" -> putIfNotBlocked("LocationDetails.MobCaps.Chunks", val, missing);
                        case "M"  -> {
                            if (mCount==0) putIfNotBlocked("LocationDetails.MobCaps.Monsters", val, missing);
                            else           putIfNotBlocked("LocationDetails.MobCaps.Misc", val, missing);
                            mCount++;
                        }
                        case "C"  -> putIfNotBlocked("LocationDetails.MobCaps.Creatures", val, missing);
                        case "A"  -> {
                            if (aCount==0) putIfNotBlocked("LocationDetails.MobCaps.Ambient", val, missing);
                            else           putIfNotBlocked("LocationDetails.MobCaps.Axolotls", val, missing);
                            aCount++;
                        }
                        case "U"  -> putIfNotBlocked("LocationDetails.MobCaps.Underground", val, missing);
                        case "W"  -> {
                            if (wCount==0) putIfNotBlocked("LocationDetails.MobCaps.Water", val, missing);
                            else           putIfNotBlocked("LocationDetails.MobCaps.Fish", val, missing);
                            wCount++;
                        }
                    }
                }
                continue;
            }

            if (line.startsWith("Sounds:")) {
                String[] parts = line.split("\\s+");
                String[] st = parts[1].split("/");
                putIfNotBlocked("LocationDetails.Sounds.Static", st[0], missing);
                putIfNotBlocked("LocationDetails.Sounds.StaticMax", st[1], missing);
                String[] sr = parts[3].split("/");
                putIfNotBlocked("LocationDetails.Sounds.Stream", sr[0], missing);
                putIfNotBlocked("LocationDetails.Sounds.StreamMax", sr[1], missing);
                String mood = parts[5].replace("(", "").replace(")", "").replace("%", "");
                putIfNotBlocked("LocationDetails.Sounds.Mood", mood, missing);
                continue;
            }
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
                // e.g. "Java: 21.0.6" or "Java: 17.0.2 (64bit)"
                String rest = line.substring("Java:".length()).trim();
                // If it contains "(XXbit)", split that out:
                if (rest.contains("(") && rest.endsWith("bit)")) {
                    int idx = rest.indexOf('(');
                    String version = rest.substring(0, idx).trim();              // "17.0.2"
                    String bits    = rest.substring(idx+1, rest.length()-1);     // "64bit"
                    putIfNotBlocked("System.Java.Version", version, missing);
                    putIfNotBlocked("System.Java.Bits",    bits, missing);
                } else {
                    // no bits-info, just version
                    putIfNotBlocked("System.Java.Version", rest, missing);
                }
                continue;
            }

            if (line.startsWith("Mem:")) {
                // Example: "Mem: 45% 512/1024"
                String[] parts = line.split("[ %/]+");
                // parts = ["Mem:", "45", "512", "1024"]
                if (parts.length >= 4) {
                    putIfNotBlocked("System.Memory.UsedPercent", parts[1], missing);
                    putIfNotBlocked("System.Memory.Used", parts[2], missing);
                    putIfNotBlocked("System.Memory.Total", parts[3], missing);
                }
            }

            if (line.startsWith("Allocation rate:")) {
                // Example: "Allocation rate: 5.0 MiB/s"
                String[] parts = line.split("\\s+");
                if (parts.length >= 3) {
                    putIfNotBlocked("System.Memory.AllocationRate", parts[2], missing);
                }
            }

            if (line.startsWith("Allocated:")) {
                // drop the prefix and split on whitespace
                String rest = line.substring("Allocated:".length()).trim();
                String[] tok = rest.split("\\s+");
                // 1) percent (always there)
                if (tok.length >= 1) {
                    String pct = tok[0].endsWith("%")
                            ? tok[0].substring(0, tok[0].length()-1)
                            : tok[0];
                    putIfNotBlocked("System.Memory.AllocatedPercent", pct, missing);
                }
                // 2) memory token (either “used/total” or “usedMB”)
                if (tok.length >= 2) {
                    String mem = tok[1];
                    if (mem.contains("/")) {
                        String[] uv = mem.split("/", 2);
                        putIfNotBlocked("System.Memory.Allocated",      uv[0], missing);
                        putIfNotBlocked("System.Memory.AllocatedTotal", uv[1], missing);
                    } else {
                        // e.g. "1072MB" → treat whole string as "Allocated"
                        putIfNotBlocked("System.Memory.Allocated", mem, missing);
                        // no total in this format, so we skip AllocatedTotal
                    }
                }
                continue;
            }

            if (line.startsWith("CPU:")) {
                // Example: "CPU: 8 Intel(R) Core(TM)..."
                String[] parts = line.split("\\s+", 3);
                if (parts.length >= 2) {
                    putIfNotBlocked("System.CPU.Cores", parts[1], missing);
                    if (parts.length >= 3) {
                        putIfNotBlocked("System.CPU.Name", parts[2], missing);
                    }
                }
            }

            if (line.startsWith("Display:")) {
                // 1) parse the Display: resolution/vendor as before
                Matcher dispMatch = Pattern.compile("Display:\\s*(\\S+)\\s*\\(([^)]+)\\)")
                        .matcher(line);
                if (dispMatch.find()) {
                    putIfNotBlocked("System.Display.Resolution", dispMatch.group(1), missing);
                    putIfNotBlocked("System.Display.Vendor",     dispMatch.group(2), missing);
                }

                // 2) the very next line is the GPU renderer
                if (i+1 < lines.size()) {
                    String rendererLine = lines.get(++i).trim();
                    putIfNotBlocked("System.Display.Renderer", rendererLine, missing);
                }

                // 3) and the line after that is the OpenGL version
                if (i+1 < lines.size()) {
                    String versionLine = lines.get(++i).trim();
                    putIfNotBlocked("System.Display.OpenGLVersion", versionLine, missing);
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

    private static int parseTargetSection(
            List<String> lines,
            int i,
            String type,
            Set<String> missing
    ) {
        // 1) header line “Targeted X: coords…”
        String header = lines.get(i).trim();
        String coords = header.substring(header.indexOf(':') + 1).trim();
        putIfNotBlocked("Targets." + type + ".Coordinates", coords, missing);

        int idx = i;

        // 2) next line is always the resource‐location
        if (idx + 1 < lines.size()) {
            String idLine = lines.get(++idx).trim();
            putIfNotBlocked("Targets." + type + ".ResourceLocation", idLine, missing);
        }

        // 3) consume 0+ state/tag lines
        List<String> states = new ArrayList<>();
        List<String> tags   = new ArrayList<>();
        while (idx + 1 < lines.size()) {
            String next = lines.get(idx + 1).trim();
            if (next.isEmpty() || next.startsWith("Targeted")) break;
            idx++;
            if (next.startsWith("#")) {
                tags.add(next.substring(1));      // “#foo” → “foo”
            } else if (next.contains(":")) {
                String[] p = next.split(":", 2);
                states.add(p[0] + "=" + p[1].trim());
            }
        }

        // 4) write states/tags if any
        if (!states.isEmpty()) {
            putIfNotBlocked("Targets." + type + ".States", String.join(";", states), missing);
        }
        if (!tags.isEmpty()) {
            putIfNotBlocked("Targets." + type + ".Tags", String.join(";", tags), missing);
        }

        return idx;
    }

    private static int lol = 0;
    private static void writeEverySecond(String msg) {
        lol++;
        if (lol == 100) {
            lol = 0;
            System.err.println("LOL: " + msg);
        }
    }

    /** Helper to put a key/value if not blocked; else remove it. */
    private static void putIfNotBlocked(String key, String value, Set<String> missing) {
        if (key == null || value == null) return;

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

    /** Returns all stored keys. */
    public static List<String> getAllKeys() {
        return new ArrayList<>(data.keySet());
    }

    /**
     * Returns immediate sub-keys under the given prefix.
     * E.g. if data has "A.X", "A.Y.Z", "A.Y.W", getSubKeys("A")
     * returns ["A.X","A.Y"].
     */
    public static List<String> getSubKeys(String prefix) {
        Set<String> subs = new LinkedHashSet<>();
        String prefixDot = prefix + ".";
        for (String key : data.keySet()) {
            if (key.startsWith(prefixDot)) {
                String remainder = key.substring(prefixDot.length());
                int dotIdx = remainder.indexOf('.');
                if (dotIdx >= 0) {
                    subs.add(prefix + "." + remainder.substring(0, dotIdx));
                } else {
                    subs.add(key);
                }
            }
        }
        return new ArrayList<>(subs);
    }

    /**
     * Returns values under the given key.  If key is a leaf, returns its value.
     * If key is a prefix, returns a list of values for each sub-key group, concatenated.
     */
    public static List<String> getValues(String key) {
        List<String> result = new ArrayList<>();
        if (data.containsKey(key) && !key.contains(".")) {
            // top-level key with value
            result.add(data.get(key));
            return result;
        }
        if (data.containsKey(key)) {
            // exact match leaf
            result.add(data.get(key));
            return result;
        }
        // prefix case: gather subkeys
        List<String> subKeys = getSubKeys(key);
        for (String subKey : subKeys) {
            // collect children of subKey
            List<String> childValues = new ArrayList<>();
            String subPrefix = subKey + ".";
            for (String k : data.keySet()) {
                if (k.equals(subKey)) {
                    childValues.add(data.get(k));
                } else if (k.startsWith(subPrefix)) {
                    childValues.add(data.get(k));
                }
            }
            // join them (space-separated) or take single
            if (!childValues.isEmpty()) {
                result.add(String.join(" ", childValues));
            }
        }
        return result;
    }


    /** Reconstructs the left‐column debug lines in order. */
    public static List<String> getLeftValues() {
        List<String> output = new ArrayList<>();

        for (String key : leftLines) {
            switch (key) {

                case "<br>" -> {
                    boolean firstIsBreak = leftLines.get(0).equals("<br>");
                    if (!output.isEmpty() || firstIsBreak) {
                        output.add("");
                    }
                }

                case "MinecraftData.VersionInfo" -> {
                    String ver    = data.get("MinecraftData.VersionInfo.VersionNumber");
                    String mod    = data.get("MinecraftData.VersionInfo.ModName");

                    // skip if absolutely nothing to show
                    if (ver == null && mod == null) {
                        break;
                    }

                    StringBuilder sb = new StringBuilder("Minecraft");
                    if (ver != null) {
                        sb.append(" ").append(ver);
                    }
                    if (mod != null) {
                        sb.append(" (").append(mod).append(")");
                    }
                    output.add(sb.toString());
                }


                case "MinecraftData.Renderer" -> {
                    String fps    = data.get("MinecraftData.Renderer.FPS");
                    String tick   = data.get("MinecraftData.Renderer.TickTime");
                    String vsync  = data.get("MinecraftData.Renderer.VSync");
                    String gfx    = data.get("MinecraftData.Renderer.Graphics");
                    String clouds = data.get("MinecraftData.Renderer.Clouds");
                    String blend  = data.get("MinecraftData.Renderer.BiomeBlend");
                    String gpu    = data.get("MinecraftData.Renderer.GPU");

                    // skip if nothing to show
                    if (fps == null && tick == null && !"on".equals(vsync)
                            && (gfx == null || gfx.isEmpty())
                            && (clouds == null || clouds.isEmpty())
                            && blend == null && gpu == null) {
                        break;
                    }

                    List<String> parts = new ArrayList<>();
                    if (fps != null)    parts.add(fps + " fps");
                    if (tick != null)   parts.add("T: " + tick);
                    if ("on".equals(vsync)) parts.add("vsync");
                    if (gfx != null && !gfx.isEmpty())    parts.add(gfx);
                    if (clouds != null && !clouds.isEmpty()) parts.add(clouds);
                    if (blend != null) parts.add("B: " + blend);
                    if (gpu != null)   parts.add("GPU: " + gpu + "%");

                    output.add(String.join(" ", parts));
                }

                case "MinecraftData.Server" -> {
                    String brandKey = data.get("MinecraftData.Server.Brand");
                    String defaultLabel = "Integrated server";
                    String label = (brandKey != null && !brandKey.isBlank()) ? brandKey : defaultLabel;

                    String tms  = data.get("MinecraftData.Server.TickTimeMs");
                    String tps  = data.get("MinecraftData.Server.TicksPerSecond");
                    String sent = data.get("MinecraftData.Server.PacketsSent");
                    String recv = data.get("MinecraftData.Server.PacketsReceived");

                    List<String> parts = new ArrayList<>();
                    if (tms != null && tps != null) parts.add(tms + "/" + tps + " ms");
                    if (sent != null)               parts.add(sent + " tx");
                    if (recv != null)               parts.add(recv + " rx");

                    // skip entire line if neither a custom brand nor any parts exist
                    if ((brandKey == null || brandKey.isBlank()) && parts.isEmpty()) {
                        break;
                    }

                    // build the output
                    StringBuilder sb = new StringBuilder(label);
                    if (!parts.isEmpty()) {
                        sb.append(" @ ").append(String.join(", ", parts));
                    }
                    output.add(sb.toString());
                }


                case "MinecraftData.Chunks" -> {
                    // gather each sub‐value only if present
                    String rs = data.get("MinecraftData.Chunks.SectionsRendered");
                    String ts = data.get("MinecraftData.Chunks.SectionsTotal");
                    String rd = data.get("MinecraftData.Chunks.RenderDistance");
                    String pc = data.get("MinecraftData.Chunks.PendingBatch");
                    String pu = data.get("MinecraftData.Chunks.PendingUploads");
                    String ab = data.get("MinecraftData.Chunks.AvailableBuffers");

                    List<String> parts = new ArrayList<>();
                    // render count pair
                    if (rs != null && ts != null) {
                        parts.add(rs + "/" + ts);
                    }
                    // optional details
                    if (rd != null) parts.add("D: " + rd);
                    if (pc != null) parts.add("pC: " + pc);
                    if (pu != null) parts.add("pU: " + pu);
                    if (ab != null) parts.add("aB: " + ab);

                    // only output if there's something to show
                    if (!parts.isEmpty()) {
                        output.add("C: " + String.join(", ", parts));
                    }
                }

                case "MinecraftData.Entities" -> {
                    String rend = data.get("MinecraftData.Entities.Rendered");
                    String tot  = data.get("MinecraftData.Entities.Total");
                    String sd   = data.get("MinecraftData.Entities.SimulationDistance");

                    // skip only if nothing is present
                    if (rend == null && tot == null && sd == null) {
                        break;
                    }

                    StringBuilder sb = new StringBuilder("E:");
                    // render/total
                    if (rend != null || tot != null) {
                        String r = rend != null ? rend : "?";
                        String t = tot  != null ? tot  : "?";
                        sb.append(" ").append(r).append("/").append(t);
                    }
                    // simulation distance
                    if (sd != null) {
                        sb.append(", SD: ").append(sd);
                    }

                    output.add(sb.toString());
                }


                case "MinecraftData.Particles" -> {
                    String cnt = data.get("MinecraftData.Particles.Count");
                    String tv  = data.get("MinecraftData.Particles.TickValue");

                    // skip if neither count nor tick-value is present
                    if (cnt == null && tv == null) {
                        break;
                    }

                    StringBuilder sb = new StringBuilder("P:");
                    if (cnt != null) {
                        sb.append(" ").append(cnt);
                    }
                    if (tv != null) {
                        // if we already added count, prefix with “. ”; otherwise just a space
                        if (cnt != null) {
                            sb.append(". T: ").append(tv);
                        } else {
                            sb.append(" T: ").append(tv);
                        }
                    }
                    output.add(sb.toString());
                }


                case "MinecraftData.ChunksClient" -> {
                    String c  = data.get("MinecraftData.ChunksClient.Cached");
                    String l  = data.get("MinecraftData.ChunksClient.Loaded");
                    String e1 = data.get("MinecraftData.ChunksClient.Entities");
                    String e2 = data.get("MinecraftData.ChunksClient.EntitySections");
                    String t  = data.get("MinecraftData.ChunksClient.Ticking");

                    // skip if absolutely nothing is present
                    if (c == null && l == null && e1 == null && e2 == null && t == null) {
                        break;
                    }

                    List<String> parts = new ArrayList<>();
                    if (c != null) parts.add("W: " + c);
                    if (l != null) parts.add("L: " + l);

                    // combine entities/sections/ticking into one E: token if any present
                    List<String> entParts = new ArrayList<>();
                    if (e1 != null) entParts.add(e1);
                    if (e2 != null) entParts.add(e2);
                    if (t  != null) entParts.add(t);
                    if (!entParts.isEmpty()) {
                        parts.add("E: " + String.join(",", entParts));
                    }

                    output.add("Chunks[C] " + String.join(", ", parts));
                }


                case "MinecraftData.ChunksServer" -> {
                    String w  = data.get("MinecraftData.ChunksServer.World");
                    String e  = data.get("MinecraftData.ChunksServer.Entities");
                    String vis= data.get("MinecraftData.ChunksServer.Visible");
                    String sec= data.get("MinecraftData.ChunksServer.Sections");
                    String ld = data.get("MinecraftData.ChunksServer.Loaded");
                    String tk = data.get("MinecraftData.ChunksServer.Ticking");
                    String tl = data.get("MinecraftData.ChunksServer.ToLoad");
                    String tu = data.get("MinecraftData.ChunksServer.ToUnload");

                    // only output if at least one value is present
                    if (w != null || e != null || vis != null || sec != null ||
                            ld != null || tk != null || tl != null || tu != null) {

                        List<String> parts = new ArrayList<>();
                        if (w   != null) parts.add("W: " + w);
                        if (e   != null) parts.add("E: " + e);
                        if (vis != null) parts.add("Visible: " + vis);
                        if (sec != null) parts.add("Sections: " + sec);
                        if (ld  != null) parts.add("Loaded: " + ld);
                        if (tk  != null) parts.add("Ticking: " + tk);
                        if (tl  != null) parts.add("ToLoad: " + tl);
                        if (tu  != null) parts.add("ToUnload: " + tu);

                        output.add("Chunks[S] " + String.join(", ", parts));
                    }
                }


                case "MinecraftData.Dimension" -> {
                    String id = data.get("MinecraftData.Dimension.ID");
                    String fc = data.get("MinecraftData.Dimension.ForceLoadedChunks");

                    // skip if neither ID nor FC is present
                    if (id == null && fc == null) {
                        break;
                    }

                    StringBuilder sb = new StringBuilder();
                    if (id != null) {
                        sb.append(id);
                    }
                    if (fc != null) {
                        if (sb.length() > 0) {
                            sb.append(" ");
                        }
                        sb.append("FC: ").append(fc);
                    }

                    output.add(sb.toString());
                }


                case "LocationDetails.Position" -> {
                    String x = data.get("LocationDetails.Position.X");
                    String y = data.get("LocationDetails.Position.Y");
                    String z = data.get("LocationDetails.Position.Z");

                    // skip if absolutely nothing is present
                    if (x == null && y == null && z == null) {
                        break;
                    }

                    // build each coordinate or "?" if missing
                    String xx = x != null ? x : "?";
                    String yy = y != null ? y : "?";
                    String zz = z != null ? z : "?";

                    output.add("XYZ: " + xx + " / " + yy + " / " + zz);
                }


                case "LocationDetails.Block" -> {
                    String wx = data.get("LocationDetails.Block.WorldX");
                    String wy = data.get("LocationDetails.Block.WorldY");
                    String wz = data.get("LocationDetails.Block.WorldZ");
                    String rx = data.get("LocationDetails.Block.RelativeX");
                    String ry = data.get("LocationDetails.Block.RelativeY");
                    String rz = data.get("LocationDetails.Block.RelativeZ");

                    // skip if absolutely nothing is present
                    if (wx == null && wy == null && wz == null
                            && rx == null && ry == null && rz == null) {
                        break;
                    }

                    StringBuilder sb = new StringBuilder("Block");
                    // world coords
                    if (wx != null || wy != null || wz != null) {
                        sb.append(": ");
                        sb.append(wx != null ? wx : "?").append(" ");
                        sb.append(wy != null ? wy : "?").append(" ");
                        sb.append(wz != null ? wz : "?");
                    }
                    // relative coords
                    if (rx != null || ry != null || rz != null) {
                        sb.append(" [");
                        sb.append(rx != null ? rx : "?").append(" ");
                        sb.append(ry != null ? ry : "?").append(" ");
                        sb.append(rz != null ? rz : "?");
                        sb.append("]");
                    }
                    output.add(sb.toString());
                }


                case "LocationDetails.Chunk" -> {
                    String wx = data.get("LocationDetails.Chunk.WorldX");
                    String wy = data.get("LocationDetails.Chunk.WorldY");
                    String wz = data.get("LocationDetails.Chunk.WorldZ");
                    String rx = data.get("LocationDetails.Chunk.RelativeX");
                    String rz = data.get("LocationDetails.Chunk.RelativeZ");
                    String rf = data.get("LocationDetails.Chunk.RegionFile");

                    // skip only if absolutely nothing is present
                    if (wx == null && wy == null && wz == null
                            && rx == null && rz == null && rf == null) {
                        break;
                    }

                    StringBuilder sb = new StringBuilder("Chunk");

                    // world coords
                    if (wx != null && wy != null && wz != null) {
                        sb.append(": ").append(wx)
                                .append(" ").append(wy)
                                .append(" ").append(wz);
                    }

                    // relative/region detail
                    List<String> details = new ArrayList<>();
                    if (rx != null) details.add(rx);
                    if (rz != null) details.add(rz);

                    if (!details.isEmpty() || rf != null) {
                        sb.append(" [");
                        if (!details.isEmpty()) {
                            sb.append(String.join(" ", details));
                        }
                        if (rf != null) {
                            if (!details.isEmpty()) sb.append(" ");
                            sb.append("in ").append(rf);
                        }
                        sb.append("]");
                    }

                    output.add(sb.toString());
                }


                case "LocationDetails.Facing" -> {
                    String c = data.get("LocationDetails.Facing.Compass");
                    String t = data.get("LocationDetails.Facing.Toward");
                    String h = data.get("LocationDetails.Facing.HeadYaw");
                    // skip entirely if nothing is present
                    if (c == null && t == null && h == null) {
                        break;
                    }
                    StringBuilder sb = new StringBuilder("Facing:");
                    // add compass if we have it
                    if (c != null) {
                        sb.append(" ").append(c);
                    }
                    // add toward if we have it
                    if (t != null) {
                        sb.append(" (Towards ").append(t).append(")");
                    }
                    // add head yaw if we have it
                    if (h != null) {
                        sb.append(" (").append(h).append(")");
                    }
                    output.add(sb.toString());
                }


                case "LocationDetails.Light" -> {
                    String tot = data.get("LocationDetails.Light.Total");
                    String sky = data.get("LocationDetails.Light.Sky");
                    String blk = data.get("LocationDetails.Light.Block");

                    // only skip if *all* three are missing
                    if (tot == null && sky == null && blk == null) {
                        break; // nothing to print
                    }

                    StringBuilder sb = new StringBuilder("Client Light");
                    // if we have a total value, prefix it
                    if (tot != null) {
                        sb.append(": ").append(tot);
                    }
                    // if we have sky or block, always parenthesize them
                    if (sky != null || blk != null) {
                        // if no total, need to add the colon before parentheses
                        if (tot == null) {
                            sb.append(": ");
                        }
                        sb.append("(");
                        List<String> parts = new ArrayList<>();
                        if (sky != null) parts.add(sky + " sky");
                        if (blk != null) parts.add(blk + " block");
                        sb.append(String.join(", ", parts));
                        sb.append(")");
                    }
                    output.add(sb.toString());
                }

                case "LocationDetails.HeightmapClient" -> {
                    String ws = data.get("LocationDetails.HeightmapClient.WorldSurface");
                    String mb = data.get("LocationDetails.HeightmapClient.MotionBlocking");

                    // only skip if both are missing
                    if (ws == null && mb == null) {
                        break;
                    }

                    StringBuilder sb = new StringBuilder("CH");
                    if (ws != null) {
                        sb.append(" S: ").append(ws);
                    }
                    if (mb != null) {
                        sb.append(" M: ").append(mb);
                    }
                    output.add(sb.toString());
                }

                case "LocationDetails.HeightmapServer" -> {
                    String ws = data.get("LocationDetails.HeightmapServer.WorldSurface");
                    String of = data.get("LocationDetails.HeightmapServer.OceanFloor");
                    String mb = data.get("LocationDetails.HeightmapServer.MotionBlocking");
                    String ml = data.get("LocationDetails.HeightmapServer.MotionBlockingNoLeaves");
                    // skip if nothing present
                    if (ws == null && of == null && mb == null && ml == null) {
                        break;
                    }
                    StringBuilder sb = new StringBuilder("SH");
                    if (ws != null) sb.append(" S: ").append(ws);
                    if (of != null) sb.append(" O: ").append(of);
                    if (mb != null) sb.append(" M: ").append(mb);
                    if (ml != null) sb.append(" ML: ").append(ml);
                    output.add(sb.toString());
                }

                case "LocationDetails.Biome" -> {
                    String bio = data.get("LocationDetails.Biome");
                    if (bio == null) {
                        break;
                    }
                    output.add("Biome: " + bio);
                }

                case "LocationDetails.Difficulty" -> {
                    String ld  = data.get("LocationDetails.LocalDifficulty.Numerator");
                    String cd  = data.get("LocationDetails.LocalDifficulty.Denominator");
                    String day = data.get("LocationDetails.LocalDifficulty.Day");
                    // skip if nothing present
                    if (ld == null && cd == null && day == null) {
                        break;
                    }
                    StringBuilder sb = new StringBuilder("Local Difficulty");
                    if (ld != null) {
                        sb.append(": ").append(ld);
                    }
                    if (cd != null) {
                        sb.append(ld != null ? " // " : ": ").append(cd);
                    }
                    if (day != null) {
                        sb.append(" (Day ").append(day).append(")");
                    }
                    output.add(sb.toString());
                }

                case "LocationDetails.NoiseRouter" -> {
                    String[] labs = {
                            "Temperature","Vegetation","Continents","Erosion",
                            "Depth","Ridges","PeaksValleys","InitialDensity","FinalDensity"
                    };
                    List<String> parts = new ArrayList<>();
                    for (String l : labs) {
                        String v = data.get("LocationDetails.NoiseRouter." + l);
                        if (v != null) {
                            parts.add(l.charAt(0) + ": " + v);
                        }
                    }
                    if (parts.isEmpty()) {
                        break;
                    }
                    output.add("NoiseRouter " + String.join(" ", parts));
                }

                case "LocationDetails.BiomeBuilder" -> {
                    String[] labs  = {"PeaksValleys","Continentalness","Erosion","Temperature","Humidity"};
                    String[] codes = {"PV","C","E","T","H"};
                    List<String> parts = new ArrayList<>();
                    for (int idx = 0; idx < labs.length; idx++) {
                        String v = data.get("LocationDetails.BiomeBuilder." + labs[idx]);
                        if (v != null) {
                            parts.add(codes[idx] + ": " + v);
                        }
                    }
                    if (parts.isEmpty()) {
                        break;
                    }
                    output.add("Biome builder " + String.join(" ", parts));
                }

                case "LocationDetails.MobCaps" -> {
                    List<String> parts = new ArrayList<>();
                    Optional.ofNullable(data.get("LocationDetails.MobCaps.Chunks"))
                            .ifPresent(v -> parts.add("SC: " + v));
                    Optional.ofNullable(data.get("LocationDetails.MobCaps.Monsters"))
                            .ifPresent(v -> parts.add("M: " + v));
                    Optional.ofNullable(data.get("LocationDetails.MobCaps.Creatures"))
                            .ifPresent(v -> parts.add("C: " + v));
                    Optional.ofNullable(data.get("LocationDetails.MobCaps.Ambient"))
                            .ifPresent(v -> parts.add("A: " + v));
                    Optional.ofNullable(data.get("LocationDetails.MobCaps.Axolotls"))
                            .ifPresent(v -> parts.add("A: " + v));
                    Optional.ofNullable(data.get("LocationDetails.MobCaps.Underground"))
                            .ifPresent(v -> parts.add("U: " + v));
                    Optional.ofNullable(data.get("LocationDetails.MobCaps.Water"))
                            .ifPresent(v -> parts.add("W: " + v));
                    Optional.ofNullable(data.get("LocationDetails.MobCaps.Fish"))
                            .ifPresent(v -> parts.add("W: " + v));
                    Optional.ofNullable(data.get("LocationDetails.MobCaps.Misc"))
                            .ifPresent(v -> parts.add("M: " + v));
                    if (parts.isEmpty()) {
                        break;
                    }
                    output.add(String.join(", ", parts));
                }

                case "LocationDetails.Sounds" -> {
                    String st  = data.get("LocationDetails.Sounds.Static");
                    String sm  = data.get("LocationDetails.Sounds.StaticMax");
                    String sr  = data.get("LocationDetails.Sounds.Stream");
                    String srm = data.get("LocationDetails.Sounds.StreamMax");
                    String mood= data.get("LocationDetails.Sounds.Mood");

                    // skip if nothing present
                    if (st == null && sm == null && sr == null && srm == null && mood == null) {
                        break;
                    }

                    List<String> parts = new ArrayList<>();
                    if (st != null && sm != null) {
                        parts.add(st + "/" + sm);
                    } else if (st != null) {
                        parts.add(st);
                    } else if (sm != null) {
                        parts.add(sm);
                    }
                    if (sr != null) {
                        parts.add(sr + (srm != null ? "/" + srm : ""));
                    }
                    if (mood != null) {
                        parts.add("(Mood " + mood + "%)");
                    }
                    output.add("Sounds: " + String.join(" + ", parts));
                }

                default -> {
                    // skip unknown keys
                }
            }
        }

        return output;
    }

    public static List<String> getRightValues() {
        List<String> output = new ArrayList<>();

        for (String key : rightLines) {
            switch (key) {

                case "<br>" -> {
                    boolean firstIsBreak = rightLines.get(0).equals("<br>");
                    if (!output.isEmpty() || firstIsBreak) {
                        output.add("");
                    }
                }

                case "System.Java" -> {
                    String version = data.get("System.Java.Version");
                    String bits    = data.get("System.Java.Bits");
                    // skip entirely if neither present
                    if (version == null && bits == null) break;

                    StringBuilder sb = new StringBuilder("Java");
                    if (version != null) {
                        sb.append(": ").append(version);
                    }
                    if (bits != null) {
                        // if no version, still need colon
                        if (version == null) sb.append(":");
                        sb.append(" (").append(bits).append(")");
                    }
                    output.add(sb.toString());
                }

                case "System.Memory" -> {
                    String up = data.get("System.Memory.UsedPercent");
                    String u  = data.get("System.Memory.Used");
                    String t  = data.get("System.Memory.Total");
                    // skip if nothing
                    if (up == null && u == null && t == null) break;

                    StringBuilder sb = new StringBuilder("Mem:");
                    boolean first = true;
                    if (up != null) {
                        sb.append(" ").append(up).append("%");
                        first = false;
                    }
                    if (u != null || t != null) {
                        if (!first) sb.append(" ");
                        if (u != null) sb.append(u);
                        if (t != null) {
                            sb.append("/");
                            sb.append(t);
                        }
                    }
                    output.add(sb.toString());
                }

                case "System.Memory.AllocationRate" -> {
                    String rate = data.get("System.Memory.AllocationRate");
                    if (rate != null) {
                        output.add("Allocation rate: " + rate);
                    }
                }

                case "System.Memory.Allocated" -> {
                    String ap = data.get("System.Memory.AllocatedPercent");
                    String au = data.get("System.Memory.Allocated");
                    String at = data.get("System.Memory.AllocatedTotal");
                    if (ap == null && au == null && at == null) break;

                    StringBuilder sb = new StringBuilder("Allocated:");
                    boolean first = true;
                    if (ap != null) {
                        sb.append(" ").append(ap).append("%");
                        first = false;
                    }
                    if (au != null || at != null) {
                        if (!first) sb.append(" ");
                        if (au != null) sb.append(au);
                        if (at != null) {
                            sb.append("/");
                            sb.append(at);
                        }
                    }
                    output.add(sb.toString());
                }

                case "System.CPU" -> {
                    String cores = data.get("System.CPU.Cores");
                    String name  = data.get("System.CPU.Name");
                    if (cores == null && name == null) break;

                    StringBuilder sb = new StringBuilder("CPU");
                    if (cores != null) sb.append(": ").append(cores);
                    if (name  != null) sb.append(cores != null ? " " : ": ").append(name);
                    output.add(sb.toString());
                }

                case "System.Display" -> {
                    String res    = data.get("System.Display.Resolution");
                    String vendor = data.get("System.Display.Vendor");
                    if (res == null && vendor == null) break;

                    StringBuilder sb = new StringBuilder("Display");
                    if (res != null) {
                        sb.append(": ").append(res);
                    }
                    if (vendor != null) {
                        // if no resolution, need colon
                        if (res == null) sb.append(":");
                        sb.append(" (").append(vendor).append(")");
                    }
                    output.add(sb.toString());
                }

                case "System.Display.Renderer" -> {
                    String renderer = data.get("System.Display.Renderer");
                    if (renderer != null) {
                        output.add(renderer);
                    }
                }

                case "System.Display.OpenGLVersion" -> {
                    String version = data.get("System.Display.OpenGLVersion");
                    if (version != null) {
                        output.add(version);
                    }
                }

                case "Targets.TargetBlock"  -> appendTargetSection(output, "TargetBlock",  "Targeted Block");
                case "Targets.TargetFluid"  -> appendTargetSection(output, "TargetFluid",  "Targeted Fluid");
                case "Targets.TargetEntity" -> appendTargetSection(output, "TargetEntity", "Targeted Entity");

                default -> {
                    // unknown key — skip
                }
            }
        }

        return output;
    }

    private static void appendTargetSection(List<String> output, String sectionType, String displayName) {
        // only proceed if we actually parsed a ResourceLocation
        String resKey = "Targets." + sectionType + ".ResourceLocation";
        String resource = data.get(resKey);
        if (resource == null) return;

        // header with coords
        String coordKey = "Targets." + sectionType + ".Coordinates";
        String coords   = data.getOrDefault(coordKey, "?");
        output.add(ChatFormatting.UNDERLINE + displayName + ": " + coords);

        // the resource itself
        output.add(resource);

        // states (semicolon-separated)
        String stateKey = "Targets." + sectionType + ".States";
        String states   = data.get(stateKey);
        if (states != null) {
            for (String st : states.split(";")) {
                output.add(st);
            }
        }

        // tags (semicolon-separated)
        String tagKey = "Targets." + sectionType + ".Tags";
        String tags   = data.get(tagKey);
        if (tags != null) {
            for (String tag : tags.split(";")) {
                output.add("#" + tag);
            }
        }
    }
}
