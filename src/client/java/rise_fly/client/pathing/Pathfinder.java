package rise_fly.client.pathing;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.biome.Biome;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.Registry;
import rise_fly.client.cache.BlockStatus;
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

        // 策略：优先级 1 - 尝试以当前高度直接寻路
        int initialAltitude = (int)startVec.y;
        DebugUtils.log("§e优先级1: 尝试当前高度 Y=" + initialAltitude);
        List<ChunkPos> chunkPath = findChunkPath(startPos, targetPos, initialAltitude);
        if (chunkPath != null && !chunkPath.isEmpty()) {
            DebugUtils.log("§a成功找到路径！");
            return smoothPath(startVec, targetVec, chunkPath, initialAltitude);
        }

        // 策略：优先级 2 - 尝试左右绕行
        DebugUtils.log("§e优先级2: 尝试左右绕行...");
        double horizontalOffsetDistance = 16 * 5;
        Vec3d direction = targetVec.subtract(startVec).normalize();
        Vec3d perpendicularDirection = new Vec3d(-direction.z, 0, direction.x).normalize();

        Vec3d leftOffsetTarget = targetVec.add(perpendicularDirection.multiply(horizontalOffsetDistance));
        List<ChunkPos> leftPath = findChunkPath(startPos, new ChunkPos(BlockPos.ofFloored(leftOffsetTarget)), initialAltitude);
        if (leftPath != null && !leftPath.isEmpty()) {
            DebugUtils.log("§a成功找到左绕行路径！");
            List<Vec3d> smoothedPath = smoothPath(startVec, leftOffsetTarget, leftPath, initialAltitude);
            smoothedPath.add(targetVec);
            return smoothedPath;
        }

        Vec3d rightOffsetTarget = targetVec.subtract(perpendicularDirection.multiply(horizontalOffsetDistance));
        List<ChunkPos> rightPath = findChunkPath(startPos, new ChunkPos(BlockPos.ofFloored(rightOffsetTarget)), initialAltitude);
        if (rightPath != null && !rightPath.isEmpty()) {
            DebugUtils.log("§a成功找到右绕行路径！");
            List<Vec3d> smoothedPath = smoothPath(startVec, rightOffsetTarget, rightPath, initialAltitude);
            smoothedPath.add(targetVec);
            return smoothedPath;
        }

        // 策略：优先级 3 - 尝试更改高度
        DebugUtils.log("§e优先级3: 尝试更改高度...");
        List<Integer> fallbackAltitudes = Arrays.asList(200, 80, 120);
        for (int altitude : fallbackAltitudes) {
            DebugUtils.log("正在尝试备选高度 Y=" + altitude + " 寻找路径...");
            chunkPath = findChunkPath(startPos, targetPos, altitude);
            if (chunkPath != null && !chunkPath.isEmpty()) {
                DebugUtils.log("§a成功在备选高度 Y=" + altitude + " 找到路径！");
                return smoothPath(startVec, targetVec, chunkPath, altitude);
            }
        }

        DebugUtils.log("§c在所有策略下均未找到可用路径。");
        return null;
    }

    public List<Vec3d> findPathXMode(Vec3d startVec, Vec3d targetVec) {
        DebugUtils.log("开始 X 模式路径计算...");
        ChunkPos startPos = new ChunkPos(BlockPos.ofFloored(startVec));
        ChunkPos targetPos = new ChunkPos(BlockPos.ofFloored(targetVec));

        List<Integer> xModeAltitudes = Arrays.asList(320, 256, 200, 120);
        for (int altitude : xModeAltitudes) {
            DebugUtils.log("§eX 模式: 尝试高空 Y=" + altitude);
            List<ChunkPos> chunkPath = findChunkPath(startPos, targetPos, altitude);
            if (chunkPath != null && !chunkPath.isEmpty()) {
                DebugUtils.log("§aX 模式成功在 Y=" + altitude + " 找到路径！");
                return smoothPath(startVec, targetVec, chunkPath, altitude);
            }
        }

        DebugUtils.log("§cX 模式在所有策略下均未找到可用路径。");
        return null;
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

    public BlockStatus checkBlockStatus(Vec3d pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            return BlockStatus.UNKNOWN;
        }
        return WorldCache.INSTANCE.getBlockStatus(BlockPos.ofFloored(pos));
    }

    public boolean isTraversable(Vec3d from, Vec3d to) {
        if (checkBlockStatus(from) == BlockStatus.SOLID || checkBlockStatus(to) == BlockStatus.SOLID) {
            return false;
        }
        int samples = (int) Math.ceil(from.distanceTo(to) / 8);
        for (int i = 0; i <= samples; i++) {
            Vec3d samplePoint = from.lerp(to, (double)i / samples);
            BlockStatus status = checkBlockStatus(samplePoint);
            if (status == BlockStatus.SOLID) {
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

                if (WorldCache.INSTANCE.getBlockStatus(neighbor.pos.getStartPos()) == BlockStatus.UNKNOWN) {
                    newMovementCostToNeighbor += 1000;
                }

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