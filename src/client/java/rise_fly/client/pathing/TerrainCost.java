// 文件路径: rise_fly/client/pathing/TerrainCost.java
package rise_fly.client.pathing;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

public class TerrainCost {

    // 给不同的危险/麻烦方块设定成本
    public static double getCost(BlockState state) {
        if (state.isOf(Blocks.LAVA) || state.isOf(Blocks.FIRE)) {
            return 50.0; // 岩浆和火非常危险，成本极高
        }
        if (state.isIn(BlockTags.LEAVES)) {
            return 10.0; // 树叶成本很高，让AI尽量避开森林
        }
        if (state.isOf(Blocks.WATER)) {
            return 5.0; // 水会减速，成本较高
        }
        if (state.isOf(Blocks.COBWEB)) {
            return 20.0; // 蜘蛛网是飞行杀手
        }
        return 1.0; // 默认成本为1（比如空气）
    }
}