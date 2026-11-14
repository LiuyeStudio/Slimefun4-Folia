package com.xzavier0722.mc.plugin.slimefun4.storage.adapter.sqlcommon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.xzavier0722.mc.plugin.slimefun4.storage.common.FieldKey;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the SQL literal generation of {@link SqlUtils}.
 * <p>
 * All statements are built by string concatenation, so any value containing a single quote (a
 * player name, a world name such as {@code world's}, or a custom backpack name) used to produce an
 * invalid statement. The write task then failed with a {@link java.sql.SQLException} and the data
 * was silently never persisted.
 */
class SqlUtilsEscapingTest {

    @Test
    void escapesSingleQuotes() {
        assertEquals("'world''s'", SqlUtils.toSqlValStr(FieldKey.LOCATION, "world's"));
    }

    @Test
    void escapesBackslashes() {
        assertEquals("'a\\\\b'", SqlUtils.toSqlValStr(FieldKey.PLAYER_NAME, "a\\b"));
    }

    @Test
    void leavesRegularValuesUntouched() {
        assertEquals("'Steve'", SqlUtils.toSqlValStr(FieldKey.PLAYER_NAME, "Steve"));
        assertEquals("''", SqlUtils.toSqlValStr(FieldKey.PLAYER_NAME, ""));
    }

    @Test
    void doesNotQuoteNumericFields() {
        assertEquals("42", SqlUtils.toSqlValStr(FieldKey.BACKPACK_NUMBER, "42"));
    }

    @Test
    void escapedLiteralKeepsTheConditionIntact() {
        var condition = SqlUtils.buildKvStr(FieldKey.LOCATION, "my world's;1:2:3");

        assertFalse(condition.endsWith("'world's;1:2:3'"), "The quote must be escaped: " + condition);
        assertEquals("loc='my world''s;1:2:3'", condition);
    }
}
