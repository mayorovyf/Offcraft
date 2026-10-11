package offcraft.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import offcraft.client.OffcraftClient;

@Mod(value = "offcraft", dist = Dist.CLIENT)
public final class OffcraftNeoForge {
    public OffcraftNeoForge() {
        OffcraftClient.initialize();
    }
}
