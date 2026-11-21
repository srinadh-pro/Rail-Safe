package com.railsafe.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.railsafe.live.provider.HttpJsonTrainDataProvider;
import com.railsafe.live.provider.RailRadarTrainDataProvider;
import com.railsafe.live.provider.SimulationTrainDataProvider;
import com.railsafe.live.provider.TrainDataProvider;
import com.railsafe.live.provider.UnavailableTrainDataProvider;
import com.railsafe.repository.RailwayGateRepository;
import com.railsafe.repository.TrainTrackingRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Locale;

/**
 * Builds the configured train provider. The factory deliberately has an explicit
 * fallback so a missing provider can never silently turn into invented live data.
 */
@Configuration
public class LiveConfig {

    @Bean
    public TrainDataProvider trainDataProvider(LiveProperties properties,
                                               RailwayGateRepository gateRepository,
                                               TrainTrackingRepository trackingRepository,
                                               ObjectMapper objectMapper,
                                               @Qualifier("liveRestClient") RestClient liveRestClient) {
        String mode = properties.getProviderMode() == null
                ? "unavailable"
                : properties.getProviderMode().trim().toLowerCase(Locale.ROOT);

        return switch (mode) {
            case "http" -> {
                if (properties.getFeedUrl() == null || properties.getFeedUrl().isBlank()) {
                    yield new UnavailableTrainDataProvider(
                            "Live train data unavailable: railsafe.live.feed-url is not configured.");
                }
                yield new HttpJsonTrainDataProvider(
                        properties.getProviderName(),
                        properties.getFeedUrl(),
                        properties.getFeedApiKey(),
                        properties.getFeedApiKeyHeader(),
                        properties.getFeedStaleSeconds(),
                        liveRestClient,
                        objectMapper
                );
            }
            case "simulation" -> {
                if (!properties.isSimulationAcknowledged()) {
                    yield new UnavailableTrainDataProvider(
                            "Simulation mode is disabled until railsafe.live.simulation-acknowledged=true. "
                                    + "Live train data unavailable.");
                }
                yield new SimulationTrainDataProvider(trackingRepository, properties);
            }
            case "railradar", "rail-radar", "third-party", "thirdparty" ->
                    new RailRadarTrainDataProvider(properties, gateRepository,
                            liveRestClient, objectMapper);
            default -> new UnavailableTrainDataProvider(
                    "Live train data unavailable. No authorised railway data provider is configured.");
        };
    }

    @Bean("liveRestClient")
    public RestClient liveRestClient(LiveProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(Math.max(2, properties.getFeedTimeoutSeconds())));
        factory.setReadTimeout(Duration.ofSeconds(Math.max(2, properties.getFeedTimeoutSeconds())));
        return RestClient.builder().requestFactory(factory).build();
    }

    @Bean("routingRestClient")
    public RestClient routingRestClient(LiveProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(Math.max(2, properties.getRoutingTimeoutSeconds())));
        factory.setReadTimeout(Duration.ofSeconds(Math.max(2, properties.getRoutingTimeoutSeconds())));
        return RestClient.builder().requestFactory(factory).build();
    }
}
