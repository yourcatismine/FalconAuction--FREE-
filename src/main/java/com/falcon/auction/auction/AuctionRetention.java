package com.falcon.auction.auction;

/** Unclaimed auction items are deleted 24 hours after the listing expires. */
public final class AuctionRetention {
    public static final long CLAIM_PERIOD_MILLIS = 24L * 60 * 60 * 1000;

    private AuctionRetention() {}

    public static long deleteAt(long listedAt, int durationSeconds) {
        return listedAt + durationSeconds * 1000L + CLAIM_PERIOD_MILLIS;
    }

    public static boolean isClaimExpired(long listedAt, int durationSeconds, long now) {
        return now >= deleteAt(listedAt, durationSeconds);
    }
}
