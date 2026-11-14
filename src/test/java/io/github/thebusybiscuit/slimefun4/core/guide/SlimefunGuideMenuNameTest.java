package io.github.thebusybiscuit.slimefun4.core.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests {@link SlimefunGuide#applyMenuName(String, String)}, which substitutes the
 * configurable {@code guide.menu-name} into our guide menu messages.
 */
class SlimefunGuideMenuNameTest {

    @Test
    @DisplayName("Placeholder should be replaced by the configured name")
    void testPlaceholderIsReplaced() {
        assertEquals("测试 指南", SlimefunGuide.applyMenuName("%slimefun% 指南", "测试"));
        assertEquals("Guide 测试", SlimefunGuide.applyMenuName("Guide %slimefun%", "测试"));
    }

    @Test
    @DisplayName("Every placeholder occurrence should be replaced")
    void testAllOccurrencesAreReplaced() {
        assertEquals("Acme / Acme Guide", SlimefunGuide.applyMenuName("%slimefun% / %slimefun% Guide", "Acme"));
    }

    @ParameterizedTest
    @DisplayName("Messages without a placeholder should stay untouched")
    @ValueSource(
            strings = {"Slimefun4 Contributors", "Go back to the Settings Panel", "&7the language in which Slimefun", ""
            })
    void testMessagesWithoutPlaceholderAreUnchanged(String message) {
        assertEquals(message, SlimefunGuide.applyMenuName(message, "Acme"));
    }

    @Test
    @DisplayName("A null message should not throw")
    void testNullMessage() {
        assertEquals("", SlimefunGuide.applyMenuName(null, "Acme"));
    }

    @Test
    @DisplayName("A name identical to the placeholder must not loop")
    void testPlaceholderAsNameIsNotReplaced() {
        assertEquals("%slimefun%", SlimefunGuide.applyMenuName("%slimefun%", SlimefunGuide.MENU_NAME_PLACEHOLDER));
    }

    @Test
    @DisplayName("An empty name should not be substituted")
    void testEmptyNameKeepsMessage() {
        assertEquals("%slimefun% Guide", SlimefunGuide.applyMenuName("%slimefun% Guide", ""));
    }

    @ParameterizedTest
    @DisplayName("Localized guide titles should resolve with the configured name")
    @CsvSource(
            delimiter = '|',
            value = {
                "&a%slimefun% Guide &7(Inventory)|&aAcme Guide &7(Inventory)",
                "Go back to the %slimefun% Guide|Go back to the Acme Guide",
                "%slimefun%指南|Acme指南",
                "返回 %slimefun%指南|返回 Acme指南",
                "Return to the %slimefun%-Handbook|Return to the Acme-Handbook"
            })
    void testLocalizedTitles(String message, String expected) {
        assertEquals(expected, SlimefunGuide.applyMenuName(message, "Acme"));
    }

    @Test
    @DisplayName("Chinese guide names should not be separated by a space")
    void testChineseGuideNameHasNoSpace() {
        // The zh-CN resource deliberately places the placeholder directly before 指南
        String title = SlimefunGuide.applyMenuName("%slimefun%指南", "粘液科技");
        assertEquals("粘液科技指南", title);
        assertFalse(title.contains(" "), "The configured name must not be followed by a space");

        assertEquals("&a粘液科技指南 &7(箱子界面)", SlimefunGuide.applyMenuName("&a%slimefun%指南 &7(箱子界面)", "粘液科技"));
    }
}
