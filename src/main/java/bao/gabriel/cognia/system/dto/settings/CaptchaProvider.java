package bao.gabriel.cognia.system.dto.settings;

import java.util.Locale;

/**
 * Human-verification provider. Exactly one provider can be active at a time;
 * {@link #NONE} disables the capability without deleting stored credentials.
 */
public enum CaptchaProvider {
    NONE,
    TURNSTILE,
    GEETEST;

    /**
     * Case-insensitive lookup used when reading the database column. Returns
     * {@code null} for blank values (legacy rows created before the column
     * existed) and for unknown values so callers can decide on a fallback.
     */
    public static CaptchaProvider fromWire(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
