package offcraft.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import offcraft.client.GameUi;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Overrides physical modifier polling only during an Offcraft screen callback. */
@Mixin(Screen.class)
public abstract class ScreenModifierMixin {
    @Inject(method = "hasShiftDown", at = @At("HEAD"), cancellable = true)
    private static void offcraft$shift(CallbackInfoReturnable<Boolean> cir) {
        int modifiers = GameUi.inputModifiers();
        if (modifiers >= 0) cir.setReturnValue((modifiers & GLFW.GLFW_MOD_SHIFT) != 0);
    }

    @Inject(method = "hasControlDown", at = @At("HEAD"), cancellable = true)
    private static void offcraft$control(CallbackInfoReturnable<Boolean> cir) {
        int modifiers = GameUi.inputModifiers();
        int mask = MinecraftClient.IS_SYSTEM_MAC ? GLFW.GLFW_MOD_SUPER : GLFW.GLFW_MOD_CONTROL;
        if (modifiers >= 0) cir.setReturnValue((modifiers & mask) != 0);
    }

    @Inject(method = "hasAltDown", at = @At("HEAD"), cancellable = true)
    private static void offcraft$alt(CallbackInfoReturnable<Boolean> cir) {
        int modifiers = GameUi.inputModifiers();
        if (modifiers >= 0) cir.setReturnValue((modifiers & GLFW.GLFW_MOD_ALT) != 0);
    }
}
