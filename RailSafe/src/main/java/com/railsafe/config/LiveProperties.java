package com.railsafe.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for the additive live-tracking layer.
 *
 * The default provider mode is "unavailable". RailSafe must never invent a live
 * train position or a gate sensor reading, so a real provider has to be configured
 * explicitly in application.properties or with environment variables.
 */
@Component
@ConfigurationProperties(prefix = "railsafe.live")
public class LiveProperties {

    /** unavailable | http | simulation | railradar */
    private String providerMode = "unavailable";

    /**
     * Simulation mode is opt-in twice: the mode must be "simulation" AND this flag
     * must be true. That prevents a single accidental property from showing fake data.
     */
    private boolean simulationAcknowledged = false;

    private String providerName = "Authorised railway feed";
    private String feedUrl = "";
    private String feedApiKey = "";
    private String feedApiKeyHeader = "X-API-Key";
    private int feedTimeoutSeconds = 8;

    // ---------------------------------------------------------------------
    // RailRadar third-party live provider.
    //
    // RailRadar is an independent Indian Railways data API. It is NOT an
    // official Indian Railways, IRCTC or NTES feed, and every payload that
    // comes from it must stay labelled as third-party data.
    //
    // RailRadar's live status endpoint is per train, so RailSafe polls the
    // configured train numbers in rotation instead of calling the API for every
    // gate on every dashboard refresh. The monthly quota is treated as a hard
    // budget: when it is used up the provider stops calling out and the API
    // reports that the cached data is stale.
    // ---------------------------------------------------------------------
    private RailRadar railRadar = new RailRadar();

    /** How long a provider response may be reused by the server. */
    private long cacheSeconds = 20;

    /** A provider response older than this is treated as stale and predictions are withheld. */
    private long feedStaleSeconds = 300;

    /** If the provider omits gateCode, match trains within this distance of the gate. */
    private double matchRadiusKm = 5.0;

    private long lookaheadSeconds = 3600;
    private double nearGateKm = 0.6;
    private long nearGateSeconds = 180;
    private long crossingWindowSeconds = 120;
    private long crossingDurationSeconds = 240;
    private long passedWindowSeconds = 120;

    /** The barrier normally starts closing this many seconds before the train reaches it. */
    private long closeLeadSeconds = 180;

    /** Notify when an approaching train is this close (default 15 minutes). */
    private long approachAlertSeconds = 900;

    /** Notify when the estimated closure is this close (default 5 minutes). */
    private long closingSoonSeconds = 300;

    /** Notify when the estimated reopening is this close (default 5 minutes). */
    private long openingSoonSeconds = 300;

    /** How often the server evaluates alerts and stores provider snapshots. */
    private long alertIntervalMillis = 60000;

    private boolean alertsEnabled = true;
    private boolean simulationNotifications = false;

    /** Repeating demo timetable length. ETAs are projected onto this cycle. */
    private long simulationCycleSeconds = 1800;

    /** Safety buffer added after a train clears before the barrier is shown reopening. */
    private long openingBufferSeconds = 60;

    /**
     * If another train is due within this gap after the previous one clears, the
     * simulated barrier is kept closed instead of reopening between the two trains.
     */
    private long reopenGapSeconds = 300;

    /** Seed the synthetic demo dataset (20 gates, 30 trains, tracking, routes, alerts). */
    private boolean syntheticDataEnabled = true;

    private String osrmBaseUrl = "https://router.project-osrm.org";
    private int routingTimeoutSeconds = 10;
    private double routeAvoidRadiusMeters = 150;
    private double routeDetourOffsetMeters = 900;
    private int routeMaxAlternatives = 3;
    private long routeCacheSeconds = 60;

    /** Optional shared secret for the gate-sensor webhook. Blank disables the endpoint. */
    private String sensorApiKey = "";

    private long snapshotRetentionHours = 72;

    public String getProviderMode() {
        return providerMode;
    }

    public void setProviderMode(String providerMode) {
        this.providerMode = providerMode;
    }

    public boolean isSimulationAcknowledged() {
        return simulationAcknowledged;
    }

