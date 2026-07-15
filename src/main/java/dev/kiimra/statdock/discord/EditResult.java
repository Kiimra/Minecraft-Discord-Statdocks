package dev.kiimra.statdock.discord;

/** Outcome of a Discord channel edit or lookup. */
public record EditResult(Outcome outcome, long retryAfterMillis, String detail) {

    public enum Outcome {
        /** The edit was applied. */
        SUCCESS,
        /** Discord returned 429; retry after {@link #retryAfterMillis}. */
        RATE_LIMITED,
        /** The channel no longer exists (404) - it should be skipped. */
        NOT_FOUND,
        /** Bad/expired token (401). */
        UNAUTHORIZED,
        /** Token is valid but lacks permission on this channel (403). */
        FORBIDDEN,
        /** Network hiccup or 5xx - safe to retry later. */
        TRANSIENT_ERROR
    }

    public static EditResult success() {
        return new EditResult(Outcome.SUCCESS, 0, null);
    }

    public static EditResult rateLimited(long retryAfterMillis) {
        return new EditResult(Outcome.RATE_LIMITED, retryAfterMillis, null);
    }

    public static EditResult of(Outcome outcome, String detail) {
        return new EditResult(outcome, 0, detail);
    }

    public boolean isSuccess() {
        return outcome == Outcome.SUCCESS;
    }
}
