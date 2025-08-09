package rise_fly.client.pathing;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.biome.Biome;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.Registry;
import rise_fly.client.cache.WorldCache;
import rise_fly.client.util.DebugUtils;

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
        DebugUtils.log("开始路径计算...");
        ChunkPos startPos = new ChunkPos(BlockPos.ofFloored(startVec));
        ChunkPos targetPos = new ChunkPos(BlockPos.ofFloored(targetVec));

        List<Integer> altitudesToTry = new ArrayList<>();
        int targetY = (int) targetVec.y;
        altitudesToTry.add(targetY);
        altitudesToTry.add((int)startVec.y);
        altitudesToTry.add(120);
        altitudesToTry.add(200);
        altitudesToTry.add(80);
        List<Integer> distinctAltitudes = altitudesToTry.stream().distinct().toList();

        for (int altitude : distinctAltitudes) {
            DebugUtils.log("正在尝试在 Y=" + altitude + " 高度寻找路径...");
            List<ChunkPos> chunkPath = findChunkPath(startPos, targetPos, altitude);

            if (chunkPath != null && !chunkPath.isEmpty()) {
                DebugUtils.log("§a成功在 Y=" + altitude + " 找到路径！区块节点数: " + chunkPath.size());
                List<Vec3d> smoothedPath = smoothPath(startVec, targetVec, chunkPath, altitude);
                DebugUtils.log("路径平滑完成，生成 " + smoothedPath.size() + " 个精确路径点。");
                return smoothedPath;
            }
        }

        DebugUtils.log("§c在所有高度层均未找到可用路径。");
        return null;
    }

    public List<Vec3d> findPredictivePath(Vec3d startVec, Vec3d targetVec, int renderDistance) {
        DebugUtils.log("开始预测性路径计算...");
        List<Vec3d> predictivePath = new ArrayList<>();

        Vec3d currentPoint = startVec;
        double distanceToTarget = currentPoint.distanceTo(targetVec);

        while (distanceToTarget > renderDistance * 0.8) {
            Vec3d direction = targetVec.subtract(currentPoint).normalize();
            Vec3d nextSegmentEnd = currentPoint.add(direction.multiply(renderDistance * 0.8));

            ChunkPos chunkPos = new ChunkPos(BlockPos.ofFloored(nextSegmentEnd));
            int predictedAltitude = getPredictedAltitudeForChunk(chunkPos);

            predictivePath.add(new Vec3d(nextSegmentEnd.x, predictedAltitude, nextSegmentEnd.z));
            currentPoint = nextSegmentEnd;
            distanceToTarget = currentPoint.distanceTo(targetVec);
        }

        predictivePath.add(targetVec);
        return predictivePath;
    }

    private int getPredictedAltitudeForChunk(ChunkPos pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return 128;

        Registry<Biome> biomeRegistry = client.world.getRegistryManager().get(RegistryKeys.BIOME);
        Biome biome = client.world.getBiome(pos.getStartPos()).value();

        String biomeId = biomeRegistry.getId(biome).getPath();

        if (biomeId.contains("ocean") || biomeId.contains("beach")) {
            return 80;
        }
        if (biomeId.contains("mountain") || biomeId.contains("hills") || biomeId.contains("plateau")) {
            return 200;
        }
        if (biomeId.contains("deep_dark")) {
            return -20;
        }

        return 120;
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

    public boolean isTraversable(Vec3d from, Vec3d to) {
        if (WorldCache.INSTANCE.isSolid(BlockPos.ofFloored(from)) || WorldCache.INSTANCE.isSolid(BlockPos.ofFloored(to))) {
            return false;
        }

        int samples = (int) Math.ceil(from.distanceTo(to) / 8);
        for (int i = 0; i <= samples; i++) {
            Vec3d samplePoint = from.lerp(to, (double)i / samples);
            if (WorldCache.INSTANCE.isSolid(BlockPos.ofFloored(samplePoint))) {
                return false;
            }
        }
        return true;
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