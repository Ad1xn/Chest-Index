package dev.adrian.chestindex.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.adrian.chestindex.ChestIndex;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Plain JSON config, no third-party config library.
 *
 * <p>Deliberately dependency-free: this mod has to work identically on two very
 * different Minecraft versions, and tying the settings to a library that may not
 * have a build for both would put that at risk. Mod Menu will eventually open a
 * screen over these values, but it stays an optional integration - the file and
 * the defaults work with nothing installed.
 */
public final class ChestIndexConfig {

    private static ChestIndexConfig instance;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // --- Master switch ----------------------------------------------------

    /**
     * Whether the mod does anything at all on this client.
     *
     * <p>One switch in front of everything, rather than turning six settings
     * off one at a time. Off means no search screen, no button on containers,
     * no key, no index being written and no boxes in the world - the mod is
     * loaded and inert.
     *
     * <p>Wanted because "is this allowed here" is a per-server question with a
     * yes/no answer, and the honest way to answer no is to stop, not to keep
     * running with the visible parts hidden.
     */
    public boolean enabled = true;

    /**
     * Servers the mod turns itself off on, matched against the address joined.
     *
     * <p>Empty by default. It used to ship with four large public servers whose
     * rules on client mods are strict, on the reasoning that the safe default
     * is off - but naming four servers out of the thousands with such a rule
     * is not a safe default, it is an arbitrary one, and it silently switched
     * the mod off for anyone who happened to play there and never found out
     * why. Deciding where this runs is the player's call to make.
     *
     * <p>Matched by host suffix, so {@code mcpvp.com} also covers
     * {@code eu.mcpvp.com} and a port on the end changes nothing.
     */
    public List<String> disabledServers = new ArrayList<>();

    /**
     * Whether the mod is switched off for the address currently joined.
     *
     * <p>Blank means singleplayer, where the list never applies.
     */
    public boolean disabledOn(String address) {
        return matchesHost(disabledServers, address);
    }

    /**
     * Whether {@code address} is one of {@code hosts}, by host suffix.
     *
     * <p>Suffix rather than equality because one server is many addresses:
     * {@code play.example.net}, {@code eu.example.net} and {@code example.net}
     * are the same place, and asking a player to list every subdomain is asking
     * them to get it wrong. A port is stripped first, and the match is on whole
     * labels so {@code notexample.net} is not covered by {@code example.net}.
     */
    public static boolean matchesHost(List<String> hosts, String address) {
        if (hosts == null || hosts.isEmpty() || address == null || address.isBlank()) return false;
        String host = host(address);
        for (String entry : hosts) {
            if (entry == null || entry.isBlank()) continue;
            String candidate = host(entry);
            if (host.equals(candidate) || host.endsWith("." + candidate)) return true;
        }
        return false;
    }

    /** An address reduced to a bare lower-case host: no port, no brackets, no trailing dot. */
    public static String host(String address) {
        String text = address.trim().toLowerCase(Locale.ROOT);
        int slash = text.indexOf('/');
        if (slash >= 0) text = text.substring(0, slash);
        // An IPv6 literal keeps its colons; only a trailing :port is a port.
        if (text.startsWith("[")) {
            int close = text.indexOf(']');
            if (close > 0) text = text.substring(1, close);
        } else {
            int colon = text.lastIndexOf(':');
            if (colon > 0 && text.indexOf(':') == colon) text = text.substring(0, colon);
        }
        while (text.endsWith(".")) text = text.substring(0, text.length() - 1);
        return text;
    }

    // --- Scanning ---------------------------------------------------------

    /**
     * Scan the whole world in the background when a world is loaded.
     *
     * <p>On by default: the point of the mod is knowing what is in containers
     * you have not visited, and that cannot happen without a scan. It runs on a
     * background thread under a tick-time budget rather than at join, so a large
     * world costs throughput rather than a freeze.
     */
    public boolean scanOnWorldJoin = true;

    /**
     * Whether the search screen starts with hoppers, furnaces and the like
     * shown.
     *
     * <p>Machines are always indexed; this is only the filter's starting
     * position. They are hidden by default because their contents churn
     * constantly and are rarely what someone is looking for - but a hopper you
     * just tipped five stacks into is exactly the case where that surprises
     * people, so it is one click away in the menu.
     *
     * <p>Replaces an earlier {@code indexMachineContents} key that nothing ever
     * read; an old config file simply falls back to this default.
     */
    public boolean showMachines = false;

