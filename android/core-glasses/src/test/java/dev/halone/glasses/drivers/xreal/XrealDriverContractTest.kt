package dev.halone.glasses.drivers.xreal

import dev.halone.glasses.api.DriverMatch
import dev.halone.glasses.api.GlassesDriver
import dev.halone.glasses.api.GlassesDriverContract
import dev.halone.glasses.api.UsbIdentity
import dev.halone.glasses.transport.HidTransport
import dev.halone.glasses.transport.HidTransportProvider
import dev.halone.glasses.transport.ReplayHidTransport

class XrealDriverContractTest : GlassesDriverContract() {

    override fun driver(): GlassesDriver = XrealDriver()

    override fun knownIdentity() = UsbIdentity(
        vendorId = 0x3318,
        productId = 0x0432,
        manufacturerName = "XREAL",
        productName = "XREAL Air 2 Pro",
    )

    override fun transportsFor(match: DriverMatch): HidTransportProvider =
        object : HidTransportProvider {
            override fun open(interfaceId: Int): Result<HidTransport> = Result.success(
                ReplayHidTransport(
                    name = "replay:iface$interfaceId",
                    inbound = listOf(XrealCaptures.SENSOR_AT_REST),
                )
            )
        }
}
