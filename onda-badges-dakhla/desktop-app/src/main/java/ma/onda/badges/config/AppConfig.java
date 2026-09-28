package ma.onda.badges.config;

import java.io.File;
import java.io.FileInputStream; // to deal with the disk (input)
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class AppConfig {
    public static final String APP_NAME = "ONDA Badges Dakhla";
    private static final String CONFIG_FILE = "application.properties";
    private static final Properties PROPERTIES = loadProperties();

    private AppConfig() {
    }

    public static String get(String key) {
        return PROPERTIES.getProperty(key, "");
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        String value = PROPERTIES.getProperty(key);
        return value == null ? defaultValue : Boolean.parseBoolean(value);
    }

    public static int getInt(String key, int defaultValue) {
        String value = PROPERTIES.getProperty(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static void set(String key, String value) {
        PROPERTIES.setProperty(key, value);
    }

    private static Properties loadProperties() {
        Properties properties = new Properties();
        // First try to load from disk if modified application.properties exists in
        // current dir
        File localFile = new File(CONFIG_FILE);
        if (localFile.exists()) {
            try (InputStream input = new FileInputStream(localFile)) {
                properties.load(input);
                return properties;
            } catch (IOException ignored) {
            }
        }

        // Fallback to classpath
        try (InputStream input = AppConfig.class.getResourceAsStream("/" + CONFIG_FILE)) {
            if (input != null) {
                properties.load(input);
            }
        } catch (IOException ex) {
            System.err.println("Unable to load application properties: " + ex.getMessage());
        }
        return properties;
    }

}
