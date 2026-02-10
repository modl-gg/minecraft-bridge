package gg.modl.bridge.http;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import gg.modl.bridge.http.request.CreatePunishmentRequest;
import gg.modl.bridge.http.request.CreateTicketRequest;
import gg.modl.bridge.http.response.CreateTicketResponse;
import gg.modl.bridge.http.response.PunishmentCreateResponse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Logger;

public class BridgeHttpClient {

    private final String baseUrl;
    private final String apiKey;
    private final String serverDomain;
    private final boolean debug;
    private final HttpClient httpClient;
    private final ExecutorService executor;
    private final Gson gson;
    private final Logger logger;

    public BridgeHttpClient(String baseUrl, String apiKey, String serverDomain, boolean debug, Logger logger) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.serverDomain = serverDomain;
        this.debug = debug;
        this.logger = logger;
        this.gson = new Gson();

        ThreadFactory threadFactory = r -> {
            Thread t = new Thread(r, "modl-bridge-http");
            t.setDaemon(true);
            return t;
        };

        this.executor = Executors.newCachedThreadPool(threadFactory);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .executor(executor)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    private HttpRequest.Builder requestBuilder(String endpoint) {
        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + endpoint))
                .header("X-API-Key", apiKey)
                .header("X-Server-Domain", serverDomain)
                .header("Content-Type", "application/json");
    }

    public CompletableFuture<PunishmentCreateResponse> createPunishment(CreatePunishmentRequest request) {
        String body = gson.toJson(request);
        if (debug) {
            logger.info("[ModlBridge] POST /minecraft/punishments/dynamic - Body: " + body);
        }

        return sendAsync(
                requestBuilder("/minecraft/punishments/dynamic")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                PunishmentCreateResponse.class
        );
    }

    public CompletableFuture<CreateTicketResponse> createTicket(CreateTicketRequest request) {
        String body = gson.toJson(request);
        if (debug) {
            logger.info("[ModlBridge] POST /minecraft/tickets - Body: " + body);
        }

        return sendAsync(
                requestBuilder("/minecraft/tickets")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                CreateTicketResponse.class
        );
    }

    private <T> CompletableFuture<T> sendAsync(HttpRequest request, Class<T> responseType) {
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (debug) {
                        logger.info("[ModlBridge] Response " + response.statusCode() + ": " + response.body());
                    }

                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        if (responseType == Void.class) {
                            return null;
                        }
                        return gson.fromJson(response.body(), responseType);
                    }

                    String errorMessage;
                    try {
                        JsonObject errorResponse = gson.fromJson(response.body(), JsonObject.class);
                        if (errorResponse != null && errorResponse.has("message")) {
                            errorMessage = errorResponse.get("message").getAsString();
                        } else {
                            errorMessage = "HTTP " + response.statusCode() + ": " + response.body();
                        }
                    } catch (Exception e) {
                        errorMessage = "HTTP " + response.statusCode() + ": " + response.body();
                    }

                    logger.warning("[ModlBridge] API request failed: " + errorMessage);
                    throw new RuntimeException(errorMessage);
                })
                .exceptionally(throwable -> {
                    if (throwable instanceof RuntimeException) {
                        throw (RuntimeException) throwable;
                    }
                    logger.severe("[ModlBridge] HTTP request failed: " + throwable.getMessage());
                    throw new RuntimeException("HTTP request failed", throwable);
                });
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
