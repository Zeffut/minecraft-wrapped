package fr.zeffut.mcwrapped.telemetry;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EventContextTest {

    @Test
    void buildSuperPropsCarriesAppTagAndVersions() {
        final Map<String, Object> props = EventContext.buildSuperProps(
                "1.1.0", "1.21.11", "0.19.2", "0.141.3+1.21.11",
                "Mac OS X", "aarch64", "21.0.2", "fr_fr");

        assertEquals("minecraft-wrapped", props.get("app"));
        assertEquals("1.1.0", props.get("mod_version"));
        assertEquals("1.21.11", props.get("mc_version"));
        assertEquals("0.19.2", props.get("fabric_loader_version"));
        assertEquals("0.141.3+1.21.11", props.get("fabric_api_version"));
        assertEquals("Mac OS X", props.get("os_name"));
        assertEquals("aarch64", props.get("os_arch"));
        assertEquals("21.0.2", props.get("java_version"));
        assertEquals("fr_fr", props.get("language"));
    }
}
