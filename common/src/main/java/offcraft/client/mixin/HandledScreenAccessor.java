package offcraft.client.mixin;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Screen-relative slot positions need the actual layout, including recipe-book shifts. */
@Mixin(HandledScreen.class)
public interface HandledScreenAccessor {
    @Accessor("x")
    int offcraft$getLeft();

    @Accessor("y")
    int offcraft$getTop();

    @Accessor("backgroundWidth")
    int offcraft$getBackgroundWidth();

    @Accessor("backgroundHeight")
    int offcraft$getBackgroundHeight();
}
