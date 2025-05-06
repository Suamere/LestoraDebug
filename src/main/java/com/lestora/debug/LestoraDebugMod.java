package com.lestora.debug;

import net.minecraft.ChatFormatting;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mod("lestora_debug")
public class LestoraDebugMod {
    public LestoraDebugMod(FMLJavaModLoadingContext constructContext) {
        defaultF3Configuration();
    }

    private void defaultF3Configuration() {
        DebugDataParser.registerHandler("MinecraftData.VersionInfo",       LestoraDebugMod::mcVersionInfo);
        DebugDataParser.registerHandler("MinecraftData.Renderer",          LestoraDebugMod::mcRenderer);
        DebugDataParser.registerHandler("MinecraftData.Server",            LestoraDebugMod::mcServer);
        DebugDataParser.registerHandler("MinecraftData.Chunks",            LestoraDebugMod::mcChunks);
        DebugDataParser.registerHandler("MinecraftData.Entities",          LestoraDebugMod::mcEntities);
        DebugDataParser.registerHandler("MinecraftData.Particles",         LestoraDebugMod::mcParticles);
        DebugDataParser.registerHandler("MinecraftData.ChunksClient",      LestoraDebugMod::mcChunksClient);
        DebugDataParser.registerHandler("MinecraftData.ChunksServer",      LestoraDebugMod::mcChunksServer);
        DebugDataParser.registerHandler("MinecraftData.Dimension",         LestoraDebugMod::mcDimension);
        DebugDataParser.registerHandler("LocationDetails.Position",        LestoraDebugMod::locPosition);
        DebugDataParser.registerHandler("LocationDetails.Block",           LestoraDebugMod::locBlock);
        DebugDataParser.registerHandler("LocationDetails.Chunk",           LestoraDebugMod::locChunk);
        DebugDataParser.registerHandler("LocationDetails.Facing",          LestoraDebugMod::locFacing);
        DebugDataParser.registerHandler("LocationDetails.Light",           LestoraDebugMod::locLight);
        DebugDataParser.registerHandler("LocationDetails.LocalDifficulty", LestoraDebugMod::locLocalDifficulty);
        DebugDataParser.registerHandler("LocationDetails.HeightmapClient", LestoraDebugMod::locHeightmapClient);
        DebugDataParser.registerHandler("LocationDetails.HeightmapServer", LestoraDebugMod::locHeightmapServer);
        DebugDataParser.registerHandler("LocationDetails.Biome",           LestoraDebugMod::locBiome);
        DebugDataParser.registerHandler("LocationDetails.NoiseRouter",     LestoraDebugMod::locNoiseRouter);
        DebugDataParser.registerHandler("LocationDetails.BiomeBuilder",    LestoraDebugMod::locBiomeBuilder);
        DebugDataParser.registerHandler("LocationDetails.MobCaps",         LestoraDebugMod::locMobCaps);
        DebugDataParser.registerHandler("LocationDetails.Sounds",          LestoraDebugMod::locSounds);

        DebugDataParser.registerHandler("System.Java",                     LestoraDebugMod::sysJava);
        DebugDataParser.registerHandler("System.Memory",                   LestoraDebugMod::sysMemory);
        DebugDataParser.registerHandler("System.AllocationRate",           LestoraDebugMod::sysAllocationRate);
        DebugDataParser.registerHandler("System.Allocated",                LestoraDebugMod::sysAllocated);
        DebugDataParser.registerHandler("System.CPU",                      LestoraDebugMod::sysCPU);
        DebugDataParser.registerHandler("System.Display",                  LestoraDebugMod::sysDisplay);
        DebugDataParser.registerHandler("System.Renderer",                 LestoraDebugMod::sysRenderer);
        DebugDataParser.registerHandler("System.OpenGLVersion",            LestoraDebugMod::sysOpenGLVersion);

        DebugDataParser.registerHandler("TargetBlock.Coords",              (line, emitter) -> targetCoords(line, "Block", emitter));
        DebugDataParser.registerHandler("TargetBlock.ResourceLocation",    LestoraDebugMod::targetResourceLocation);
        DebugDataParser.registerHandler("TargetBlock.States",              LestoraDebugMod::targetStates);
        DebugDataParser.registerHandler("TargetBlock.Tags",                LestoraDebugMod::targetBlockTags);

        DebugDataParser.registerHandler("TargetFluid.Coords",              (line, emitter) -> targetCoords(line, "Fluid", emitter));
        DebugDataParser.registerHandler("TargetFluid.ResourceLocation",    LestoraDebugMod::targetResourceLocation);
        DebugDataParser.registerHandler("TargetFluid.States",              LestoraDebugMod::targetStates);
        DebugDataParser.registerHandler("TargetFluid.Tags",                LestoraDebugMod::targetBlockTags);

        DebugDataParser.registerHandler("TargetEntity.Coords",             (line, emitter) -> targetCoords(line, "Entity", emitter));
        DebugDataParser.registerHandler("TargetEntity.ResourceLocation",   LestoraDebugMod::targetResourceLocation);
        DebugDataParser.registerHandler("TargetEntity.States",             LestoraDebugMod::targetStates);
        DebugDataParser.registerHandler("TargetEntity.Tags",               LestoraDebugMod::targetBlockTags);

        //DebugDataParser.rebuilderMap.put("MyKey", data -> Collections.singletonList("Hello" + LocalDateTime.now()));
    }

