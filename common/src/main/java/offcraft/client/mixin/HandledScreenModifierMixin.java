package offcraft.client.mixin;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.util.InputUtil;
import offcraft.client.GameUi;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** HandledScreen also polls Shift directly instead of using Screen.hasShiftDown. */
@Mixin(HandledScreen.class)
public abstract class HandledScreenModifierMixin {
    @Redirect(method = {"mouseClicked", "mouseReleased"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/util/InputUtil;isKeyPressed(JI)Z"))
    private boolean offcraft$shift(long window, int key) {
        int modifiers = GameUi.inputModifiers();
        if (modifiers >= 0 && (key == GLFW.GLFW_KEY_LEFT_SHIFT || key == GLFW.GLFW_KEY_RIGHT_SHIFT)) {
            return (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        }
        return InputUtil.isKeyPressed(window, key);
    }
}
