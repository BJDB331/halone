package dev.halone.glasses.drivers.rayneo

import dev.halone.glasses.api.DriverMatch
import dev.halone.glasses.api.GlassesDriver
import dev.halone.glasses.api.GlassesDriverContract
import dev.halone.glasses.api.UsbIdentity
import dev.halone.glasses.transport.HidTransport
import dev.halone.glasses.transport.HidTransportProvider

class RayneoDriverContractTest : GlassesDriverContract() {

    override fun driver(): GlassesDriver = RayneoDriver()

    override fun knownIdentity() = UsbIdentity(
        vendorId = 0x3941,
        productId = 0xAF50,
        productName = "RayNeo AR Glasses",
    )

    override fun transportsFor(match: DriverMatch): HidTransportProvider =
        object : HidTransportProvider {
            override fun open(interfaceId: Int): Result<HidTransport> =
                Result.success(FakeRayneoGlasses(name = "fake:iface$interfaceId"))
        }
}
