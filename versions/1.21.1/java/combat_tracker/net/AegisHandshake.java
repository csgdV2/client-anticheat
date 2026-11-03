package combat_tracker.net;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Announces the current Client Anticheat protocol and release to the server plugin.
 * This is a compatibility check, not proof that the client has not been modified.
 */
public final class AegisHandshake {
    /** Shared channel id  :  must match the server plugin's channel exactly. */
    public static final ResourceLocation CHANNEL =
            ResourceLocation.fromNamespaceAndPath("clientanticheat", "handshake_v2");

    public static final int PROTOCOL_VERSION = 2;

    private static volatile boolean pending = false;
    private static volatile boolean sent = false;

    private AegisHandshake() {
    }

    /** Protocol revision followed by the mod release. */
    public record Payload(int protocol, String version) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(CHANNEL);
        public static final StreamCodec<FriendlyByteBuf, Payload> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.protocol());
                    buf.writeUtf(p.version(), 32);
                },
                buf -> new Payload(buf.readVarInt(), buf.readUtf(32)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Register the outgoing channel. Call once during client init. */
    public static void register() {
        PayloadTypeRegistry.playC2S().register(Payload.TYPE, Payload.CODEC);
    }

    /** Queue a handshake to be sent as soon as the server accepts the channel. */
    public static void requestSend() {
        pending = true;
        sent = false;
    }

    /** Clear state on disconnect so the next join sends a fresh handshake. */
    public static void reset() {
        pending = false;
        sent = false;
    }

    /**
     * Send the handshake once the connection is ready. Safe to call every client tick:
     * it does nothing until a send is pending and the server has declared the channel,
     * then fires exactly once.
     */
    public static void tick(String version) {
        if (!pending || sent) {
            return;
        }
        if (ClientPlayNetworking.canSend(Payload.TYPE)) {
            ClientPlayNetworking.send(new Payload(PROTOCOL_VERSION, version));
            sent = true;
            pending = false;
        }
    }
}
