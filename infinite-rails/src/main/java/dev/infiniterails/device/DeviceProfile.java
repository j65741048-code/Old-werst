package dev.infiniterails.device;

import dev.infiniterails.config.InfiniteRailsConfig;

import java.util.Locale;
import java.util.regex.Pattern;

/** Works out whether the game process is running on a phone/tablet launcher or on a normal PC. */
public final class DeviceProfile {

    public enum Kind { MOBILE, DESKTOP }

    private static final Pattern MOBILE_KEY =
            Pattern.compile("(?i)^(pojav|fcl|zalith|zl_|mg_|mobileglues|android|app_runtime).*");

    private static Kind detected;

    private DeviceProfile() {
    }

    public static Kind kind() {
        int override = 0;
        try {
            override = InfiniteRailsConfig.server().deviceClass.get();
        } catch (RuntimeException notLoaded) {
            // config not loaded yet, fall through to detection
        }
        if (override == 1) {
            return Kind.MOBILE;
        }
        if (override == 2) {
            return Kind.DESKTOP;
        }
        if (detected == null) {
            detected = detect();
        }
        return detected;
    }

    private static Kind detect() {
        for (String key : System.getenv().keySet()) {
            if (MOBILE_KEY.matcher(key).matches()) {
                return Kind.MOBILE;
            }
        }
        for (Object key : System.getProperties().keySet()) {
            if (MOBILE_KEY.matcher(String.valueOf(key)).matches()) {
                return Kind.MOBILE;
            }
        }
        String vendor = (System.getProperty("java.vendor", "") + System.getProperty("java.vm.vendor", ""))
                .toLowerCase(Locale.ROOT);
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (vendor.contains("android") || os.contains("android")) {
            return Kind.MOBILE;
        }
        return Kind.DESKTOP;
    }
}
