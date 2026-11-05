package hev.htproxy;

/** JNI entry points exported by libhev-socks5-tunnel.so (heiher/hev-socks5-tunnel, default contract). */
public final class TProxyService {
    static { System.loadLibrary("hev-socks5-tunnel"); }

    private TProxyService() {}

    public static native boolean TProxyStartService(String configPath, int fd);
    public static native boolean TProxyStopService();
    public static native boolean TProxyIsRunning();
    /** [txPackets, txBytes, rxPackets, rxBytes] */
    public static native long[] TProxyGetStats();
}
