package dev.adrian.chestindex.core;

import dev.adrian.chestindex.core.util.SearchQuery;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SearchQueryTest {

    @Test
    void plainWordsStayPlain() {
        SearchQuery query = SearchQuery.parse("light blue wool");
        assertEquals("light blue wool", query.text());
        assertTrue(query.terms().isEmpty());
    }

    @Test
    void prefixedWordsBecomeTerms() {
        SearchQuery query = SearchQuery.parse("pickaxe @create #logs >barrel");

        assertEquals("pickaxe", query.text());
        assertEquals(java.util.List.of("create"), query.valuesOf(SearchQuery.Category.MOD));
        assertEquals(java.util.List.of("logs"), query.valuesOf(SearchQuery.Category.TAG));
        assertEquals(java.util.List.of("barrel"), query.valuesOf(SearchQuery.Category.TYPE));
    }

    @Test
    void aPrefixOnItsOwnConstrainsNothing() {
        SearchQuery query = SearchQuery.parse("@");

        assertEquals(1, query.terms().size());
        assertTrue(query.terms().get(0).isIncomplete());
        assertTrue(query.valuesOf(SearchQuery.Category.MOD).isEmpty());
    }

    @Test
    void wordPrefixesAreRecognisedAndCaseIsNot() {
        SearchQuery query = SearchQuery.parse("TAB:Redstone Ench:Mending");

        assertEquals(java.util.List.of("redstone"), query.valuesOf(SearchQuery.Category.TAB));
        assertEquals(java.util.List.of("mending"), query.valuesOf(SearchQuery.Category.ENCHANTMENT));
        assertEquals("", query.text());
    }

    @Test
    void severalOfOneCategoryAreAllKept() {
        SearchQuery query = SearchQuery.parse("@create @minecraft");
        assertEquals(java.util.List.of("create", "minecraft"),
                query.valuesOf(SearchQuery.Category.MOD));
    }

    @Test
    void aColonInAnOrdinaryWordIsNotACategory() {
        SearchQuery query = SearchQuery.parse("minecraft:diamond");
        assertEquals("minecraft:diamond", query.text());
        assertTrue(query.terms().isEmpty());
    }

    @Test
    void onlyTheCategoriesNeedingStackDetailSaySo() {
        assertFalse(SearchQuery.parse("@create #logs >barrel tab:redstone").needsStackDetail());
        assertTrue(SearchQuery.parse("ench:mending").needsStackDetail());
        assertTrue(SearchQuery.parse("potion:healing").needsStackDetail());
        assertTrue(SearchQuery.parse("text:silk").needsStackDetail());
        assertFalse(SearchQuery.parse("ench:").needsStackDetail());
    }

    @Test
    void emptyIsEmpty() {
        assertTrue(SearchQuery.parse(null).isEmpty());
        assertTrue(SearchQuery.parse("   ").isEmpty());
        assertFalse(SearchQuery.parse("@create").isEmpty());
    }
}
