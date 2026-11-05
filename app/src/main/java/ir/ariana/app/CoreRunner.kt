package ir.ariana.app

import android.content.Context
import java.io.File

/**
 * Runs the bundled engine (libaether.so, official Android arm64 build) as a child process.
 * Flags are verified against the engine source (cli.rs).
 *
 *  - SOCKS5 proxy:      127.0.0.1:1819  (engine default)
 *  - HTTP CONNECT proxy: 127.0.0.1:1830  (--http-proxy)
 *  - ports 1820 (Tor) and 1825 (Psiphon) are used by the engine's own modes.
 */
enum class Mode(val label: String, val flags: List<String>) {
    AUTO("Auto (recommended)", emptyList()),
    MASQUE_H3("MASQUE (HTTP/3)", listOf("--masque")),
    MASQUE_H2("MASQUE (HTTP/2)", listOf("--masque", "--h2")),
    WARP_WG("WireGuard", listOf("--wg")),
    GOOL("WireGuard over MASQUE", listOf("--gool")),
    PSIPHON("Chained + Psiphon", listOf("--psiphon")),
    TOR("Chained + Tor", listOf("--tor")),
}

object CoreRunner {
    const val SOCKS_PORT = 1819
    const val HTTP_PORT = 1830

    private var process: Process? = null

    fun binary(context: Context) = File(context.applicationInfo.nativeLibraryDir, "libaether.so")
    fun psiphon(context: Context) = File(context.applicationInfo.nativeLibraryDir, "libpsiphon.so")

    fun isAvailable(context: Context) = binary(context).exists()

    @Synchronized
    fun start(context: Context, mode: Mode) {
        stop()
        val cmd = mutableListOf(binary(context).absolutePath)
        cmd += mode.flags
        cmd += listOf("--http-proxy", "127.0.0.1:$HTTP_PORT")
        val pb = ProcessBuilder(cmd).redirectErrorStream(true)
        pb.directory(context.filesDir)
        val env = pb.environment()
        env["HOME"] = context.filesDir.absolutePath
        env["AETHER_PSIPHON_BIN"] = psiphon(context).absolutePath
        process = pb.start()
        // drain output so the process never blocks on a full pipe
        val p = process!!
        Thread { p.inputStream.bufferedReader().forEachLine { } }.apply { isDaemon = true }.start()
    }

    @Synchronized
    fun stop() {
        process?.destroy()
        process = null
    }

    fun isRunning() = process?.isAlive == true
}
