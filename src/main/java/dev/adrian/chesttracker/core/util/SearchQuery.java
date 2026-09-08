package dev.adrian.chesttracker.core.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What was typed into the search box, split into what it constrains.
 *
 * <p>A search box that only matches names answers one question. Most of the
 * questions people actually have about their storage are about something else:
 * which mod an item came from, what kind of thing it is, what it is being kept
 * in. Those are all things the index or the game already knows, and none of
 * them can be asked by typing a name.
 *
 * <p>So a word with a prefix is a question about that property instead:
 *
 * <pre>
 *   diamond          the name, as before
 *   {@literal @}create           anything from that mod
 *   #logs            anything with that item tag
 *   &gt;barrel          only what is in barrels
 *   tab:redstone     only what the game files under that creative tab
 *   ench:mending     only enchanted with that
 *   potion:healing   only that potion
 *   text:silk        anything whose tooltip says so
 * </pre>
 *
 * <p>Parsing lives here, in {@code core}, because both ends need the same
 * answer: the server narrows the query with the terms it can resolve from the
 * index and the registries, and the client resolves the rest and drives the
 * completions. Two parsers would drift, and the one place that would show is a
 * search that highlights differently from how it counts.
 *
 * <p>An <em>incomplete</em> term - a prefix with nothing after it, which is
 * what every term looks like a keystroke after it is started - constrains
 * nothing. Anything else would empty the grid the instant somebody typed
 * {@code @}, which reads as the search breaking rather than as it waiting.
 */
public record SearchQuery(String text, List<Term> terms) {

    public SearchQuery {
        text = text == null ? "" : text;
        terms = terms == null ? List.of() : List.copyOf(terms);
    }

    /** One prefixed word, and what it was asking about. */
    public record Term(Category category, String value) {

        public Term {
            value = value == null ? "" : value.toLowerCase(Locale.ROOT);
        }

        /** A prefix with nothing typed after it yet. */
        public boolean isIncomplete() {
            return value.isEmpty();
        }

        /** How this term reads in the box, prefix and all. */
        public String written() {
            return category.prefix() + value;
        }
    }

    /**
     * The kinds of question a search can ask.
     *
     * <p>The symbol prefixes are the ones the rest of the game and its mods
     * already use - {@code @} for a mod and {@code #} for a tag are what the
     * creative search and the command parser use, so they are what people
     * already have in their fingers. The rest read as words because there is no
     * established symbol for them and an invented one would have to be learnt.
     *
     * @param indexed whether answering it needs data the index stores per
     *                stack, rather than something derivable from an item id
     */
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

        /** The word for it, for a list somebody is reading. */
        public String label() {
            return label;
        }

        public String help() {
            return help;
        }

        /** Whether this needs per-stack detail out of the index; see the codec. */
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

    /**
     * Which category a word is written in, or null for an ordinary word.
     *
     * <p>Longest prefix first, so that a category whose prefix begins with
     * another's could never be swallowed by it.
     */
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

    /** Every value given for one category, ignoring the ones still being typed. */
    public List<String> valuesOf(Category category) {
        List<String> values = new ArrayList<>(1);
        for (Term term : terms) {
            if (term.category() == category && !term.isIncomplete()) values.add(term.value());
        }
        return values;
    }

    /** Whether anything at all was asked. */
    public boolean isEmpty() {
        return text.isBlank() && terms.isEmpty();
    }

    /** Whether any term needs per-stack detail the index may not hold yet. */
    public boolean needsStackDetail() {
        for (Term term : terms) {
            if (term.category().isIndexed() && !term.isIncomplete()) return true;
        }
        return false;
    }
}
