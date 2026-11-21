package com.railsafe.live.provider;

import com.railsafe.live.model.TrainFeed;

/**
 * Integration point for an authorised railway data provider.
 *
 * Implement this interface (or use the built-in HTTP JSON adapter) with a provider
 * you are licensed to use. RailSafe never generates live positions itself.
 */
public interface TrainDataProvider {

    String providerId();

    TrainFeed fetch();

    default boolean isConfigured() {
        return true;
    }
}
