package offcraft.client.mixin;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import offcraft.client.MessageJournal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatHud.class)
abstract class ChatHudMixin {
    // The one-argument overload delegates here. Refresh/restore do not, so no replay duplicates.
    @Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("RETURN"))
    private void offcraft$journalMessage(Text message, MessageSignatureData signature,
                                       MessageIndicator indicator, CallbackInfo ci) {
        boolean system = !MessageJournal.isProfilelessChat() && signature == null && (indicator == MessageIndicator.system()
                || indicator == MessageIndicator.singlePlayer());
        MessageJournal.append(system ? "system" : "chat", message);
    }
}
