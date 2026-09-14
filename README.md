# ChestTracker

**Find your stuff without opening a single chest.**

ChestTracker indexes **every container in your world** within a configurable radius — including
chunks that aren't loaded — and lets you search it. It's a proactive replacement for
[Chest Tracker](https://modrinth.com/mod/chest-tracker), which only remembers containers after
you've physically opened them.

Fabric · Minecraft **1.21.11** and **26.2**

### Supported versions

**1.21.11 and newer.** Both current targets sit in Minecraft's unobfuscated era, so adding another
release from it (26.1, a future 26.3) is a one-line entry in `settings.gradle.kts` plus a
`versions/<mc>/gradle.properties` file.

**Below 1.21.11 is not supported and is not a small change.** Fabric Loom 1.17 reports even 1.21.8 as
a non-obfuscated environment and refuses both `officialMojangMappings()` and
`createRemapConfigurations()`, so it cannot build those versions at all. Supporting one would mean a
second Stonecutter buildscript pinned to an older Loom (~1.14) with Mojang mappings and the remap
configurations — a parallel build path to maintain, not a version bump.

## What it does

- **Indexes the whole world, not your render distance.** A background scanner reads region files
  straight off disk, so containers in unloaded chunks are found too. Radius is configurable.
- **Only reads what changed.** Each region file is remembered with its size and timestamp, so the
  second scan of a world reads almost nothing — and a scan interrupted by quitting resumes where it
  stopped instead of starting over.
- **Filters natural vs built chests.** Loot chests in villages, temples and strongholds are
  classified separately from anything a player put there, and you can filter either way.
- **Searches nested storage.** A diamond inside a shulker box inside a barrel still shows up, and
  the item panel tells you how many of your total are sealed inside something.
- **Every container type**, individually toggleable: chests, barrels, shulker boxes, ender chests,
  hoppers, droppers, dispensers, furnaces, brewing stands, crafters, and more.
- **Containers that move.** Chest minecarts, hopper minecarts and chest boats are found too. They're
  read live rather than indexed, because a stored position for something on rails is a lie the
  moment it's written.
- **Removed containers disappear.** Break a chest — or let a creeper do it — and it leaves the index
  immediately. Anything broken while the mod wasn't running is cleaned up on the next scan.

## Using it

**Two keys**, both under a **ChestTracker** heading in Controls:

| Default | What it does |
|---|---|
| `` ` `` | Open the search screen |
| `Z` | Search for whatever your cursor is over, without opening anything |

> On a non-QWERTY keyboard the second one is wherever `Z` sits on a US layout — the key labelled
> **Y** on QWERTZ, for instance. That is how Minecraft handles every keybind; rebind it if you'd
> rather press the key with `Z` printed on it.

**In the search screen**, items are shown in a chest-shaped grid, most plentiful first.

- **Left-click** an item to outline every container holding it and close the screen.
- **Right-click** an item for the list of places, nearest first, with distances.
- **Hold shift** over an item for the detail panel: totals, how many containers, how many are sealed
  inside shulker boxes, and how far the nearest one is. The `Item detail` setting switches this
  between *holding shift* (the default), *always* and *never*.
- **A button at the bottom left** shows which view you're reading. Hover it and the rest fan out to
  the right — your **ender chest** first, then overworld, Nether, End, then anything a mod added.
  Only views that actually hold something appear, and whichever you pick is still selected the next
  time you open the screen.
- A **bar under the search field** appears while the world is still being read.

### Searching

Typing matches **words, in any order**: `blue wool` finds `light_blue_wool`, and so does
`wool blue`.

A word with a prefix asks about a property instead. Click the search box for the list, or start
typing a prefix and it completes:

| Prefix | Finds |
|---|---|
| `@` | items from one mod — `@create` |
| `#` | items sharing an item tag — `#logs` |
| `>` | only containers of one kind — `>barrel` |
| `tab:` | the game's own creative tab — `tab:redstone` |
| `ench:` | enchanted with this — `ench:mending` |
| `potion:` | this potion, however bottled — `potion:swiftness` |
| `text:` | words anywhere in the tooltip — `text:mining` |

Prefixes combine with each other and with plain words, so `@create ench:efficiency drill` is a
single question.

**In any container window**, a small magnifier sits at the top right. Left-click opens the search
screen; **right-drag** moves it. Each kind of window remembers its own position, so the button can
sit in the middle of a hopper's title bar and in the corner of a double chest.

**Once you find something**, ChestTracker marks it in the world: a box on each container, drawn
through whatever is in front of it, because a marker you can only see once you can already see the
chest isn't telling you anything. Every box rests yellow and swells through purple and back; the
nearest one holds the purple outright, which is what picks it out without labelling it. A double
chest gets one box across both halves. Containers past render distance are drawn at the horizon
rather than not at all.

**Further out than twenty chunks** — editable, and past most render distances — each match also gets
a trail of marks standing on it, running up to the build limit. That one is drawn *behind* the
world rather than through it, so it reads as standing somewhere in the landscape instead of floating
in front of it. Closer in it stays out of the way: the box already says which container it is.

When you arrive and open the chest, the slot holding your item swings between the two colours for a
moment and then settles on yellow — an outline, a wash over the item, or both, whichever you prefer
— and if it's inside a shulker box, the shulker is marked in purple instead, then the item once you
open that, and the bundle inside that if that's where it ended up.

**If it's in your ender chest**, that whole trail still works: the nearest ender chest in the world
is boxed, an ender chest *item* in whatever you have open is marked, and the item itself is marked
once the chest is open.

**If it isn't in this dimension**, the search says where it is rather than saying you don't have
any — the ender chest is checked first, then every other dimension the index knows about.

Everything above is configurable through Mod Menu. The settings are grouped into General,
Containers, Searching, Guidance and Assist, and the box at the top filters every section at once —
by the wording of the explanations as well as the labels, so you can find a setting by what it does
rather than by what it's called.

### With Litematica

If Litematica is installed, its material list grows two buttons of its own:

- **Search items** highlights every container holding anything the schematic still needs. From
  there the highlight narrows itself — each material drops out as you pick up enough of it, and it
  clears and says so when the last one goes. A material that has once been satisfied stays that
  way, so laying the blocks back down doesn't relight the chests you got them from.
- **One item…** opens the material list as a list of searches, for when the question is *"where is
  the rest of the stone brick stairs"* rather than *"where is all forty of these"*.

**Right-drag** moves both. That screen belongs to malilib rather than to vanilla, its buttons are
laid out by code this mod can't measure, and every Litematica release is free to move them — so
rather than guess again when the button lands on top of one of theirs, you can just move it.

## Where it works

Minecraft never sends container *contents* to clients, only block positions. That single protocol
fact decides what's possible in each setup:

| Setup | Container locations | Contents | Unloaded chunks |
|---|---|---|---|
| Singleplayer / LAN host | yes | yes | yes |
| Server with ChestTracker installed | yes | yes (permission-gated) | yes |
| Vanilla server (no mod on the server) | chunks you've visited | containers you've opened | no |

Singleplayer gets everything, because there the client *is* the server.

**On a vanilla server the mod keeps its own index, on your machine.** It notices the server doesn't
have the mod and falls back to what this client can see for itself:

- **Where containers are**, from the chunks the server has already sent you. A chunk packet carries
  every block entity in it, so the moment your client can draw a chest, it knows one is there.
- **What is inside one**, from containers *you opened yourself*. The stacks in an open menu are
  already on your screen; recording them is reading your own client's memory.

A chest therefore holds nothing here until you open it once. There is no way around that — chunk
data carries block states and never inventories — so the screen says so rather than looking broken.

**Nothing is sent to the server, and nothing is automated.** No packet leaves that wouldn't have
left with the mod uninstalled: no scanning, no probing, no containers opened for you, and your view
is never moved. A vanilla server cannot tell the mod is running, which is the point — the servers
this exists for are the ones that would ban you for the alternative.

The two features that *do* move you — turning to face a match, and opening a container already
within reach — are off on anyone else's server for exactly that reason. They look like aim-assist
and auto-interact from the far end, and the far end doesn't get to hear why. In your own world
(including hosting a LAN game) they work as before. On a server they need naming that server under
**Servers those two are allowed on**, which is the setting to prefer — `assistOnServers` turns them
on for *every* server you ever join, which means answering for the strictest one.

The client-side index is kept per server address under `config/chest-tracker/servers/`, and can be
turned off with `clientSideIndex`. **Stored indexes** in the settings lists every world and server
this machine holds one for, showing where each one lives and offering to throw it away.

### Switching it off

**ChestTracker: off** stops everything — no search screen, no button on containers, no key, nothing
indexed, nothing drawn. Not hidden: stopped.

**Servers it stays off on** does the same automatically for the addresses listed, for as long as
you're connected to one. It ships with `donutsmp.net`, `gommehd.net`, `mcpvp.com` and `mcpvp.club`,
because those are strict enough that off is the safe default — remove any of them if you'd rather
decide for yourself. Matching is by host, so `example.net` covers `eu.example.net` and a port makes
no difference.

**Client and server must run the same version.** The two speak a versioned protocol and refuse each
other when it doesn't match, rather than silently misreading one another.

## Server operators

By default **everyone on the server can search** — installing the mod is the decision that players
should be able to. Narrow it with `/chesttracker access <tier>` (takes effect immediately, no
restart) or the `permissionTier` config key:

| `permissionTier` | Who can search | What they see |
|---|---|---|
| `ALL` (default) | everyone | everything |
| `OWNED` | everyone | only containers they placed themselves (operators still see everything) |
| `OP` | operators only | everything |

Worth knowing when choosing: a full index is effectively loot x-ray — it shows where every unopened
generated chest is, and what is in other people's bases.

The tier applies to every player arriving over a connection, LAN guests included. A host playing
their own world is never gated — their screen reads the world directly.

Clients connecting to a server without the mod fall back automatically; no configuration is needed
on either side.

The server side is useful on its own — `/chesttracker find <item>` works from a vanilla client with
no mod installed.

### Commands

Operator-only, since a full index is loot x-ray.

| Command | What it does |
|---|---|
| `/chesttracker scan [chunkRadius]` | Reads the chunks around you, out to a radius |
| `/chesttracker scanworld` | Reads region files that changed since the last scan |
| `/chesttracker scanworld override` | Throws the index away and reads the whole world again |
| `/chesttracker scanworld cancel` | Stops a running scan; what it read is kept |
| `/chesttracker stats` | Container counts, origins, scan progress |
| `/chesttracker find <item>` | Where an item is, in chat |
| `/chesttracker access [tier]` | Show or set who may search |

Use `override` when the index looks *wrong* rather than merely incomplete: an ordinary scan corrects
and adds, but never removes something it doesn't encounter. Searches are empty until it finishes.

The index lives in the world folder, at `<world>/data/chest-tracker/`, beside a plain-text record of
which region files have been read. Deleting that record makes the next scan read everything again;
deleting the folder starts from nothing.

## Installing

Drop the jar for your Minecraft version in `mods/`. Fabric API is required; Mod Menu is optional and
only adds the settings screen.

The mod id is **`chest-tracker`**, deliberately *not* the original mod's `chesttracker` — the two
would collide, and Fabric resolves duplicate ids by silently loading one of them. If you are
upgrading from a build that called itself `chestindex`, delete that jar: the ids differ, so both
would load at once.

## Building

Two Minecraft versions are built from one source tree via
[Stonecutter](https://stonecutter.kikugie.dev/).

```bash
./gradlew ":1.21.11:build"   # -> versions/1.21.11/build/libs/
./gradlew ":26.2:build"      # -> versions/26.2/build/libs/
./gradlew build              # both
```

**Gradle itself must run on JDK 25 or newer.** Loom validates the Gradle JVM, not just the compile
toolchain, so building the 26.2 target on an older JVM fails with
`Minecraft 26.2 requires Java 25 but Gradle is using <n>`. Point `JAVA_HOME` at a JDK 25+ before
building:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 26)   # macOS; any JDK >= 25 works
```

The per-target compile toolchain (21 for 1.21.11, 25 for 26.2) is provisioned by Gradle
automatically, and each target emits bytecode at its own level. Compiling everything at 21 was
tidier, but it leaves a target unable to consume a dependency built for 25 — Gradle's variant
matching rejects it outright.

Tagging a commit `v*` builds both targets and publishes them to GitHub Releases.

## Licence

MIT
