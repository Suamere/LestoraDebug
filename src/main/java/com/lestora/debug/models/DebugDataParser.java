package com.lestora.debug.models;
import java.util.*;
import java.util.regex.*;

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
            "Targets.TargetFluid",
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

    public static void parse(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) continue;

            // ─── first paragraph (MinecraftData) ───
            if (line.startsWith("Minecraft ")) {
                // "Minecraft 1.21.4 (MOD_DEV/forge)"
                String[] parts = line.split(" ", 3);
                putIfNotBlocked("MinecraftData.VersionInfo.VersionNumber", parts[1]);
                if (line.contains("(")) {
                    String mod = line.substring(line.indexOf('(')+1, line.indexOf(')'));
                    putIfNotBlocked("MinecraftData.VersionInfo.ModName", mod);
                }
                continue;
            }

            if (line.contains(" fps ") && line.contains("GPU:")) {
                // "60 fps T: 120 vsync fancy clouds B: 2 GPU: 20%"
                String[] tok = line.split("\\s+");
                putIfNotBlocked("MinecraftData.Renderer.FPS", tok[0]);
                for (int j = 1; j < tok.length; j++) {
                    switch (tok[j]) {
                        case "T:" -> putIfNotBlocked("MinecraftData.Renderer.TickTime", tok[j+1]);
                        case "vsync" -> putIfNotBlocked("MinecraftData.Renderer.VSync", "on");
                        case "fast","fancy","fabulous" -> putIfNotBlocked("MinecraftData.Renderer.Graphics", tok[j]);
                        case "fancy-clouds","fast-clouds" -> putIfNotBlocked("MinecraftData.Renderer.Clouds", tok[j]);
                        case "B:" -> putIfNotBlocked("MinecraftData.Renderer.BiomeBlend", tok[j+1]);
                        case "GPU:" -> putIfNotBlocked("MinecraftData.Renderer.GPU", tok[j+1].replace("%",""));
                    }
                }
                continue;
            }

            if (line.startsWith("Integrated server @")) {
                // "Integrated server @ 3.1/50.0 ms, 22 tx, 1053 rx"
                String[] at = line.split("@");
                putIfNotBlocked("MinecraftData.Server.Brand",
                        at[0].replace("Integrated server","").trim());
                String[] parts = at[1].split(",");
                String timing = parts[0].replace("ms","").trim();
                String[] times = timing.split("/");
                putIfNotBlocked("MinecraftData.Server.TickTimeMs", times[0]);
                putIfNotBlocked("MinecraftData.Server.TicksPerSecond", times[1]);
                putIfNotBlocked("MinecraftData.Server.PacketsSent", parts[1].trim().split(" ")[0]);
                putIfNotBlocked("MinecraftData.Server.PacketsReceived", parts[2].trim().split(" ")[0]);
                continue;
            }

            if (line.startsWith("C: ")) {
                // "C: 305/15000 (s) D: 12, pC: 000, pU: 00, aB: 16"
                String[] p = line.split("\\s+");
                String[] ct = p[1].split("/");
                putIfNotBlocked("MinecraftData.Chunks.SectionsRendered", ct[0]);
                putIfNotBlocked("MinecraftData.Chunks.SectionsTotal",    ct[1]);
                for (int j = 2; j < p.length; j++) {
                    switch(p[j]) {
                        case "D:"  -> putIfNotBlocked("MinecraftData.Chunks.RenderDistance", p[j+1].replace(",",""));
                        case "pC:" -> putIfNotBlocked("MinecraftData.Chunks.PendingBatch",    p[j+1].replace(",",""));
                        case "pU:" -> putIfNotBlocked("MinecraftData.Chunks.PendingUploads",  p[j+1].replace(",",""));
                        case "aB:" -> putIfNotBlocked("MinecraftData.Chunks.AvailableBuffers", p[j+1]);
                    }
                }
                continue;
            }

            if (line.startsWith("E: ")) {
                // "E: 3/127, SD: 12"
                String entityPart = line.substring(0, line.indexOf(',')).trim();       // "E: 3/127"
                String[] counts     = entityPart.substring(entityPart.indexOf(' ')+1).split("/");
                putIfNotBlocked("MinecraftData.Entities.Rendered", counts[0]);
                putIfNotBlocked("MinecraftData.Entities.Total",    counts[1]);
                int sdIdx = line.indexOf("SD:");
                putIfNotBlocked("MinecraftData.Entities.SimulationDistance",
                        line.substring(sdIdx+3).trim());
                continue;
            }

            if (line.startsWith("P: ")) {
                // "P: 1270. T: 127"
                String[] parts = line.split("\\s+");
                putIfNotBlocked("MinecraftData.Particles.Count",     parts[1].replace(".",""));
                putIfNotBlocked("MinecraftData.Particles.TickValue", parts[3]);
                continue;
            }

            if (line.startsWith("Chunks[C]")) {
                // "Chunks[C] W: 961, 637 E: 127,76,637"
                String wPart = line.substring(line.indexOf("W:")+2, line.indexOf("E:")).trim();
                String ePart = line.substring(line.indexOf("E:")+2).trim();
                String[] wc = wPart.split(",");
                String[] ec = ePart.split(",");
                putIfNotBlocked("MinecraftData.ChunksClient.Cached",        wc[0].trim());
                putIfNotBlocked("MinecraftData.ChunksClient.Loaded",        wc[1].trim());
                if (ec.length >= 3) {
                    putIfNotBlocked("MinecraftData.ChunksClient.Entities",       ec[0].trim());
                    putIfNotBlocked("MinecraftData.ChunksClient.EntitySections", ec[1].trim());
                    putIfNotBlocked("MinecraftData.ChunksClient.Ticking",        ec[2].trim());
                }
                continue;
            }

            if (line.startsWith("Chunks[S]")) {
                // "Chunks[S] W: 3338 E: 173,103,890,890,0,0"
                String wVal = line.substring(line.indexOf("W:")+2, line.indexOf("E:")).trim();
                putIfNotBlocked("MinecraftData.ChunksServer.World", wVal);
                String[] es = line.substring(line.indexOf("E:")+2).split(",");
                if (es.length >= 6) {
                    putIfNotBlocked("MinecraftData.ChunksServer.Entities", es[0].trim());
                    putIfNotBlocked("MinecraftData.ChunksServer.Visible",  es[1].trim());
                    putIfNotBlocked("MinecraftData.ChunksServer.Sections", es[2].trim());
                    putIfNotBlocked("MinecraftData.ChunksServer.Loaded",   es[3].trim());
                    putIfNotBlocked("MinecraftData.ChunksServer.Ticking",  es[4].trim());
                    putIfNotBlocked("MinecraftData.ChunksServer.ToLoad",   es[5].trim());
                    if (es.length > 6) putIfNotBlocked("MinecraftData.ChunksServer.ToUnload", es[6].trim());
                }
                continue;
            }

            if (line.startsWith("minecraft:") && line.contains("FC:")) {
                // "minecraft:overworld FC: 0"
                String[] parts = line.split("\\s+");
                putIfNotBlocked("MinecraftData.Dimension.ID", parts[0]);
                putIfNotBlocked("MinecraftData.Dimension.ForceLoadedChunks", parts[2]);
                continue;
            }

            // ─── second paragraph (LocationDetails) ───
            if (line.startsWith("XYZ:")) {
                // "XYZ: -123.000 / 64.000 / -123.000"
                String[] xyz = line.substring(5).split("/");
                putIfNotBlocked("LocationDetails.Position.X", xyz[0].trim());
                putIfNotBlocked("LocationDetails.Position.Y", xyz[1].trim());
                putIfNotBlocked("LocationDetails.Position.Z", xyz[2].trim());
                continue;
            }

            if (line.startsWith("Block:")) {
                // "Block: -124 64 -124 [1 2 3]"
                String world = line.substring(7, line.indexOf("[")).trim();
                String rel   = line.substring(line.indexOf("[")+1, line.indexOf("]")).trim();
                String[] w   = world.split(" ");
                String[] r   = rel.split(" ");
                putIfNotBlocked("LocationDetails.Block.WorldX", w[0]);
                putIfNotBlocked("LocationDetails.Block.WorldY", w[1]);
                putIfNotBlocked("LocationDetails.Block.WorldZ", w[2]);
                putIfNotBlocked("LocationDetails.Block.RelativeX", r[0]);
                putIfNotBlocked("LocationDetails.Block.RelativeY", r[1]);
                putIfNotBlocked("LocationDetails.Block.RelativeZ", r[2]);
                continue;
            }

            if (line.startsWith("Chunk:")) {
                // "Chunk: -9 4 -9 [14 20 in r.-1.-1.mca]"
                String coord = line.substring(7, line.indexOf("[")).trim();
                String detail= line.substring(line.indexOf("[")+1, line.indexOf("]")).trim();
                String[] c    = coord.split(" ");
                putIfNotBlocked("LocationDetails.Chunk.WorldX", c[0]);
                putIfNotBlocked("LocationDetails.Chunk.WorldY", c[1]);
                putIfNotBlocked("LocationDetails.Chunk.WorldZ", c[2]);
                String[] dt   = detail.split(" in ");
                String[] rc   = dt[0].split(" ");
                putIfNotBlocked("LocationDetails.Chunk.RelativeX", rc[0]);
                putIfNotBlocked("LocationDetails.Chunk.RelativeZ", rc[1]);
                if (dt.length>1) putIfNotBlocked("LocationDetails.Chunk.RegionFile", dt[1]);
                continue;
            }

            if (line.startsWith("Facing:")) {
                // "Facing: south (Towards positive Z) (1.5 / 66.8)"
                String[] seg = line.split("\\(");
                putIfNotBlocked("LocationDetails.Facing.Compass", seg[0].split(":")[1].trim());
                putIfNotBlocked("LocationDetails.Facing.Toward", seg[1].replace(")","").replace("Towards","").trim());
                putIfNotBlocked("LocationDetails.Facing.HeadYaw", seg[2].replace(")","").trim());
                continue;
            }

            if (line.startsWith("Client Light:")) {
                // "Client Light: 15 (15 sky, 9 block)"
                String tot = line.substring(13,line.indexOf("(")).trim();
                putIfNotBlocked("LocationDetails.Light.Total", tot);
                String sub = line.substring(line.indexOf("(")+1, line.indexOf(")"));
                String[] sp = sub.split(",");
                putIfNotBlocked("LocationDetails.Light.Sky",   sp[0].replace("sky","").trim());
                putIfNotBlocked("LocationDetails.Light.Block", sp[1].replace("block","").trim());
                continue;
            }

            if (line.startsWith("CH ")) {
                // "CH S: 63 M: 63"
                String[] p = line.split("\\s+");
                putIfNotBlocked("LocationDetails.HeightmapClient.WorldSurface",    p[2]);
                putIfNotBlocked("LocationDetails.HeightmapClient.MotionBlocking", p[4]);
                continue;
            }

            if (line.startsWith("SH ")) {
                // "SH S: 63 O: 63 M: 63 ML: 63"
                String[] p = line.split("\\s+");
                putIfNotBlocked("LocationDetails.HeightmapServer.WorldSurface",    p[2]);
                putIfNotBlocked("LocationDetails.HeightmapServer.OceanFloor",      p[4]);
                putIfNotBlocked("LocationDetails.HeightmapServer.MotionBlocking",  p[6]);
                putIfNotBlocked("LocationDetails.HeightmapServer.MotionBlockingNoLeaves", p[8]);
                continue;
            }

            if (line.startsWith("Biome:")) {
                putIfNotBlocked("LocationDetails.Biome", line.substring(7).trim());
                continue;
            }

            if (line.startsWith("Local Difficulty:")) {
                // drop the prefix
                String rest = line.substring("Local Difficulty:".length()).trim();
                // handle the "??" case early
                if (rest.equals("??")) {
                    putIfNotBlocked("LocationDetails.LocalDifficulty", "??");
                } else {
                    // rest should look like "0.75 // 0.00 (Day 0)"
                    String[] halves = rest.split("//", 2);
                    // left of "//" = raw local diff
                    String local = halves[0].trim();
                    putIfNotBlocked("LocationDetails.LocalDifficulty", local);

                    if (halves.length > 1) {
                        // right of "//" = clamped + day
                        String right = halves[1].trim();            // e.g. "0.00 (Day 0)"
                        // clamped is the first token
                        String[] tokens = right.split("\\s+", 2);
                        putIfNotBlocked("LocationDetails.ClampedDifficulty", tokens[0]);

                        if (tokens.length > 1) {
                            // tokens[1] should be "(Day 0)" or similar
                            String dayPart = tokens[1].trim();
                            if (dayPart.startsWith("(Day") && dayPart.endsWith(")")) {
                                // extract the number between "Day" and ")"
                                String dayNum = dayPart.substring(4, dayPart.length() - 1).trim();
                                putIfNotBlocked("LocationDetails.Day", dayNum);
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
                        case "T"  -> putIfNotBlocked("LocationDetails.NoiseRouter.Temperature", val);
                        case "V"  -> putIfNotBlocked("LocationDetails.NoiseRouter.Vegetation", val);
                        case "C"  -> putIfNotBlocked("LocationDetails.NoiseRouter.Continents", val);
                        case "E"  -> putIfNotBlocked("LocationDetails.NoiseRouter.Erosion", val);
                        case "D"  -> putIfNotBlocked("LocationDetails.NoiseRouter.Depth", val);
                        case "W"  -> putIfNotBlocked("LocationDetails.NoiseRouter.Ridges", val);
                        case "PV" -> putIfNotBlocked("LocationDetails.NoiseRouter.PeaksValleys", val);
                        case "AS" -> putIfNotBlocked("LocationDetails.NoiseRouter.InitialDensity", val);
                        case "N"  -> putIfNotBlocked("LocationDetails.NoiseRouter.FinalDensity", val);
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
                        case "PV:" -> putIfNotBlocked("LocationDetails.BiomeBuilder.PeaksValleys", val);
                        case "C:"  -> putIfNotBlocked("LocationDetails.BiomeBuilder.Continentalness", val);
                        case "E:"  -> putIfNotBlocked("LocationDetails.BiomeBuilder.Erosion", val);
                        case "T:"  -> putIfNotBlocked("LocationDetails.BiomeBuilder.Temperature", val);
                        case "H:"  -> putIfNotBlocked("LocationDetails.BiomeBuilder.Humidity", val);
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
                        case "SC" -> putIfNotBlocked("LocationDetails.MobCaps.Chunks", val);
                        case "M"  -> {
                            if (mCount==0) putIfNotBlocked("LocationDetails.MobCaps.Monsters", val);
                            else           putIfNotBlocked("LocationDetails.MobCaps.Misc", val);
                            mCount++;
                        }
                        case "C"  -> putIfNotBlocked("LocationDetails.MobCaps.Creatures", val);
                        case "A"  -> {
                            if (aCount==0) putIfNotBlocked("LocationDetails.MobCaps.Ambient", val);
                            else           putIfNotBlocked("LocationDetails.MobCaps.Axolotls", val);
                            aCount++;
                        }
                        case "U"  -> putIfNotBlocked("LocationDetails.MobCaps.Underground", val);
                        case "W"  -> {
                            if (wCount==0) putIfNotBlocked("LocationDetails.MobCaps.Water", val);
                            else           putIfNotBlocked("LocationDetails.MobCaps.Fish", val);
                            wCount++;
                        }
                    }
                }
                continue;
            }

            if (line.startsWith("Sounds:")) {
                String[] parts = line.split("\\s+");
                String[] st = parts[1].split("/");
                putIfNotBlocked("LocationDetails.Sounds.Static", st[0]);
                putIfNotBlocked("LocationDetails.Sounds.StaticMax", st[1]);
                String[] sr = parts[3].split("/");
                putIfNotBlocked("LocationDetails.Sounds.Stream", sr[0]);
                putIfNotBlocked("LocationDetails.Sounds.StreamMax", sr[1]);
                String mood = parts[5].replace("(", "").replace(")", "").replace("%", "");
                putIfNotBlocked("LocationDetails.Sounds.Mood", mood);
                continue;
            }

            // ─── Right Side sections ───
            if (line.startsWith("Mem:")) {
                // Example: "Mem: 45% 512/1024"
                String[] parts = line.split("[ %/]+");
                // parts = ["Mem:", "45", "512", "1024"]
                if (parts.length >= 4) {
                    putIfNotBlocked("System.Memory.UsedPercent", parts[1]);
                    putIfNotBlocked("System.Memory.Used", parts[2]);
                    putIfNotBlocked("System.Memory.Total", parts[3]);
                }
            }

            if (line.startsWith("Allocation rate:")) {
                // Example: "Allocation rate: 5.0 MiB/s"
                String[] parts = line.split("\\s+");
                if (parts.length >= 3) {
                    putIfNotBlocked("System.Memory.AllocationRate", parts[2]);
                }
            }

            if (line.startsWith("Allocated:")) {
                // Example: "Allocated: 50% 512/1024"
                String[] parts = line.split("[ %/]+");
                if (parts.length >= 4) {
                    putIfNotBlocked("System.Memory.AllocatedPercent", parts[1]);
                    putIfNotBlocked("System.Memory.Allocated", parts[2]);
                    putIfNotBlocked("System.Memory.AllocatedTotal", parts[3]);
                }
            }

            if (line.startsWith("CPU:")) {
                // Example: "CPU: 8 Intel(R) Core(TM)..."
                String[] parts = line.split("\\s+", 3);
                if (parts.length >= 2) {
                    putIfNotBlocked("System.CPU.Cores", parts[1]);
                    if (parts.length >= 3) {
                        putIfNotBlocked("System.CPU.Name", parts[2]);
                    }
                }
            }

            if (line.startsWith("Display:")) {
                // 1) parse the Display: resolution/vendor as before
                Matcher dispMatch = Pattern.compile("Display:\\s*(\\S+)\\s*\\(([^)]+)\\)")
                        .matcher(line);
                if (dispMatch.find()) {
                    putIfNotBlocked("System.Display.Resolution", dispMatch.group(1));
                    putIfNotBlocked("System.Display.Vendor",     dispMatch.group(2));
                }

                // 2) the very next line is the GPU renderer
                if (i+1 < lines.size()) {
                    String rendererLine = lines.get(++i).trim();
                    putIfNotBlocked("System.Display.Renderer", rendererLine);
                }

                // 3) and the line after that is the OpenGL version
                if (i+1 < lines.size()) {
                    String versionLine = lines.get(++i).trim();
                    putIfNotBlocked("System.Display.OpenGLVersion", versionLine);
                }

                continue;
            }

            // TARGETED BLOCK / FLUID / ENTITY sections
            if (line.startsWith("Targeted Block:")) {
                // Coordinates
                String coords = line.substring(line.indexOf(':')+1).trim();
                putIfNotBlocked("Targets.TargetBlock.Coordinates", coords);
                // Following lines: identifier, states, tags
                List<String> states = new ArrayList<>();
                List<String> tags = new ArrayList<>();
                // Next line should be resource location (identifier)
                if (i+1 < lines.size()) {
                    String idLine = lines.get(++i).trim();
                    putIfNotBlocked("Targets.TargetBlock.ResourceLocation", idLine);
                }
                // Collect state and tag lines until a blank or new section
                while (i+1 < lines.size()) {
                    String next = lines.get(i+1).trim();
                    if (next.isEmpty() || next.startsWith("Targeted") ) break;
                    i++;
                    if (next.startsWith("#")) {
                        // Tag line
                        tags.add(next.substring(1)); // remove leading '#'
                    } else if (next.contains(":")) {
                        // State line (e.g. "waterlogged: true")
                        String[] parts = next.split(":");
                        if (parts.length == 2) {
                            states.add(parts[0] + "=" + parts[1].trim());
                        }
                    }
                }
                if (!states.isEmpty()) {
                    putIfNotBlocked("Targets.TargetBlock.States", String.join(";", states));
                }
                if (!tags.isEmpty()) {
                    putIfNotBlocked("Targets.TargetBlock.Tags", String.join(";", tags));
                }
            }
            if (line.startsWith("Targeted Fluid:")) {
                String coords = line.substring(line.indexOf(':')+1).trim();
                putIfNotBlocked("Targets.TargetFluid.Coordinates", coords);
                List<String> states = new ArrayList<>();
                List<String> tags = new ArrayList<>();
                if (i+1 < lines.size()) {
                    String idLine = lines.get(++i).trim();
                    putIfNotBlocked("Targets.TargetFluid.ResourceLocation", idLine);
                }
                while (i+1 < lines.size()) {
                    String next = lines.get(i+1).trim();
                    if (next.isEmpty() || next.startsWith("Targeted")) break;
                    i++;
                    if (next.startsWith("#")) {
                        tags.add(next.substring(1));
                    } else if (next.contains(":")) {
                        String[] parts = next.split(":");
                        if (parts.length == 2) {
                            states.add(parts[0] + "=" + parts[1].trim());
                        }
                    }
                }
                if (!states.isEmpty()) {
                    putIfNotBlocked("Targets.TargetFluid.States", String.join(";", states));
                }
                if (!tags.isEmpty()) {
                    putIfNotBlocked("Targets.TargetFluid.Tags", String.join(";", tags));
                }
            }
            if (line.startsWith("Targeted Entity:")) {
                String val = line.substring(line.indexOf(':')+1).trim();
                putIfNotBlocked("Targets.TargetEntity.ResourceLocation", val);
                // No coordinates or states/tags are printed for entities in F3 :contentReference[oaicite:6]{index=6}.
            }
        }
    }


    /** Helper to put a key/value if not blocked; else remove it. */
    private static void putIfNotBlocked(String key, String value) {
        if (key == null || value == null) return;
        if (blocklist.contains(key)) {
            data.remove(key);
        } else {
            data.put(key, value);
        }
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

    public static List<String> getRightValues() {
        List<String> output = new ArrayList<>();

        for (String key : rightLines) {
            switch (key) {
                case "<br>" -> {
                    // blank line
                    output.add("");
                }

                case "System.Java" -> {
                    // Java: <version> (<bits>bit)
                    String version = data.getOrDefault("System.Java.Version", "?");
                    String bits    = data.getOrDefault("System.Java.Bits", "?");
                    output.add("Java: " + version + " (" + bits + "bit)");
                }

                case "System.Memory" -> {
                    // Mem: <usedPercent>% <used>/<total>MB
                    String up = data.getOrDefault("System.Memory.UsedPercent", "?");
                    String u  = data.getOrDefault("System.Memory.Used",        "?");
                    String t  = data.getOrDefault("System.Memory.Total",       "?");
                    output.add("Mem: " + up + "% " + u + "/" + t + "MB");
                }

                case "System.Memory.AllocationRate" -> {
                    // Allocation rate: <rate>MB/s
                    String rate = data.getOrDefault("System.Memory.AllocationRate", "?");
                    output.add("Allocation rate: " + rate + "MB/s");
                }

                case "System.Memory.Allocated" -> {
                    // Allocated: <percent>% <used>/<total>MB
                    String ap = data.getOrDefault("System.Memory.AllocatedPercent", "?");
                    String au = data.getOrDefault("System.Memory.Allocated",        "?");
                    String at = data.getOrDefault("System.Memory.AllocatedTotal",   "?");
                    output.add("Allocated: " + ap + "% " + au + "/" + at + "MB");
                }

                case "System.CPU" -> {
                    // CPU: <cores> <name>
                    String cores = data.getOrDefault("System.CPU.Cores", "?");
                    String name  = data.getOrDefault("System.CPU.Name",  "?");
                    output.add("CPU: " + cores + " " + name);
                }

                case "System.Display" -> {
                    // Display: <resolution> (<vendor>)
                    String res    = data.getOrDefault("System.Display.Resolution", "?");
                    String vendor = data.getOrDefault("System.Display.Vendor",     "?");
                    output.add("Display: " + res + " (" + vendor + ")");
                }

                case "System.Display.Renderer" -> {
                    // renderer: <gpu>
                    String gpu = data.getOrDefault("System.Display.Renderer", "?");
                    output.add("renderer: " + gpu);
                }

                case "System.Display.OpenGLVersion" -> {
                    // version: <version>
                    String gl = data.getOrDefault("System.Display.OpenGLVersion", "?");
                    output.add("version: " + gl);
                }

                case "Targets.TargetBlock" -> {
                    // Targeted Block header + resource + states + tags
                    String coords = data.getOrDefault("Targets.TargetBlock.Coordinates", "?");
                    output.add("Targeted Block: " + coords);

                    String id = data.getOrDefault("Targets.TargetBlock.ResourceLocation", "?");
                    output.add(id);

                    String states = data.get("Targets.TargetBlock.States");
                    if (states != null) {
                        for (String st : states.split(";")) {
                            output.add(st);
                        }
                    }

                    String tags = data.get("Targets.TargetBlock.Tags");
                    if (tags != null) {
                        for (String tag : tags.split(";")) {
                            output.add("#" + tag);
                        }
                    }
                }

                case "Targets.TargetFluid" -> {
                    String coords = data.getOrDefault("Targets.TargetFluid.Coordinates", "?");
                    output.add("Targeted Fluid: " + coords);

                    String id = data.getOrDefault("Targets.TargetFluid.ResourceLocation", "?");
                    output.add(id);

                    String states = data.get("Targets.TargetFluid.States");
                    if (states != null) {
                        for (String st : states.split(";")) {
                            output.add(st);
                        }
                    }

                    String tags = data.get("Targets.TargetFluid.Tags");
                    if (tags != null) {
                        for (String tag : tags.split(";")) {
                            output.add("#" + tag);
                        }
                    }
                }

                case "Targets.TargetEntity" -> {
                    // Targeted Entity header + type
                    // parser only stores ResourceLocation for entity
                    output.add("Targeted Entity:");
                    String type = data.getOrDefault("Targets.TargetEntity.ResourceLocation", "?");
                    output.add(type);
                }

                default -> {
                    // unknown key — skip
                }
            }
        }

        return output;
    }

    /** Reconstructs the left‐column debug lines in order. */
    public static List<String> getLeftValues() {
        List<String> output = new ArrayList<>();

        for (String key : leftLines) {
            switch (key) {

                case "<br>" -> {
                    output.add("");
                }

                case "MinecraftData.VersionInfo" -> {
                    String ver = data.getOrDefault("MinecraftData.VersionInfo.VersionNumber", "?");
                    String mod = data.getOrDefault("MinecraftData.VersionInfo.ModName", "");
                    if (!mod.isEmpty()) mod = " (" + mod + ")";
                    output.add("Minecraft " + ver + mod);
                }

                case "MinecraftData.Renderer" -> {
                    String fps    = data.getOrDefault("MinecraftData.Renderer.FPS", "?");
                    String tick   = data.getOrDefault("MinecraftData.Renderer.TickTime", "?");
                    String vsync  = data.getOrDefault("MinecraftData.Renderer.VSync", "off");
                    String gfx    = data.getOrDefault("MinecraftData.Renderer.Graphics", "");
                    String clouds = data.getOrDefault("MinecraftData.Renderer.Clouds", "");
                    String blend  = data.getOrDefault("MinecraftData.Renderer.BiomeBlend", "?");
                    String gpu    = data.getOrDefault("MinecraftData.Renderer.GPU", "?");
                    StringBuilder sb = new StringBuilder();
                    sb.append(fps).append(" fps")
                            .append(" T: ").append(tick)
                            .append(vsync.equals("on") ? " vsync" : "");
                    if (!gfx.isEmpty())    sb.append(" ").append(gfx);
                    if (!clouds.isEmpty()) sb.append(" ").append(clouds);
                    sb.append(" B: ").append(blend)
                            .append(" GPU: ").append(gpu).append("%");
                    output.add(sb.toString());
                }

                case "MinecraftData.Server" -> {
                    String brand   = data.getOrDefault("MinecraftData.Server.Brand", "");
                    String tms     = data.getOrDefault("MinecraftData.Server.TickTimeMs", "?");
                    String tps     = data.getOrDefault("MinecraftData.Server.TicksPerSecond", "?");
                    String sent    = data.getOrDefault("MinecraftData.Server.PacketsSent", "?");
                    String recv    = data.getOrDefault("MinecraftData.Server.PacketsReceived", "?");
                    output.add("Integrated server @ " +
                            tms + "/" + tps + " ms, " +
                            sent + " tx, " + recv + " rx");
                }

                case "MinecraftData.Chunks" -> {
                    String rs = data.getOrDefault("MinecraftData.Chunks.SectionsRendered", "?");
                    String ts = data.getOrDefault("MinecraftData.Chunks.SectionsTotal",    "?");
                    String rd = data.getOrDefault("MinecraftData.Chunks.RenderDistance",    "?");
                    String pc = data.getOrDefault("MinecraftData.Chunks.PendingBatch",      "?");
                    String pu = data.getOrDefault("MinecraftData.Chunks.PendingUploads",    "?");
                    String ab = data.getOrDefault("MinecraftData.Chunks.AvailableBuffers",  "?");
                    output.add("C: " + rs + "/" + ts +
                            " D: " + rd + ", pC: " + pc +
                            ", pU: " + pu + ", aB: " + ab);
                }

                case "MinecraftData.Entities" -> {
                    String rend = data.getOrDefault("MinecraftData.Entities.Rendered", "?");
                    String tot  = data.getOrDefault("MinecraftData.Entities.Total",    "?");
                    String sd   = data.getOrDefault("MinecraftData.Entities.SimulationDistance", "?");
                    output.add("E: " + rend + "/" + tot + ", SD: " + sd);
                }

                case "MinecraftData.Particles" -> {
                    String cnt = data.getOrDefault("MinecraftData.Particles.Count",     "?");
                    String tv  = data.getOrDefault("MinecraftData.Particles.TickValue", "?");
                    output.add("P: " + cnt + ". T: " + tv);
                }

                case "MinecraftData.ChunksClient" -> {
                    String c  = data.getOrDefault("MinecraftData.ChunksClient.Cached",        "?");
                    String l  = data.getOrDefault("MinecraftData.ChunksClient.Loaded",        "?");
                    String e1 = data.getOrDefault("MinecraftData.ChunksClient.Entities",      "?");
                    String e2 = data.getOrDefault("MinecraftData.ChunksClient.EntitySections","?");
                    String t  = data.getOrDefault("MinecraftData.ChunksClient.Ticking",       "?");
                    output.add("Chunks[C] W: " + c + ", " + l +
                            " E: " + e1 + "," + e2 + "," + t);
                }

                case "MinecraftData.ChunksServer" -> {
                    String w   = data.getOrDefault("MinecraftData.ChunksServer.World",       "?");
                    String e   = data.getOrDefault("MinecraftData.ChunksServer.Entities",    "?");
                    String vis = data.getOrDefault("MinecraftData.ChunksServer.Visible",     "?");
                    String sec = data.getOrDefault("MinecraftData.ChunksServer.Sections",    "?");
                    String ld  = data.getOrDefault("MinecraftData.ChunksServer.Loaded",      "?");
                    String tk  = data.getOrDefault("MinecraftData.ChunksServer.Ticking",     "?");
                    String tl  = data.getOrDefault("MinecraftData.ChunksServer.ToLoad",      "?");
                    String tu  = data.getOrDefault("MinecraftData.ChunksServer.ToUnload",    "");
                    String line = "Chunks[S] W: " + w +
                            " E: " + e + "," + vis + "," + sec + "," + ld + "," + tk + "," + tl;
                    if (!tu.isEmpty()) line += "," + tu;
                    output.add(line);
                }

                case "MinecraftData.Dimension" -> {
                    String id = data.getOrDefault("MinecraftData.Dimension.ID", "?");
                    String fc = data.getOrDefault("MinecraftData.Dimension.ForceLoadedChunks", "?");
                    output.add(id + " FC: " + fc);
                }

                case "LocationDetails.Position" -> {
                    String x = data.getOrDefault("LocationDetails.Position.X", "?");
                    String y = data.getOrDefault("LocationDetails.Position.Y", "?");
                    String z = data.getOrDefault("LocationDetails.Position.Z", "?");
                    output.add("XYZ: " + x + " / " + y + " / " + z);
                }

                case "LocationDetails.Block" -> {
                    String wx = data.getOrDefault("LocationDetails.Block.WorldX", "?");
                    String wy = data.getOrDefault("LocationDetails.Block.WorldY", "?");
                    String wz = data.getOrDefault("LocationDetails.Block.WorldZ", "?");
                    String rx = data.getOrDefault("LocationDetails.Block.RelativeX", "?");
                    String ry = data.getOrDefault("LocationDetails.Block.RelativeY", "?");
                    String rz = data.getOrDefault("LocationDetails.Block.RelativeZ", "?");
                    output.add("Block: " + wx + " " + wy + " " + wz +
                            " [" + rx + " " + ry + " " + rz + "]");
                }

                case "LocationDetails.Chunk" -> {
                    String wx = data.getOrDefault("LocationDetails.Chunk.WorldX", "?");
                    String wy = data.getOrDefault("LocationDetails.Chunk.WorldY", "?");
                    String wz = data.getOrDefault("LocationDetails.Chunk.WorldZ", "?");
                    String rx = data.getOrDefault("LocationDetails.Chunk.RelativeX",    "?");
                    String rz = data.getOrDefault("LocationDetails.Chunk.RelativeZ",    "?");
                    String rf = data.getOrDefault("LocationDetails.Chunk.RegionFile",   "");
                    String detail = "[" + rx + " " + rz + (rf.isEmpty() ? "" : " in " + rf) + "]";
                    output.add("Chunk: " + wx + " " + wy + " " + wz + " " + detail);
                }

                case "LocationDetails.Facing" -> {
                    String c = data.getOrDefault("LocationDetails.Facing.Compass", "?");
                    String t = data.getOrDefault("LocationDetails.Facing.Toward",  "?");
                    String h = data.getOrDefault("LocationDetails.Facing.HeadYaw", "?");
                    output.add("Facing: " + c +
                            " (Towards " + t + ")" +
                            " (" + h + ")");
                }

                case "LocationDetails.Light" -> {
                    String tot = data.getOrDefault("LocationDetails.Light.Total", "?");
                    String sky = data.getOrDefault("LocationDetails.Light.Sky",   "?");
                    String blk = data.getOrDefault("LocationDetails.Light.Block", "?");
                    output.add("Client Light: " + tot +
                            " (" + sky + " sky, " + blk + " block)");
                }

                case "LocationDetails.HeightmapClient" -> {
                    String ws = data.getOrDefault("LocationDetails.HeightmapClient.WorldSurface",    "?");
                    String mb = data.getOrDefault("LocationDetails.HeightmapClient.MotionBlocking", "?");
                    output.add("CH S: " + ws + " M: " + mb);
                }

                case "LocationDetails.HeightmapServer" -> {
                    String ws = data.getOrDefault("LocationDetails.HeightmapServer.WorldSurface",         "?");
                    String of = data.getOrDefault("LocationDetails.HeightmapServer.OceanFloor",           "?");
                    String mb = data.getOrDefault("LocationDetails.HeightmapServer.MotionBlocking",       "?");
                    String ml = data.getOrDefault("LocationDetails.HeightmapServer.MotionBlockingNoLeaves","?");
                    output.add("SH S: " + ws + " O: " + of + " M: " + mb + " ML: " + ml);
                }

                case "LocationDetails.Biome" -> {
                    String bio = data.getOrDefault("LocationDetails.Biome", "?");
                    output.add("Biome: " + bio);
                }

                case "LocationDetails.Difficulty" -> {
                    String ld = data.getOrDefault("LocationDetails.LocalDifficulty", "?");
                    String cd = data.getOrDefault("LocationDetails.ClampedDifficulty", "");
                    String day= data.getOrDefault("LocationDetails.Day", "");
                    String line = "Local Difficulty: " + ld;
                    if (!cd.isEmpty()) line += " // " + cd;
                    if (!day.isEmpty()) line += " (Day " + day + ")";
                    output.add(line);
                }

                case "LocationDetails.NoiseRouter" -> {
                    // labels in order
                    String[] labs = {"Temperature","Vegetation","Continents","Erosion",
                            "Depth","Ridges","PeaksValleys","InitialDensity","FinalDensity"};
                    StringBuilder sb = new StringBuilder("NoiseRouter");
                    for (String l : labs) {
                        String v = data.getOrDefault("LocationDetails.NoiseRouter." + l, "?");
                        sb.append(" ").append(l.charAt(0)).append(": ").append(v);
                    }
                    output.add(sb.toString());
                }

                case "LocationDetails.BiomeBuilder" -> {
                    String[] labs = {"PeaksValleys","Continentalness","Erosion","Temperature","Humidity"};
                    String[] codes= {"PV","C","E","T","H"};
                    StringBuilder sb = new StringBuilder("Biome builder");
                    for (int i = 0; i < labs.length; i++) {
                        String v = data.getOrDefault("LocationDetails.BiomeBuilder." + labs[i], "?");
                        sb.append(" ").append(codes[i]).append(": ").append(v);
                    }
                    output.add(sb.toString());
                }

                case "LocationDetails.MobCaps" -> {
                    String chunks   = data.getOrDefault("LocationDetails.MobCaps.Chunks",   "?");
                    String mons     = data.getOrDefault("LocationDetails.MobCaps.Monsters", "?");
                    String creat    = data.getOrDefault("LocationDetails.MobCaps.Creatures","?");
                    String ambient  = data.getOrDefault("LocationDetails.MobCaps.Ambient",  "?");
                    String axolotl  = data.getOrDefault("LocationDetails.MobCaps.Axolotls","?");
                    String underground = data.getOrDefault("LocationDetails.MobCaps.Underground","?");
                    String water    = data.getOrDefault("LocationDetails.MobCaps.Water",    "?");
                    String fish     = data.getOrDefault("LocationDetails.MobCaps.Fish",     "?");
                    String misc     = data.getOrDefault("LocationDetails.MobCaps.Misc",     "?");
                    output.add("SC: "  + chunks +
                            ", M: " + mons +
                            ", C: " + creat +
                            ", A: " + ambient +
                            ", A: " + axolotl +
                            ", U: " + underground +
                            ", W: " + water +
                            ", W: " + fish +
                            ", M: " + misc);
                }

                case "LocationDetails.Sounds" -> {
                    String st  = data.getOrDefault("LocationDetails.Sounds.Static",    "?");
                    String sm  = data.getOrDefault("LocationDetails.Sounds.StaticMax", "?");
                    String sr  = data.getOrDefault("LocationDetails.Sounds.Stream",    "?");
                    String srm = data.getOrDefault("LocationDetails.Sounds.StreamMax", "?");
                    String mood= data.getOrDefault("LocationDetails.Sounds.Mood",      "?");
                    output.add("Sounds: " + st + "/" + sm +
                            " + " + sr + "/" + srm +
                            " (Mood " + mood + "%)");
                }

                default -> {
                    // skip unknown keys
                }
            }
        }

        return output;
    }
}