    /**
     * Whether the search screen starts with furnaces, jukeboxes and the like
     * shown.
     *
     * <p>A second group rather than more entries in the first, because the two
     * are not the same question. A hopper's contents are in transit and are
     * almost never what somebody is looking for. A decorated pot, a jukebox or
     * a chiseled bookshelf holds exactly what a player deliberately put in it -
     * that is storage, just storage that is also furniture - and a furnace is
     * where half a stack of iron goes missing to.
     *
     * <p>Off by default only because that is what these types did when they
     * were lumped in with the machines; turning it on is one click and is
     * probably what most people want.
     */
    public boolean showUtility = false;

    /**
     * Read container entities at all.
     *
     * <p>The master switch, distinct from the filter below: this decides
     * whether the entities around the asker are looked at when a query runs,
     * and the filter decides whether the results are shown. A server operator
     * turning this off is declining the per-query sweep; a player turning the
     * filter off is tidying their grid.
     */
    public boolean trackEntityContainers = true;

    /**
     * Whether the search screen starts with chest minecarts and the like shown.
     *
     * <p>On, because unlike the machines these are storage - somebody put a
     * chest on a minecart on purpose. See {@link #entityContainersOnVanillaServers}
     * for the one place they are held back.
     */
    public boolean showEntities = true;

    /**
     * Read container entities on servers that do not have the mod.
     *
     * <p>Off, and this is the careful default rather than a timid one. These
     * containers move, and on a server the only thing moving them is somebody
     * else - a minecart another player sends down a rail is gone from where it
     * was found, and the client has no way to be told. The guidance would point
     * at a place a chest used to be, confidently, which is the one failure this
     * mod must not have.
     *
     * <p>In your own world, and on a server running the mod, the index is read
     * from the same authority that moves them, so they are tracked by default.
     */
    public boolean entityContainersOnVanillaServers = false;

    /**
     * Container types counted as machines, by block id.
     *
     * <p>A list rather than a constant so a modded machine can be filed with
     * the vanilla ones. Anything in neither this nor {@link #utilityTypes} is
     * treated as ordinary storage and is always shown, which is the right
     * default for a type nobody has classified.
     */
    public List<String> machineTypes = new ArrayList<>(List.of(
            "minecraft:hopper", "minecraft:dropper", "minecraft:dispenser",
            "minecraft:crafter"));

    /** Container types counted as functional blocks. See {@link #showUtility}. */
    public List<String> utilityTypes = new ArrayList<>(List.of(
            "minecraft:furnace", "minecraft:blast_furnace", "minecraft:smoker",
            "minecraft:brewing_stand", "minecraft:campfire", "minecraft:soul_campfire",
            "minecraft:jukebox", "minecraft:lectern", "minecraft:decorated_pot",
            "minecraft:chiseled_bookshelf"));

    // --- Search -----------------------------------------------------------

    /** The value {@link #maxResults} takes to mean "no limit". */
    public static final int UNLIMITED_RESULTS = 0;

    /**
     * Distinct items the grid will show. {@link #UNLIMITED_RESULTS} means no cap.
     *
     * <p>Unlimited by default. A cap is a performance setting wearing the
     * clothes of a search setting: nine hundred was chosen to keep the grid
     * cheap, but what it actually does is quietly not answer the question, and
     * "it isn't in any of my chests" is a worse outcome than a slow grid. The
     * slider is still there for anyone who wants the cap back.
     */
    public int maxResults = UNLIMITED_RESULTS;

    /** {@link #maxResults} as a query limit, where zero already means unlimited. */
    public int resultLimit() {
        return Math.max(0, maxResults);
    }

    /** Whether items inside shulker boxes count as being in the outer container. */
    public boolean includeNested = true;

    // --- Remembered search screen state -----------------------------------
    //
    // The screen is reopened constantly, and re-picking the same filters every
    // time is the kind of small friction that makes a tool feel unfinished.
    // These are written when it closes.

    /** {@code COUNT}, {@code NEAREST} or {@code NAME}; anything else reads as COUNT. */
    public String sortMode = "COUNT";

    /** 0 any, 1 player-placed, 2 natural. Out-of-range reads as 0. */
    public int originFilter = 0;

    /** Whatever was last typed in the search box. */
    public String searchText = "";

    // --- Container screens ------------------------------------------------

    /**
     * Offer the ender chest as a view of its own.
     *
     * <p>Client-side, and read by the screen rather than by the server: it is
     * about whether this player wants the button, not about what the server is
     * willing to answer. A server that has never heard of the setting still
     * behaves correctly for a client that turns it off.
     */
    public boolean enderChestView = true;

