package com.example.mapdistance;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 同一 WiFi（含热点）或蓝牙直连交换轨迹，不经过网盘和公网。
 * 对话框打开期间才监听；对方也要同时打开「附近同步」。
 */
final class NearbySession {
    static final int PORT = 17866;
    static final String NSD_TYPE = "_amiceju._tcp.";
    static final UUID BT_UUID = UUID.fromString("c3e7a1b0-4d2e-4f91-9a66-8b1e2c4d5e70");
    private static final int MAGIC = 0x414D4331;
    private static final int MAX_BODY = 8 * 1024 * 1024;

    interface Callbacks {
        void onStatus(String line);
        void onPeers(List<Peer> peers);
        void onMerged(String message);
        void requestBtDiscoverable();
    }

    static final class Peer {
        final String key;
        final boolean bluetooth;
        final String host;
        final int port;
        final String btAddress;
        final String fallback;
        final String deviceId;
        final String advertisedNick;

        Peer(String key, String fallback, boolean bluetooth, String host, int port,
             String btAddress, String deviceId, String advertisedNick) {
            this.key = key;
            this.fallback = fallback == null ? "" : fallback;
            this.bluetooth = bluetooth;
            this.host = host;
            this.port = port;
            this.btAddress = btAddress;
            this.deviceId = deviceId == null ? "" : deviceId;
            this.advertisedNick = advertisedNick == null ? "" : advertisedNick;
        }

        String[] keys() {
            List<String> k = new ArrayList<>();
            k.add(key);
            if (deviceId != null && !deviceId.isEmpty()) {
                k.add("id:" + deviceId);
            }
            if (btAddress != null && !btAddress.isEmpty()) {
                k.add("bt:" + btAddress);
            }
            if (host != null && !host.isEmpty()) {
                k.add("wifi:" + host);
            }
            return k.toArray(new String[0]);
        }

        String displayLabel(Context ctx) {
            String name = PhoneNotes.display(ctx, advertisedNick, fallback, keys());
            if (bluetooth) {
                return name + "  ·  蓝牙";
            }
            if (host != null && !host.isEmpty()) {
                return name + "  ·  WiFi " + host;
            }
            return name;
        }

        Peer withInfo(String id, String nick) {
            return new Peer(key, fallback, bluetooth, host, port, btAddress, id, nick);
        }
    }

    private final Context app;
    private final TrackStore store;
    private final Callbacks cb;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final Map<String, Peer> peers = new LinkedHashMap<>();

    private volatile boolean running;
    private ServerSocket httpServer;
    private NsdManager nsd;
    private NsdManager.RegistrationListener nsdReg;
    private NsdManager.DiscoveryListener nsdDisc;
    private WifiManager.MulticastLock multicast;
    private BluetoothServerSocket btServer;
    private BroadcastReceiver btFound;
    private String localIp = "";
    private String serviceName = "";

    static String[] runtimePerms() {
        if (Build.VERSION.SDK_INT >= 33) {
            return new String[]{
                    android.Manifest.permission.NEARBY_WIFI_DEVICES,
                    android.Manifest.permission.BLUETOOTH_SCAN,
                    android.Manifest.permission.BLUETOOTH_CONNECT,
                    android.Manifest.permission.BLUETOOTH_ADVERTISE
            };
        }
        if (Build.VERSION.SDK_INT >= 31) {
            return new String[]{
                    android.Manifest.permission.BLUETOOTH_SCAN,
                    android.Manifest.permission.BLUETOOTH_CONNECT,
                    android.Manifest.permission.BLUETOOTH_ADVERTISE,
                    android.Manifest.permission.ACCESS_FINE_LOCATION
            };
        }
        return new String[]{
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
        };
    }

    NearbySession(Context ctx, TrackStore store, Callbacks cb) {
        this.app = ctx.getApplicationContext();
        this.store = store;
        this.cb = cb;
    }

    void start() {
        if (running) {
            return;
        }
        running = true;
        serviceName = "AC-" + Prefs.deviceId(app);
        localIp = findIpv4();
        status(hint());
        startHttp();
        startNsd();
        startBluetooth();
    }

