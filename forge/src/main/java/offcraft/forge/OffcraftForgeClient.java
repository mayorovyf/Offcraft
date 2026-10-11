package offcraft.forge;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import offcraft.client.OffcraftClient;

/** Forge does not load this subscriber on a dedicated server. */
@Mod.EventBusSubscriber(modid = "offcraft", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class OffcraftForgeClient {
    private OffcraftForgeClient() {}

    @SubscribeEvent
    public static void initialize(FMLClientSetupEvent event) {
        event.enqueueWork(OffcraftClient::initialize);
    }
}
