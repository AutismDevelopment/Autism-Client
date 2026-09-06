package autismclient.util.mm.relay;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class MqttRelay implements Relay {

    private static final long ROTATE_LEAD_MS = 120_000;
    private static final long ROTATE_JITTER_MS = 20_000;

    private static final int MAX_QUEUED_FRAMES = 256;
    private static final long QUEUE_TTL_MS = 90_000;

    private final String serverUri;
    private final RelayStatus status;
    private final Map<String, Consumer<byte[]>> subs = new ConcurrentHashMap<>();

    private final Supplier<String> tokenSupplier;

    private final Supplier<String> usernameSupplier;
    private final ScheduledExecutorService reconnects;
    private final AtomicInteger backoff = new AtomicInteger();

    private final String stableClientId;

    private final ArrayDeque<Queued> queue = new ArrayDeque<>();
    private volatile MqttAsyncClient client;
    private volatile ScheduledFuture<?> rotation;
    private volatile boolean closed;

    private MqttRelay(String name, String serverUri, Supplier<String> tokenSupplier, Supplier<String> usernameSupplier) {
        this.status = new RelayStatus(name);
        this.serverUri = serverUri;
        this.tokenSupplier = tokenSupplier;
        this.usernameSupplier = usernameSupplier;
        this.stableClientId = "mm" + autismclient.util.mm.crypto.MmCrypto.hex(autismclient.util.mm.crypto.MmCrypto.randomBytes(16));
        this.reconnects = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "mm-mqtt-reconnect");
            t.setDaemon(true);
            return t;
        });

        reconnects.execute(this::connect);
    }

    public static MqttRelay authed(String name, String serverUri, Supplier<String> tokenSupplier, Supplier<String> usernameSupplier) {
        return new MqttRelay(name, serverUri, tokenSupplier, usernameSupplier);
    }

    @Override public String name() { return status.name; }
    @Override public RelayStatus status() { return status; }

    private boolean logDiag() {
        return autismclient.util.mm.MmPrefs.get().debugLog();
    }

    private synchronized void connect() {
        if (closed) return;
        try {

            String jwt = tokenSupplier.get();
            if (jwt == null || jwt.isBlank()) {

                scheduleReconnect();
                return;
            }
            String user = usernameSupplier == null ? null : usernameSupplier.get();

            MqttAsyncClient old = client;
            if (old != null) quietClose(old);
            MqttAsyncClient c = new MqttAsyncClient(serverUri, stableClientId, new MemoryPersistence());
            c.setCallback(new MqttCallbackExtended() {
                @Override public void connectComplete(boolean reconnect, String uri) {

                    if (closed) { quietClose(c); return; }
                    if (c != client) return;
                    status.connected = true;
                    status.lastError = "";
                    status.markActivity();
                    for (String topic : subs.keySet()) trySubscribe(topic);
                    flushQueued(c);
                }
                @Override public void connectionLost(Throwable cause) {
                    if (closed) { quietClose(c); return; }
                    if (c != client) return;
                    if (logDiag())
                        autismclient.AutismClientAddon.LOG.info("[mm-relay] {} connection lost: {}",
                            status.name, cause == null ? "?" : String.valueOf(cause.getMessage()));
                    status.connected = false;
                    status.lastError = cause == null ? "lost" : String.valueOf(cause.getMessage());
                    scheduleReconnect();
                }
                @Override public void messageArrived(String topic, MqttMessage message) {
                    Consumer<byte[]> consumer = subs.get(topic);
                    if (consumer == null) return;
                    status.received.incrementAndGet();
                    status.markActivity();
                    try {
                        consumer.accept(message.getPayload());
                    } catch (Throwable t) {

                        autismclient.AutismClientAddon.LOG.debug("MQTT inbound handler threw (dropped)", t);
                    }
                }
                @Override public void deliveryComplete(IMqttDeliveryToken token) {}
            });
            client = c;

            MqttConnectOptions opts = new MqttConnectOptions();

            opts.setCleanSession(false);

            opts.setAutomaticReconnect(false);
            opts.setUserName(user == null || user.isBlank() ? "jwt" : user);
            opts.setPassword((jwt == null ? "" : jwt).toCharArray());
            opts.setConnectionTimeout(15);

            opts.setKeepAliveInterval(30);
            opts.setMaxInflight(64);
            final String presentedJwt = jwt;
            final long runwayS = jwt == null ? -1
                : (jwtExpMs(jwt) - autismclient.util.mm.ServerClock.nowMs()) / 1000;
            if (logDiag())
                autismclient.AutismClientAddon.LOG.info("[mm-relay] {} connecting (tokenRunway={}s, subs={})",
                    status.name, runwayS, subs.size());
            c.connect(opts, null, new IMqttActionListener() {
                @Override public void onSuccess(IMqttToken asyncActionToken) {
                    if (closed) { quietClose(c); return; }
                    if (c != client) return;
                    boolean resumed = false;
                    try { resumed = asyncActionToken != null && asyncActionToken.getSessionPresent(); } catch (Throwable ignored) { }
                    if (logDiag())
                        autismclient.AutismClientAddon.LOG.info("[mm-relay] {} connected (sessionPresent={}, tokenRunway={}s)",
                            status.name, resumed, runwayS);
                    backoff.set(0);
                    status.connected = true;
                    status.lastError = "";
                    status.markActivity();
                    for (String topic : subs.keySet()) trySubscribe(topic);
                    flushQueued(c);
                    scheduleRotation(presentedJwt);
                }
                @Override public void onFailure(IMqttToken asyncActionToken, Throwable e) {
                    if (closed) { quietClose(c); return; }
                    if (c != client) return;
                    if (logDiag())
                        autismclient.AutismClientAddon.LOG.info("[mm-relay] {} connect FAILED: {}",
                            status.name, e == null ? "?" : String.valueOf(e.getMessage()));
                    status.connected = false;
                    status.lastError = e == null ? "connect failed" : String.valueOf(e.getMessage());
                    scheduleReconnect();
                }
            });
        } catch (Throwable t) {
            status.lastError = String.valueOf(t.getMessage());
            scheduleReconnect();
        }
    }

    @Override public void reconnect() {
        if (closed) return;
        backoff.set(0);
        reconnects.execute(this::connect);
    }

    private void scheduleReconnect() {
        if (closed) return;
        int attempt = backoff.incrementAndGet();
        long ceil = Math.min(30_000L, 1000L * (1L << Math.min(attempt, 5)));
        long delay = 250L + (long) (ThreadLocalRandom.current().nextDouble() * (ceil - 250L));
        try {
            reconnects.schedule(() -> { if (!closed) connect(); }, delay, TimeUnit.MILLISECONDS);
        } catch (Throwable ignored) {  }
    }

    private void scheduleRotation(String jwt) {
        long expMs = jwtExpMs(jwt);
        if (expMs <= 0) return;
        ScheduledFuture<?> prev = rotation;
        if (prev != null) prev.cancel(false);
        long jitter = (long) (ThreadLocalRandom.current().nextDouble() * ROTATE_JITTER_MS);
        long runway = expMs - autismclient.util.mm.ServerClock.nowMs();

        long delay = runway > ROTATE_LEAD_MS + ROTATE_JITTER_MS + 5_000L
            ? runway - ROTATE_LEAD_MS - jitter
            : Math.max(20_000L, runway / 2);
        try {
            rotation = reconnects.schedule(() -> { if (!closed) { backoff.set(0); connect(); } }, delay, TimeUnit.MILLISECONDS);
            if (logDiag())
                autismclient.AutismClientAddon.LOG.info("[mm-relay] {} token rotation in {}s", status.name, delay / 1000);
        } catch (Throwable ignored) {  }
    }

    private static long jwtExpMs(String jwt) {
        if (jwt == null || jwt.isEmpty()) return 0;
        try {
            int d1 = jwt.indexOf('.'), d2 = jwt.indexOf('.', d1 + 1);
            if (d1 <= 0 || d2 <= d1) return 0;
            byte[] payload = Base64.getUrlDecoder().decode(jwt.substring(d1 + 1, d2));
            com.google.gson.JsonObject o = com.google.gson.JsonParser
                .parseString(new String(payload, StandardCharsets.UTF_8)).getAsJsonObject();
            return o.has("exp") ? o.get("exp").getAsLong() * 1000L : 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    @Override public void publish(String topic, byte[] frame) { publish(topic, frame, false); }

    @Override public void publish(String topic, byte[] frame, boolean durable) {
        if (closed) return;
        MqttAsyncClient c = client;
        boolean up = c != null && c.isConnected();
        synchronized (queue) {
            if (!up) {

                if (durable) enqueueLocked(topic, frame);
                return;
            }
            if (!queue.isEmpty()) {

                enqueueLocked(topic, frame);
                drainLocked(c);
                return;
            }
        }
        if (!publishNow(c, topic, frame) && durable) {

            synchronized (queue) { enqueueLocked(topic, frame); }
        }
    }

    private void enqueueLocked(String topic, byte[] frame) {
        while (queue.size() >= MAX_QUEUED_FRAMES) { queue.pollFirst(); status.queueDropped.incrementAndGet(); }
        queue.addLast(new Queued(topic, frame, System.currentTimeMillis()));
    }

    private void drainLocked(MqttAsyncClient c) {
        long now = System.currentTimeMillis();
        for (Queued q; (q = queue.pollFirst()) != null; ) {
            if (now - q.enqueuedMs > QUEUE_TTL_MS) { status.queueDropped.incrementAndGet(); continue; }
            if (!publishNow(c, q.topic, q.frame)) { queue.addFirst(q); return; }
        }
    }

    private void flushQueued(MqttAsyncClient c) {
        synchronized (queue) { drainLocked(c); }
    }

    private boolean publishNow(MqttAsyncClient c, String topic, byte[] frame) {
        try {
            MqttMessage m = new MqttMessage(frame);
            m.setQos(0);
            m.setRetained(false);
            c.publish(topic, m);
            status.published.incrementAndGet();
            status.markActivity();
            return true;
        } catch (Throwable t) {
            status.lastError = String.valueOf(t.getMessage());
            return false;
        }
    }

    @Override public void subscribe(String topic, Consumer<byte[]> onFrame) {
        if (closed) return;
        subs.put(topic, onFrame);
        trySubscribe(topic);
    }

    private void trySubscribe(String topic) {
        MqttAsyncClient c = client;
        if (c == null || !c.isConnected()) return;
        try {

            c.subscribe(topic, 0, null, new IMqttActionListener() {
                @Override public void onSuccess(IMqttToken t) {
                    int[] q = t == null ? null : t.getGrantedQos();
                    int granted = q != null && q.length > 0 ? q[0] : -1;
                    if (logDiag())
                        autismclient.AutismClientAddon.LOG.info("[mm-relay] subscribe …{} -> {}",
                            tail(topic), granted >= 0x80 ? "DENIED" : "granted qos" + granted);
                }
                @Override public void onFailure(IMqttToken t, Throwable e) {
                    if (logDiag())
                        autismclient.AutismClientAddon.LOG.info("[mm-relay] subscribe …{} FAILED: {}",
                            tail(topic), e == null ? "?" : String.valueOf(e.getMessage()));
                }
            });
        } catch (Throwable t) {
            status.lastError = String.valueOf(t.getMessage());
        }
    }

    private static String tail(String topic) {
        return topic == null ? "?" : topic.substring(Math.max(0, topic.length() - 6));
    }

    @Override public void unsubscribe(String topic) {
        subs.remove(topic);
        MqttAsyncClient c = client;
        if (c != null && c.isConnected()) {
            try { c.unsubscribe(topic); } catch (Throwable ignored) {  }
        }
    }

    @Override public void close() {
        closed = true;
        ScheduledFuture<?> r = rotation;
        if (r != null) r.cancel(false);
        try { reconnects.shutdownNow(); } catch (Throwable ignored) {  }
        MqttAsyncClient c = client;
        if (c != null) quietClose(c);
        status.connected = false;
    }

    private static void quietClose(MqttAsyncClient c) {

        try { c.disconnectForcibly(0L, 250L); } catch (Throwable ignored) {  }
        try { c.close(true); } catch (Throwable ignored) {  }
    }

    private static final class Queued {
        final String topic;
        final byte[] frame;
        final long enqueuedMs;

        Queued(String topic, byte[] frame, long enqueuedMs) {
            this.topic = topic;
            this.frame = frame;
            this.enqueuedMs = enqueuedMs;
        }
    }
}