    String hint() {
        StringBuilder sb = new StringBuilder();
        sb.append("本机：").append(PhoneNotes.selfName(app)).append('\n');
        sb.append("双方都打开这个窗口。点列表同步，长按可备注对方。\n");
        if (localIp.isEmpty()) {
            sb.append("本机还没有局域网 IP，可用蓝牙，或先连 WiFi/热点。");
        } else {
            sb.append("本机地址：").append(localIp).append(':').append(PORT)
                    .append("（对方可手填）");
        }
        String book = PhoneNotes.bookSummary(app);
        if (!book.isEmpty()) {
            sb.append('\n').append(book);
        }
        return sb.toString();
    }

    void stop() {
        running = false;
        stopNsd();
        stopHttp();
        stopBluetooth();
        pool.shutdownNow();
    }

    void refresh() {
        if (!running) {
            return;
        }
        localIp = findIpv4();
        status(hint() + "\n正在重新查找附近手机…");
        synchronized (peers) {
            peers.clear();
        }
        emitPeers();
        stopNsd();
        startNsd();
        restartBtScan();
        addBondedPhones();
    }

    void relabel() {
        status(hint());
        emitPeers();
    }

    void connect(final Peer peer) {
        pool.execute(() -> {
            try {
                String theirs;
                if (peer.bluetooth) {
                    theirs = btExchange(peer.btAddress, SyncPack.taggedJson(app, store));
                } else {
                    theirs = httpExchange(peer.host, peer.port, SyncPack.taggedJson(app, store));
                }
                finishMerge(theirs, peer, peer.bluetooth ? "bt:" + peer.btAddress : "wifi:" + peer.host);
            } catch (Exception e) {
                status("连接失败：" + safeMsg(e) + "。双方都要打开「附近同步」，并在同一 WiFi / 热点，或打开蓝牙。");
            }
        });
    }

    void connectIp(String raw) {
        String ip = raw == null ? "" : raw.trim();
        int port = PORT;
        int colon = ip.lastIndexOf(':');
        if (colon > 0) {
            try {
                port = Integer.parseInt(ip.substring(colon + 1));
                ip = ip.substring(0, colon);
            } catch (NumberFormatException ignored) {
                port = PORT;
            }
        }
        if (ip.isEmpty()) {
            status("请填写对方屏幕上的 IP");
            return;
        }
        final String host = ip;
        final int p = port;
        pool.execute(() -> {
            try {
                String theirs = httpExchange(host, p, SyncPack.taggedJson(app, store));
                finishMerge(theirs, null, "wifi:" + host);
            } catch (Exception e) {
                status("连接 " + host + " 失败：" + safeMsg(e));
            }
        });
    }

    private void finishMerge(String theirs, Peer peer, String extraKey) {
        SyncPack.Meta meta = SyncPack.parseMeta(theirs);
        PhoneNotes.remember(app, meta, extraKey);
        if (peer != null && peer.btAddress != null && !peer.btAddress.isEmpty()
                && extraKey != null && !extraKey.startsWith("bt:")) {
            PhoneNotes.remember(app, meta, "bt:" + peer.btAddress);
        }
        SyncPack.Delta d = SyncPack.mergeJson(store, theirs);
        String fallback = peer != null ? peer.displayLabel(app) : extraKey;
        String who = PhoneNotes.display(app, meta.nick, fallback,
                extraKey,
                meta.deviceId.isEmpty() ? "" : "id:" + meta.deviceId);
        final String msg = SyncPack.mergeResult(d, store, who);
        Prefs.setLastSync(app, System.currentTimeMillis(), msg);
        emitPeers();
        main.post(() -> cb.onMerged(msg));
    }

    private void status(final String line) {
        main.post(() -> cb.onStatus(line));
    }

    private void emitPeers() {
        final List<Peer> copy;
        synchronized (peers) {
            copy = new ArrayList<>(peers.values());
        }
        main.post(() -> cb.onPeers(copy));
    }

    private void addPeer(Peer p) {
        synchronized (peers) {
            peers.put(p.key, p);
        }
        emitPeers();
    }

    private static String safeMsg(Exception e) {
        String m = e.getMessage();
        return m == null || m.isEmpty() ? e.getClass().getSimpleName() : m;
    }

