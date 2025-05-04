package com.lestora.debug;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
        DebugDataParser.registerHandler("LocationDetails.Difficulty",      LestoraDebugMod::locDifficulty);
        DebugDataParser.registerHandler("LocationDetails.NoiseRouter",     LestoraDebugMod::locNoiseRouter);
        DebugDataParser.registerHandler("LocationDetails.BiomeBuilder",    LestoraDebugMod::locBiomeBuilder);
        DebugDataParser.registerHandler("LocationDetails.MobCaps",         LestoraDebugMod::locMobCaps);
        DebugDataParser.registerHandler("LocationDetails.Sounds",          LestoraDebugMod::locSounds);
    }

    private static Function<Map<String, String>, String> mcVersionInfo(String lineKey, String line, BiConsumer<String, String> emit) {
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
            return sb.toString();
        };
    }

    private static Function<Map<String, String>, String> mcRenderer(String lineKey, String line, BiConsumer<String, String> emit) {
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

            return String.join(" ", parts);
        };
    }

    private static Function<Map<String, String>, String> mcServer(String lineKey, String line, BiConsumer<String, String> emit) {

        // "Integrated server @ 3.1/50.0 ms, 22 tx, 1053 rx"
        String[] at = line.split("@");
        emit.accept("MinecraftData.Server.Brand", at[0].replace("Integrated server","").trim());
        String[] lineParts = at[1].split(",");
        String timing = lineParts[0].replace("ms","").trim();
        String[] times = timing.split("/");
        emit.accept("MinecraftData.Server.TickTimeMs", times[0]);
        emit.accept("MinecraftData.Server.TicksPerSecond", times[1]);
        emit.accept("MinecraftData.Server.PacketsSent", lineParts[1].trim().split(" ")[0]);
        emit.accept("MinecraftData.Server.PacketsReceived", lineParts[2].trim().split(" ")[0]);

        return (data) -> {
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
                return null;
            }

            // build the output
            StringBuilder sb = new StringBuilder(label);
            if (!parts.isEmpty()) {
                sb.append(" @ ").append(String.join(", ", parts));
            }
            return sb.toString();
        };
    }

    private static Function<Map<String, String>, String> mcChunks(String lineKey, String line, BiConsumer<String, String> emit) {


        return (data) -> {
            return "Not Implemented Yet";
        };
    }

    private static Function<Map<String, String>, String> mcEntities(String lineKey, String line, BiConsumer<String, String> emit) {


        return (data) -> {
            return "Not Implemented Yet";
        };
    }

    private static Function<Map<String, String>, String> mcParticles(String lineKey, String line, BiConsumer<String, String> emit) {


        return (data) -> {
            return "Not Implemented Yet";
        };
    }

    private static Function<Map<String, String>, String> mcChunksClient(String lineKey, String line, BiConsumer<String, String> emit) {


        return (data) -> {
            return "Not Implemented Yet";
        };
    }

    private static Function<Map<String, String>, String> mcChunksServer(String lineKey, String line, BiConsumer<String, String> emit) {


        return (data) -> {
            return "Not Implemented Yet";
        };
    }

    private static Function<Map<String, String>, String> mcDimension(String lineKey, String line, BiConsumer<String, String> emit) {


        return (data) -> {
            return "Not Implemented Yet";
        };
    }

    private static Function<Map<String, String>, String> locPosition(String lineKey, String line, BiConsumer<String, String> emit) {


        return (data) -> {
            return "Not Implemented Yet";
        };
    }

    private static Function<Map<String, String>, String> locBlock(String lineKey, String line, BiConsumer<String, String> emit) {


        return (data) -> {
            return "Not Implemented Yet";
        };
    }

    private static Function<Map<String, String>, String> locChunk(String lineKey, String line, BiConsumer<String, String> emit) {


        return (data) -> {
            return "Not Implemented Yet";
        };
    }

    private static Function<Map<String, String>, String> locFacing(String lineKey, String line, BiConsumer<String, String> emit) {


        return (data) -> {
            return "Not Implemented Yet";
        };
    }

    private static Function<Map<String, String>, String> locLight(String lineKey, String line, BiConsumer<String, String> emit) {


        return (data) -> {
            return "Not Implemented Yet";
        };
    }

    private static Function<Map<String, String>, String> locLocalDifficulty(String lineKey, String line, BiConsumer<String, String> emit) {


        return (data) -> {
            return "Not Implemented Yet";
        };
    }

    private static Function<Map<String, String>, String> locHeightmapClient(String lineKey, String line, BiConsumer<String, String> emit) {


        return (data) -> {
            return "Not Implemented Yet";
        };
    }

    private static Function<Map<String, String>, String> locHeightmapServer(String lineKey, String line, BiConsumer<String, String> emit) {


        return (data) -> {
            return "Not Implemented Yet";
        };
    }

    private static Function<Map<String, String>, String> locBiome(String lineKey, String line, BiConsumer<String, String> emit) {


        return (data) -> {
            return "Not Implemented Yet";
        };
    }

    private static Function<Map<String, String>, String> locDifficulty(String lineKey, String line, BiConsumer<String, String> emit) {


        return (data) -> {
            return "Not Implemented Yet";
        };
    }

    private static Function<Map<String, String>, String> locNoiseRouter(String lineKey, String line, BiConsumer<String, String> emit) {


        return (data) -> {
            return "Not Implemented Yet";
        };
    }

    private static Function<Map<String, String>, String> locBiomeBuilder(String lineKey, String line, BiConsumer<String, String> emit) {


        return (data) -> {
            return "Not Implemented Yet";
        };
    }

    private static Function<Map<String, String>, String> locMobCaps(String lineKey, String line, BiConsumer<String, String> emit) {


        return (data) -> {
            return "Not Implemented Yet";
        };
    }

    private static Function<Map<String, String>, String> locSounds(String lineKey, String line, BiConsumer<String, String> emit) {


        return (data) -> {
            return "Not Implemented Yet";
        };
    }
}