    /**
     * Pointing the search key at a shulker box looks for what is inside it,
     * rather than for more shulker boxes.
     *
     * <p>On by default, because it is almost always the question being asked.
     * A shulker box with things in it is packaging: standing at a chest,
     * pointing at one and asking "where is this" means "where is the rest of
     * what is in here", not "where are my other shulker boxes". Fill level does
     * not come into it - one item or a full twenty-seven, the contents are what
     * is being asked about.
     *
     * <p>An <em>empty</em> shulker box still searches for shulker boxes, which
     * is the one case where the box really is the question: you are looking for
     * more storage.
     *
     * <p>Turn this off to always search for the item being pointed at, shulker
     * boxes included.
     */
    public boolean searchShulkerContents = true;

    /**
     * Add search buttons to Litematica's material list.
     *
     * <p>Only ever appear when Litematica is installed - it is found by class
     * name at runtime, is not a build dependency, and its absence is simply no
     * buttons. One per row finds that row's material; the one beside
     * {@code Export} finds everything the schematic still needs.
     *
     * <p>They used to be a floating pair the player had to drag out of the way,
     * because a fixed position on somebody else's screen is a guess. They are
     * now placed off the real widgets they sit beside instead, which is the
     * answer that cannot be wrong for anybody - see
     * {@code LitematicaGui}.
     */
    public boolean litematicaButton = true;

    /** Whether a search button is drawn on chests, furnaces and the like. */
    public boolean containerSearchButton = true;

    /**
     * Where that button sits, measured from the container window's top-right
     * corner.
     *
     * <p>From the right rather than the left because container windows are not
     * all the same width - a fixed offset from the left edge puts the button in
     * the middle of a narrow one. Negative x is inside the window.
     *
     * <p>Right-dragging the button writes these, so the setting exists mostly
     * to persist what the player did rather than to be edited by hand.
     */
    public int searchButtonX = -15;

    public int searchButtonY = 4;

    /**
     * Where the button sits on one particular kind of container window.
     *
     * <p>Keyed by menu type - {@code minecraft:hopper}, {@code minecraft:generic_9x6}
     * and so on - because "the same corner of every window" turned out not to
     * be what people want. A hopper is five slots wide and its top-right corner
     * is directly over its only row; a double chest has a whole empty title bar
     * up there. Somewhere good on one is in the way on the other.
     *
     * <p>Absent means the shared {@link #searchButtonX}/{@link #searchButtonY}
     * default, so a player who never drags it never gets an entry, and one who
     * drags it on a hopper moves the hopper's button alone.
     */
    public Map<String, int[]> searchButtonPositions = new LinkedHashMap<>();

    /** The offset to draw the button at on {@code menuKey}, defaulting to the shared one. */
    public int[] searchButtonOffset(String menuKey) {
        if (menuKey != null && searchButtonPositions != null) {
            int[] stored = searchButtonPositions.get(menuKey);
            if (stored != null && stored.length == 2) return stored;
        }
        return new int[] {searchButtonX, searchButtonY};
    }

    /** Remembers where the player dropped the button on this kind of window. */
    public void setSearchButtonOffset(String menuKey, int x, int y) {
        if (menuKey == null || menuKey.isBlank()) {
            searchButtonX = x;
            searchButtonY = y;
            return;
        }
        if (searchButtonPositions == null) searchButtonPositions = new LinkedHashMap<>();
        if (machineTypes == null) machineTypes = new ArrayList<>();
        if (utilityTypes == null) utilityTypes = new ArrayList<>();
        searchButtonPositions.put(menuKey, new int[] {x, y});
    }

    // --- Multiplayer ------------------------------------------------------

    /**
     * Who may query the index over the network.
     *
     * <p>Named rather than numbered so the file stays readable, and parsed
     * leniently so a typo degrades to the safe end rather than to the open one.
     */
    public enum Access {
        /** Anyone on the server. Right for a private world among friends. */
        ALL,
        /** Only containers the asking player placed. */
        OWNED,
        /** Operators only. */
        OP;

        /** Unknown text means the default rather than throwing on a typo. */
        public static Access parse(String value) {
            if (value == null) return ALL;
            for (Access access : values()) {
                if (access.name().equalsIgnoreCase(value.trim())) return access;
            }
            return ALL;
        }
    }

