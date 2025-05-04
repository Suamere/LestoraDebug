package com.lestora.debug.commands;

import com.lestora.debug.DebugOverlay;
import com.lestora.debug.models.DebugDataParser;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.function.Supplier;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DebugCommands {

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        var root = Commands.literal("lestora");

        registerIgnoreKeyCommand(root);

        event.getDispatcher().register(root);
    }

    private static void registerIgnoreKeyCommand(LiteralArgumentBuilder<CommandSourceStack> root) {
        root
                .then(Commands.literal("ignoreKey")
                        .then(Commands.argument("key", StringArgumentType.word())
                                .suggests((ctx, builder) ->
                                        // use your parser’s key list for tab‐completion
                                        SharedSuggestionProvider.suggest(DebugDataParser.getAllKeys(), builder)
                                )
                                .executes(ctx -> {
                                    String key = StringArgumentType.getString(ctx, "key");
                                    // add to your global blocklist
                                    DebugDataParser.addToBlocklist(key);
                                    // let the user know
                                    ctx.getSource().sendSuccess(() -> Component.literal("Now ignoring debug key: " + key), false);
                                    return 1;
                                })
                        )
                );
    }
}