package dev.adrian.chesttracker.core.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public record SearchQuery(String text, List<Term> terms) {

    public SearchQuery {
        text = text == null ? "" : text;
        terms = terms == null ? List.of() : List.copyOf(terms);
    }

    public record Term(Category category, String value) {

        public Term {
            value = value == null ? "" : value.toLowerCase(Locale.ROOT);
        }

        public boolean isIncomplete() {
            return value.isEmpty();
        }

        public String written() {
            return category.prefix() + value;
        }
    }

    public enum Category {
        MOD("@", "mod", "items from one mod", false),
        TAG("#", "tag", "items sharing an item tag", false),
        TYPE(">", "in", "only containers of this kind", false),
        TAB("tab:", "tab", "the game's own creative tab", false),
        ENCHANTMENT("ench:", "enchantment", "enchanted with this", true),
        POTION("potion:", "potion", "this potion, however bottled", true),
        TOOLTIP("text:", "text", "words anywhere in the tooltip", true);

        private final String prefix;
        private final String label;
        private final String help;
        private final boolean indexed;

        Category(String prefix, String label, String help, boolean indexed) {
            this.prefix = prefix;
            this.label = label;
            this.help = help;
            this.indexed = indexed;
        }

        public String prefix() {
            return prefix;
        }

        public String label() {
            return label;
        }

        public String help() {
            return help;
        }

        public boolean isIndexed() {
            return indexed;
        }
    }

    private static final SearchQuery EMPTY = new SearchQuery("", List.of());

    public static SearchQuery parse(String raw) {
        if (raw == null || raw.isBlank()) return EMPTY;

        List<Term> terms = new ArrayList<>(2);
        StringBuilder plain = new StringBuilder();

        for (String word : raw.trim().split("\\s+")) {
            Category category = categoryOf(word);
            if (category == null) {
                if (!plain.isEmpty()) plain.append(' ');
                plain.append(word);
                continue;
            }
            terms.add(new Term(category, word.substring(category.prefix().length())));
        }
        return new SearchQuery(plain.toString(), terms);
    }

    public static Category categoryOf(String word) {
        if (word == null || word.isEmpty()) return null;
        String lower = word.toLowerCase(Locale.ROOT);

        Category best = null;
        for (Category category : Category.values()) {
            if (!lower.startsWith(category.prefix())) continue;
            if (best == null || category.prefix().length() > best.prefix().length()) best = category;
        }
        return best;
    }

    public List<String> valuesOf(Category category) {
        List<String> values = new ArrayList<>(1);
        for (Term term : terms) {
            if (term.category() == category && !term.isIncomplete()) values.add(term.value());
        }
        return values;
    }

    public boolean isEmpty() {
        return text.isBlank() && terms.isEmpty();
    }

    public boolean needsStackDetail() {
        for (Term term : terms) {
            if (term.category().isIndexed() && !term.isIncomplete()) return true;
        }
        return false;
    }
}