    private static Function<Map<String, String>, List<String>> mcVersionInfo(String line, BiConsumer<String, String> emit) {
        Matcher m = Pattern.compile("^Minecraft\\s+(\\S+)(?:\\s+\\(([^)]+)\\))?").matcher(line);
        if (m.find()) {
            String version = m.group(1);
            emit.accept("VersionNumber", version);

            String modName = m.group(2);
            if (modName != null && !modName.isEmpty()) {
                emit.accept("ModName", modName);
            }
        }
        else {
            System.err.println("Regex couldn't match to MinecraftData.VersionInfo line");
        }

        return (data) -> {
            String ver = data.get("VersionNumber");
            String mod = data.get("ModName");

            if (ver == null && mod == null) {
                return null;
            }

            StringBuilder sb = new StringBuilder("Minecraft");
            if (ver != null) {
                sb.append(" ").append(ver);
            }
            if (mod != null) {
                sb.append(" (").append(mod).append(")");
            }
            return Collections.singletonList(sb.toString());
        };
    }

    private static Function<Map<String, String>, List<String>> mcRenderer(String line, BiConsumer<String, String> emit) {
        // e.g. "60 fps T: 120 vsync fancy fancy-clouds B: 2 GPU: 20%"
        String[] tok = line.split("\\s+");
        emit.accept("FPS", tok[0]);

        // Find the T: and B: indices
        int tIdx = -1, bIdx = -1, gpuIdx = -1;
        for (int i = 1; i < tok.length; i++) {
            if ("T:".equals(tok[i])) tIdx = i;
            else if ("B:".equals(tok[i])) bIdx = i;
            else if ("GPU:".equals(tok[i])) gpuIdx = i;
        }

        if (tIdx >= 0 && tIdx + 1 < tok.length) {
            emit.accept("TickTime", tok[tIdx + 1]);
        }

        // SomeCategory: everything between T:<value> and B:<value>
        if (tIdx >= 0 && bIdx > tIdx + 1) {
            StringBuilder cat = new StringBuilder();
            for (int i = tIdx + 2; i < bIdx; i++) {
                if (!cat.isEmpty()) cat.append(" ");
                cat.append(tok[i]);
            }
            emit.accept("Options", cat.toString());
        }

        if (bIdx >= 0 && bIdx + 1 < tok.length) {
            emit.accept("BiomeBlend", tok[bIdx + 1]);
        }

        if (gpuIdx >= 0 && gpuIdx + 1 < tok.length) {
            emit.accept("GPU", tok[gpuIdx + 1].replace("%", ""));
        }

        return (data) -> {
            String fps    = data.get("FPS");
            String tick   = data.get("TickTime");
            String options  = data.get("Options");
            String blend  = data.get("BiomeBlend");
            String gpu    = data.get("GPU");

            if (fps == null && tick == null
                    && !StringUtils.isBlank(options)
                    && blend == null && gpu == null) {
                return null;
            }

            List<String> parts = new ArrayList<>();
            if (fps != null)    parts.add(fps + " fps");
            if (tick != null)   parts.add("T: " + tick);
            if (!StringUtils.isBlank(options)) parts.add(options);
            if (blend != null) parts.add("B: " + blend);
            if (gpu != null)   parts.add("GPU: " + gpu + "%");

            return Collections.singletonList(String.join(" ", parts));
        };
    }

    private static Function<Map<String, String>, List<String>> mcServer(String line, BiConsumer<String, String> emit) {
        // "Integrated server @ 3.1/50.0 ms, 22 tx, 1053 rx"
        String[] at = line.split("@");
        emit.accept("Brand", at[0].replace("Integrated server","").trim());
        String[] lineParts = at[1].split(",");
        String timing = lineParts[0].replace("ms","").trim();
        String[] times = timing.split("/");
        emit.accept("TickTimeMs", times[0]);
        emit.accept("TicksPerSecond", times[1]);
        emit.accept("PacketsSent", lineParts[1].trim().split(" ")[0]);
        emit.accept("PacketsReceived", lineParts[2].trim().split(" ")[0]);

        return (data) -> {
            String brandKey = data.get("Brand");
            String defaultLabel = "Integrated server";
            String label = (brandKey != null && !brandKey.isBlank()) ? brandKey : defaultLabel;

            String tms  = data.get("TickTimeMs");
            String tps  = data.get("TicksPerSecond");
            String sent = data.get("PacketsSent");
            String recv = data.get("PacketsReceived");

            List<String> parts = new ArrayList<>();
            if (tms != null && tps != null) parts.add(tms + "/" + tps + " ms");
            if (sent != null)               parts.add(sent + " tx");
            if (recv != null)               parts.add(recv + " rx");

            // skip entire line if neither a custom brand nor any parts exist
            if ((brandKey == null || brandKey.isBlank()) && parts.isEmpty()) {
                return null;
            }

            // build the output
            StringBuilder sb = new StringBuilder(label);
            if (!parts.isEmpty()) {
                sb.append(" @ ").append(String.join(", ", parts));
            }
            return Collections.singletonList(sb.toString());
        };
    }

