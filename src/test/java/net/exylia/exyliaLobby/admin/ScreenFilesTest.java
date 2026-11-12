package net.exylia.exyliaLobby.admin;

import net.exylia.lib.screen.ScreenMarkup;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The players' screens as shipped: each reads, and speaks every language. */
class ScreenFilesTest {

    private static final Path SCREENS = Path.of("src/main/resources/screens");

    @ParameterizedTest
    @ValueSource(strings = {"gadgets", "parkour"})
    void readsWithoutProblems(String id) throws IOException {
        // the actions are the plugin's, which only a server registers
        List<String> problems = ScreenMarkup.load(SCREENS.resolve(id + ".html"), SCREENS.resolve("theme.css")).problems().stream()
                .filter(problem -> !problem.contains("no action \"exylialobby:"))
                .toList();
        assertEquals(List.of(), problems);
    }

    @ParameterizedTest
    @ValueSource(strings = {"gadgets", "parkour"})
    void everyLanguageHasEveryWord(String id) throws IOException {
        String markup = Files.readString(SCREENS.resolve(id + ".html")).replaceAll("(?s)<!--.*?-->", "");
        List<String> keys = Pattern.compile("\\bt(?:-[a-z]+)?=\"([a-z_.]+)\"").matcher(markup).results()
                .map(match -> match.group(1)).distinct().toList();
        assertTrue(keys.size() > 10, "found " + keys.size());
        for (String code : List.of("en", "es", "fr", "pt", "ru", "tr")) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                    Path.of("src/main/resources/lang", code, "screens", id + ".yml").toFile());
            for (String key : keys) assertTrue(yaml.isString(key), code + "/" + id + " misses " + key);
        }
    }
}
