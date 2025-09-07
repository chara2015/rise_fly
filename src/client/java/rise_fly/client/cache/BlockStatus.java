package rise_fly.client.cache;

public enum BlockStatus {
    /**
     * 已确认是固体方块
     */
    SOLID,
    /**
     * 已确认是可通过的空气类方块
     */
    AIR,
    /**
     * 区块尚未加载，完全未知
     */
    UNKNOWN_CHUNK,
    /**
     * 区块已加载，但此Y坐标尚未被扫描
     */
    UNSCANNED_Y
}