    private static Function<Map<String, String>, List<String>> mcChunks(String line, BiConsumer<String, String> emit) {
        // "C: 305/15000 (s) D: 12, pC: 000, pU: 00, aB: 16"
        String[] p = line.split("\\s+");
        String[] ct = p[1].split("/");
        emit.accept("SectionsRendered", ct[0]);
        emit.accept("SectionsTotal",    ct[1]);
        for (int j = 2; j < p.length; j++) {
            switch(p[j]) {
                case "D:"  -> emit.accept("RenderDistance",   p[j+1].replace(",",""));
                case "pC:" -> emit.accept("PendingBatch",     p[j+1].replace(",",""));
                case "pU:" -> emit.accept("PendingUploads",   p[j+1].replace(",",""));
                case "aB:" -> emit.accept("AvailableBuffers", p[j+1]);
            }
        }

        return (data) -> {
            String rs = data.get("SectionsRendered");
            String ts = data.get("SectionsTotal");
            String rd = data.get("RenderDistance");
            String pc = data.get("PendingBatch");
            String pu = data.get("PendingUploads");
            String ab = data.get("AvailableBuffers");

            List<String> parts = new ArrayList<>();
            // render count pair
            if (rs != null && ts != null) {
                parts.add("C: " + rs + "/" + ts + " (s)");
            }
            // optional details
            if (rd != null) parts.add("D: " + rd);
            if (pc != null) parts.add("pC: " + pc);
            if (pu != null) parts.add("pU: " + pu);
            if (ab != null) parts.add("aB: " + ab);

            // only output if there's something to show
            if (parts.isEmpty()) {
                return null;
            }

            return Collections.singletonList(String.join(", ", parts));
        };
    }