    /**
     * Who may query this copy of the mod over the network.
     *
     * <p>Defaults to {@code ALL}: installing the mod on a server is the act of
     * deciding players should be able to search, and a default nobody can use
     * reads as broken rather than as safe. A server that wants it narrower has
     * {@code OWNED} and {@code OP}, settable live with
     * {@code /chestindex access}.
     *
     * <p>Worth knowing when choosing: a full index is effectively loot x-ray -
     * it shows where every unopened generated chest is, and what is in other
     * people's bases.
     *
     * <p>Applies to every player who arrives over a connection, which includes
     * guests in a world opened to LAN. The host themselves is never gated:
     * their screen reads the integrated server directly and never goes near
     * this, which is why it does nothing in plain singleplayer.
     */
    public String permissionTier = Access.ALL.name();

    public Access permissionTier() {
        return Access.parse(permissionTier);
    }

    /**
     * How long the client waits for a server's hello before deciding there is
     * no server-side index.
     *
     * <p>Long enough to cover a join under load, short enough that a vanilla
     * server does not leave the screen saying "connecting" for any noticeable
     * time.
     */
    public int serverHelloTimeoutMs = 3000;

    /**
     * Keep an index on this machine for servers that have no index of their
     * own.
     *
     * <p>This is what makes the mod do anything at all on a vanilla server.
     * It is built entirely from what the server has already sent: where
     * containers are, from the chunks it gave us, and what is inside one, from
     * a container the player opened themselves. No packet is sent, nothing is
     * opened automatically, and the player is never moved - a vanilla server
     * cannot tell it is running.
     *
     * <p>The cost of that honesty is that a chest holds nothing here until it
     * has been opened once. There is no way around it: chunk data carries block
     * states and never inventories, so a client that has not looked inside a
     * chest genuinely does not know.
     *
     * <p>Ignored in singleplayer and on a server running the mod, both of which
     * have a real index built from the world itself.
     */
    public boolean clientSideIndex = true;

    // --- Highlight --------------------------------------------------------

    /** How a highlight tells the player where the containers are. */
    public enum Display {
        /** Boxes in the world, nothing above the hotbar. */
        BOXES,
        /** A bearing and a distance above the hotbar, no boxes. */
        ACTION_BAR,
        BOTH,
        NONE;

        // values() hands out a defensive copy on every call, and these are
        // parsed out of the config on the render path rather than stored.
        private static final Display[] VALUES = values();

        /** Unknown text reads as the default rather than throwing on a typo. */
        public static Display parse(String value) {
            if (value == null) return BOXES;
            for (Display display : VALUES) {
                if (display.name().equalsIgnoreCase(value.trim())) return display;
            }
            return BOXES;
        }

        public boolean drawsBoxes() {
            return this == BOXES || this == BOTH;
        }

        public boolean writesActionBar() {
            return this == ACTION_BAR || this == BOTH;
        }
    }

    /**
     * Which of the two ways of showing a highlight are used.
     *
     * <p>Boxes alone by default. The action bar can only ever describe one
     * container, and "there are four more behind you" is something a box says
     * better than a sentence - so once the boxes exist, the text is a second
     * description of the same thing competing for the same strip of screen.
     *
     * <p>Replaces an earlier {@code inWorldHighlight} boolean; an old config
     * file simply falls back to this default.
     */
    public String highlightDisplay = Display.BOXES.name();

    public Display highlightDisplay() {
        return Display.parse(highlightDisplay);
    }

    /**
     * Open the best matching container when one is already within arm's reach.
     *
     * <p>Only ever a container the search just found, and only at normal reach:
     * this sends the same interaction as right-clicking it, so a server applies
     * the same distance check to it as to anything else. Out of reach it does
     * nothing and the boxes do the work.
     */
    public boolean openInReach = true;

    /**
     * Turn to face the nearest match when a search lands.
     *
     * <p>Costs nothing on a vanilla server because there is no index to search
     * there in the first place. Worth knowing before enabling it on a server
     * you do not run: a client that moves the player's view is the shape of
     * thing some anti-cheats look for, however innocent the reason.
     */
    public boolean turnToTarget = true;

    /**
     * Whether the two settings above may act while connected to somebody
     * else's server.
     *
     * <p>Off by default, and the default is the important part. Turning the
     * view towards a container and opening one at range are the only two
     * things this mod does that a server can see at all, and both look exactly
     * like the cheats an anti-cheat is written to catch: a view that snaps to
     * a block the server never highlighted, and an interaction landing on it
     * with no aiming in between. The reason being innocent is not something
     * the far end can check.
     *
     * <p>In the player's own world - singleplayer, or hosting a LAN game -
     * both work regardless of this, because there is nobody to convince.
     *
     * <p>Turning this on is a deliberate choice for a server you trust or run
     * yourself. It is not needed for the mod to be useful on a vanilla server:
     * the client-side index still draws boxes and writes a bearing, which are
     * drawn on this machine and never leave it.
     */
    public boolean assistOnServers = false;

