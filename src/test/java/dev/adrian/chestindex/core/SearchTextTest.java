package dev.adrian.chestindex.core;

import dev.adrian.chestindex.core.util.SearchText;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchTextTest {

    @Test
    void blankMatchesEverything() {
        assertTrue(SearchText.matches("minecraft:stone", SearchText.tokens("")));
        assertTrue(SearchText.matches("minecraft:stone", SearchText.tokens("   ")));
        assertTrue(SearchText.matches("minecraft:stone", SearchText.tokens(null)));
    }

    @Test
    void plainSubstringStillWorks() {
        assertTrue(SearchText.matches("minecraft:diamond_block", SearchText.tokens("diamond")));
        assertFalse(SearchText.matches("minecraft:diamond_block", SearchText.tokens("emerald")));
    }

    /** The case this exists for: the name as read, against the id as stored. */
    @Test
    void spacedNameFindsUnderscoredId() {
        assertTrue(SearchText.matches("minecraft:blue_wool", SearchText.tokens("blue wool")));
        assertTrue(SearchText.matches("minecraft:light_blue_wool", SearchText.tokens("blue wool")));
    }

    /** Every word has to be there, or the box would stop narrowing as you type. */
    @Test
    void allWordsMustAppear() {
        assertFalse(SearchText.matches("minecraft:blue_wool", SearchText.tokens("blue carpet")));
        assertFalse(SearchText.matches("minecraft:blue_wool", SearchText.tokens("wool green")));
    }

    @Test
    void wordOrderDoesNotMatter() {
        assertTrue(SearchText.matches("minecraft:blue_wool", SearchText.tokens("wool blue")));
    }

    @Test
    void caseIsIgnoredOnBothSides() {
        assertTrue(SearchText.matches("Minecraft:Blue_Wool", SearchText.tokens("BLUE wool")));
    }
}
