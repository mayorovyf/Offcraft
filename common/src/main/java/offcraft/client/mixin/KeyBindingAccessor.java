package offcraft.client.mixin;

import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Direct binding state avoids sticky toggles and physical-key rebinding conflicts. */
@Mixin(KeyBinding.class)
public interface KeyBindingAccessor {
    @Accessor("pressed")
    void offcraft$setPressed(boolean pressed);

    @Accessor("timesPressed")
    void offcraft$setTimesPressed(int timesPressed);

    @Accessor("timesPressed")
    int offcraft$getTimesPressed();
}
