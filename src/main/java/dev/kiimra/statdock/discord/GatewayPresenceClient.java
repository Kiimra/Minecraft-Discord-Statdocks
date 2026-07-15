package dev.kiimra.statdock.discord;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;
import java.util.logging.Logger;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kiimra.statdock.config.PluginConfig.PresenceConfig;

/**
 * A deliberately minimal Discord Gateway connection whose only job is to keep
 * the bot's presence (status text) updated. It identifies with zero intents,
 * sends heartbeats, cycles the configured messages, and reconnects with
 * backoff - it never subscribes to or caches any events. Only started when
 * {@code presence.enabled} is true; otherwise the plugin is pure REST and the
 * bot simply shows as offline in Discord.
 */
public final class GatewayPresenceClient {

    private static final String GATEWAY_URL = "wss://gateway.discord.gg/?v=10&encoding=json";
    private static final long MAX_BACKOFF_MILLIS = 60_000L;

    private final String token;
    private final PresenceConfig config;
    private final UnaryOperator<String> placeholderResolver;
    private final Logger logger;

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "Statdock-Gateway");
                t.setDaemon(true);
                return t;
            });
    private final StringBuilder buffer = new StringBuilder();
    private final AtomicInteger messageIndex = new AtomicInteger(0);

    private volatile boolean running;
    private volatile WebSocket webSocket;
    private volatile Integer lastSeq;
    private volatile long backoffMillis = 5_000L;
    private ScheduledFuture<?> heartbeatTask;
    private ScheduledFuture<?> presenceTask;

    public GatewayPresenceClient(String token, PresenceConfig config,
                                 UnaryOperator<String> placeholderResolver, Logger logger) {
        this.token = token;
        this.config = config;
        this.placeholderResolver = placeholderResolver;
        this.logger = logger;
    }

    public void start() {
        running = true;
        connect();
    }

    public void stop() {
        running = false;
        cancelTasks();
        WebSocket ws = this.webSocket;
        if (ws != null) {
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown");
            } catch (Exception ignored) {
                // best effort
            }
        }
        scheduler.shutdownNow();
    }

    private void connect() {
        if (!running) {
            return;
        }
        try {
            buffer.setLength(0);
            HttpClient.newHttpClient()
                    .newWebSocketBuilder()
                    .buildAsync(URI.create(GATEWAY_URL), new Listener())
                    .whenComplete((ws, err) -> {
                        if (err != null) {
                            logger.warning("Presence gateway connect failed (" + err.getMessage() + "); retrying.");
                            scheduleReconnect();
                        } else {
                            this.webSocket = ws;
                        }
                    });
        } catch (Exception e) {
            logger.warning("Presence gateway error (" + e.getMessage() + "); retrying.");
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        if (!running) {
            return;
        }
        cancelTasks();
        long delay = backoffMillis;
        backoffMillis = Math.min(backoffMillis * 2, MAX_BACKOFF_MILLIS);
        scheduler.schedule(this::connect, delay, TimeUnit.MILLISECONDS);
    }

    private void cancelTasks() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
            heartbeatTask = null;
        }
        if (presenceTask != null) {
            presenceTask.cancel(false);
            presenceTask = null;
        }
    }

    private void handle(String raw) {
        JsonObject payload;
        try {
            payload = JsonParser.parseString(raw).getAsJsonObject();
        } catch (Exception e) {
            return;
        }
        if (payload.has("s") && !payload.get("s").isJsonNull()) {
            lastSeq = payload.get("s").getAsInt();
        }
        int op = payload.has("op") ? payload.get("op").getAsInt() : -1;
        switch (op) {
            case 10 -> { // HELLO
                long interval = payload.getAsJsonObject("d").get("heartbeat_interval").getAsLong();
                startHeartbeat(interval);
                send(identifyPayload());
            }
            case 1 -> send(heartbeatPayload()); // Discord asked for a heartbeat
            case 7, 9 -> scheduleReconnect(); // RECONNECT / INVALID SESSION
            case 0 -> { // DISPATCH
                String type = payload.has("t") && !payload.get("t").isJsonNull()
                        ? payload.get("t").getAsString() : "";
                if ("READY".equals(type)) {
                    backoffMillis = 5_000L; // healthy connection; reset backoff
                    startPresenceCycle();
                }
            }
            default -> {
                // op 11 (heartbeat ACK) and anything else: nothing to do.
            }
        }
    }

    private void startHeartbeat(long intervalMillis) {
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
        }
        heartbeatTask = scheduler.scheduleAtFixedRate(
                () -> send(heartbeatPayload()), intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
    }

    private void startPresenceCycle() {
        if (presenceTask != null) {
            presenceTask.cancel(false);
        }
        long period = Math.max(15L, config.intervalSeconds());
        presenceTask = scheduler.scheduleAtFixedRate(
                this::sendNextPresence, 0, period, TimeUnit.SECONDS);
    }

    private void sendNextPresence() {
        List<String> messages = config.messages();
        if (messages.isEmpty()) {
            return;
        }
        int idx = Math.floorMod(messageIndex.getAndIncrement(), messages.size());
        String text = placeholderResolver.apply(messages.get(idx));
        send(presencePayload(text));
    }

    private void send(String json) {
        WebSocket ws = this.webSocket;
        if (ws == null) {
            return;
        }
        // Route every send through the single scheduler thread so we never have
        // two outstanding sends at once (a WebSocket API requirement).
        scheduler.execute(() -> {
            try {
                ws.sendText(json, true).get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                // The listener's onError/onClose will drive reconnection.
            }
        });
    }

    private String identifyPayload() {
        JsonObject properties = new JsonObject();
        properties.addProperty("os", "linux");
        properties.addProperty("browser", "DiscordStatdockUpdater");
        properties.addProperty("device", "DiscordStatdockUpdater");

        JsonObject d = new JsonObject();
        d.addProperty("token", token);
        d.addProperty("intents", 0);
        d.add("properties", properties);
        d.add("presence", initialPresence());

        JsonObject payload = new JsonObject();
        payload.addProperty("op", 2);
        payload.add("d", d);
        return payload.toString();
    }

    private JsonObject initialPresence() {
        List<String> messages = config.messages();
        String text = messages.isEmpty() ? "" : placeholderResolver.apply(messages.get(0));
        return presenceData(text);
    }

    private String presencePayload(String text) {
        JsonObject payload = new JsonObject();
        payload.addProperty("op", 3);
        payload.add("d", presenceData(text));
        return payload.toString();
    }

    private JsonObject presenceData(String text) {
        JsonObject activity = new JsonObject();
        activity.addProperty("name", text);
        activity.addProperty("type", config.activityTypeId());

        JsonArray activities = new JsonArray();
        activities.add(activity);

        JsonObject d = new JsonObject();
        d.add("since", com.google.gson.JsonNull.INSTANCE);
        d.add("activities", activities);
        d.addProperty("status", config.status());
        d.addProperty("afk", false);
        return d;
    }

    private String heartbeatPayload() {
        JsonObject payload = new JsonObject();
        payload.addProperty("op", 1);
        if (lastSeq == null) {
            payload.add("d", com.google.gson.JsonNull.INSTANCE);
        } else {
            payload.addProperty("d", lastSeq);
        }
        return payload.toString();
    }

    private final class Listener implements WebSocket.Listener {
        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String message = buffer.toString();
                buffer.setLength(0);
                try {
                    handle(message);
                } catch (Exception e) {
                    logger.warning("Presence gateway message error (" + e.getMessage() + ").");
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            if (running) {
                scheduleReconnect();
            }
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            if (running) {
                logger.warning("Presence gateway connection error (" + error.getMessage() + "); reconnecting.");
                scheduleReconnect();
            }
        }
    }
}
