package dev.aegisac.force;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

final class HandshakeProtocol {
    static final String CHANNEL = "clientanticheat:handshake_v2";
    static final int PROTOCOL_VERSION = 2;
    static final String MINIMUM_VERSION = "2.2";
    private static final Pattern RELEASE = Pattern.compile(
            "(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:\\.(0|[1-9][0-9]*))?"
                    + "(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?"
                    + "(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?");

    private HandshakeProtocol() {
    }

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
            return supportsVersion(version) ? version : null;
        } catch (IllegalArgumentException | java.nio.BufferUnderflowException
                 | CharacterCodingException invalid) {
            return null;
        }
    }

    static boolean supportsVersion(String version) {
        if (version == null || version.length() > 32) return false;
        var match = RELEASE.matcher(version);
        if (!match.matches()) return false;
        try {
            int major = Integer.parseInt(match.group(1));
            int minor = Integer.parseInt(match.group(2));
            int patch = match.group(3) == null ? 0 : Integer.parseInt(match.group(3));
            int[] actual = {major, minor, patch};
            String[] minimum = MINIMUM_VERSION.split("\\.");
            for (int index = 0; index < actual.length; index++) {
                int floor = index < minimum.length ? Integer.parseInt(minimum[index]) : 0;
                if (actual[index] != floor) return actual[index] > floor;
            }
            return match.group(4) == null;
        } catch (NumberFormatException invalid) {
            return false;
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
