package com.lestora.debug.commands;

import com.lestora.debug.DebugOverlay;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DebugCommands {

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        var root = Commands.literal("lestora");

        registerShowDebug(root);
        registerListDebugs(root);
        registerEnableHud(root);

        event.getDispatcher().register(root);
    }

    private static void registerShowDebug(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("debug")
                .then(Commands.literal("enableF3")
                        .executes(ctx -> {
                            DebugOverlay.setF3Enabled(true);
                            return 1;
                        })
                        .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    DebugOverlay.setF3Enabled(BoolArgumentType.getBool(ctx, "value"));
                                    return 1;
                                })
                        )
                )
        );
    }

    private static void registerEnableHud(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("debug")
                .then(Commands.literal("enableHud")
                        .executes(ctx -> {
                            DebugOverlay.setHudEnabled(true);
                            return 1;
                        })
                        .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    DebugOverlay.setHudEnabled(BoolArgumentType.getBool(ctx, "value"));
                                    return 1;
                                })
                        )
                )
        );
    }

    private static void registerListDebugs(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("debug")
                .then(Commands.literal("list")
                        .executes(ctx -> {
                            var logs = DebugOverlay.getLogs();
                            var player = Minecraft.getInstance().player;
                            for (var log : logs) {
                                player.displayClientMessage(Component.literal(log), false);
                            }
                            return 1;
                        })
                )
        );
    }
}