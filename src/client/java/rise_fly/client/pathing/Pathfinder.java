package rise_fly.client.pathing;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
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

    /**
     * [最终版] 检查从 'from' 点到 'to' 点之间是否存在一条安全的 "飞行走廊"。
     * 这不再是检查一条线，而是检查一个有体积的通道。
     */
    public boolean isTraversable(Vec3d from, Vec3d to) {
        // 采样密度：每1.5格采样一次，确保不会跳过方块
        double distance = from.distanceTo(to);
        int samples = (int) Math.ceil(distance / 1.5);
        if (samples == 0) return true;

        Vec3d direction = to.subtract(from).normalize();

        // 计算与飞行方向垂直的 "上" 和 "右" 向量，用于构建安全空间
        Vec3d up = new Vec3d(0, 1, 0);
        Vec3d right = direction.crossProduct(up).normalize();
        // 如果飞行方向垂直，crossProduct会是0，需要一个备用向量
        if (right.lengthSquared() < 0.1) {
            right = new Vec3d(1, 0, 0);
        }

        // 重新计算真正的 "上" 向量，确保它与飞行路径和 "右" 向量都垂直
        up = right.crossProduct(direction).normalize();

        // 定义安全走廊的检查点偏移量 (检查中心点周围的8个点)
        Vec3d[] offsets = {
                up,                              // 上
                up.negate(),                       // 下
                right,                           // 右
                right.negate(),                    // 左
                up.add(right),                   // 右上
                up.add(right.negate()),          // 左上
                up.negate().add(right),          // 右下
                up.negate().add(right.negate())  // 左下
        };

        // 沿飞行路径进行采样
        for (int i = 0; i <= samples; i++) {
            Vec3d centerPoint = from.lerp(to, (double)i / samples);

            // 1. 检查中心点本身 (身体和头顶)
            if (isVolumeOccupied(centerPoint)) {
                return false;
            }

            // 2. 检查中心点周围的安全空间
            for (Vec3d offset : offsets) {
                // 将偏移量应用到中心点上，检查安全半径内的方块
                Vec3d checkPoint = centerPoint.add(offset.multiply(1.2)); // 1.2的半径提供额外安全边际
                if (WorldCache.INSTANCE.isSolid(BlockPos.ofFloored(checkPoint))) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * 辅助方法：检查给定中心点代表的玩家体积是否被方块占据。
     * 检查脚下、身体和头顶。
     */
    private boolean isVolumeOccupied(Vec3d center) {
        BlockPos centerPos = BlockPos.ofFloored(center);
        // 检查玩家从脚到头的大致范围
        return WorldCache.INSTANCE.isSolid(centerPos.down(1)) || // 脚下
                WorldCache.INSTANCE.isSolid(centerPos)          || // 身体
                WorldCache.INSTANCE.isSolid(centerPos.up(1));      // 头顶
    }

    private List<ChunkPos> findChunkPath(ChunkPos startPos, ChunkPos targetPos, int cruiseAltitude) {
        PathNode startNode = new PathNode(startPos);
        PathNode targetNode = new PathNode(targetPos);
        PriorityQueue<PathNode> openSet = new PriorityQueue<>();
        HashSet<ChunkPos> closedSet = new HashSet<>();
        HashMap<ChunkPos, PathNode> openMap = new HashMap<>();

        startNode.gCost = 0;
        startNode.hCost = getDistance(startNode, targetNode);
        openSet.add(startNode);
        openMap.put(startNode.pos, startNode);

        while (!openSet.isEmpty()) {
            PathNode currentNode = openSet.poll();
            openMap.remove(currentNode.pos);

            if (currentNode.equals(targetNode)) {
                return retracePath(startNode, currentNode);
            }

            closedSet.add(currentNode.pos);

            for (PathNode neighbor : getNeighbors(currentNode)) {
                if (closedSet.contains(neighbor.pos)) {
                    continue;
                }

                Vec3d currentNodeCenter = new Vec3d(currentNode.pos.getCenterX(), cruiseAltitude, currentNode.pos.getCenterZ());
                Vec3d neighborNodeCenter = new Vec3d(neighbor.pos.getCenterX(), cruiseAltitude, neighbor.pos.getCenterZ());

                if (!isTraversable(currentNodeCenter, neighborNodeCenter)) {
                    continue;
                }

                double costMultiplier = costMap.getOrDefault(neighbor.pos, 1.0);

                double newMovementCostToNeighbor = currentNode.gCost + getDistance(currentNode, neighbor) * costMultiplier;

                if (WorldCache.INSTANCE.getBlockStatus(neighbor.pos.getStartPos()) == rise_fly.client.cache.BlockStatus.UNKNOWN) {
                    newMovementCostToNeighbor += 1000;
                }

                PathNode neighborNodeInOpen = openMap.get(neighbor.pos);
                if (neighborNodeInOpen == null || newMovementCostToNeighbor < neighborNodeInOpen.gCost) {
                    if(neighborNodeInOpen != null) {
                        openSet.remove(neighborNodeInOpen);
                    }

                    neighbor.gCost = newMovementCostToNeighbor;
                    neighbor.hCost = getDistance(neighbor, targetNode);
                    neighbor.parent = currentNode;

                    openSet.add(neighbor);
                    openMap.put(neighbor.pos, neighbor);
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
        // 8向寻路
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) continue;
                neighbors.add(new PathNode(new ChunkPos(node.pos.x + x, node.pos.z + z)));
            }
        }
        return neighbors;
    }

    private double getDistance(PathNode nodeA, PathNode nodeB) {
        int dstX = Math.abs(nodeA.pos.x - nodeB.pos.x);
        int dstZ = Math.abs(nodeA.pos.z - nodeB.pos.z);
        // 对角线距离
        if (dstX > dstZ) {
            return 1.4 * dstZ + (dstX - dstZ);
        }
        return 1.4 * dstX + (dstZ - dstX);
    }
}