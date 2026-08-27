package eu.purrtech.purrtechQuest.config;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers {@link MenuLayoutConfig}'s two jobs: resolving a technician's custom {@code menus.yml} values with
 * bounds-checking (never letting an invalid override crash a GUI), and the same self-healing merge behavior
 * {@link MessagesConfig} has for {@code lang/*.yml} — missing keys get filled in, existing customizations
 * never get touched.
 */
class MenuLayoutConfigTest {

    @Test
    void newlyExtractedFileMatchesBundledDefaultVerbatim(@TempDir Path tempDir) {
        Path dataFolder = tempDir.resolve("plugin-data");
        MenuLayoutConfig config = MenuLayoutConfig.load(mockPlugin(dataFolder));

        assertEquals(27, config.resolveSize("quest-category", -1));
        assertEquals(22, config.resolveSlot("quest-category", "close", -1, 27));
    }

    @Test
    void aCustomSizeAndSlotAreHonored(@TempDir Path tempDir) throws IOException {
        Path dataFolder = tempDir.resolve("plugin-data");
        writeMenusFile(dataFolder, """
                quest-category:
                  size: 36
                  slots:
                    guide: 13
                    close: 31
                """);

        MenuLayoutConfig config = MenuLayoutConfig.load(mockPlugin(dataFolder));
        int size = config.resolveSize("quest-category", 27);

        assertEquals(36, size);
        assertEquals(13, config.resolveSlot("quest-category", "guide", 3, size));
        assertEquals(31, config.resolveSlot("quest-category", "close", 22, size));
    }

    @Test
    void anInvalidSizeFallsBackToTheDefault(@TempDir Path tempDir) throws IOException {
        Path dataFolder = tempDir.resolve("plugin-data");
        writeMenusFile(dataFolder, """
                quest-category:
                  size: 40
                  slots:
                    guide: 3
                """);

        MenuLayoutConfig config = MenuLayoutConfig.load(mockPlugin(dataFolder));

        assertEquals(27, config.resolveSize("quest-category", 27),
                "40 isn't a multiple of 9 - must fall back rather than produce a broken inventory");
    }

    @Test
    void aSlotOutsideTheResolvedSizeFallsBackToTheDefault(@TempDir Path tempDir) throws IOException {
        Path dataFolder = tempDir.resolve("plugin-data");
        writeMenusFile(dataFolder, """
                quest-category:
                  size: 27
                  slots:
                    guide: 99
                """);

        MenuLayoutConfig config = MenuLayoutConfig.load(mockPlugin(dataFolder));
        int size = config.resolveSize("quest-category", 27);

        assertEquals(3, config.resolveSlot("quest-category", "guide", 3, size),
                "slot 99 doesn't exist in a 27-slot inventory - must fall back rather than throw");
    }

    @Test
    void anUnknownMenuOrButtonIdFallsBackToTheDefault(@TempDir Path tempDir) {
        Path dataFolder = tempDir.resolve("plugin-data");
        MenuLayoutConfig config = MenuLayoutConfig.load(mockPlugin(dataFolder));

        assertEquals(54, config.resolveSize("not-a-real-menu", 54));
        assertEquals(7, config.resolveSlot("quest-category", "not-a-real-button", 7, 27));
    }

    @Test
    void leavesAnAdminCustomizedValueUntouchedWhileFillingInMissingMenus(@TempDir Path tempDir) throws IOException {
        Path dataFolder = tempDir.resolve("plugin-data");
        writeMenusFile(dataFolder, """
                quest-category:
                  size: 27
                  slots:
                    guide: 5
                    close: 22
                """);

        MenuLayoutConfig config = MenuLayoutConfig.load(mockPlugin(dataFolder));

        // The customized menu keeps the admin's value...
        assertEquals(5, config.resolveSlot("quest-category", "guide", 3, 27));
        // ...while a menu missing entirely from the on-disk file got filled in from the bundled default.
        assertEquals(54, config.resolveSize("quest-log", -1));

        String stillOnDisk = Files.readString(dataFolder.resolve("menus.yml"), StandardCharsets.UTF_8);
        assertTrue(stillOnDisk.contains("guide: 5"), "an admin's custom slot must not be overwritten by the merge");
    }

    private static void writeMenusFile(Path dataFolder, String content) throws IOException {
        Path file = dataFolder.resolve("menus.yml");
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private static JavaPlugin mockPlugin(Path dataFolder) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(plugin.getResource(anyString()))
                .thenAnswer(invocation -> MenuLayoutConfigTest.class.getClassLoader()
                        .getResourceAsStream(invocation.getArgument(0)));
        return plugin;
    }
}