    /**
     * Servers the two assist features are allowed on, whatever
     * {@link #assistOnServers} says.
     *
     * <p>A list rather than one switch because the question is never "servers,
     * yes or no". It is "my friend's SMP, yes; the anarchy server I am a guest
     * on, absolutely not" - and a single toggle forces the player to answer for
     * the strictest server they play on, which means answering no everywhere.
     *
     * <p>Matched by host suffix, the same way {@link #disabledServers} is.
     * Empty by default: nothing is trusted until somebody says so.
     */
    public List<String> assistServers = new ArrayList<>();

    /** Whether turning and opening may act on the address currently joined. */
    public boolean assistAllowedOn(String address) {
        return assistOnServers || matchesHost(assistServers, address);
    }

    /**
     * Stand a column of light on each match.
     *
     * <p>Replaces a line drawn from the camera to the nearest container, which
     * was a mistake. Anchored at the eye, the only part of it a player could
     * actually see was the far end, which read as something hanging off the
     * chest rather than as a direction to walk. A column stands where the
     * container is, needs nothing around it to make sense, and so still says
     * something in a chunk that was never loaded.
     */
    public boolean guideBeam = true;

    /**
     * Draw the boxes through whatever is in front of them.
     *
     * <p>On, because a marker you can only see once you can already see the
     * chest is telling you something you no longer need to know - the whole
     * point of pointing at a container is that it is behind something.
     *
     * <p>The one setting here that exists partly as an escape hatch. Drawing
     * through the world needs a line type vanilla does not have, so the mod
     * builds one out of vanilla's own line pipeline with the depth test taken
     * out; that is the only place this mod reaches past the API into how the
     * game draws. If a shader pack or another rendering mod does not like it,
     * turning this off puts the boxes back on vanilla's ordinary line type,
     * where the worst that happens is that a box hides behind a wall.
     *
     * <p>Never applies to the trail, which is drawn against the world on
     * purpose - see {@link #guideBeamFromChunks}.
     */
    public boolean highlightThroughWalls = true;

    /**
     * How far away a match has to be before a trail is stood on it, in chunks.
     *
     * <p>Nought would mean one on every match, which is what it used to do and
     * why the trail was the first thing people turned off: a chest across the
     * room does not need a column of marks over it, and a base full of them is
     * a picket fence you cannot see the chests through. The box already says
     * everything there is to say at that range.
     *
     * <p>Twenty chunks is deliberately past most render distances. That is the
     * distance at which the box stops being enough - there is no terrain drawn
     * to place it against, and the marker is being pulled in to the horizon
     * rather than drawn where it is - and it is exactly where a column that
     * rises to the build limit starts being the only thing that can be seen.
     *
     * <p>Unlike the box, the trail is drawn <em>behind</em> the world rather
     * than through it, so it reads as standing somewhere in the landscape
     * instead of floating in front of it.
     */
    public int guideBeamFromChunks = 20;

    /** Where the trail starts, in blocks. */
    public double guideBeamFromBlocks() {
        return Math.max(0, guideBeamFromChunks) * 16.0;
    }

    /** When the search grid's detail panel appears over an item. */
    public enum Detail {
        /** Always, as an ordinary tooltip does. */
        ALWAYS,
        /** Only while shift is held. */
        SHIFT,
        /** Never; the title row still carries the one-line version. */
        OFF;

        private static final Detail[] VALUES = values();

        /** Unknown text reads as the default rather than throwing on a typo. */
        public static Detail parse(String value) {
            if (value == null) return SHIFT;
            for (Detail detail : VALUES) {
                if (detail.name().equalsIgnoreCase(value.trim())) return detail;
            }
            return SHIFT;
        }
    }

    /**
     * Whether the search grid describes the item under the cursor, and when.
     *
     * <p>Behind shift by default. A panel that appears on its own covers the
     * grid the player is reading and follows the cursor around it, which is
     * worse than useless while they are scanning for something - but it is the
     * panel that answers "I have hundreds of these, so why can I never find
     * one", so it should be one modifier away rather than off.
     *
     * <p>{@code ALWAYS} is for players who would rather have it behave like an
     * ordinary tooltip. The panel is the whole of this feature: an earlier
     * version also put a small shulker in the corner of the slot, which was a
     * second thing to learn to read for what the panel already says properly.
     *
     * <p>Replaces an earlier {@code nestedTooltip} boolean; an old config file
     * simply falls back to this default, which is what that boolean did.
     */
    public String itemDetail = Detail.SHIFT.name();

