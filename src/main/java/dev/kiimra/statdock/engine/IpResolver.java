package dev.kiimra.statdock.engine;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Logger;
import dev.kiimra.statdock.config.PluginConfig.IpConfig;

/**
 * Resolves the {@code {ip}} placeholder once, in priority order:
 * <ol>
 *   <li>the {@code ip.override} config value (most reliable, e.g. play.server.com)</li>
 *   <li>the server's configured {@code server-ip}</li>
 *   <li>a one-off public-IP lookup via api.ipify.org (opt-out via config)</li>
 * </ol>
 * Falls back to a configurable "unknown" string if nothing resolves.
 */
public final class IpResolver {

    private final IpConfig config;
    private final Logger logger;

    public IpResolver(IpConfig config, Logger logger) {
        this.config = config;
        this.logger = logger;
    }

    public String resolve(String serverIp) {
        if (config.override() != null && !config.override().isBlank()) {
            return config.override().trim();
        }
        if (serverIp != null && !serverIp.isBlank()) {
            return serverIp.trim();
        }
        if (config.resolvePublicIp()) {
            String publicIp = lookupPublicIp();
            if (publicIp != null) {
                return publicIp;
            }
        }
        return config.unknownText();
    }

    private String lookupPublicIp() {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.ipify.org"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                String body = response.body().trim();
                if (!body.isEmpty()) {
                    return body;
                }
            }
            logger.warning("Public IP lookup returned HTTP " + response.statusCode() + "; using fallback for {ip}.");
        } catch (Exception e) {
            logger.warning("Could not resolve public IP (" + e.getMessage() + "); using fallback for {ip}.");
        }
        return null;
    }
}
