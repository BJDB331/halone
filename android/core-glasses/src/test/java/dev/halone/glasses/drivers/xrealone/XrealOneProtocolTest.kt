package dev.halone.glasses.drivers.xrealone

import dev.halone.glasses.transport.ReplayHidTransport.Companion.parseHex
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Expected bytes are the published examples in `docs/protocol/xreal-one.md`, not output of this code. */
class XrealOneProtocolTest {

    @Test
    fun `get config request matches the published bytes`() {
        assertArrayEquals(
            "27 1f 00 00 00 06 80 00 00 01 18 00".parseHex(),
            XrealOneProtocol.request(XrealOneProtocol.MSG_GET_CONFIG, 1, XrealOneProtocol.getBody()),
        )
    }

    @Test
    fun `set side-by-side request matches the published bytes`() {
        assertArrayEquals(
            "28 22 00 00 00 08 80 00 00 02 1a 02 08 01".parseHex(),
            XrealOneProtocol.request(
                XrealOneProtocol.MSG_SET_INPUT_MODE, 2, XrealOneProtocol.setBody(XrealOneProtocol.INPUT_MODE_SBS),
            ),
        )
    }

    @Test
    fun `a numeric reply decodes its value`() {
        val value = XrealOneProtocol.decodeReply("22 02 10 01".parseHex()).getOrThrow()
        assertEquals(XrealOneProtocol.Value.Number(1), value)
    }

    @Test
    fun `an empty reply is success with the default value`() {
        val value = XrealOneProtocol.decodeReply("22 00".parseHex()).getOrThrow()
        assertEquals(XrealOneProtocol.Value.Number(0), value)
    }

    @Test
    fun `a nonzero status is a rejection`() {
        val error = XrealOneProtocol.decodeReply("22 03 08 91 4e".parseHex()).exceptionOrNull()
        assertTrue(error is XrealOneProtocol.RejectedException)
        assertEquals(10001L, (error as XrealOneProtocol.RejectedException).status)
    }

    @Test
    fun `a string reply longer than one varint byte decodes`() {
        val text = "x".repeat(300)
        val value = XrealOneProtocol.decodeReply(XrealOneCaptures.stringReplyBody(text)).getOrThrow()
        assertEquals(text, String((value as XrealOneProtocol.Value.Bytes).value))
    }

    @Test
    fun `a set body encodes multi-byte varints`() {
        assertArrayEquals("1a 03 08 ac 02".parseHex(), XrealOneProtocol.setBody(300))
    }
}
