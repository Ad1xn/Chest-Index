package dev.adrian.chestindex.core.net;

import dev.adrian.chestindex.core.model.Origin;

import java.util.List;
import java.util.Set;

/**
 * The shapes that cross between client and server.
 *
 * <p>Everything here names items and container types by their <b>registry
 * string</b>, never by a palette id. Palette ids are local to whichever palette
 * produced them, so the same int means different things on either side of the
 * connection; sending them would appear to work and silently mislabel
 * everything.
 *
 * <p>Singleplayer produces these too, rather than shortcutting to the index
 * directly. One shape means the screen has a single code path whether or not a
 * network is involved, and the singleplayer path is not a special case that can
 * rot while nobody is looking.
 *
 * <p>Neither request carries a position or a dimension, deliberately. The
 * server already knows where the asking player is, so sending it would add a
 * value the server must either trust or ignore - and a client that could move
 * the query centre could rank a search around somewhere it has never been.
 */
public final class QueryDto {

    private QueryDto() {}

    /** What the toolbar buttons select. */
    /**
     * @param includeMachines containers that move items about on their own -
     *                        hoppers, droppers, dispensers, crafters
     * @param includeUtility  containers that hold things but are not storage -
     *                        furnaces, brewing stands, jukeboxes, pots,
     *                        bookshelves
     * @param includeEntities containers that are entities and therefore move -
     *                        chest and hopper minecarts, chest boats. Read live
     *                        and never stored; see
     *                        {@link dev.adrian.chestindex.platform.EntityContainers}
     */
    public record Filters(boolean includeNested, boolean includeMachines,
                          boolean includeUtility, boolean includeEntities, int originFilter) {

        public static final int ORIGIN_ANY = 0;
        public static final int ORIGIN_PLAYER_PLACED = 1;
        public static final int ORIGIN_NATURAL = 2;

        public Filters {
            // A byte off the wire could name an origin that does not exist.
            if (originFilter < ORIGIN_ANY || originFilter > ORIGIN_NATURAL) originFilter = ORIGIN_ANY;
        }

        public static Filters defaults() {
            return new Filters(true, false, false, true, ORIGIN_ANY);
        }

        /**
         * What the origin filter actually selects.
         *
         * <p>"Player-built" deliberately includes {@link Origin#UNKNOWN}, and
         * this matters more than it looks. Placement can only be observed as it
         * happens, so on a world that existed before the mod was installed
         * <em>every</em> chest a player ever built is {@code UNKNOWN} - and a
         * filter that took the label literally would show them none of their
         * own base and tell them to go and re-place every chest they own.
         *
         * <p>The reverse reading is sound: generated containers are positively
         * identified, by a structure piece or an unrolled loot table. Anything
         * that is not generated and is standing in the world was put there by
         * somebody. So the filter is really "generated or not", and the
         * uncertain case belongs on the side that does not lose the player
         * their own storage.
         */
        public Set<Origin> origins() {
            return switch (originFilter) {
                case ORIGIN_PLAYER_PLACED -> Set.of(Origin.PLAYER_PLACED, Origin.UNKNOWN);
                case ORIGIN_NATURAL -> Set.of(Origin.NATURAL);
                default -> Set.of();
            };
        }
    }

    /**
     * Ask for item totals.
     *
     * <p>The id is echoed in the reply. Every keystroke starts a query, replies
     * need not arrive in the order they were asked for, and without this a slow
     * early reply lands after a fast later one and shows results for a search
     * the player has already moved on from.
     *
     * @param requestId caller's correlation id
     * @param text      free text matched against item ids; blank means everything
     */
    /**
     * @param dimensionId which index to search; blank means the one the player
     *                    is standing in. Sent so the screen can look at the
     *                    Nether from the overworld, which is most of the point
     *                    of knowing the Nether has anything in it
     */
    public record SummaryRequest(int requestId, String text, Filters filters, int limit,
                                 String dimensionId) {

        public SummaryRequest {
            if (dimensionId == null) dimensionId = "";
        }

        /** The common case: whichever dimension the player is in. */
        public SummaryRequest(int requestId, String text, Filters filters, int limit) {
            this(requestId, text, filters, limit, "");
        }
    }

