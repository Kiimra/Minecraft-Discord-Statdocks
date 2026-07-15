package dev.kiimra.statdock.discord;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.logging.Logger;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Thin, dependency-light Discord REST wrapper. Only two operations are needed:
 * renaming a channel and reading a channel's type/name for startup validation.
 * Uses the JDK {@link HttpClient} so no Discord library is bundled, and never
 * opens a gateway connection.
 */
public final class DiscordRestClient {

    private static final String API_BASE = "https://discord.com/api/v10";
    private static final String USER_AGENT =
            "DiscordStatdockUpdater (https://github.com/Kiimra/Minecraft-Discord-Statdock, 1.0.0)";

    private final String token;
    private final Logger logger;
    private final HttpClient http;

    public DiscordRestClient(String token, Logger logger) {
        this.token = token;
        this.logger = logger;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /** Renames a channel. Blocks until Discord responds or the request times out. */
    public EditResult setChannelName(String channelId, String name) {
        JsonObject body = new JsonObject();
        body.addProperty("name", name);
        try {
            HttpRequest request = baseRequest(channelId)
                    .timeout(Duration.ofSeconds(15))
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return interpret(response);
        } catch (Exception e) {
            return EditResult.of(EditResult.Outcome.TRANSIENT_ERROR, e.getMessage());
        }
    }

    /** Reads a channel's Discord type + current name for startup validation. */
    public Optional<ChannelInfo> fetchChannel(String channelId) {
        try {
            HttpRequest request = baseRequest(channelId)
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                int type = json.has("type") ? json.get("type").getAsInt() : -1;
                String name = json.has("name") && !json.get("name").isJsonNull()
                        ? json.get("name").getAsString() : "";
                return Optional.of(new ChannelInfo(type, name));
            }
            logger.warning("Could not read channel " + channelId + " (HTTP " + response.statusCode() + ").");
        } catch (Exception e) {
            logger.warning("Could not read channel " + channelId + " (" + e.getMessage() + ").");
        }
        return Optional.empty();
    }

    private HttpRequest.Builder baseRequest(String channelId) {
        return HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + "/channels/" + channelId))
                .header("Authorization", "Bot " + token)
                .header("Content-Type", "application/json")
                .header("User-Agent", USER_AGENT);
    }

    private EditResult interpret(HttpResponse<String> response) {
        int code = response.statusCode();
        if (code >= 200 && code < 300) {
            return EditResult.success();
        }
        switch (code) {
            case 401:
                return EditResult.of(EditResult.Outcome.UNAUTHORIZED, "Invalid bot token (HTTP 401).");
            case 403:
                return EditResult.of(EditResult.Outcome.FORBIDDEN,
                        "Bot lacks permission to edit this channel (HTTP 403).");
            case 404:
                return EditResult.of(EditResult.Outcome.NOT_FOUND, "Channel not found (HTTP 404).");
            case 429:
                return EditResult.rateLimited(parseRetryAfterMillis(response));
            default:
                if (code >= 500) {
                    return EditResult.of(EditResult.Outcome.TRANSIENT_ERROR, "Discord server error (HTTP " + code + ").");
                }
                return EditResult.of(EditResult.Outcome.TRANSIENT_ERROR, "Unexpected HTTP " + code + ".");
        }
    }

    private long parseRetryAfterMillis(HttpResponse<String> response) {
        // Prefer the JSON body's retry_after (seconds, may be fractional).
        try {
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            if (json.has("retry_after")) {
                double seconds = json.get("retry_after").getAsDouble();
                return Math.max(0, (long) Math.ceil(seconds * 1000.0));
            }
        } catch (Exception ignored) {
            // Fall through to header.
        }
        Optional<String> header = response.headers().firstValue("retry-after");
        if (header.isPresent()) {
            try {
                return Math.max(0, (long) Math.ceil(Double.parseDouble(header.get().trim()) * 1000.0));
            } catch (NumberFormatException ignored) {
                // ignore
            }
        }
        return 10_000L; // Sensible default when Discord doesn't tell us.
    }

    /** A channel's Discord numeric type and current name. */
    public record ChannelInfo(int type, String name) {

        public boolean isSupported() {
            // 2 = voice, 4 = category, 13 = stage voice (all renamable statdocks).
            return type == 2 || type == 4 || type == 13;
        }

        public String typeName() {
            return switch (type) {
                case 0 -> "text";
                case 2 -> "voice";
                case 4 -> "category";
                case 5 -> "announcement";
                case 13 -> "stage";
                default -> "type-" + type;
            };
        }
    }
}
