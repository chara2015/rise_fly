package rise_fly.client.pathing;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import rise_fly.client.cache.WorldCache;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Pathfinder {
    public static final Pathfinder INSTANCE = new Pathfinder();
    private final Map<ChunkPos, Double> costMap = new ConcurrentHashMap<>();

    private Pathfinder() {}

    public void reportBadChunk(ChunkPos pos) {
        costMap.put(pos, costMap.getOrDefault(pos, 1.0) * 2);
    }

    public void clearCosts() {
        costMap.clear();
    }

    public List<Vec3d> findPath(Vec3d startVec, Vec3d targetVec) {
        ChunkPos startPos = new ChunkPos(BlockPos.ofFloored(startVec));
        ChunkPos targetPos = new ChunkPos(BlockPos.ofFloored(targetVec));

        int cruiseAltitude = getCruiseAltitude(startPos, targetPos);

        List<ChunkPos> chunkPath = findChunkPath(startPos, targetPos, cruiseAltitude);
        if (chunkPath == null) { // A* 未找到路径时也应返回null
            return null;
        }

        return smoothPath(startVec, targetVec, chunkPath, cruiseAltitude);
    }

    private List<Vec3d> smoothPath(Vec3d startPoint, Vec3d endPoint, List<ChunkPos> path, int cruiseAltitude) {
        List<Vec3d> waypoints = new ArrayList<>();
        waypoints.add(startPoint);

        if (path.isEmpty()) {
            waypoints.add(endPoint);
            return waypoints;
        }

        int currentPathIndex = 0;
        while(currentPathIndex < path.size()) {
            Vec3d lastWaypoint = waypoints.get(waypoints.size() - 1);
            int nextNodeIndex = path.size() - 1;

            while(nextNodeIndex > currentPathIndex) {
                ChunkPos checkChunk = path.get(nextNodeIndex);
                Vec3d checkPoint = new Vec3d(checkChunk.getCenterX(), cruiseAltitude, checkChunk.getCenterZ());

                if (isTraversable(lastWaypoint, checkPoint)) {
                    waypoints.add(checkPoint);
                    currentPathIndex = nextNodeIndex;
                    break;
                }
                nextNodeIndex--;
            }

            if (nextNodeIndex <= currentPathIndex) {
                ChunkPos nextChunk = path.get(currentPathIndex);
                waypoints.add(new Vec3d(nextChunk.getCenterX(), cruiseAltitude, nextChunk.getCenterZ()));
                currentPathIndex++;
            }
        }

        waypoints.add(endPoint);
        return waypoints;
    }


    private List<ChunkPos> findChunkPath(ChunkPos startPos, ChunkPos targetPos, int cruiseAltitude) {
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
                Vec3d currentNodeCenter = new Vec3d(currentNode.pos.getCenterX(), cruiseAltitude, currentNode.pos.getCenterZ());
                Vec3d neighborNodeCenter = new Vec3d(neighbor.pos.getCenterX(), cruiseAltitude, neighbor.pos.getCenterZ());

                if (closedSet.contains(neighbor.pos) || !isTraversable(currentNodeCenter, neighborNodeCenter)) {
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

    private boolean isTraversable(Vec3d from, Vec3d to) {
        int samples = (int) Math.ceil(from.distanceTo(to) / 8);
        for (int i = 0; i <= samples; i++) {
            Vec3d samplePoint = from.lerp(to, (double)i / samples);
            if (WorldCache.INSTANCE.isSolid(BlockPos.ofFloored(samplePoint))) {
                return false;
            }
        }
        return true;
    }

    private int getCruiseAltitude(ChunkPos start, ChunkPos end) {
        int maxTerrainY = -64;
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        ChunkPos[] checkPoints = {start, new ChunkPos((start.x + end.x) / 2, (start.z + end.z) / 2), end};

        for (ChunkPos cp : checkPoints) {
            for (int y = 319; y > -64; y--) {
                mutable.set(cp.getCenterX(), y, cp.getCenterZ());
                if (WorldCache.INSTANCE.isSolid(mutable)) {
                    if (y > maxTerrainY) {
                        maxTerrainY = y;
                    }
                    break;
                }
            }
        }
        return Math.min(maxTerrainY + 30, 256);
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