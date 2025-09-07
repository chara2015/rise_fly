// 文件路径: rise_fly/client/pathing/Pathfinder.java
package rise_fly.client.pathing;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import rise_fly.client.cache.BlockStatus;
import rise_fly.client.cache.WorldCache;
import rise_fly.client.util.DebugUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Pathfinder {
    public static final Pathfinder INSTANCE = new Pathfinder();
    private final Map<ChunkPos, Double> costMap = new ConcurrentHashMap<>();

    private static final double COST_PENALTY_UNSCANNED_Y = 200.0;
    private static final double COST_PENALTY_UNKNOWN_CHUNK = 10000.0;

    private Pathfinder() {}

    public void reportBadChunk(ChunkPos pos) {
        costMap.put(pos, costMap.getOrDefault(pos, 1.0) * 2);
    }

    public void clearCosts() {
        costMap.clear();
    }

    // ... [findPath 方法和之前一样，包含了渐进式爬升策略] ...
    public List<Vec3d> findPath(Vec3d startVec, Vec3d targetVec) {
        DebugUtils.log("开始路径计算...");
        ChunkPos startPos = new ChunkPos(BlockPos.ofFloored(startVec));
        ChunkPos targetPos = new ChunkPos(BlockPos.ofFloored(targetVec));
        int initialAltitude = (int)startVec.y;

        DebugUtils.log("§e[策略1] 尝试当前高度 Y=" + initialAltitude);
        List<ChunkPos> chunkPath = findChunkPath(startPos, targetPos, initialAltitude);
        if (chunkPath != null && !chunkPath.isEmpty()) {
            DebugUtils.log("§a[策略1] 成功找到路径！");
            return smoothPath(startVec, targetVec, chunkPath, initialAltitude);
        }

        DebugUtils.log("§e[策略1.5] 尝试渐进式爬升...");
        for (int climb = 20; climb <= 60; climb += 20) {
            int newAltitude = initialAltitude + climb;
            if (newAltitude >= 319) break;
            DebugUtils.log("§e[策略1.5] ...正在尝试高度 Y=" + newAltitude);
            chunkPath = findChunkPath(startPos, targetPos, newAltitude);
            if (chunkPath != null && !chunkPath.isEmpty()) {
                DebugUtils.log("§a[策略1.5] 在 Y=" + newAltitude + " 找到渐进爬升路径！");
                return smoothPath(startVec, targetVec, chunkPath, newAltitude);
            }
        }

        DebugUtils.log("§e[策略2] 尝试左右绕行...");
        double horizontalOffsetDistance = 16 * 5;
        Vec3d direction = targetVec.subtract(startVec).normalize();
        Vec3d perpendicularDirection = new Vec3d(-direction.z, 0, direction.x).normalize();

        Vec3d leftOffsetTarget = targetVec.add(perpendicularDirection.multiply(horizontalOffsetDistance));
        List<ChunkPos> leftPath = findChunkPath(startPos, new ChunkPos(BlockPos.ofFloored(leftOffsetTarget)), initialAltitude);
        if (leftPath != null && !leftPath.isEmpty()) {
            DebugUtils.log("§a[策略2] 成功找到左绕行路径！");
            List<Vec3d> smoothedPath = smoothPath(startVec, leftOffsetTarget, leftPath, initialAltitude);
            smoothedPath.add(targetVec);
            return smoothedPath;
        }

        Vec3d rightOffsetTarget = targetVec.subtract(perpendicularDirection.multiply(horizontalOffsetDistance));
        List<ChunkPos> rightPath = findChunkPath(startPos, new ChunkPos(BlockPos.ofFloored(rightOffsetTarget)), initialAltitude);
        if (rightPath != null && !rightPath.isEmpty()) {
            DebugUtils.log("§a[策略2] 成功找到右绕行路径！");
            List<Vec3d> smoothedPath = smoothPath(startVec, rightOffsetTarget, rightPath, initialAltitude);
            smoothedPath.add(targetVec);
            return smoothedPath;
        }

        DebugUtils.log("§e[策略3] 尝试高空飞行...");
        List<Integer> fallbackAltitudes = Arrays.asList(200, 120, 80);
        for (int altitude : fallbackAltitudes) {
            DebugUtils.log("§e[策略3] ...正在尝试备选高度 Y=" + altitude);
            chunkPath = findChunkPath(startPos, targetPos, altitude);
            if (chunkPath != null && !chunkPath.isEmpty()) {
                DebugUtils.log("§a[策略3] 成功在备选高度 Y=" + altitude + " 找到路径！");
                return smoothPath(startVec, targetVec, chunkPath, altitude);
            }
        }

        DebugUtils.log("§c在所有策略下均未找到可用路径。");
        return null;
    }

    public boolean isTraversable(Vec3d from, Vec3d to) {
        double distance = from.distanceTo(to);
        // 【改进】增加采样密度，将步长从1.5格缩短到0.8格，使路径检查更精细
        int samples = (int) Math.ceil(distance / 0.8);
        if (samples == 0) return true;

        Vec3d direction = to.subtract(from).normalize();
        Vec3d up = new Vec3d(0, 1, 0);
        Vec3d right = direction.crossProduct(up).normalize();

        if (right.lengthSquared() < 0.1) {
            right = new Vec3d(1, 0, 0);
        }
        up = right.crossProduct(direction).normalize();

        Vec3d[] offsets = {
                up, up.negate(), right, right.negate(),
                up.add(right), up.add(right.negate()),
                up.negate().add(right), up.negate().add(right.negate())
        };

        for (int i = 0; i <= samples; i++) {
            Vec3d centerPoint = from.lerp(to, (double)i / samples);
            if (isVolumeOccupied(centerPoint)) return false;
            for (Vec3d offset : offsets) {
                Vec3d checkPoint = centerPoint.add(offset.multiply(1.2));
                if (WorldCache.INSTANCE.getBlockStatus(BlockPos.ofFloored(checkPoint)) == BlockStatus.SOLID) {
                    return false;
                }
            }
        }
        return true;
    }

    // ... [其他方法保持不变] ...
    public List<Vec3d> findPathXMode(Vec3d startVec, Vec3d targetVec) { DebugUtils.log("开始 X 模式路径计算..."); ChunkPos startPos = new ChunkPos(BlockPos.ofFloored(startVec)); ChunkPos targetPos = new ChunkPos(BlockPos.ofFloored(targetVec)); List<Integer> xModeAltitudes = Arrays.asList(320, 256, 200, 120); for (int altitude : xModeAltitudes) { DebugUtils.log("§eX 模式: 尝试高空 Y=" + altitude); List<ChunkPos> chunkPath = findChunkPath(startPos, targetPos, altitude); if (chunkPath != null && !chunkPath.isEmpty()) { DebugUtils.log("§aX 模式成功在 Y=" + altitude + " 找到路径！"); return smoothPath(startVec, targetVec, chunkPath, altitude); } } DebugUtils.log("§cX 模式在所有策略下均未找到可用路径。"); return null; }
    private List<Vec3d> smoothPath(Vec3d startPoint, Vec3d endPoint, List<ChunkPos> path, int cruiseAltitude) { List<Vec3d> waypoints = new ArrayList<>(); waypoints.add(startPoint); if (path.isEmpty()) { waypoints.add(endPoint); return waypoints; } int currentPathIndex = 0; while(currentPathIndex < path.size()) { Vec3d lastWaypoint = waypoints.get(waypoints.size() - 1); int nextNodeIndex = path.size() - 1; while(nextNodeIndex > currentPathIndex) { ChunkPos checkChunk = path.get(nextNodeIndex); Vec3d checkPoint = new Vec3d(checkChunk.getCenterX(), cruiseAltitude, checkChunk.getCenterZ()); if (isTraversable(lastWaypoint, checkPoint)) { waypoints.add(checkPoint); currentPathIndex = nextNodeIndex; break; } nextNodeIndex--; } if (nextNodeIndex <= currentPathIndex) { ChunkPos nextChunk = path.get(currentPathIndex); waypoints.add(new Vec3d(nextChunk.getCenterX(), cruiseAltitude, nextChunk.getCenterZ())); currentPathIndex++; } } waypoints.add(endPoint); return waypoints; }
    private boolean isVolumeOccupied(Vec3d center) { BlockPos centerPos = BlockPos.ofFloored(center); return WorldCache.INSTANCE.getBlockStatus(centerPos.down(1)) == BlockStatus.SOLID || WorldCache.INSTANCE.getBlockStatus(centerPos) == BlockStatus.SOLID || WorldCache.INSTANCE.getBlockStatus(centerPos.up(1)) == BlockStatus.SOLID; }
    private List<ChunkPos> findChunkPath(ChunkPos startPos, ChunkPos targetPos, int cruiseAltitude) { PathNode startNode = new PathNode(startPos); PathNode targetNode = new PathNode(targetPos); PriorityQueue<PathNode> openSet = new PriorityQueue<>(); HashSet<ChunkPos> closedSet = new HashSet<>(); HashMap<ChunkPos, PathNode> openMap = new HashMap<>(); startNode.gCost = 0; startNode.hCost = getDistance(startNode, targetNode); openSet.add(startNode); openMap.put(startNode.pos, startNode); while (!openSet.isEmpty()) { PathNode currentNode = openSet.poll(); openMap.remove(currentNode.pos); if (currentNode.equals(targetNode)) { return retracePath(startNode, currentNode); } closedSet.add(currentNode.pos); for (PathNode neighbor : getNeighbors(currentNode)) { if (closedSet.contains(neighbor.pos)) continue; Vec3d currentNodeCenter = new Vec3d(currentNode.pos.getCenterX(), cruiseAltitude, currentNode.pos.getCenterZ()); Vec3d neighborNodeCenter = new Vec3d(neighbor.pos.getCenterX(), cruiseAltitude, neighbor.pos.getCenterZ()); if (!isTraversable(currentNodeCenter, neighborNodeCenter)) continue; double costMultiplier = costMap.getOrDefault(neighbor.pos, 1.0); double newMovementCostToNeighbor = currentNode.gCost + getDistance(currentNode, neighbor) * costMultiplier; BlockPos neighborBlockPos = BlockPos.ofFloored(neighborNodeCenter); BlockStatus status = WorldCache.INSTANCE.getBlockStatus(neighborBlockPos); switch (status) { case UNSCANNED_Y: newMovementCostToNeighbor += COST_PENALTY_UNSCANNED_Y; break; case UNKNOWN_CHUNK: newMovementCostToNeighbor += COST_PENALTY_UNKNOWN_CHUNK; break; case SOLID: continue; case AIR: break; } PathNode neighborNodeInOpen = openMap.get(neighbor.pos); if (neighborNodeInOpen == null || newMovementCostToNeighbor < neighborNodeInOpen.gCost) { if(neighborNodeInOpen != null) openSet.remove(neighborNodeInOpen); neighbor.gCost = newMovementCostToNeighbor; neighbor.hCost = getDistance(neighbor, targetNode); neighbor.parent = currentNode; openSet.add(neighbor); openMap.put(neighbor.pos, neighbor); } } } return null; }
    private List<ChunkPos> retracePath(PathNode startNode, PathNode endNode) { List<ChunkPos> path = new ArrayList<>(); PathNode currentNode = endNode; while (currentNode != null && !currentNode.equals(startNode)) { path.add(currentNode.pos); currentNode = currentNode.parent; } Collections.reverse(path); return path; }
    private List<PathNode> getNeighbors(PathNode node) { List<PathNode> neighbors = new ArrayList<>(); for (int x = -1; x <= 1; x++) { for (int z = -1; z <= 1; z++) { if (x == 0 && z == 0) continue; neighbors.add(new PathNode(new ChunkPos(node.pos.x + x, node.pos.z + z))); } } return neighbors; }
    private double getDistance(PathNode nodeA, PathNode nodeB) { int dstX = Math.abs(nodeA.pos.x - nodeB.pos.x); int dstZ = Math.abs(nodeA.pos.z - nodeB.pos.z); if (dstX > dstZ) { return 14 * dstZ + 10 * (dstX - dstZ); } return 14 * dstX + 10 * (dstZ - dstX); }

}