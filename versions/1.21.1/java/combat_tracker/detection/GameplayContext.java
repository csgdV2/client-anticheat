package combat_tracker.detection;

import net.minecraft.client.Minecraft;

public final class GameplayContext {
    private GameplayContext() {
    }

    public static long windowHandle(Minecraft client) {
        return client.getWindow().getWindow();
    }

    public static boolean active(Minecraft client) {
        return client.player != null && client.level != null && client.screen == null
                && client.isWindowActive() && client.mouseHandler.isMouseGrabbed()
                && !client.player.isSpectator() && !client.player.isCreative();
    }
}