    public void setSimulationAcknowledged(boolean simulationAcknowledged) {
        this.simulationAcknowledged = simulationAcknowledged;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public String getFeedUrl() {
        return feedUrl;
    }

    public void setFeedUrl(String feedUrl) {
        this.feedUrl = feedUrl;
    }

    public String getFeedApiKey() {
        return feedApiKey;
    }

    public void setFeedApiKey(String feedApiKey) {
        this.feedApiKey = feedApiKey;
    }

    public String getFeedApiKeyHeader() {
        return feedApiKeyHeader;
    }

    public void setFeedApiKeyHeader(String feedApiKeyHeader) {
        this.feedApiKeyHeader = feedApiKeyHeader;
    }

    public int getFeedTimeoutSeconds() {
        return feedTimeoutSeconds;
    }

    public void setFeedTimeoutSeconds(int feedTimeoutSeconds) {
        this.feedTimeoutSeconds = feedTimeoutSeconds;
    }

    public RailRadar getRailRadar() {
        return railRadar;
    }

    public void setRailRadar(RailRadar railRadar) {
        this.railRadar = railRadar;
    }

    /**
     * Nested so the configuration keys stay grouped as
     * {@code railsafe.live.railradar.*}. Spring Boot relaxed binding treats the
     * prefix as case-insensitive and dash-insensitive.
     */
    public static class RailRadar {

        private String baseUrl = "https://api.railradar.in";
        private String apiKey = "";
        private String trainNumbers =
                "12702,12701,12760,12759,12727,12728,17011,17012,12713,12714";

        /** Lower bound between two requests. The public plan allows 10/minute. */
        private int minRequestIntervalSeconds = 8;

        /** Target spacing between polled trains. The quota rule may lengthen it. */
        private int pollIntervalSeconds = 10;

        /**
         * Requests per calendar month. 0 means "no local limit". The free plan is
         * 100/month; set this to the real limit of your plan.
         */
        private int monthlyQuota = 100;

        /** Requests spent on the first run so the dashboard is not empty. */
        private int initialBurst = 3;

        /** Stores the month/usage counters so a restart cannot reset the budget. */
        private String quotaFile = "data/railradar-quota.json";

        /**
         * Last successfully parsed trains. Restoring this on startup means a
         * restart does not lose real values or spend quota re-fetching them.
         */
        private String cacheFile = "data/railradar-cache.json";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getTrainNumbers() {
            return trainNumbers;
        }

        public void setTrainNumbers(String trainNumbers) {
            this.trainNumbers = trainNumbers;
        }

        public int getMinRequestIntervalSeconds() {
            return minRequestIntervalSeconds;
        }

        public void setMinRequestIntervalSeconds(int minRequestIntervalSeconds) {
            this.minRequestIntervalSeconds = minRequestIntervalSeconds;
        }

        public int getPollIntervalSeconds() {
            return pollIntervalSeconds;
        }

        public void setPollIntervalSeconds(int pollIntervalSeconds) {
            this.pollIntervalSeconds = pollIntervalSeconds;
        }

        public int getMonthlyQuota() {
            return monthlyQuota;
        }

        public void setMonthlyQuota(int monthlyQuota) {
            this.monthlyQuota = monthlyQuota;
        }

        public int getInitialBurst() {
            return initialBurst;
        }

        public void setInitialBurst(int initialBurst) {
            this.initialBurst = initialBurst;
        }

        public String getQuotaFile() {
            return quotaFile;
        }

        public void setQuotaFile(String quotaFile) {
            this.quotaFile = quotaFile;
        }

        public String getCacheFile() {
            return cacheFile;
        }

        public void setCacheFile(String cacheFile) {
            this.cacheFile = cacheFile;
        }
    }

    public long getCacheSeconds() {
        return cacheSeconds;
    }

    public void setCacheSeconds(long cacheSeconds) {
        this.cacheSeconds = cacheSeconds;
    }

    public long getFeedStaleSeconds() {
        return feedStaleSeconds;
    }

    public void setFeedStaleSeconds(long feedStaleSeconds) {
        this.feedStaleSeconds = feedStaleSeconds;
    }

    public double getMatchRadiusKm() {
        return matchRadiusKm;
    }

    public void setMatchRadiusKm(double matchRadiusKm) {
        this.matchRadiusKm = matchRadiusKm;
    }

    public long getLookaheadSeconds() {
        return lookaheadSeconds;
    }

    public void setLookaheadSeconds(long lookaheadSeconds) {
        this.lookaheadSeconds = lookaheadSeconds;
    }

    public double getNearGateKm() {
        return nearGateKm;
    }

    public void setNearGateKm(double nearGateKm) {
        this.nearGateKm = nearGateKm;
    }

    public long getNearGateSeconds() {
        return nearGateSeconds;
    }

    public void setNearGateSeconds(long nearGateSeconds) {
        this.nearGateSeconds = nearGateSeconds;
    }

    public long getCrossingWindowSeconds() {
        return crossingWindowSeconds;
    }

    public void setCrossingWindowSeconds(long crossingWindowSeconds) {
        this.crossingWindowSeconds = crossingWindowSeconds;
    }

    public long getCrossingDurationSeconds() {
        return crossingDurationSeconds;
    }

    public void setCrossingDurationSeconds(long crossingDurationSeconds) {
        this.crossingDurationSeconds = crossingDurationSeconds;
    }

    public long getPassedWindowSeconds() {
        return passedWindowSeconds;
    }

    public void setPassedWindowSeconds(long passedWindowSeconds) {
        this.passedWindowSeconds = passedWindowSeconds;
    }

    public long getCloseLeadSeconds() {
        return closeLeadSeconds;
    }

    public void setCloseLeadSeconds(long closeLeadSeconds) {
        this.closeLeadSeconds = closeLeadSeconds;
    }

    public long getApproachAlertSeconds() {
        return approachAlertSeconds;
    }

    public void setApproachAlertSeconds(long approachAlertSeconds) {
        this.approachAlertSeconds = approachAlertSeconds;
    }

    public long getClosingSoonSeconds() {
        return closingSoonSeconds;
    }

    public void setClosingSoonSeconds(long closingSoonSeconds) {
        this.closingSoonSeconds = closingSoonSeconds;
    }

    public long getOpeningSoonSeconds() {
        return openingSoonSeconds;
    }

    public void setOpeningSoonSeconds(long openingSoonSeconds) {
        this.openingSoonSeconds = openingSoonSeconds;
    }

    public long getAlertIntervalMillis() {
        return alertIntervalMillis;
    }

    public void setAlertIntervalMillis(long alertIntervalMillis) {
        this.alertIntervalMillis = alertIntervalMillis;
    }

    public boolean isAlertsEnabled() {
        return alertsEnabled;
    }

    public void setAlertsEnabled(boolean alertsEnabled) {
        this.alertsEnabled = alertsEnabled;
    }

    public boolean isSimulationNotifications() {
        return simulationNotifications;
    }

    public void setSimulationNotifications(boolean simulationNotifications) {
        this.simulationNotifications = simulationNotifications;
    }

    public long getSimulationCycleSeconds() {
        return simulationCycleSeconds;
    }

    public void setSimulationCycleSeconds(long simulationCycleSeconds) {
        this.simulationCycleSeconds = simulationCycleSeconds;
    }

    public long getOpeningBufferSeconds() {
        return openingBufferSeconds;
    }

    public void setOpeningBufferSeconds(long openingBufferSeconds) {
        this.openingBufferSeconds = openingBufferSeconds;
    }

    public long getReopenGapSeconds() {
        return reopenGapSeconds;
    }

    public void setReopenGapSeconds(long reopenGapSeconds) {
        this.reopenGapSeconds = reopenGapSeconds;
    }

    public boolean isSyntheticDataEnabled() {
        return syntheticDataEnabled;
    }

    public void setSyntheticDataEnabled(boolean syntheticDataEnabled) {
        this.syntheticDataEnabled = syntheticDataEnabled;
    }

    public String getOsrmBaseUrl() {
        return osrmBaseUrl;
    }

    public void setOsrmBaseUrl(String osrmBaseUrl) {
        this.osrmBaseUrl = osrmBaseUrl;
    }

    public int getRoutingTimeoutSeconds() {
        return routingTimeoutSeconds;
    }

    public void setRoutingTimeoutSeconds(int routingTimeoutSeconds) {
        this.routingTimeoutSeconds = routingTimeoutSeconds;
    }

    public double getRouteAvoidRadiusMeters() {
        return routeAvoidRadiusMeters;
    }

    public void setRouteAvoidRadiusMeters(double routeAvoidRadiusMeters) {
        this.routeAvoidRadiusMeters = routeAvoidRadiusMeters;
    }

    public double getRouteDetourOffsetMeters() {
        return routeDetourOffsetMeters;
    }

    public void setRouteDetourOffsetMeters(double routeDetourOffsetMeters) {
        this.routeDetourOffsetMeters = routeDetourOffsetMeters;
    }

    public int getRouteMaxAlternatives() {
        return routeMaxAlternatives;
    }

    public void setRouteMaxAlternatives(int routeMaxAlternatives) {
        this.routeMaxAlternatives = routeMaxAlternatives;
    }

    public long getRouteCacheSeconds() {
        return routeCacheSeconds;
    }

    public void setRouteCacheSeconds(long routeCacheSeconds) {
        this.routeCacheSeconds = routeCacheSeconds;
    }

    public String getSensorApiKey() {
        return sensorApiKey;
    }

    public void setSensorApiKey(String sensorApiKey) {
        this.sensorApiKey = sensorApiKey;
    }

    public long getSnapshotRetentionHours() {
        return snapshotRetentionHours;
    }

    public void setSnapshotRetentionHours(long snapshotRetentionHours) {
        this.snapshotRetentionHours = snapshotRetentionHours;
    }
}
