package rise_fly.client.cache;

/**
 * 代表AI对空间状态的判断，是整个项目中唯一的环境状态标准。
 */
public enum BlockStatus {
    /**
     * 可安全通行的空间，如空气。
     */
    TRAVERSABLE,

    /**
     * 不可通行的空间，包括固体方块、危险流体、特殊阻碍物等。
     */
    OBSTACLE,

    /**
     * 状态未知的空间，如未加载的区块或世界边界。飞行AI应将其视为最高级别的危险。
     */
    UNKNOWN
}