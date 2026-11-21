package com.railsafe.live.provider;

import com.railsafe.live.model.TrainFeed;

/** Default provider: no authorised feed configured, so no live data is shown. */
public class UnavailableTrainDataProvider implements TrainDataProvider {

    private final String reason;

    public UnavailableTrainDataProvider(String reason) {
        this.reason = reason;
    }

    @Override
    public String providerId() {
        return "Not configured";
    }

    @Override
    public TrainFeed fetch() {
        return TrainFeed.unavailable(providerId(), reason);
    }

    @Override
    public boolean isConfigured() {
        return false;
    }
}
