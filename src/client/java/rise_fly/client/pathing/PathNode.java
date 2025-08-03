package rise_fly.client.pathing;

import net.minecraft.util.math.ChunkPos;
import org.jetbrains.annotations.NotNull;

public class PathNode implements Comparable<PathNode> {
    public final ChunkPos pos;
    public PathNode parent;
    public double gCost;
    public double hCost;

    public PathNode(ChunkPos pos) {
        this.pos = pos;
    }

    public double getFCost() {
        return gCost + hCost;
    }

    @Override
    public int compareTo(@NotNull PathNode other) {
        int compare = Double.compare(this.getFCost(), other.getFCost());
        if (compare == 0) {
            compare = Double.compare(this.hCost, other.hCost);
        }
        return compare;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        PathNode pathNode = (PathNode) obj;
        return pos.equals(pathNode.pos);
    }

    @Override
    public int hashCode() {
        return pos.hashCode();
    }
}