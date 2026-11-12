package net.exylia.exyliaLobby.module.movement;

import net.exylia.lib.config.MutableConfig;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PadMigrationTest {

    @Test
    void identicalPadsMergeAndOthersStay() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                pads:
                  list:
                    pad-1: {world: world, x: 0, y: 100, z: -3, power: 1.6, vertical: 0.9, fixed: false, yaw: 0}
                    pad-2: {world: world, x: 1, y: 100, z: -3, power: 1.6, vertical: 0.9, fixed: false, yaw: 0}
                    pad-3: {world: world, x: 2, y: 100, z: -3, power: 3.0, vertical: 0.9, fixed: true, yaw: 90}
                    pad-4: {world: world, x: 0, y: 99, z: -1, power: 1.6, vertical: 0.9, fixed: false, yaw: 0}
                """);
        MovementSettings.oneBlockPerPad(new MutableConfig() {
            public Object get(String path) { return yaml.get(path); }
            public void set(String path, Object value) { yaml.set(path, value); }
            public void remove(String path) { yaml.set(path, null); }
            public boolean contains(String path) { return yaml.contains(path); }
        });
        assertEquals(List.of("world 0 100 -3", "world 1 100 -3", "world 0 99 -1"), yaml.getStringList("pads.list.pad-1.blocks"));
        assertEquals(List.of("world 2 100 -3"), yaml.getStringList("pads.list.pad-3.blocks"));
        assertEquals(List.of("pad-1", "pad-3"), List.copyOf(yaml.getConfigurationSection("pads.list").getKeys(false)));
        assertFalse(yaml.contains("pads.list.pad-1.world"));
    }
}
