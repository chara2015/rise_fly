package rise_fly.client.pathing;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import rise_fly.client.cache.BlockStatus;
import rise_fly.client.cache.WorldCache;
import rise_fly.client.util.DebugUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Pathfinder {
    public static final Pathfinder INSTANCE = new Pathfinder();
    private final Map<ChunkPos, Double> costMap = new ConcurrentHashMap<>();

    // 寻路策略的常量
    private static final int DEFAULT_CRUISE_ALTITUDE = 100;
    private static final int LONG_DISTANCE_THRESHOLD = 500; // 优化：长距离寻路的阈值，单位为区块
    private static final List<Integer> FALLBACK_ALTITUDES = Arrays.asList(200, 80, 120, 256, 320);
    private static final List<Integer> X_MODE_ALTITUDES = Arrays.asList(320, 256, 200, 120);
    private static final int DETOUR_CHUNK_DISTANCE = 5;

    private Pathfinder() {}

    public void reportBadChunk(ChunkPos pos) {
        costMap.compute(pos, (k, v) -> v == null ? 2.0 : v * 2);
    }

    public void clearCosts() {
        costMap.clear();
    }

    public List<Vec3d> findPath(Vec3d startVec, Vec3d targetVec, FlightMode mode) {
        DebugUtils.log("开始路径计算，模式: " + mode);
        ChunkPos startPos = new ChunkPos(BlockPos.ofFloored(startVec));
        ChunkPos targetPos = new ChunkPos(BlockPos.ofFloored(targetVec));

        if (startPos.getChebyshevDistance(targetPos) > LONG_DISTANCE_THRESHOLD && mode == FlightMode.NORMAL) {
            DebugUtils.log("§e检测到超远距离飞行，自动切换到 X_MODE 高空巡航。");
            mode = FlightMode.X_MODE;
        }

        List<Function<Void, List<ChunkPos>>> strategies = new ArrayList<>();

        if (mode == FlightMode.NORMAL) {
            strategies.add(v -> {
                int initialAltitude = (int) startVec.y;
                DebugUtils.log("§e策略1: 尝试当前高度 Y=" + initialAltitude);
                try {
                    return findChunkPath(startPos, targetPos, initialAltitude);
                } catch (InterruptedException e) {
                    return null;
                }
            });
            strategies.add(v -> {
                DebugUtils.log("§e策略2: 尝试左右绕行...");
                try {
                    List<ChunkPos> leftPath = findDetourPath(startPos, targetPos, startVec, targetVec, -1);
                    if (leftPath != null) return leftPath;

                    List<ChunkPos> rightPath = findDetourPath(startPos, targetPos, startVec, targetVec, 1);
                    if (rightPath != null) return rightPath;
                } catch (InterruptedException e) {
                    return null;
                }
                return null;
            });
            strategies.add(v -> {
                DebugUtils.log("§e策略3: 尝试备选高度...");
                for (int altitude : FALLBACK_ALTITUDES) {
                    try {
                        DebugUtils.log("正在尝试备选高度 Y=" + altitude + " 寻找路径...");
                        List<ChunkPos> chunkPath = findChunkPath(startPos, targetPos, altitude);
                        if (chunkPath != null && !chunkPath.isEmpty()) {
                            DebugUtils.log("§a成功在备选高度 Y=" + altitude + " 找到路径！");
                            return chunkPath;
                        }
                    } catch (InterruptedException e) {
                        return null;
                    }
                }
                return null;
            });

        } else if (mode == FlightMode.X_MODE) {
            strategies.add(v -> {
                DebugUtils.log("§eX 模式策略: 尝试高空...");
                for (int altitude : X_MODE_ALTITUDES) {
                    try {
                        DebugUtils.log("正在尝试高空 Y=" + altitude + " 寻找路径...");
                        List<ChunkPos> chunkPath = findChunkPath(startPos, targetPos, altitude);
                        if (chunkPath != null && !chunkPath.isEmpty()) {
                            DebugUtils.log("§aX 模式成功在 Y=" + altitude + " 找到路径！");
                            return chunkPath;
                        }
                    } catch (InterruptedException e) {
                        return null;
                    }
                }
                return null;
            });
        }

        List<ChunkPos> chunkPath = null;
        for (Function<Void, List<ChunkPos>> strategy : strategies) {
            if (Thread.currentThread().isInterrupted()) {
                return null;
            }
            chunkPath = strategy.apply(null);
            if (chunkPath != null && !chunkPath.isEmpty()) {
                List<Vec3d> smoothedPath = smoothPath(startVec, targetVec, chunkPath);
                if (Thread.currentThread().isInterrupted()) {
                    return null;
                }
                return smoothedPath;
            }
        }

        DebugUtils.log("§c在所有策略下均未找到可用路径。");
        return null;
    }

    private List<ChunkPos> findDetourPath(ChunkPos startPos, ChunkPos targetPos, Vec3d startVec, Vec3d targetVec, int side) throws InterruptedException {
        Vec3d direction = targetVec.subtract(startVec).normalize();
        Vec3d perpendicularDirection = new Vec3d(-direction.z, 0, direction.x).normalize();
        Vec3d offsetTarget = targetVec.add(perpendicularDirection.multiply(16.0 * DETOUR_CHUNK_DISTANCE * side));

        List<ChunkPos> path = findChunkPath(startPos, new ChunkPos(BlockPos.ofFloored(offsetTarget)), (int) startVec.y);
        if (path != null && !path.isEmpty()) {
            DebugUtils.log("§a成功找到" + (side == -1 ? "左" : "右") + "绕行路径！");
            List<Vec3d> smoothedPath = smoothPath(startVec, offsetTarget, path);
            smoothedPath.add(targetVec);
            return smoothedPath.stream().map(v -> new ChunkPos(BlockPos.ofFloored(v))).collect(Collectors.toList());
        }
        return null;
    }

    private List<Vec3d> smoothPath(Vec3d startPoint, Vec3d endPoint, List<ChunkPos> path) {
        if (path == null || path.isEmpty()) {
            return Collections.singletonList(endPoint);
        }

        List<Vec3d> waypoints = new ArrayList<>();
        waypoints.add(startPoint);

        Vec3d currentStartPoint = startPoint;
        int currentPathIndex = 0;
        int cruiseAltitude = (int) startPoint.y;

        while (currentPathIndex < path.size()) {
            if (Thread.currentThread().isInterrupted()) return null;

            int nextNodeIndex = path.size() - 1;
            while(nextNodeIndex >= currentPathIndex) {
                ChunkPos checkChunk = path.get(nextNodeIndex);
                Vec3d checkPoint = new Vec3d(checkChunk.getCenterX(), cruiseAltitude, checkChunk.getCenterZ());
                if (isTraversable(currentStartPoint, checkPoint)) {
                    waypoints.add(checkPoint);
                    currentStartPoint = checkPoint;
                    currentPathIndex = nextNodeIndex + 1;
                    break;
                }
                nextNodeIndex--;
            }
            if (nextNodeIndex < currentPathIndex) {
                ChunkPos nextChunk = path.get(currentPathIndex);
                waypoints.add(new Vec3d(nextChunk.getCenterX(), cruiseAltitude, nextChunk.getCenterZ()));
                currentStartPoint = waypoints.get(waypoints.size() - 1);
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

        double distance = from.distanceTo(to);
        if (distance == 0) return true;

        int samples = (int) Math.ceil(distance);
        for (int i = 0; i <= samples; i++) {
            if (Thread.currentThread().isInterrupted()) return false;
            Vec3d samplePoint = from.lerp(to, (double) i / samples);
            BlockStatus status = checkBlockStatus(samplePoint);
            if (status == BlockStatus.SOLID) {
                return false;
            } else if (status == BlockStatus.UNKNOWN) {
                return false;
            }
        }
        return true;
    }

    private List<ChunkPos> findChunkPath(ChunkPos startPos, ChunkPos targetPos, int cruiseAltitude) throws InterruptedException {
        PathNode startNode = new PathNode(startPos);
        PathNode targetNode = new PathNode(targetPos);
        PriorityQueue<PathNode> openSet = new PriorityQueue<>();
        Map<ChunkPos, PathNode> openSetMap = new HashMap<>();
        HashSet<ChunkPos> closedSet = new HashSet<>();

        openSet.add(startNode);
        openSetMap.put(startPos, startNode);
        startNode.gCost = 0;
        startNode.hCost = getDistance(startNode, targetNode);

        while (!openSet.isEmpty()) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Pathfinding thread interrupted.");
            }

            PathNode currentNode = openSet.poll();
            openSetMap.remove(currentNode.pos);

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

                if (newMovementCostToNeighbor < neighbor.gCost || !openSetMap.containsKey(neighbor.pos)) {
                    neighbor.gCost = newMovementCostToNeighbor;
                    neighbor.hCost = getDistance(neighbor, targetNode);
                    neighbor.parent = currentNode;

                    if (!openSetMap.containsKey(neighbor.pos)) {
                        openSet.add(neighbor);
                        openSetMap.put(neighbor.pos, neighbor);
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