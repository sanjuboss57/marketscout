package com.marketscout.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Networking & Data Parsing:
 * Demonstrates making HTTP requests to external internet APIs and parsing JSON responses.
 */
@Slf4j
@Service
public class ExternalMarketDataService {

    private static final String EXTERNAL_API_URL = "https://open.er-api.com/v6/latest/USD";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final DatabaseService databaseService;
    private final Map<String, Object> latestForexRates = new ConcurrentHashMap<>();

    public ExternalMarketDataService(DatabaseService databaseService, ObjectMapper objectMapper) {
        this.databaseService = databaseService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @PostConstruct
    public void initFetch() {
        // Initial fetch on application startup
        fetchAndParseExternalMarketData();
    }

    /**
     * Periodically fetch live foreign exchange benchmarks to correlate with commodity prices.
     */
    @Scheduled(fixedRate = 60000) // Every 60 seconds
    public void fetchAndParseExternalMarketData() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(EXTERNAL_API_URL))
                    .timeout(Duration.ofSeconds(6))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            log.info("Sending outbound HTTP request to external API: {}", EXTERNAL_API_URL);

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                // Parse the JSON payload using Jackson ObjectMapper
                JsonNode rootNode = objectMapper.readTree(response.body());

                String baseCode = rootNode.path("base_code").asText("USD");
                JsonNode ratesNode = rootNode.path("rates");

                double eurRate = ratesNode.path("EUR").asDouble(0.92);
                double gbpRate = ratesNode.path("GBP").asDouble(0.79);
                double jpyRate = ratesNode.path("JPY").asDouble(155.20);
                double cadRate = ratesNode.path("CAD").asDouble(1.36);

                latestForexRates.put("base", baseCode);
                latestForexRates.put("EUR", eurRate);
                latestForexRates.put("GBP", gbpRate);
                latestForexRates.put("JPY", jpyRate);
                latestForexRates.put("CAD", cadRate);
                latestForexRates.put("status", "SUCCESS");
                latestForexRates.put("provider", "open.er-api.com");

                // Persist snapshot to SQLite database
                databaseService.saveExternalSnapshot(baseCode, eurRate, gbpRate, jpyRate, cadRate);

                log.info("Successfully fetched and parsed external JSON: USD/EUR={}, USD/GBP={}, USD/JPY={}",
                        eurRate, gbpRate, jpyRate);
            } else {
                log.warn("External API responded with non-200 status: {}", response.statusCode());
            }

        } catch (Exception e) {
            log.warn("Networking note: Could not reach external market API ({}). Using cached fallback.", e.getMessage());
            // Fallback default benchmark rates
            latestForexRates.putIfAbsent("base", "USD");
            latestForexRates.putIfAbsent("EUR", 0.92);
            latestForexRates.putIfAbsent("GBP", 0.79);
            latestForexRates.putIfAbsent("JPY", 155.40);
            latestForexRates.putIfAbsent("CAD", 1.36);
            latestForexRates.putIfAbsent("status", "CACHED_FALLBACK");
        }

        // Outbound HTTP requests to Coinbase API for real-time live crypto spot prices
        fetchLiveCryptoPrices();
    }

    private void fetchLiveCryptoPrices() {
        fetchCryptoSpot("BTC-USD", "BTC", 64500.0);
        fetchCryptoSpot("ETH-USD", "ETH", 3450.0);
        fetchCryptoSpot("SOL-USD", "SOL", 145.0);
    }

    private void fetchCryptoSpot(String pair, String symbol, double fallback) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.coinbase.com/v2/prices/" + pair + "/spot"))
                    .timeout(Duration.ofSeconds(4))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(resp.body());
                double price = root.path("data").path("amount").asDouble(fallback);
                if (price > 0) {
                    latestForexRates.put(symbol, price);
                }
            } else {
                latestForexRates.putIfAbsent(symbol, fallback);
            }
        } catch (Exception e) {
            latestForexRates.putIfAbsent(symbol, fallback);
        }
    }

    public Map<String, Object> getLatestRates() {
        return latestForexRates;
    }
}