    private void startHttp() {
        Thread httpThread = new Thread(() -> {
            try {
                httpServer = new ServerSocket();
                httpServer.setReuseAddress(true);
                httpServer.bind(new InetSocketAddress(PORT));
                while (running) {
                    Socket s;
                    try {
                        s = httpServer.accept();
                    } catch (IOException e) {
                        if (!running) {
                            break;
                        }
                        continue;
                    }
                    final Socket sock = s;
                    pool.execute(() -> handleHttp(sock));
                }
            } catch (IOException e) {
                if (running) {
                    status("局域网端口占用或失败：" + safeMsg(e) + "。可改用蓝牙。");
                }
            }
        }, "hg-lan");
        httpThread.setDaemon(true);
        httpThread.start();
    }

    private void stopHttp() {
        try {
            if (httpServer != null) {
                httpServer.close();
            }
        } catch (IOException ignored) {
        }
        httpServer = null;
    }

    private void handleHttp(Socket sock) {
        try {
            sock.setSoTimeout(20000);
            BufferedInputStream in = new BufferedInputStream(sock.getInputStream());
            String request = readLine(in);
            if (request == null) {
                return;
            }
            int contentLength = 0;
            while (true) {
                String h = readLine(in);
                if (h == null || h.isEmpty()) {
                    break;
                }
                int c = h.indexOf(':');
                if (c > 0 && h.substring(0, c).trim().equalsIgnoreCase("Content-Length")) {
                    contentLength = Integer.parseInt(h.substring(c + 1).trim());
                }
            }
            if (contentLength < 0 || contentLength > MAX_BODY) {
                writeHttp(sock, 413, "too large");
                return;
            }
            byte[] body = readExact(in, contentLength);
            if (request.startsWith("POST ")) {
                String incoming = SyncPack.decode(body);
                String extra = "";
                try {
                    extra = "wifi:" + sock.getInetAddress().getHostAddress();
                } catch (Exception ignored) {
                }
                SyncPack.Meta meta = SyncPack.parseMeta(incoming);
                PhoneNotes.remember(app, meta, extra);
                SyncPack.Delta d = SyncPack.mergeJson(store, incoming);
                writeHttp(sock, 200, SyncPack.taggedJson(app, store));
                String who = PhoneNotes.display(app, meta.nick, "连入的手机", extra,
                        meta.deviceId.isEmpty() ? "" : "id:" + meta.deviceId);
                final String msg = SyncPack.mergeResult(d, store, who);
                Prefs.setLastSync(app, System.currentTimeMillis(), msg);
                emitPeers();
                main.post(() -> cb.onMerged(msg));
            } else if (request.startsWith("GET /info")) {
                writeHttp(sock, 200, SyncPack.infoText(app));
            } else if (request.startsWith("GET /ping") || request.startsWith("GET / ")) {
                writeHttp(sock, 200, "ok");
            } else {
                writeHttp(sock, 200, SyncPack.taggedJson(app, store));
            }
        } catch (Exception ignored) {
        } finally {
            try {
                sock.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static void writeHttp(Socket sock, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String reason = code == 200 ? "OK" : "ERR";
        String head = "HTTP/1.1 " + code + " " + reason + "\r\n"
                + "Content-Type: text/plain; charset=utf-8\r\n"
                + "Content-Length: " + bytes.length + "\r\n"
                + "Connection: close\r\n\r\n";
        OutputStream out = sock.getOutputStream();
        out.write(head.getBytes(StandardCharsets.US_ASCII));
        out.write(bytes);
        out.flush();
    }

    private static String httpExchange(String host, int port, String csv) throws IOException {
        URL url = new URL("http://" + host + ":" + port + "/pack");
        HttpURLConnection c = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);
        try {
            c.setConnectTimeout(6000);
            c.setReadTimeout(20000);
            c.setDoOutput(true);
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            c.setInstanceFollowRedirects(false);
            byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(bytes.length);
            OutputStream os = c.getOutputStream();
            os.write(bytes);
            os.close();
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            if (in == null) {
                throw new IOException("HTTP " + code);
            }
            byte[] resp = SyncPack.readAll(in);
            if (code != 200) {
                throw new IOException("HTTP " + code);
            }
            return SyncPack.decode(resp);
        } finally {
            c.disconnect();
        }
    }

    private static String readLine(BufferedInputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        while (true) {
            int b = in.read();
            if (b < 0) {
                if (bos.size() == 0) {
                    return null;
                }
                break;
            }
            if (b == '\n') {
                break;
            }
            if (b != '\r') {
                bos.write(b);
            }
            if (bos.size() > 8192) {
                throw new IOException("header too long");
            }
        }
        return new String(bos.toByteArray(), StandardCharsets.US_ASCII).trim();
    }

    private void fetchWifiInfo(final String ip, final int port) {
        pool.execute(() -> {
            try {
                String raw = httpGet(ip, port, "/info");
                SyncPack.Meta meta = SyncPack.parseMeta(raw);
                if (meta.deviceId.isEmpty() && meta.nick.isEmpty()) {
                    return;
                }
                PhoneNotes.remember(app, meta, "wifi:" + ip);
                synchronized (peers) {
                    Peer old = peers.get("wifi:" + ip);
                    if (old != null) {
                        peers.put(old.key, old.withInfo(meta.deviceId, meta.nick));
                    }
                }
                emitPeers();
            } catch (Exception ignored) {
            }
        });
    }

    private static String httpGet(String host, int port, String path) throws IOException {
        URL url = new URL("http://" + host + ":" + port + path);
        HttpURLConnection c = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);
        try {
            c.setConnectTimeout(4000);
            c.setReadTimeout(6000);
            c.setRequestMethod("GET");
            c.setInstanceFollowRedirects(false);
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            if (in == null || code != 200) {
                throw new IOException("HTTP " + code);
            }
            return SyncPack.decode(SyncPack.readAll(in));
        } finally {
            c.disconnect();
        }
    }

    private static byte[] readExact(InputStream in, int n) throws IOException {
        if (n <= 0) {
            return new byte[0];
        }
        byte[] buf = new byte[n];
        int off = 0;
        while (off < n) {
            int r = in.read(buf, off, n - off);
            if (r < 0) {
                throw new IOException("body truncated");
            }
            off += r;
        }
        return buf;
    }

    private void startNsd() {
        try {
            WifiManager wifi = (WifiManager) app.getSystemService(Context.WIFI_SERVICE);
            if (wifi != null) {
                multicast = wifi.createMulticastLock("amiceju");
                multicast.setReferenceCounted(false);
                multicast.acquire();
            }
        } catch (Exception ignored) {
        }
        nsd = (NsdManager) app.getSystemService(Context.NSD_SERVICE);
        if (nsd == null) {
            return;
        }
        NsdServiceInfo info = new NsdServiceInfo();
        info.setServiceName(serviceName);
        info.setServiceType(NSD_TYPE);
        info.setPort(PORT);
        nsdReg = new NsdManager.RegistrationListener() {
            @Override public void onRegistrationFailed(NsdServiceInfo si, int errorCode) {}
            @Override public void onUnregistrationFailed(NsdServiceInfo si, int errorCode) {}
            @Override public void onServiceRegistered(NsdServiceInfo si) {}
            @Override public void onServiceUnregistered(NsdServiceInfo si) {}
        };
        try {
            nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, nsdReg);
        } catch (Exception ignored) {
        }
        nsdDisc = new NsdManager.DiscoveryListener() {
            @Override public void onStartDiscoveryFailed(String type, int errorCode) {}
            @Override public void onStopDiscoveryFailed(String type, int errorCode) {}
            @Override public void onDiscoveryStarted(String type) {}
            @Override public void onDiscoveryStopped(String type) {}
            @Override public void onServiceLost(NsdServiceInfo service) {}

            @Override
            public void onServiceFound(NsdServiceInfo service) {
                String name = service.getServiceName();
                if (name == null || name.equals(serviceName) || !name.startsWith("AC-")) {
                    return;
                }
                try {
                    nsd.resolveService(service, new NsdManager.ResolveListener() {
                        @Override
                        public void onResolveFailed(NsdServiceInfo si, int errorCode) {}

                        @Override
                        public void onServiceResolved(NsdServiceInfo si) {
                            InetAddress host = si.getHost();
                            if (host == null) {
                                return;
                            }
                            String ip = host.getHostAddress();
                            if (ip == null || ip.equals(localIp)) {
                                return;
                            }
                            int p = si.getPort() > 0 ? si.getPort() : PORT;
                            addPeer(new Peer("wifi:" + ip, ip, false, ip, p, null, "", ""));
                            fetchWifiInfo(ip, p);
                        }
                    });
                } catch (Exception ignored) {
                }
            }
        };
        try {
            nsd.discoverServices(NSD_TYPE, NsdManager.PROTOCOL_DNS_SD, nsdDisc);
        } catch (Exception ignored) {
        }
    }

