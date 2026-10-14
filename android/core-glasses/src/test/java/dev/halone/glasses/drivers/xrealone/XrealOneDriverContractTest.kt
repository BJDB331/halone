package dev.halone.glasses.drivers.xrealone

import dev.halone.glasses.api.DriverMatch
import dev.halone.glasses.api.GlassesDriver
import dev.halone.glasses.api.GlassesDriverContract
import dev.halone.glasses.api.UsbIdentity
import dev.halone.glasses.transport.HidTransport
import dev.halone.glasses.transport.HidTransportProvider
import dev.halone.glasses.transport.ReplayHidTransport

class XrealOneDriverContractTest : GlassesDriverContract() {

    override fun driver(): GlassesDriver = XrealOneDriver()

    override fun knownIdentity() = UsbIdentity(
        vendorId = 0x3318,
        productId = 0x0436,
        manufacturerName = "XREAL",
        productName = "XREAL One Pro",
    )

    override fun transportsFor(match: DriverMatch): HidTransportProvider =
        object : HidTransportProvider {
            override fun open(interfaceId: Int): Result<HidTransport> = Result.success(
                ReplayHidTransport(
                    name = "replay:port$interfaceId",
                    inbound = if (interfaceId == XrealOneProtocol.PORT_REPORTS) {
                        listOf(XrealOneCaptures.firstImuMessage())
                    } else {
                        emptyList()
                    },
                )
            )
        }
}
