package bao.gabriel.cognia.common.util;

import java.util.Locale;
import java.util.Set;

/**
 * Route-level reserved identifiers. A user username and a team slug share the
 * same one-segment URL namespace, so neither may use a reserved word.
 */
public final class ReservedNames {

    private static final Set<String> RESERVED = Set.of(
            "login", "logout", "signin", "sign-in", "signup", "sign-up", "sign",
            "settings", "admin", "console", "notifications", "notification",
            "explore", "api", "about", "chat", "teams", "team", "users", "user",
            "channel", "channels", "kb", "storage", "audit", "task", "tasks",
            "download", "favorites", "favorite", "legal", "privacy", "terms",
            "avatar", "avatars", "static", "assets", "public", "system", "help",
            "docs", "doc", "new", "search", "profile", "profiles", "mail",
            "s3", "minio", "root", "www", "support", "status", "account",
            "accounts", "security", "verify", "oauth", "callback", "webhook",
            "readme", "agent", "recycle", "trash", "dashboard", "home", "index"
    );

    private ReservedNames() {
    }

    public static boolean isReserved(String value) {
        return value != null && RESERVED.contains(value.trim().toLowerCase(Locale.ROOT));
    }

    public static void assertNotReserved(String value, String label) {
        if (isReserved(value)) {
            throw new IllegalArgumentException(label + "「" + value + "」是系统保留字，请更换");
        }
    }
}