    /**
     * One item, totalled across the containers holding it.
     *
     * @param itemId registry name, e.g. {@code minecraft:redstone}
     */
    /**
     * @param nestedCount how many of {@code totalCount} are inside a shulker
     *                    box rather than loose. Sent because the screen cannot
     *                    work it out: a slot showing 900 wool looks identical
     *                    whether it is stacked in a barrel or sealed inside
     *                    nine shulkers, and those are different answers to
     *                    "where is my wool".
     */
    public record ItemSummary(String itemId, int totalCount, int containerCount,
                              int nestedCount, double nearestDistSq) {}

    /**
     * @param permitted whether the server answered this at all. Carried on
     *                  every reply, not just the greeting: permission can
     *                  change while a player is connected - being opped is the
     *                  obvious case - and a greeting sent once at join would
     *                  leave the client believing the old answer until it
     *                  reconnected.
     */
    public record SummaryResponse(int requestId, boolean permitted, List<ItemSummary> items) {
        public SummaryResponse {
            items = items == null ? List.of() : List.copyOf(items);
        }

        public static SummaryResponse refused(int requestId) {
            return new SummaryResponse(requestId, false, List.of());
        }

        public static SummaryResponse of(int requestId, List<ItemSummary> items) {
            return new SummaryResponse(requestId, true, items);
        }
    }

    /** Ask where one item is. */
    /**
     * Where one or more items are.
     *
     * <p>{@code itemIds} is a list rather than a single id because two features
     * ask the same question about a whole set at once: pressing the search key
     * over a shulker box asks where everything <em>inside</em> it is, and a
     * schematic's material list asks where everything it needs is. Both would
     * otherwise be a query per item - dozens of round trips for one keypress,
     * each with its own result limit, so the nearest containers overall could
     * not be picked out of them.
     *
     * <p>A container matches if it holds <em>any</em> of the ids, and its
     * matched count totals all of them.
     */
    public record ContainerRequest(int requestId, List<String> itemIds, Filters filters, int limit,
                                   String dimensionId, String text) {

        /** Never ask for more than this many at once, whatever the caller passes. */
        public static final int MAX_ITEMS = 256;

        public ContainerRequest {
            if (dimensionId == null) dimensionId = "";
            if (text == null) text = "";
            itemIds = itemIds == null ? List.of() : List.copyOf(itemIds);
            if (itemIds.size() > MAX_ITEMS) itemIds = List.copyOf(itemIds.subList(0, MAX_ITEMS));
        }

        /**
         * Without the search text, for the callers that have none.
         *
         * <p>The text is here so that a question asked of the grid is asked the
         * same way of the list behind it: ">barrel" or "ench:mending" narrowed
         * the counts, and a list of places that ignored them would contradict
         * the number the player just clicked on.
         */
        public ContainerRequest(int requestId, List<String> itemIds, Filters filters, int limit,
                                String dimensionId) {
            this(requestId, itemIds, filters, limit, dimensionId, "");
        }
        public ContainerRequest(int requestId, String itemId, Filters filters, int limit,
                                String dimensionId) {
            this(requestId, itemId == null ? List.of() : List.of(itemId), filters, limit, dimensionId, "");
        }

        public ContainerRequest(int requestId, String itemId, Filters filters, int limit) {
            this(requestId, itemId, filters, limit, "");
        }

        /**
         * The single item asked for, when there is exactly one.
         *
         * <p>The detail pane and the highlight both still work one item at a
         * time; this keeps them from having to care that the wire can carry
         * more.
         */
        public String soleItemId() {
            return itemIds.size() == 1 ? itemIds.get(0) : null;
        }
    }

    /**
     * One container holding the requested item.
     *
     * @param contentsKnown false when the container's contents cannot be known,
     *                      so the UI can say so rather than imply it is empty
     * @param entityId      network id of the entity this container is, or
     *                      {@link #NOT_AN_ENTITY} for an ordinary block. A
     *                      minecart's position is a fact about the moment it
     *                      was read and nothing else - it is on a rail, being
     *                      moved - so {@code pos} is where it <em>was</em>.
     *                      The id is what lets the client ask the game where
     *                      it is now, every frame, instead of drawing a box
     *                      around the place it left
     */
    public record ContainerHit(String typeId, long pos, int matchedCount, double distanceSq,
                               boolean nested, boolean natural, boolean contentsKnown,
                               int entityId) {

        /** No entity behind this hit: it is a block, and {@code pos} is the whole truth. */
        public static final int NOT_AN_ENTITY = 0;

        public ContainerHit(String typeId, long pos, int matchedCount, double distanceSq,
                            boolean nested, boolean natural, boolean contentsKnown) {
            this(typeId, pos, matchedCount, distanceSq, nested, natural, contentsKnown, NOT_AN_ENTITY);
        }

        /** Whether this container moves, and so has to be re-read rather than remembered. */
        public boolean isEntity() {
            return entityId != NOT_AN_ENTITY;
        }
    }

