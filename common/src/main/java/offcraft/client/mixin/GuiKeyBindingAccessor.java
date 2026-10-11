package offcraft.client.mixin;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Logical GUI keys respect the user's keyboard/scancode rebinding. */
@Mixin(KeyBinding.class)
public interface GuiKeyBindingAccessor {
    @Accessor("boundKey")
    InputUtil.Key offcraft$getBoundKey();
}
