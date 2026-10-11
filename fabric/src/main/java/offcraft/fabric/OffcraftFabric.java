package offcraft.fabric;

import net.fabricmc.api.ClientModInitializer;
import offcraft.client.OffcraftClient;

public final class OffcraftFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        OffcraftClient.initialize();
    }
}
