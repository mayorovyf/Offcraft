package offcraft.client.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Uses vanilla chat availability/account restrictions rather than bypassing them. */
@Mixin(MinecraftClient.class)
public interface ClientGuiInvoker {
    @Invoker("openChatScreen")
    void offcraft$openChatScreen(String text);
}
