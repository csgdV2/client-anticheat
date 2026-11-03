package dev.aegisac.force;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Release compatibility gate; a client can still impersonate this public protocol. */
final class HandshakeProtocol {
    static final String CHANNEL = "clientanticheat:handshake_v2";
    static final int PROTOCOL_VERSION = 2;
    static final String REQUIRED_VERSION = "2.1";

    private HandshakeProtocol() {
    }

    /** Returns the supported release only for a complete, well-formed handshake. */
    static String readSupportedVersion(byte[] message) {
        if (message == null || message.length == 0 || message.length > 106) {
            return null;
        }
        ByteBuffer input = ByteBuffer.wrap(message);
        try {
            if (readVarInt(input) != PROTOCOL_VERSION) {
                return null;
            }
            int length = readVarInt(input);
            if (length < 1 || length > 96 || length != input.remaining()) {
                return null;
            }
            String version = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(input).toString();
            return REQUIRED_VERSION.equals(version) ? version : null;
        } catch (IllegalArgumentException | java.nio.BufferUnderflowException
                 | CharacterCodingException invalid) {
            return null;
        }
    }

    private static int readVarInt(ByteBuffer input) {
        int value = 0;
        for (int index = 0; index < 5; index++) {
            int next = Byte.toUnsignedInt(input.get());
            if (index == 4 && (next & 0xf8) != 0) {
                throw new IllegalArgumentException("Invalid positive VarInt");
            }
            value |= (next & 0x7f) << (index * 7);
            if ((next & 0x80) == 0) {
                if (index > 0 && next == 0) {
                    throw new IllegalArgumentException("Non-canonical VarInt");
                }
                return value;
            }
        }
        throw new IllegalArgumentException("VarInt too long");
    }
}
