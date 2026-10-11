package offcraft.client.mixin;

import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Creative catalogue and inventory slots use vanilla's creative packet translation. */
@Mixin(CreativeInventoryScreen.class)
public interface CreativeInventoryScreenInvoker {
    @Invoker("onMouseClick")
    void offcraft$onMouseClick(Slot slot, int slotId, int button, SlotActionType action);

    @Accessor("lastClickOutsideBounds")
    boolean offcraft$wasLastClickOutsideBounds();

    @Accessor("lastClickOutsideBounds")
    void offcraft$setLastClickOutsideBounds(boolean outside);
}
