package offcraft.client.mixin;

import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
abstract class WindowMixin {
    @Shadow private boolean fullscreen;
    @Shadow private boolean currentFullscreen;

    @Redirect(method = "<init>", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/glfw/GLFW;glfwDefaultWindowHints()V", remap = false))
    private void offcraft$hiddenWindowHints() {
        GLFW.glfwDefaultWindowHints();
        fullscreen = false;
        currentFullscreen = false;
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
    }

    @Inject(method = "toggleFullscreen", at = @At("HEAD"), cancellable = true)
    private void offcraft$remainWindowed(CallbackInfo ci) {
        ci.cancel();
    }
}