    /** @param permitted see {@link SummaryResponse#permitted()} */
    public record ContainerResponse(int requestId, boolean permitted, List<ContainerHit> hits) {
        public ContainerResponse {
            hits = hits == null ? List.of() : List.copyOf(hits);
        }

        public static ContainerResponse refused(int requestId) {
            return new ContainerResponse(requestId, false, List.of());
        }

        public static ContainerResponse of(int requestId, List<ContainerHit> hits) {
            return new ContainerResponse(requestId, true, hits);
        }
    }

    /**
     * The name the ender chest answers to, where a dimension id would go.
     *
     * <p>Not a dimension, and deliberately in this mod's namespace so it can
     * never collide with a real one. Ender chest contents belong to a player
     * rather than to a world - they are the same wherever you stand, and they
     * are nobody else's business - so they cannot be indexed alongside the
     * containers in a world, and are read live from whoever is asking.
     */
    public static final String ENDER_CHEST = "chestindex:ender_chest";

    /** Ask what the index holds and whether it is still filling. */
    public record StatusRequest(int requestId) {}

    /**
     * One dimension the index knows something about.
     *
     * @param dimensionId the dimension's registry id
     * @param containers  how many containers are indexed there
     */
    public record DimensionSummary(String dimensionId, int containers) {}

    /**
     * What the index holds, and whether it is still filling.
     *
     * <p>Both halves matter to a player looking at an empty screen: "nothing
     * here" and "nothing here yet" are different answers, and only one of them
     * means come back in a minute.
     *
     * @param scanning     whether an offline region scan is running
     * @param regionsRead  region files read so far, which is what the scanner
     *                     actually counts against a known total
     * @param regionsTotal region files it expects to read, 0 before it knows
     * @param chunksRead   chunks read so far, for saying something concrete
     *                     while the fraction is still zero
     * @param dimensions   every dimension with at least one container indexed
     */
    public record StatusResponse(int requestId, boolean scanning,
                                 int regionsRead, int regionsTotal, int chunksRead,
                                 List<DimensionSummary> dimensions) {

        public StatusResponse {
            dimensions = dimensions == null ? List.of() : List.copyOf(dimensions);
        }

        public static StatusResponse empty(int requestId) {
            return new StatusResponse(requestId, false, 0, 0, 0, List.of());
        }

        /** 0..1, or 0 when the total is not known yet. */
        public float progress() {
            return regionsTotal <= 0 ? 0.0f : Math.min(1.0f, regionsRead / (float) regionsTotal);
        }
    }

    /**
     * Sent unprompted by a server that has the mod, once the player is in.
     *
     * <p>Custom payloads are silently dropped by a server that does not know
     * them, so a client cannot learn by asking - a vanilla server's reply to a
     * query is indistinguishable from a slow one. Announcing instead means the
     * client waits a short grace period and then knows.
     *
     * @param canQuery whether this player's tier allows a query right now. Only
     *                 an opening position - permission can change mid-session,
     *                 so every reply carries it too and the client believes the
     *                 most recent one
     */
    public record Hello(int protocolVersion, boolean canQuery) {

        /**
         * Bumped when the payload shapes change incompatibly.
         *
         * <p>5 turned a container request's single item id into a list, so one
         * query can ask where a whole set of items is. 4 added a dimension to
         * both requests and a status route. 3 added a nested count to every
         * item summary. Two peers that disagree about a payload's shape while
         * both claiming the same version do not fail, they desync - the reader
         * takes the next field from the middle of the previous one - so this
         * has to move whenever a field does.
         */
        public static final int PROTOCOL_VERSION = 7;
    }
}
