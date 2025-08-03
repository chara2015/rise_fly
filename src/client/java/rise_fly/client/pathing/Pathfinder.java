package rise_fly.client.pathing;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import rise_fly.client.cache.WorldCache;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Pathfinder {
    public static final Pathfinder INSTANCE = new Pathfinder();
    private static final int FLIGHT_Y = 200;

    private final Map<ChunkPos, Double> costMap = new ConcurrentHashMap<>();

    private Pathfinder() {}

    public void reportBadChunk(ChunkPos pos) {
        costMap.put(pos, costMap.getOrDefault(pos, 1.0) * 2);
    }

    public void clearCosts() {
        costMap.clear();
    }

    public List<ChunkPos> findPath(ChunkPos startPos, ChunkPos targetPos) {
        PathNode startNode = new PathNode(startPos);
        PathNode targetNode = new PathNode(targetPos);
        PriorityQueue<PathNode> openSet = new PriorityQueue<>();
        HashSet<ChunkPos> closedSet = new HashSet<>();
        openSet.add(startNode);
        startNode.gCost = 0;
        startNode.hCost = getDistance(startNode, targetNode);

        while (!openSet.isEmpty()) {
            PathNode currentNode = openSet.poll();
            if (currentNode.equals(targetNode)) {
                return retracePath(startNode, currentNode);
            }
            closedSet.add(currentNode.pos);

            for (PathNode neighbor : getNeighbors(currentNode)) {
                if (closedSet.contains(neighbor.pos) || !isTraversable(currentNode.pos, neighbor.pos)) {
                    continue;
                }
                double costMultiplier = costMap.getOrDefault(neighbor.pos, 1.0);
                double newMovementCostToNeighbor = currentNode.gCost + getDistance(currentNode, neighbor) * costMultiplier;

                if (newMovementCostToNeighbor < neighbor.gCost || !openSet.contains(neighbor)) {
                    neighbor.gCost = newMovementCostToNeighbor;
                    neighbor.hCost = getDistance(neighbor, targetNode);
                    neighbor.parent = currentNode;
                    if (!openSet.contains(neighbor)) {
                        openSet.add(neighbor);
                    }
                }
            }
        }
        return null;
    }

    private boolean isTraversable(ChunkPos from, ChunkPos to) {
        Vec3d start = new Vec3d(from.getCenterX(), FLIGHT_Y, from.getCenterZ());
        Vec3d end = new Vec3d(to.getCenterX(), FLIGHT_Y, to.getCenterZ());
        int samples = 5;
        for (int i = 0; i <= samples; i++) {
            Vec3d samplePoint = start.lerp(end, (double)i / samples);
            if (WorldCache.INSTANCE.isSolid(BlockPos.ofFloored(samplePoint))) {
                return false;
            }
        }
        return true;
    }

    private List<ChunkPos> retracePath(PathNode startNode, PathNode endNode) {
        List<ChunkPos> path = new ArrayList<>();
        PathNode currentNode = endNode;
        while (currentNode != null && !currentNode.equals(startNode)) {
            path.add(currentNode.pos);
            currentNode = currentNode.parent;
        }
        Collections.reverse(path);
        return path;
    }

    private List<PathNode> getNeighbors(PathNode node) {
        List<PathNode> neighbors = new ArrayList<>();
        neighbors.add(new PathNode(new ChunkPos(node.pos.x + 1, node.pos.z)));
        neighbors.add(new PathNode(new ChunkPos(node.pos.x - 1, node.pos.z)));
        neighbors.add(new PathNode(new ChunkPos(node.pos.x, node.pos.z + 1)));
        neighbors.add(new PathNode(new ChunkPos(node.pos.x, node.pos.z - 1)));
        return neighbors;
    }

    private double getDistance(PathNode nodeA, PathNode nodeB) {
        int dstX = Math.abs(nodeA.pos.x - nodeB.pos.x);
        int dstZ = Math.abs(nodeA.pos.z - nodeB.pos.z);
        return dstX + dstZ;
    }
}