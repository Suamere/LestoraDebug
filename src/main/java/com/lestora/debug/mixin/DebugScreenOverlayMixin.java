package com.lestora.debug.mixin;

import com.lestora.debug.DebugDataParser;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(DebugScreenOverlay.class)
public class DebugScreenOverlayMixin {

    @Inject(method = "getGameInformation", at = @At("RETURN"), cancellable = true)
    private void onGetGameInformation(CallbackInfoReturnable<List<String>> cir) {
        DebugDataParser.parse(cir.getReturnValue());
        cir.setReturnValue(DebugDataParser.getLeftValues());
    }

    @Inject(method = "getSystemInformation", at = @At("RETURN"), cancellable = true)
    private void onGetSystemInformation(CallbackInfoReturnable<List<String>> cir) {
        DebugDataParser.parse(cir.getReturnValue());
        cir.setReturnValue(DebugDataParser.getRightValues());
    }

    // This removes the forced two lines on the left of the F3 menu that says Debug charts and For help...
    @Inject(
            method = "renderLines(Lnet/minecraft/client/gui/GuiGraphics;Ljava/util/List;Z)V",
            at = @At("HEAD")
    )
    private void stripDebugHints(GuiGraphics p_286519_, List<String> lines, boolean p_286644_, CallbackInfo ci) {
        lines.removeIf(s ->
                s != null
                        && (s.startsWith("Debug charts")
                        || s.startsWith("For help"))
        );
    }

    // ToDo: Disable Pie + Charts
}