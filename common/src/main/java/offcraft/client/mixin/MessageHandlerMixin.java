package offcraft.client.mixin;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.network.message.MessageHandler;
import net.minecraft.text.Text;
import offcraft.client.MessageJournal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MessageHandler.class)
abstract class MessageHandlerMixin {
    // Yarn 1.21.1 build 3: onProfilelessMessage's delayed processor lambda.
    // Vanilla uses the system-looking one-argument ChatHud overload for this chat packet.
    @Redirect(method = "method_45745", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/hud/ChatHud;addMessage(Lnet/minecraft/text/Text;)V"))
    private void offcraft$profilelessChat(ChatHud hud, Text message) {
        MessageJournal.displayProfilelessChat(hud, message);
    }
}