    public Detail itemDetail() {
        return Detail.parse(itemDetail);
    }

    /**
     * Mark the slots holding what was searched for, in whatever container is
     * open.
     *
     * <p>Walking to the right chest is only most of the answer; opening it
     * leaves fifty-four slots to scan for the thing the mod just found.
     */
    public boolean highlightFoundSlots = true;

    /**
     * How a marked slot is drawn: an outline round it, a wash over its item,
     * or both.
     *
     * <p>Stored as a name rather than the enum so an unreadable value in a
     * hand-edited config falls back to the default instead of refusing to load,
     * the same as every other choice here.
     */
    public String slotHighlightStyle = SlotStyle.OUTLINE.name();

    public SlotStyle slotHighlightStyle() {
        return SlotStyle.parse(slotHighlightStyle);
    }

    /**
     * What a marked slot looks like.
     *
     * <p>The outline is the default because it leaves the item and its stack
     * count fully visible - but it is also thin, and against a busy modded
     * container texture it can be the one thing on screen that does not stand
     * out. The background reads at a glance and costs some of the item art;
     * which of those matters more depends on the pack, so it is a choice.
     */
    public enum SlotStyle {
        /** A border round the slot. Shows the whole item. */
        OUTLINE,
        /** A tint over the item. Easiest to pick out, dims the art. */
        BACKGROUND,
        BOTH;

        private static final SlotStyle[] VALUES = values();

        /** Unknown text reads as the default rather than throwing on a typo. */
        public static SlotStyle parse(String value) {
            if (value == null) return OUTLINE;
            for (SlotStyle style : VALUES) {
                if (style.name().equalsIgnoreCase(value.trim())) return style;
            }
            return OUTLINE;
        }

        public boolean drawsOutline() {
            return this == OUTLINE || this == BOTH;
        }

        public boolean drawsBackground() {
            return this == BACKGROUND || this == BOTH;
        }

        /** How this reads in the settings screen. */
        public String label() {
            return switch (this) {
                case OUTLINE -> "outline";
                case BACKGROUND -> "background";
                case BOTH -> "both";
            };
        }
    }

    // --- Highlight colours -------------------------------------------------

    /**
     * The nearest match, as 0xRRGGBB, and the colour the rest pulse through.
     *
     * <p>Purple because it does two jobs at once. It is what the nearest match
     * holds steadily - the one the guidance is talking about and the one being
     * walked towards - and it is also the far end of every other marker's
     * pulse. So "the marker that is always this colour" is the answer, and the
     * ones that only pass through it are the alternatives, without either
     * needing a label.
     *
     * <p>It also survives the backgrounds it is seen against: the sun is yellow
     * and the sky is blue, so a marker in either disappears into exactly what
     * it is most often in front of, and red reads as damage. Purple occurs
     * almost nowhere in Minecraft's terrain.
     */
    public int nearestColour = 0x9B30E0;

    /**
     * What every other match rests at, as 0xRRGGBB.
     *
     * <p>Yellow, and the markers sit here most of the time - the pulse towards
     * {@link #nearestColour} is a swell out of this colour and back, not an
     * even alternation between the two. See {@code HighlightPulse}, where that
     * curve lives and is tested.
     */
    public int otherColour = 0xFFD21E;

    // Unpacked once per colour change rather than per frame. The box renderer
    // asks for both every frame it draws; the colours move only when somebody
    // is dragging the picker.
    //
    // Whether the cache is filled is asked of the array, not of the remembered
    // colour. Every int is a colour somebody could write in the config file -
    // -1 is opaque white - so a sentinel that means "not worked out yet" has no
    // free value to use: keying on one made a config holding exactly that
    // colour return null on the first call, and the box renderer reads the
    // components without checking.
    private transient int nearestRgbFor;
    private transient float[] nearestRgb;
    private transient int otherRgbFor;
    private transient float[] otherRgb;

    /** The colour's components, 0-1. Shared and must not be written to. */
    public float[] nearestRgb() {
        if (nearestRgb == null || nearestRgbFor != nearestColour) {
            nearestRgbFor = nearestColour;
            nearestRgb = rgb(nearestColour);
        }
        return nearestRgb;
    }

    /** The colour's components, 0-1. Shared and must not be written to. */
    public float[] otherRgb() {
        if (otherRgb == null || otherRgbFor != otherColour) {
            otherRgbFor = otherColour;
            otherRgb = rgb(otherColour);
        }
        return otherRgb;
    }

