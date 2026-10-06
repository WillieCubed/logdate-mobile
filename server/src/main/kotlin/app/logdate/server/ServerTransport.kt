package app.logdate.server

import io.ktor.server.netty.NettyApplicationEngine

internal fun NettyApplicationEngine.Configuration.configureLogDateTransport() {
    enableHttp2 = true
    enableH2c = true
}
