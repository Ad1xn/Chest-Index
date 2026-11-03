package dev.adrian.chestindex.core.anvil;

import dev.adrian.chestindex.core.model.ContainerRecord;
import dev.adrian.chestindex.core.model.Origin;
import dev.adrian.chestindex.core.util.BlockKey;
import dev.adrian.chestindex.core.util.MonsterRooms;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class OriginClassifier {
    public static final int NO_CHEST_TYPE = -1;

    private OriginClassifier() {}

    public static List<ContainerRecord> classify(ChunkExtractor.ChunkContents contents) {
        return classify(contents.containers(), contents.structureBoxes());
    }

    public static List<ContainerRecord> classify(Collection<ContainerRecord> containers,
                                                 Collection<ChunkExtractor.StructureBox> boxes) {
        return classify(containers, boxes, List.of(), NO_CHEST_TYPE);
    }

    public static List<ContainerRecord> classify(Collection<ContainerRecord> containers,
                                                 Collection<ChunkExtractor.StructureBox> boxes,
                                                 Collection<Long> spawners, int chestTypeId) {
        List<ContainerRecord> classified = new ArrayList<>(containers.size());
        for (ContainerRecord container : containers) {
            classified.add(classifyOne(container, boxes, spawners, chestTypeId));
        }
        return classified;
    }

    private static ContainerRecord classifyOne(ContainerRecord container,
                                               Collection<ChunkExtractor.StructureBox> boxes,
                                               Collection<Long> spawners, int chestTypeId) {
        if (container.origin() == Origin.NATURAL) return container;

        if (container.typeId() == chestTypeId && MonsterRooms.nearAnySpawner(container.pos(), spawners)) {
            return container.withOrigin(Origin.NATURAL, null);
        }

        int x = BlockKey.x(container.pos());
        int y = BlockKey.y(container.pos());
        int z = BlockKey.z(container.pos());

        for (ChunkExtractor.StructureBox box : boxes) {
            if (box.contains(x, y, z)) {
                return container.withOrigin(Origin.NATURAL, null);
            }
        }
        return container;
    }
}
