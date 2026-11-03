package dev.aegisac.force;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class HandshakeProtocolTest {
    private static byte[] handshake(int protocol, String version) {
        byte[] text = version.getBytes(StandardCharsets.UTF_8);
        byte[] data = new byte[text.length + 2];
        data[0] = (byte) protocol;
        data[1] = (byte) text.length;
        System.arraycopy(text, 0, data, 2, text.length);
        return data;
    }

    @Test
    public void acceptsCurrentProtocolAndRelease() {
        assertEquals("2.1", HandshakeProtocol.readSupportedVersion(handshake(2, "2.1")));
    }

    @Test
    public void rejectsOldAndUnsupportedReleases() {
        assertNull(HandshakeProtocol.readSupportedVersion(handshake(2, "2.0")));
        assertNull(HandshakeProtocol.readSupportedVersion(handshake(2, "2.10")));
        assertNull(HandshakeProtocol.readSupportedVersion(handshake(2, "unknown")));
    }

    @Test
    public void rejectsOldAndUnsupportedProtocols() {
        assertNull(HandshakeProtocol.readSupportedVersion(handshake(1, "2.1")));
        assertNull(HandshakeProtocol.readSupportedVersion(handshake(3, "2.1")));
        // Legacy wire format was just a VarInt-prefixed version string.
        assertNull(HandshakeProtocol.readSupportedVersion(new byte[]{3, '2', '.', '0'}));
    }

    @Test
    public void rejectsEmptyTruncatedAndOversizedMessages() {
        assertNull(HandshakeProtocol.readSupportedVersion(null));
        assertNull(HandshakeProtocol.readSupportedVersion(new byte[0]));
        assertNull(HandshakeProtocol.readSupportedVersion(new byte[]{2}));
        assertNull(HandshakeProtocol.readSupportedVersion(new byte[]{2, 3, '2', '.'}));
        assertNull(HandshakeProtocol.readSupportedVersion(new byte[107]));
        assertNull(HandshakeProtocol.readSupportedVersion(new byte[]{2, 0}));
    }

    @Test
    public void rejectsMalformedVarInts() {
        assertNull(HandshakeProtocol.readSupportedVersion(new byte[]{(byte) 0x80}));
        assertNull(HandshakeProtocol.readSupportedVersion(new byte[]{2, (byte) 0xff,
                (byte) 0xff, (byte) 0xff, (byte) 0xff, 0x7f}));
        assertNull(HandshakeProtocol.readSupportedVersion(new byte[]{(byte) 0x82, 0,
                3, '2', '.', '1'}));
    }

    @Test
    public void rejectsTrailingDataAndInvalidUtf8() {
        assertNull(HandshakeProtocol.readSupportedVersion(new byte[]{2, 3, '2', '.', '1', 0}));
        assertNull(HandshakeProtocol.readSupportedVersion(new byte[]{2, 3, '2', '.', (byte) 0xff}));
    }
}