    private static float[] rgb(int packed) {
        return new float[] {
                ((packed >> 16) & 0xFF) / 255.0f,
                ((packed >> 8) & 0xFF) / 255.0f,
                (packed & 0xFF) / 255.0f,
        };
    }

    // --- How a marker is drawn ---------------------------------------------

    /** What shape a world marker is drawn as. */
    public enum Shape {
        /** A wireframe box. Says which block, and hides none of it. */
        OUTLINE,
        /** A solid translucent box. Far easier to pick out across a base. */
        CUBE,
        /** Both: a solid box with its edges picked out. */
        BOTH;

        public static Shape parse(String value) {
            if (value == null) return CUBE;
            for (Shape shape : values()) {
                if (shape.name().equalsIgnoreCase(value.trim())) return shape;
            }
            return CUBE;
        }

        public boolean drawsCube() {
            return this == CUBE || this == BOTH;
        }

        public boolean drawsOutline() {
            return this == OUTLINE || this == BOTH;
        }

        /** What the settings screen shows, in the game's own lower case. */
        public String label() {
            return switch (this) {
                case OUTLINE -> "outline";
                case CUBE -> "full cube";
                case BOTH -> "cube and outline";
            };
        }
    }

    /**
     * Which of those shapes the markers use.
     *
     * <p>A solid cube by default. The outline was the original and is the more
     * precise of the two - it says which block and hides nothing - but precision
     * is not the problem a marker has at forty blocks in a base full of wire.
     * A filled box is found by looking rather than by scanning, which is the
     * job, and the outline is one click away for anyone who wants the chest
     * visible inside its own marker.
     */
    public String highlightShape = Shape.CUBE.name();

    public Shape highlightShape() {
        return Shape.parse(highlightShape);
    }

    /** How a marker arrives when a search lands. */
    public enum Entry {
        /** It is simply there. */
        NONE,
        /** It fades up from nothing. */
        FADE,
        /** It swells outwards into place, fading as it goes. */
        GROW;

        public static Entry parse(String value) {
            if (value == null) return GROW;
            for (Entry entry : values()) {
                if (entry.name().equalsIgnoreCase(value.trim())) return entry;
            }
            return GROW;
        }

        public String label() {
            return switch (this) {
                case NONE -> "appear";
                case FADE -> "fade in";
                case GROW -> "grow in";
            };
        }
    }

    /**
     * How the markers arrive.
     *
     * <p>Growing, by default. A search closes the screen and drops the player
     * back into the world, and half a dozen boxes that are simply <em>there</em>
     * the moment it closes read as part of the scenery - the eye has nothing to
     * catch. Something that arrives is something the mod is saying. It is over
     * in under half a second either way.
     */
    public String highlightEntry = Entry.GROW.name();

    public Entry highlightEntry() {
        return Entry.parse(highlightEntry);
    }

    /**
     * How solid a filled marker is, as a percentage.
     *
     * <p>Low. It has to be obvious from across a base and still leave the chest
     * inside it recognisable close up, and those pull in opposite directions -
     * this is the number that settles the argument, so it is a setting rather
     * than a constant.
     */
    public int highlightCubeOpacity = 35;

    /** {@link #highlightCubeOpacity} as an alpha, 0-1. */
    public float cubeAlpha() {
        return Math.clamp(highlightCubeOpacity, 5, 100) / 100.0f;
    }

    // --- Cheat-like features ----------------------------------------------

    /**
     * The master switch over everything that is arguably cheating.
     *
     * <p>Off, and this is the one default in this file chosen on principle
     * rather than on usefulness. Knowing what is in a chest you have never
     * opened is a convenience when it is your own base and it is x-ray when it
     * is a structure you have not walked into yet, and the difference is not
     * something the mod can tell from the outside - only the player knows which
     * world they are in and what they agreed to there.
     *
     * <p>So the features that cross that line are collected behind one switch
     * that has to be found and turned on deliberately, rather than being
     * scattered through the settings at defaults somebody might never read.
     * Turning it off does not forget the choices underneath it: each keeps its
     * own value and simply stops acting, so this can be switched off for one
     * server and back on at home without setting them all up again.
     */
    public boolean cheatFeatures = false;