    private static Function<Map<String, String>, List<String>> mcEntities(String line, BiConsumer<String, String> emit) {
        // "E: 3/127, SD: 12"
        String entityPart = line.substring(0, line.indexOf(',')).trim();       // "E: 3/127"
        String[] counts     = entityPart.substring(entityPart.indexOf(' ')+1).split("/");
        emit.accept("Rendered", counts[0]);
        emit.accept("Total",    counts[1]);
        int sdIdx = line.indexOf("SD:");
        emit.accept("SimulationDistance", line.substring(sdIdx+3).trim());

        return (data) -> {
            String rend = data.get("Rendered");
            String tot  = data.get("Total");
            String sd   = data.get("SimulationDistance");

            // skip only if nothing is present
            if (rend == null && tot == null && sd == null) {
                return null;
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

            return Collections.singletonList(sb.toString());
        };
    }

    private static Function<Map<String, String>, List<String>> mcParticles(String line, BiConsumer<String, String> emit) {
        // "P: 1270. T: 127"
        String[] parts = line.split("\\s+");
        emit.accept("Count",     parts[1].replace(".",""));
        emit.accept("TickValue", parts[3]);

        return (data) -> {
            String cnt = data.get("Count");
            String tv  = data.get("TickValue");

            // skip if neither count nor tick-value is present
            if (cnt == null && tv == null) {
                return null;
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
            return Collections.singletonList(sb.toString());
        };
    }

    private static Function<Map<String, String>, List<String>> mcChunksClient(String line, BiConsumer<String, String> emit) {
        // "Chunks[C] W: 961, 637 E: 127,76,637"
        String wPart = line.substring(line.indexOf("W:")+2, line.indexOf("E:")).trim();
        String ePart = line.substring(line.indexOf("E:")+2).trim();
        String[] wc = wPart.split(",");
        String[] ec = ePart.split(",");
        emit.accept("Cached",        wc[0].trim());
        emit.accept("Loaded",        wc[1].trim());
        if (ec.length >= 3) {
            emit.accept("Entities",       ec[0].trim());
            emit.accept("EntitySections", ec[1].trim());
            emit.accept("Ticking",        ec[2].trim());
        }

        return (data) -> {
            String c  = data.get("Cached");
            String l  = data.get("Loaded");
            String e1 = data.get("Entities");
            String e2 = data.get("EntitySections");
            String t  = data.get("Ticking");

            // skip if absolutely nothing is present
            if (c == null && l == null && e1 == null && e2 == null && t == null) {
                return null;
            }

            List<String> parts = new ArrayList<>();
            if (c != null) parts.add("W: " + c);
            if (l != null) parts.add(l); // "L: " + l, if we need extra description

            // combine entities/sections/ticking into one E: token if any present
            List<String> entParts = new ArrayList<>();
            if (e1 != null) entParts.add(e1);
            if (e2 != null) entParts.add(e2);
            if (t  != null) entParts.add(t);
            if (!entParts.isEmpty()) {
                parts.add("E: " + String.join(",", entParts));
            }

            return Collections.singletonList("Chunks[C] " + String.join(", ", parts));
        };
    }

    private static Function<Map<String, String>, List<String>> mcChunksServer(String line, BiConsumer<String, String> emit) {
        // "Chunks[S] W: 3338 E: 173,103,890,890,0,0"
        String wVal = line.substring(line.indexOf("W:")+2, line.indexOf("E:")).trim();
        emit.accept("World", wVal);
        String[] es = line.substring(line.indexOf("E:")+2).split(",");
        if (es.length >= 6) {
            emit.accept("Entities", es[0].trim());
            emit.accept("Visible",  es[1].trim());
            emit.accept("Sections", es[2].trim());
            emit.accept("Loaded",   es[3].trim());
            emit.accept("Ticking",  es[4].trim());
            emit.accept("ToLoad",   es[5].trim());
            if (es.length > 6) emit.accept("ToUnload", es[6].trim());
        }

        return (data) -> {
            String w  = data.get("World");
            String e  = data.get("Entities");
            String vis= data.get("Visible");
            String sec= data.get("Sections");
            String ld = data.get("Loaded");
            String tk = data.get("Ticking");
            String tl = data.get("ToLoad");
            String tu = data.get("ToUnload");

            // only output if at least one value is present
            if (w != null || e != null || vis != null || sec != null ||
                    ld != null || tk != null || tl != null || tu != null) {

                var dubya = "";
                var eee = "";
                var hasE = false;
                List<String> parts = new ArrayList<>();
                if (e   != null) {parts.add(e); hasE = true;}
                if (vis != null) {parts.add(vis); hasE = true;}
                if (sec != null) {parts.add(sec); hasE = true;}
                if (ld  != null) {parts.add(ld); hasE = true;}
                if (tk  != null) {parts.add(tk); hasE = true;}
                if (tl  != null) {parts.add(tl); hasE = true;}
                if (tu  != null) {parts.add(tu); hasE = true;}
                if (hasE) eee = " E: " + String.join(",", parts);

                return Collections.singletonList("Chunks[S] " + dubya + eee);
            }

            return null;
        };
    }

    private static Function<Map<String, String>, List<String>> mcDimension(String line, BiConsumer<String, String> emit) {
        // "minecraft:overworld FC: 0"
        String[] parts = line.split("\\s+");
        emit.accept("ID", parts[0]);
        emit.accept("ForceLoadedChunks", parts[2]);

        return (data) -> {
            String id = data.get("ID");
            String fc = data.get("ForceLoadedChunks");

            // skip if neither ID nor FC is present
            if (id == null && fc == null) {
                return null;
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

            return Collections.singletonList(sb.toString());
        };
    }

    private static Function<Map<String, String>, List<String>> locPosition(String line, BiConsumer<String, String> emit) {
        // "XYZ: -123.000 / 64.000 / -123.000"
        String[] xyz = line.substring(5).split("/");
        emit.accept("X", xyz[0].trim());
        emit.accept("Y", xyz[1].trim());
        emit.accept("Z", xyz[2].trim());

        return (data) -> {
            String x = data.get("X");
            String y = data.get("Y");
            String z = data.get("Z");

            // skip if absolutely nothing is present
            if (x == null && y == null && z == null) {
                return null;
            }

            // build each coordinate or "?" if missing
            String xx = x != null ? x : "?";
            String yy = y != null ? y : "?";
            String zz = z != null ? z : "?";

            return Collections.singletonList("XYZ: " + xx + " / " + yy + " / " + zz);
        };
    }

    private static Function<Map<String, String>, List<String>> locBlock(String line, BiConsumer<String, String> emit) {
        // "Block: -124 64 -124 [1 2 3]"
        String world = line.substring(7, line.indexOf("[")).trim();
        String rel   = line.substring(line.indexOf("[")+1, line.indexOf("]")).trim();
        String[] w   = world.split(" ");
        String[] r   = rel.split(" ");
        emit.accept("WorldX", w[0]);
        emit.accept("WorldY", w[1]);
        emit.accept("WorldZ", w[2]);
        emit.accept("RelativeX", r[0]);
        emit.accept("RelativeY", r[1]);
        emit.accept("RelativeZ", r[2]);

        return (data) -> {
            String wx = data.get("WorldX");
            String wy = data.get("WorldY");
            String wz = data.get("WorldZ");
            String rx = data.get("RelativeX");
            String ry = data.get("RelativeY");
            String rz = data.get("RelativeZ");

            // skip if absolutely nothing is present
            if (wx == null && wy == null && wz == null
                    && rx == null && ry == null && rz == null) {
                return null;
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
            return Collections.singletonList(sb.toString());
        };
    }

    private static Function<Map<String, String>, List<String>> locChunk(String line, BiConsumer<String, String> emit) {
        // "Chunk: -9 4 -9 [14 20 in r.-1.-1.mca]"
        String coord = line.substring(7, line.indexOf("[")).trim();
        String detail= line.substring(line.indexOf("[")+1, line.indexOf("]")).trim();
        String[] c    = coord.split(" ");
        emit.accept("WorldX", c[0]);
        emit.accept("WorldY", c[1]);
        emit.accept("WorldZ", c[2]);
        String[] dt   = detail.split(" in ");
        String[] rc   = dt[0].split(" ");
        emit.accept("RelativeX", rc[0]);
        emit.accept("RelativeZ", rc[1]);
        if (dt.length>1) emit.accept("RegionFile", dt[1]);

        return (data) -> {
            String wx = data.get("WorldX");
            String wy = data.get("WorldY");
            String wz = data.get("WorldZ");
            String rx = data.get("RelativeX");
            String rz = data.get("RelativeZ");
            String rf = data.get("RegionFile");

            // skip only if absolutely nothing is present
            if (wx == null && wy == null && wz == null
                    && rx == null && rz == null && rf == null) {
                return null;
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

            return Collections.singletonList(sb.toString());
        };
    }

    private static Function<Map<String, String>, List<String>> locFacing(String line, BiConsumer<String, String> emit) {
        // "Facing: south (Towards positive Z) (1.5 / 66.8)"
        String[] seg = line.split("\\(");
        emit.accept("Compass", seg[0].split(":")[1].trim());
        emit.accept("Toward", seg[1].replace(")","").replace("Towards","").trim());
        emit.accept("HeadYaw", seg[2].replace(")","").trim());

        return (data) -> {
            String c = data.get("Compass");
            String t = data.get("Toward");
            String h = data.get("HeadYaw");
            // skip entirely if nothing is present
            if (c == null && t == null && h == null) {
                return null;
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
            return Collections.singletonList(sb.toString());
        };
    }

    private static Function<Map<String, String>, List<String>> locLight(String line, BiConsumer<String, String> emit) {
        if (line.contains("Waiting for chunk")) { return (x) -> Collections.singletonList(line); }
        // "Client Light: 15 (15 sky, 9 block)"
        String totx = line.substring(13,line.indexOf("(")).trim();
        emit.accept("Total", totx);
        String sub = line.substring(line.indexOf("(")+1, line.indexOf(")"));
        String[] sp = sub.split(",");
        emit.accept("Sky",   sp[0].replace("sky","").trim());
        emit.accept("Block", sp[1].replace("block","").trim());

        return (data) -> {
            String tot = data.get("Total");
            String sky = data.get("Sky");
            String blk = data.get("Block");

            // only skip if *all* three are missing
            if (tot == null && sky == null && blk == null) {
                return null; // nothing to print
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
            return Collections.singletonList(sb.toString());
        };
    }

    private static Function<Map<String, String>, List<String>> locLocalDifficulty(String line, BiConsumer<String, String> emit) {
        if (line.contains("Waiting for chunk")) { return (x) -> Collections.singletonList(line); }

        // drop the prefix
        String rest = line.substring("Local Difficulty:".length()).trim();
        // handle the "??" case early
        if (rest.equals("??")) {
            emit.accept("Numerator", "??");
        } else {
            // rest should look like "0.75 // 0.00 (Day 0)"
            String[] halves = rest.split("//", 2);
            // left of "//" = raw local diff
            String local = halves[0].trim();
            emit.accept("Numerator", local);

            if (halves.length > 1) {
                // right of "//" = clamped + day
                String right = halves[1].trim();            // e.g. "0.00 (Day 0)"
                // clamped is the first token
                String[] tokens = right.split("\\s+", 2);
                emit.accept("Denominator", tokens[0]);

                if (tokens.length > 1) {
                    // tokens[1] should be "(Day 0)" or similar
                    String dayPart = tokens[1].trim();
                    if (dayPart.startsWith("(Day") && dayPart.endsWith(")")) {
                        // extract the number between "Day" and ")"
                        String dayNum = dayPart.substring(4, dayPart.length() - 1).trim();
                        emit.accept("Day", dayNum);
                    }
                }
            }
        }

        return (data) -> {
            String ld  = data.get("Numerator");
            String cd  = data.get("Denominator");
            String day = data.get("Day");
            // skip if nothing present
            if (ld == null && cd == null && day == null) {
                return null;
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
            return Collections.singletonList(sb.toString());
        };
    }

    private static Function<Map<String, String>, List<String>> locHeightmapClient(String line, BiConsumer<String, String> emit) {
        if (line.contains("Waiting for chunk")) { return (x) -> Collections.singletonList(line); }
        // "CH S: 63 M: 63"
        String[] p = line.split("\\s+");
        emit.accept("WorldSurface",    p[2]);
        emit.accept("MotionBlocking", p[4]);

        return (data) -> {
            String ws = data.get("WorldSurface");
            String mb = data.get("MotionBlocking");

            // only skip if both are missing
            if (ws == null && mb == null) {
                return null;
            }

            StringBuilder sb = new StringBuilder("CH");
            if (ws != null) {
                sb.append(" S: ").append(ws);
            }
            if (mb != null) {
                sb.append(" M: ").append(mb);
            }
            return Collections.singletonList(sb.toString());
        };
    }

    private static Function<Map<String, String>, List<String>> locHeightmapServer(String line, BiConsumer<String, String> emit) {
        if (line.contains("Waiting for chunk")) { return (x) -> Collections.singletonList(line); }
        // "SH S: 63 O: 63 M: 63 ML: 63"
        String[] p = line.split("\\s+");
        emit.accept("WorldSurface",    p[2]);
        emit.accept("OceanFloor",      p[4]);
        emit.accept("MotionBlocking",  p[6]);
        emit.accept("MotionBlockingNoLeaves", p[8]);

        return (data) -> {
            String ws = data.get("WorldSurface");
            String of = data.get("OceanFloor");
            String mb = data.get("MotionBlocking");
            String ml = data.get("MotionBlockingNoLeaves");
            // skip if nothing present
            if (ws == null && of == null && mb == null && ml == null) {
                return null;
            }
            StringBuilder sb = new StringBuilder("SH");
            if (ws != null) sb.append(" S: ").append(ws);
            if (of != null) sb.append(" O: ").append(of);
            if (mb != null) sb.append(" M: ").append(mb);
            if (ml != null) sb.append(" ML: ").append(ml);
            return Collections.singletonList(sb.toString());
        };
    }

    private static Function<Map<String, String>, List<String>> locBiome(String line, BiConsumer<String, String> emit) {
        if (line.contains("Waiting for chunk")) { return (x) -> Collections.singletonList(line); }
        emit.accept("LocationDetails.Biome", line.substring(7).trim());

        return (data) -> {
            String bio = data.get("LocationDetails.Biome");
            if (bio == null) {
                return null;
            }
            return Collections.singletonList("Biome: " + bio);
        };
    }

    private static Function<Map<String, String>, List<String>> locNoiseRouter(String line, BiConsumer<String, String> emit) {
        String[] tok = line.split("\\s+");
        for (int k = 1; k < tok.length; k+=2) {
            String key = tok[k].replace(":", "");
            String val = tok[k+1];
            switch (key) {
                case "T"  -> emit.accept("Temperature", val);
                case "V"  -> emit.accept("Vegetation", val);
                case "C"  -> emit.accept("Continents", val);
                case "E"  -> emit.accept("Erosion", val);
                case "D"  -> emit.accept("Depth", val);
                case "W"  -> emit.accept("Ridges", val);
                case "PV" -> emit.accept("PeaksValleys", val);
                case "AS" -> emit.accept("InitialDensity", val);
                case "N"  -> emit.accept("FinalDensity", val);
            }
        }

        return (data) -> {
            List<String> parts = new ArrayList<>();
            String t = data.get("Temperature");
            if (t != null) parts.add("T: " + t);
            String v = data.get("Vegetation");
            if (v != null) parts.add("V: " + v);
            String c = data.get("Continents");
            if (c != null) parts.add("C: " + c);
            String e = data.get("Erosion");
            if (e != null) parts.add("E: " + e);
            String d = data.get("Depth");
            if (d != null) parts.add("D: " + d);
            String w = data.get("Ridges");
            if (w != null) parts.add("W: " + w);
            String pv = data.get("PeaksValleys");
            if (pv != null) parts.add("PV: " + pv);
            String as = data.get("InitialDensity");
            if (as != null) parts.add("AS: " + as);
            String n = data.get("FinalDensity");
            if (n != null) parts.add("N: " + n);

            if (parts.isEmpty()) {
                return null;
            }
            return Collections.singletonList("NoiseRouter " + String.join(" ", parts));
        };
    }

    private static Function<Map<String, String>, List<String>> locBiomeBuilder(String line, BiConsumer<String, String> emit) {
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
                case "PV:" -> emit.accept("PeaksValleys", val);
                case "C:"  -> emit.accept("Continentalness", val);
                case "E:"  -> emit.accept("Erosion", val);
                case "T:"  -> emit.accept("Temperature", val);
                case "H:"  -> emit.accept("Humidity", val);
            }
        }

        return (data) -> {
            String[] labs  = {"PeaksValleys","Continentalness","Erosion","Temperature","Humidity"};
            String[] codes = {"PV","C","E","T","H"};
            List<String> parts = new ArrayList<>();
            for (int idx = 0; idx < labs.length; idx++) {
                String v = data.get(labs[idx]);
                if (v != null) {
                    parts.add(codes[idx] + ": " + v);
                }
            }
            if (parts.isEmpty()) {
                return null;
            }
            return Collections.singletonList("Biome builder " + String.join(" ", parts));
        };
    }

    private static Function<Map<String, String>, List<String>> locMobCaps(String line, BiConsumer<String, String> emit) {
        String[] tok = line.replace(",", "").split("\\s+");
        int aCount = 0, wCount = 0, mCount = 0;
        for (int k = 0; k < tok.length; k+=2) {
            String key = tok[k].replace(":", "");
            String val = tok[k+1];
            switch (key) {
                case "SC" -> emit.accept("Chunks", val);
                case "M"  -> {
                    if (mCount==0) emit.accept("Monsters", val);
                    else           emit.accept("Misc", val);
                    mCount++;
                }
                case "C"  -> emit.accept("Creatures", val);
                case "A"  -> {
                    if (aCount==0) emit.accept("Ambient", val);
                    else           emit.accept("Axolotls", val);
                    aCount++;
                }
                case "U"  -> emit.accept("Underground", val);
                case "W"  -> {
                    if (wCount==0) emit.accept("Water", val);
                    else           emit.accept("Fish", val);
                    wCount++;
                }
            }
        }

        return (data) -> {
            List<String> parts = new ArrayList<>();
            Optional.ofNullable(data.get("Chunks"))
                    .ifPresent(v -> parts.add("SC: " + v));
            Optional.ofNullable(data.get("Monsters"))
                    .ifPresent(v -> parts.add("M: " + v));
            Optional.ofNullable(data.get("Creatures"))
                    .ifPresent(v -> parts.add("C: " + v));
            Optional.ofNullable(data.get("Ambient"))
                    .ifPresent(v -> parts.add("A: " + v));
            Optional.ofNullable(data.get("Axolotls"))
                    .ifPresent(v -> parts.add("A: " + v));
            Optional.ofNullable(data.get("Underground"))
                    .ifPresent(v -> parts.add("U: " + v));
            Optional.ofNullable(data.get("Water"))
                    .ifPresent(v -> parts.add("W: " + v));
            Optional.ofNullable(data.get("Fish"))
                    .ifPresent(v -> parts.add("W: " + v));
            Optional.ofNullable(data.get("Misc"))
                    .ifPresent(v -> parts.add("M: " + v));
            if (parts.isEmpty()) {
                return null;
            }
            return Collections.singletonList(String.join(", ", parts));
        };
    }

    private static Function<Map<String, String>, List<String>> locSounds(String line, BiConsumer<String, String> emit) {
        String[] partsx = line.split("\\s+");
        String[] stx = partsx[1].split("/");
        emit.accept("Static", stx[0]);
        emit.accept("StaticMax", stx[1]);
        String[] srx = partsx[3].split("/");
        emit.accept("Stream", srx[0]);
        emit.accept("StreamMax", srx[1]);
        String moodx = partsx[5].replace("(", "").replace(")", "").replace("%", "");
        emit.accept("Mood", moodx);

        return (data) -> {
            String st  = data.get("Static");
            String sm  = data.get("StaticMax");
            String sr  = data.get("Stream");
            String srm = data.get("StreamMax");
            String mood= data.get("Mood");

            // skip if nothing present
            if (st == null && sm == null && sr == null && srm == null && mood == null) {
                return null;
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
            return Collections.singletonList("Sounds: " + String.join(" + ", parts));
        };
    }

    private static Function<Map<String, String>, List<String>> sysJava(String line, BiConsumer<String, String> emit) {

        // e.g. "Java: 21.0.6" or "Java: 17.0.2 (64bit)"
        String rest = line.substring("Java:".length()).trim();
        // If it contains "(XXbit)", split that out:
        if (rest.contains("(") && rest.endsWith("bit)")) {
            int idx = rest.indexOf('(');
            String version = rest.substring(0, idx).trim();              // "17.0.2"
            String bits    = rest.substring(idx+1, rest.length()-1);     // "64bit"
            emit.accept("Version", version);
            emit.accept("Bits",    bits);
        } else {
            // no bits-info, just version
            emit.accept("Version", rest);
        }

        return (data) -> {
            String version = data.get("Version");
            String bits    = data.get("Bits");
            // skip entirely if neither present
            if (version == null && bits == null) return null;

            StringBuilder sb = new StringBuilder("Java");
            if (version != null) {
                sb.append(": ").append(version);
            }
            if (bits != null) {
                // if no version, still need colon
                if (version == null) sb.append(":");
                sb.append(" (").append(bits).append(")");
            }
            return Collections.singletonList(sb.toString());
        };
    }

    private static Function<Map<String, String>, List<String>> sysMemory(String line, BiConsumer<String, String> emit) {

        // Example: "Mem: 45% 512/1024"
        String[] parts = line.split("[ %/]+");
        // parts = ["Mem:", "45", "512", "1024"]
        if (parts.length >= 4) {
            emit.accept("UsedPercent", parts[1]);
            emit.accept("Used", parts[2]);
            emit.accept("Total", parts[3]);
        }

        return (data) -> {
            String up = data.get("UsedPercent");
            String u  = data.get("Used");
            String t  = data.get("Total");
            // skip if nothing
            if (up == null && u == null && t == null) return null;

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
            return Collections.singletonList(sb.toString());
        };
    }

    private static Function<Map<String, String>, List<String>> sysAllocationRate(String line, BiConsumer<String, String> emit) {

        // Example: "Allocation rate: 5.0 MiB/s"
        String[] parts = line.split("\\s+");
        if (parts.length >= 3) {
            emit.accept("AllocationRate", parts[2]);
        }

        return (data) -> {
            String rate = data.get("AllocationRate");
            if (rate != null) {
                return Collections.singletonList("Allocation rate: " + rate);
            }
            return null;
        };
    }

    private static Function<Map<String, String>, List<String>> sysAllocated(String line, BiConsumer<String, String> emit) {

        // drop the prefix and split on whitespace
        String rest = line.substring("Allocated:".length()).trim();
        String[] tok = rest.split("\\s+");
        // 1) percent (always there)
        if (tok.length >= 1) {
            String pct = tok[0].endsWith("%")
                    ? tok[0].substring(0, tok[0].length()-1)
                    : tok[0];
            emit.accept("AllocatedPercent", pct);
        }
        // 2) memory token (either “used/total” or “usedMB”)
        if (tok.length >= 2) {
            String mem = tok[1];
            if (mem.contains("/")) {
                String[] uv = mem.split("/", 2);
                emit.accept("Allocated",      uv[0]);
                emit.accept("AllocatedTotal", uv[1]);
            } else {
                // e.g. "1072MB" → treat whole string as "Allocated"
                emit.accept("Allocated", mem);
                // no total in this format, so we skip AllocatedTotal
            }
        }

        return (data) -> {
            String ap = data.get("AllocatedPercent");
            String au = data.get("Allocated");
            String at = data.get("AllocatedTotal");
            if (ap == null && au == null && at == null) return null;

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
            return Collections.singletonList(sb.toString());
        };
    }

    private static Function<Map<String, String>, List<String>> sysCPU(String line, BiConsumer<String, String> emit) {

        // Example: "CPU: 8 Intel(R) Core(TM)..."
        String[] parts = line.split("\\s+", 3);
        if (parts.length >= 2) {
            emit.accept("Cores", parts[1]);
            if (parts.length >= 3) {
                emit.accept("Name", parts[2]);
            }
        }

        return (data) -> {
            String cores = data.get("Cores");
            String name  = data.get("Name");
            if (cores == null && name == null) return null;

            StringBuilder sb = new StringBuilder("CPU");
            if (cores != null) sb.append(": ").append(cores);
            if (name  != null) sb.append(cores != null ? " " : ": ").append(name);
            return Collections.singletonList(sb.toString());
        };
    }

    private static Function<Map<String, String>, List<String>> sysDisplay(String line, BiConsumer<String, String> emit) {

        // 1) parse the Display: resolution/vendor as before
        Matcher dispMatch = Pattern.compile("Display:\\s*(\\S+)\\s*\\(([^)]+)\\)")
                .matcher(line);
        if (dispMatch.find()) {
            emit.accept("Resolution", dispMatch.group(1));
            emit.accept("Vendor",     dispMatch.group(2));
        }

        return (data) -> {
            String res    = data.get("Resolution");
            String vendor = data.get("Vendor");
            if (res == null && vendor == null) return null;

            StringBuilder sb = new StringBuilder("Display");
            if (res != null) {
                sb.append(": ").append(res);
            }
            if (vendor != null) {
                // if no resolution, need colon
                if (res == null) sb.append(":");
                sb.append(" (").append(vendor).append(")");
            }
            return Collections.singletonList(sb.toString());
        };
    }

    private static Function<Map<String, String>, List<String>> sysRenderer(String line, BiConsumer<String, String> emit) {

        emit.accept("Renderer", line);

        return (data) -> {
            String renderer = data.get("Renderer");
            if (renderer != null) {
                return Collections.singletonList(renderer);
            }
            return null;
        };
    }

    private static Function<Map<String, String>, List<String>> sysOpenGLVersion(String line, BiConsumer<String, String> emit) {

        emit.accept("OpenGLVersion", line);

        return (data) -> {
            String version = data.get("OpenGLVersion");
            if (version != null) {
                return Collections.singletonList(version);
            }
            return null;
        };
    }








    private static Function<Map<String, String>, List<String>> targetCoords(String line, String type, BiConsumer<String, String> emit) {
        emit.accept("Coords", line);

        return (data) -> {
            String coords = data.get("Coords");
            if (coords == null) coords = "";
            else coords = ": " + coords;

            return Collections.singletonList(ChatFormatting.UNDERLINE + "Targeted " + type + coords);
        };
    }

    private static Function<Map<String, String>, List<String>> targetResourceLocation(String line, BiConsumer<String, String> emit) {
        emit.accept("ResourceLocation", line);
        return (data) -> Collections.singletonList(data.get("ResourceLocation"));
    }

    private static Function<Map<String, String>, List<String>> targetStates(String line, BiConsumer<String, String> emit) {
        emit.accept("States", line);

        return (data) -> {
            String states = data.get("States");
            if (states == null || states.isBlank()) return null;

            return List.of(states.split(";"));
        };
    }

    private static Function<Map<String, String>, List<String>> targetBlockTags(String line, BiConsumer<String, String> emit) {
        emit.accept("Tags", line);

        return (data) -> {
            String tags = data.get("Tags");
            if (tags == null || tags.isBlank()) return null;

            return List.of(tags.split(";"));
        };
    }
}