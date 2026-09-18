# ChestIndex

> Search your storage without opening every chest.

ChestIndex indexes containers across your Minecraft world and lets you search their contents — including containers in unloaded chunks.

**Minecraft:** `1.21.11` · `26.2`  
**Loader:** Fabric

## Features

- Search containers across your world
- Scan unloaded chunks
- Search nested storage
- Cross-dimension search
- World container markers
- Litematica integration
- Incremental scanning
- Server-side indexing and permissions

## Requirements

- [Fabric](https://fabricmc.net/)
- [Fabric API](https://modrinth.com/mod/fabric-api)
- [Mod Menu](https://modrinth.com/mod/modmenu) — optional

## Controls

| Key | Action |
|---|---|
| `` ` `` | Open ChestIndex |
| `Z` | Search under cursor |

Both keybinds can be changed in Minecraft's Controls menu.

## Server

ChestIndex supports:

- Singleplayer
- LAN
- ChestIndex servers
- Vanilla servers

On vanilla servers, the client can only index containers and contents it has received.

## Litematica

When Litematica is installed, ChestIndex can search for containers containing materials required by your schematics.

## Build

```bash
./gradlew build

./gradlew ":1.21.11:build"
./gradlew ":26.2:build"
```

## License:

MIT
