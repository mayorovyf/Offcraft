package offcraft.client.mixin;

import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
abstract class MouseMixin {
    @Inject(method = "lockCursor", at = @At("HEAD"), cancellable = true)
    private void offcraft$leaveDesktopCursorAlone(CallbackInfo ci) {
        ci.cancel();
    }
}
