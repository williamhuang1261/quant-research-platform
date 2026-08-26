package io.github.williamhuang1261.qrp.report;

import io.github.williamhuang1261.qrp.core.Instrument;
import java.util.Objects;

/**
 * A fund's identity for reporting purposes: what it is called, what it holds,
 * and what it costs.
 *
 * <p>The fee lives here rather than on {@link Instrument} because it is a
 * property of the fund wrapper sold to an investor, not of the underlying
 * security the platform already models — the same instrument could back two
 * funds charging different fees.
 */
public record FundProfile(String displayName, Instrument instrument, ManagementFeeModel fee) {

    public FundProfile {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        Objects.requireNonNull(instrument, "instrument");
        Objects.requireNonNull(fee, "fee");
    }
}