    private void stopNsd() {
        if (nsd != null) {
            try {
                if (nsdDisc != null) {
                    nsd.stopServiceDiscovery(nsdDisc);
                }
            } catch (Exception ignored) {
            }
            try {
                if (nsdReg != null) {
                    nsd.unregisterService(nsdReg);
                }
            } catch (Exception ignored) {
            }
        }
        nsdDisc = null;
        nsdReg = null;
        if (multicast != null && multicast.isHeld()) {
            try {
                multicast.release();
            } catch (Exception ignored) {
            }
        }
        multicast = null;
    }

    @SuppressLint("MissingPermission")
    private void startBluetooth() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            return;
        }
        if (!adapter.isEnabled()) {
            status(hint() + "\n蓝牙未打开，只走 WiFi。可先打开蓝牙再点刷新。");
            return;
        }
        cb.requestBtDiscoverable();
        try {
            btServer = adapter.listenUsingInsecureRfcommWithServiceRecord("AmiCeju", BT_UUID);
        } catch (Exception e) {
            try {
                btServer = adapter.listenUsingRfcommWithServiceRecord("AmiCeju", BT_UUID);
            } catch (Exception e2) {
                status(hint() + "\n蓝牙监听失败，可用同一 WiFi。");
                return;
            }
        }
        Thread btThread = new Thread(() -> {
            while (running && btServer != null) {
                try {
                    BluetoothSocket sock = btServer.accept();
                    pool.execute(() -> handleBt(sock));
                } catch (IOException e) {
                    if (!running) {
                        break;
                    }
                }
            }
        }, "hg-bt");
        btThread.setDaemon(true);
        btThread.start();
        btFound = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null || !BluetoothDevice.ACTION_FOUND.equals(intent.getAction())) {
                    return;
                }
                BluetoothDevice d = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                addBtDevice(d);
            }
        };
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        if (Build.VERSION.SDK_INT >= 33) {
            app.registerReceiver(btFound, filter, Context.RECEIVER_EXPORTED);
        } else {
            app.registerReceiver(btFound, filter);
        }
        addBondedPhones();
        restartBtScan();
    }

    @SuppressLint("MissingPermission")
    private void addBondedPhones() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            return;
        }
        try {
            for (BluetoothDevice d : adapter.getBondedDevices()) {
                addBtDevice(d);
            }
        } catch (Exception ignored) {
        }
    }

    @SuppressLint("MissingPermission")
    private void addBtDevice(BluetoothDevice d) {
        if (d == null) {
            return;
        }
        String addr = d.getAddress();
        if (addr == null) {
            return;
        }
        String name;
        try {
            name = d.getName();
        } catch (Exception e) {
            name = null;
        }
        if (name == null || name.isEmpty()) {
            name = addr;
        }
        if (!likelyPhone(d)) {
            return;
        }
        addPeer(new Peer("bt:" + addr, name, true, null, 0, addr, "", ""));
    }

    @SuppressLint("MissingPermission")
    private static boolean likelyPhone(BluetoothDevice d) {
        try {
            android.bluetooth.BluetoothClass cls = d.getBluetoothClass();
            if (cls == null) {
                return true;
            }
            int major = cls.getMajorDeviceClass();
            return major == android.bluetooth.BluetoothClass.Device.Major.PHONE
                    || major == android.bluetooth.BluetoothClass.Device.Major.UNCATEGORIZED
                    || major == android.bluetooth.BluetoothClass.Device.Major.COMPUTER
                    || major == android.bluetooth.BluetoothClass.Device.Major.MISC;
        } catch (Exception e) {
            return true;
        }
    }

    @SuppressLint("MissingPermission")
    private void restartBtScan() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            return;
        }
        try {
            if (adapter.isDiscovering()) {
                adapter.cancelDiscovery();
            }
            adapter.startDiscovery();
        } catch (Exception ignored) {
        }
    }

    private void stopBluetooth() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        try {
            if (adapter != null) {
                adapter.cancelDiscovery();
            }
        } catch (Exception ignored) {
        }
        if (btFound != null) {
            try {
                app.unregisterReceiver(btFound);
            } catch (Exception ignored) {
            }
            btFound = null;
        }
        try {
            if (btServer != null) {
                btServer.close();
            }
        } catch (IOException ignored) {
        }
        btServer = null;
    }

    private void handleBt(BluetoothSocket sock) {
        String extra = "";
        try {
            extra = "bt:" + sock.getRemoteDevice().getAddress();
        } catch (Exception ignored) {
        }
        try {
            DataInputStream in = new DataInputStream(sock.getInputStream());
            DataOutputStream out = new DataOutputStream(sock.getOutputStream());
            String incoming = readFrame(in);
            SyncPack.Meta meta = SyncPack.parseMeta(incoming);
            PhoneNotes.remember(app, meta, extra);
            SyncPack.Delta d = SyncPack.mergeJson(store, incoming);
            writeFrame(out, SyncPack.taggedJson(app, store));
            String who = PhoneNotes.display(app, meta.nick, "蓝牙对方", extra,
                    meta.deviceId.isEmpty() ? "" : "id:" + meta.deviceId);
            final String msg = SyncPack.mergeResult(d, store, who);
            Prefs.setLastSync(app, System.currentTimeMillis(), msg);
            emitPeers();
            main.post(() -> cb.onMerged(msg));
        } catch (Exception ignored) {
        } finally {
            try {
                sock.close();
            } catch (IOException ignored) {
            }
        }
    }

    @SuppressLint("MissingPermission")
    private String btExchange(String address, String csv) throws IOException {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            throw new IOException("无蓝牙");
        }
        try {
            adapter.cancelDiscovery();
        } catch (Exception ignored) {
        }
        BluetoothDevice device = adapter.getRemoteDevice(address);
        BluetoothSocket sock;
        try {
            sock = device.createInsecureRfcommSocketToServiceRecord(BT_UUID);
        } catch (Exception e) {
            sock = device.createRfcommSocketToServiceRecord(BT_UUID);
        }
        try {
            sock.connect();
            DataOutputStream out = new DataOutputStream(sock.getOutputStream());
            DataInputStream in = new DataInputStream(sock.getInputStream());
            writeFrame(out, csv);
            return readFrame(in);
        } finally {
            try {
                sock.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static void writeFrame(DataOutputStream out, String csv) throws IOException {
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
        out.writeInt(MAGIC);
        out.writeInt(bytes.length);
        out.write(bytes);
        out.flush();
    }

    private static String readFrame(DataInputStream in) throws IOException {
        int magic = in.readInt();
        if (magic != MAGIC) {
            throw new IOException("不是阿米测距");
        }
        int n = in.readInt();
        if (n < 0 || n > MAX_BODY) {
            throw new IOException("数据过大");
        }
        byte[] buf = new byte[n];
        in.readFully(buf);
        return SyncPack.decode(buf);
    }

    private String findIpv4() {
        try {
            ConnectivityManager cm = (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null && Build.VERSION.SDK_INT >= 23) {
                Network net = cm.getActiveNetwork();
                LinkProperties lp = net == null ? null : cm.getLinkProperties(net);
                if (lp != null) {
                    for (LinkAddress a : lp.getLinkAddresses()) {
                        InetAddress ia = a.getAddress();
                        if (ia instanceof Inet4Address && !ia.isLoopbackAddress()) {
                            return ia.getHostAddress();
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        try {
            Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
            List<String> found = new ArrayList<>();
            while (en.hasMoreElements()) {
                NetworkInterface nif = en.nextElement();
                if (!nif.isUp() || nif.isLoopback()) {
                    continue;
                }
                Enumeration<InetAddress> addrs = nif.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress ia = addrs.nextElement();
                    if (ia instanceof Inet4Address && !ia.isLoopbackAddress()) {
                        found.add(ia.getHostAddress());
                    }
                }
            }
            for (String ip : found) {
                if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                    return ip;
                }
            }
            if (!found.isEmpty()) {
                return found.get(0);
            }
        } catch (SocketException ignored) {
        }
        return "";
    }
}
