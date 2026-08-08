package com.ceclientmod.version;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class BridgeClientTarget {
    private static final String MINECRAFT_TARGET = readTarget();

    private BridgeClientTarget() {
    }

    public static String minecraftTarget() {
        return MINECRAFT_TARGET;
    }

    private static String readTarget() {
        Properties properties = new Properties();
        try (InputStream input = BridgeClientTarget.class.getResourceAsStream("/bridge-target.properties")) {
            if (input == null) throw new IllegalStateException("bridge-target.properties is missing");
            properties.load(input);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read bridge target metadata", e);
        }
        String target = properties.getProperty("minecraft_target");
        if (target == null || target.isBlank()) throw new IllegalStateException("minecraft_target is missing");
        return target;
    }
}
