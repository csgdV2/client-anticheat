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
        assertEquals("2.2", HandshakeProtocol.readSupportedVersion(handshake(2, "2.2")));
    }

    @Test
    public void rejectsOldAndUnsupportedReleases() {
        assertNull(HandshakeProtocol.readSupportedVersion(handshake(2, "2.0")));
        assertNull(HandshakeProtocol.readSupportedVersion(handshake(2, "2.1")));
        assertNull(HandshakeProtocol.readSupportedVersion(handshake(2, "unknown")));
    }

    @Test
    public void acceptsNewerCompatibleReleases() {
        for (String version : new String[]{"2.2.0", "2.2.1", "2.3", "2.10", "3.0", "2.3-beta.1", "2.2+build.7"}) {
            assertEquals(version, HandshakeProtocol.readSupportedVersion(handshake(2, version)));
        }
    }

    @Test
    public void rejectsOutdatedPrereleasesAndInvalidVersionStrings() {
        for (String version : new String[]{"2.1.99", "2.2-beta", "1.99", "02.2", "2.02", "2", "2.2 ", "2.2x", "2.2-", "999999999999999999999.0"}) {
            assertNull(HandshakeProtocol.readSupportedVersion(handshake(2, version)));
        }
    }

    @Test
    public void rejectsOldAndUnsupportedProtocols() {
        assertNull(HandshakeProtocol.readSupportedVersion(handshake(1, "2.2")));
        assertNull(HandshakeProtocol.readSupportedVersion(handshake(3, "2.2")));
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
                3, '2', '.', '2'}));
    }

    @Test
    public void rejectsTrailingDataAndInvalidUtf8() {
        assertNull(HandshakeProtocol.readSupportedVersion(new byte[]{2, 3, '2', '.', '2', 0}));
        assertNull(HandshakeProtocol.readSupportedVersion(new byte[]{2, 3, '2', '.', (byte) 0xff}));
    }
}
