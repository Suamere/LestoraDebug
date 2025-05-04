package com.lestora.debug.commands;

import com.lestora.debug.DebugDataParser;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DebugCommands {

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        var root = Commands.literal("debug");

        addCommand("listIgnoredKeys", root, DebugCommands::listIgnoredKeys);
        addCommand("ignoreKey",       root, DebugCommands::ignoreKey);
        addCommand("allowKey",        root, DebugCommands::allowKey);

        event.getDispatcher().register(Commands.literal("lestora").then(root));
    }

    private static void addCommand(String name, LiteralArgumentBuilder<CommandSourceStack> root, Consumer<LiteralArgumentBuilder<CommandSourceStack>> configurator) {
        var child = Commands.literal(name);
        configurator.accept(child);
        root.then(child);
    }

    private static void listIgnoredKeys(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.executes(ctx -> {
                if (DebugDataParser.blocklist.isEmpty()) {
                    ctx.getSource().sendSuccess(() ->
                            Component.literal("no debug data is currently ignored"), false
                    );
                } else {
                    ctx.getSource().sendSuccess(() ->
                            Component.literal("Currently ignored keys:"), false
                    );
                    for (String key : DebugDataParser.blocklist) {
                        ctx.getSource().sendSuccess(() ->
                                Component.literal(" - " + key), false
                        );
                    }
                }
                return 1;
            });
    }

    // ToDo: Make these changes save to and pull from a config toml?
    private static void ignoreKey(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.argument("key", StringArgumentType.greedyString())
                .suggests((ctx, builder) -> {
                    // collect every possible suggestion
                    List<String> all = new ArrayList<>();
                    all.add("!All");
                    all.add("!MinecraftData (TopLeft)");
                    all.add("!SystemData (TopRight)");
                    all.add("!LocationData (BottomLeft)");
                    all.add("!TargetData (BottomRight)");
                    all.addAll(DebugDataParser.getAllKeys());

                    // manual filtering
                    String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
                    for (String s : all) {
                        if (s.toLowerCase(Locale.ROOT).contains(remaining)) {
                            builder.suggest(s);
                        }
                    }
                    return builder.buildFuture();
                })
            .executes(ctx -> {
                String key = StringArgumentType.getString(ctx, "key");
                switch (key) {
                    case "!All" -> {
                        for (String lineKey : DebugDataParser.leftLines) {
                            if (!"<br>".equals(lineKey)) {
                                DebugDataParser.blocklist.add(lineKey);
                            }
                        }
                        for (String lineKey : DebugDataParser.rightLines) {
                            if (!"<br>".equals(lineKey)) {
                                DebugDataParser.blocklist.add(lineKey);
                            }
                        }
                        ctx.getSource().sendSuccess(() -> Component.literal("Now ignoring all debug sections"), false);
                    }
                    case "!MinecraftData (TopLeft)" -> {
                        for (String fullKey : DebugDataParser.data.keySet()) {
                            if (fullKey.startsWith("MinecraftData.")) {
                                DebugDataParser.blocklist.add(fullKey);
                            }
                        }
                        ctx.getSource().sendSuccess(() -> Component.literal("Now ignoring the MinecraftData section"), false);
                    }
                    case "!SystemData (TopRight)" -> {
                        for (String fullKey : DebugDataParser.data.keySet()) {
                            if (fullKey.startsWith("System.")) {
                                DebugDataParser.blocklist.add(fullKey);
                            }
                        }
                        ctx.getSource().sendSuccess(() -> Component.literal("Now ignoring the System section"), false);
                    }
                    case "!LocationData (BottomLeft)" -> {
                        for (String fullKey : DebugDataParser.data.keySet()) {
                            if (fullKey.startsWith("LocationDetails.")) {
                                DebugDataParser.blocklist.add(fullKey);
                            }
                        }
                        ctx.getSource().sendSuccess(() -> Component.literal("Now ignoring the LocationDetails section"), false);
                    }
                    case "!TargetData (BottomRight)" -> {
                        for (String fullKey : DebugDataParser.data.keySet()) {
                            if (fullKey.startsWith("Targets.")) {
                                DebugDataParser.blocklist.add(fullKey);
                            }
                        }
                        ctx.getSource().sendSuccess(() -> Component.literal("Now ignoring all Targeting sections"), false);
                    }
                    default -> {
                        DebugDataParser.blocklist.add(key);
                        ctx.getSource().sendSuccess(() -> Component.literal("Now ignoring debug key: " + key), false);
                    }
                }
                return 1;
            })
        );
    }

    private static void allowKey(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.argument("key", StringArgumentType.greedyString())
            .suggests((ctx, builder) -> {
                List<String> all = new ArrayList<>();
                all.add("!All");
                all.add("!MinecraftData (TopLeft)");
                all.add("!SystemData (TopRight)");
                all.add("!LocationData (BottomLeft)");
                all.add("!TargetData (BottomRight)");
                all.addAll(DebugDataParser.blocklist);

                // manual filtering
                String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
                for (String s : all) {
                    if (s.toLowerCase(Locale.ROOT).contains(remaining)) {
                        builder.suggest(s);
                    }
                }
                return builder.buildFuture();
            })
                .executes(ctx -> {
                    String key = StringArgumentType.getString(ctx, "key");
                    switch (key) {
                        case "!All" -> {
                            DebugDataParser.blocklist.clear();
                            ctx.getSource().sendSuccess(() -> Component.literal("No longer ignoring any debug keys"), false);
                        }
                        case "!MinecraftData (TopLeft)" -> {
                            DebugDataParser.blocklist.removeIf(bk -> bk.startsWith("MinecraftData."));
                            ctx.getSource().sendSuccess(() -> Component.literal("Stopped ignoring the MinecraftData section"), false);
                        }
                        case "!SystemData (TopRight)" -> {
                            DebugDataParser.blocklist.removeIf(bk -> bk.startsWith("System."));
                            ctx.getSource().sendSuccess(() -> Component.literal("Stopped ignoring the System section"), false);
                        }
                        case "!LocationData (BottomLeft)" -> {
                            DebugDataParser.blocklist.removeIf(bk -> bk.startsWith("LocationDetails."));
                            ctx.getSource().sendSuccess(() -> Component.literal("Stopped ignoring the LocationDetails section"), false);
                        }
                        case "!TargetData (BottomRight)" -> {
                            DebugDataParser.blocklist.removeIf(bk -> bk.startsWith("Targets."));
                            ctx.getSource().sendSuccess(() -> Component.literal("Stopped ignoring all Targeting sections"), false);
                        }
                        default -> {
                            DebugDataParser.blocklist.remove(key);
                            ctx.getSource().sendSuccess(() -> Component.literal("No longer ignoring debug key: " + key), false);
                        }
                    }
                    return 1;
                })
        );
    }
}