    /**
     * Index generated chests - loot that has never been opened.
     *
     * <p>The strongest thing in here. The scanner reads region files directly,
     * so it sees structures in chunks nobody has ever loaded: turning this on
     * puts every unlooted chest in the world into the index, which is x-ray in
     * the plainest sense. Off, containers that worldgen placed are neither
     * recorded nor returned, and the index is a map of what players put
     * somewhere - which is what the mod is for.
     *
     * <p>Narrower than "generated", on purpose: it holds back loot that is
     * still <em>unopened</em>. A structure chest somebody has already been
     * through is a chest with real contents that a player put there, and
     * remembering it is the whole mod - hiding it would break the ordinary case
     * to prevent the unusual one.
     *
     * <p>Only as good as the classification behind it, which is deliberately
     * conservative: a container is generated when it sits in a structure or
     * still holds an unrolled loot table, and {@code UNKNOWN} counts as
     * player-placed here rather than being hidden on suspicion.
     *
     * <p>Held back at the region scan - the one path that reads chunks nobody
     * has ever loaded - and again when results are returned, so turning it off
     * hides what an earlier scan already found rather than waiting for the next
     * one.
     */
    public boolean indexGeneratedChests = false;

    /** Whether generated containers may be recorded and returned at all. */
    public boolean indexesGeneratedChests() {
        return cheatFeatures && indexGeneratedChests;
    }

    /** Whether a match within reach may be opened for the player. */
    public boolean opensInReach() {
        return cheatFeatures && openInReach;
    }

    /** Whether the view may be turned to face a match. */
    public boolean turnsToTarget() {
        return cheatFeatures && turnToTarget;
    }

    /** Whether carts and boats may be read on a server without the mod. */
    public boolean readsEntitiesOnVanillaServers() {
        return cheatFeatures && entityContainersOnVanillaServers;
    }

    /** Seconds a highlight lasts while the player keeps making progress towards it. */
    public int highlightSeconds = 45;

    /** Seconds a highlight survives once the player is heading away. */
    public int highlightRecedingGraceSeconds = 10;

    public static ChestIndexConfig get() {
        if (instance == null) instance = load();
        return instance;
    }

    /**
     * Where the settings live, or null when there is nowhere to put them.
     *
     * <p>Null happens outside a running game - a unit test, or anything else
     * holding this class without a mod loader under it. The settings are then
     * whatever the defaults are, which is a working answer; the alternative was
     * the loader's own failure thrown from a getter that every caller treats as
     * infallible, and it reached {@code applyFilters} on the query path.
     */
    private static Path file() {
        try {
            return FabricLoader.getInstance().getConfigDir().resolve(ChestIndex.MOD_ID + ".json");
        } catch (RuntimeException | LinkageError noLoader) {
            return null;
        }
    }

    private static ChestIndexConfig load() {
        Path path = file();
        if (path == null) return new ChestIndexConfig();
        if (!Files.isRegularFile(path)) {
            ChestIndexConfig fresh = new ChestIndexConfig();
            fresh.save();
            return fresh;
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            ChestIndexConfig loaded = GSON.fromJson(reader, ChestIndexConfig.class);
            // An empty or truncated file parses to null rather than throwing.
            if (loaded == null) return new ChestIndexConfig();
            loaded.normalise();
            return loaded;
        } catch (IOException | RuntimeException e) {
            // Never let a broken config stop the mod loading; defaults are fine.
            ChestIndex.LOG.warn("Could not read {}, using defaults: {}", path, e.toString());
            return new ChestIndexConfig();
        }
    }

    /**
     * Replaces nulls a hand-edited file can leave behind.
     *
     * <p>Gson keeps a field's initialiser when its key is absent, but writes a
     * real null when the key is present and says {@code null} - so a file with
     * {@code "disabledServers": null} in it would otherwise reach every caller
     * as a null list.
     */
    private void normalise() {
        if (disabledServers == null) disabledServers = new ArrayList<>();
        if (assistServers == null) assistServers = new ArrayList<>();
        if (searchButtonPositions == null) searchButtonPositions = new LinkedHashMap<>();
        if (machineTypes == null) machineTypes = new ArrayList<>();
        if (utilityTypes == null) utilityTypes = new ArrayList<>();
        if (searchText == null) searchText = "";
        if (sortMode == null) sortMode = "COUNT";
    }

    public void save() {
        Path path = file();
        if (path == null) return;
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            ChestIndex.LOG.warn("Could not write {}: {}", path, e.toString());
        }
    }

    public long highlightDurationMs() {
        return Math.max(1, highlightSeconds) * 1000L;
    }

    public long highlightRecedingGraceMs() {
        return Math.max(1, highlightRecedingGraceSeconds) * 1000L;
    }
}
