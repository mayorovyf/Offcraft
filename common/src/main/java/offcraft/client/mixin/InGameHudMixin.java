package offcraft.client.mixin;

import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.text.Text;
import offcraft.client.MessageJournal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
abstract class InGameHudMixin {
    @Inject(method = "setOverlayMessage", at = @At("RETURN"))
    private void offcraft$journalActionBar(Text message, boolean tinted, CallbackInfo ci) {
        MessageJournal.append("action_bar", message);
    }
}
