// 文件路径: rise_fly/client/util/Render.java
package rise_fly.client.util;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import rise_fly.client.flight.FlightControl;

import java.util.List;

public class Render {

    public static void register() {
        WorldRenderEvents.LAST.register(context -> {
            List<Vec3d> path = FlightControl.INSTANCE.getCurrentPath();
            if (path == null || path.isEmpty() || !FlightControl.INSTANCE.isEnabled()) {
                return;
            }

            // 获取渲染所需的各种对象
            MinecraftClient client = MinecraftClient.getInstance();
            Camera camera = context.camera();
            MatrixStack matrixStack = context.matrixStack();
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder bufferBuilder = tessellator.getBuffer();

            // 准备渲染环境
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            // 【修复】禁用深度测试，让线条可以穿透地形显示，方便观察
            RenderSystem.disableDepthTest();
            // 【修复】RenderSystem.lineWidth() 在新版MC中不可靠，已移除。线条将以默认宽度1渲染。
            // RenderSystem.lineWidth(3.0f);

            // 将坐标系原点移动到世界原点，以便我们使用绝对坐标
            matrixStack.push();
            Vec3d cameraPos = camera.getPos();
            matrixStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
            Matrix4f positionMatrix = matrixStack.peek().getPositionMatrix();

            // 开始绘制线段
            bufferBuilder.begin(VertexFormat.DrawMode.LINE_STRIP, VertexFormats.POSITION_COLOR);

            // 遍历路径点并添加到缓冲区
            for (int i = 0; i < path.size(); i++) {
                Vec3d point = path.get(i);
                float progress = (float) i / path.size();
                // 线的颜色从亮绿渐变到天蓝
                bufferBuilder.vertex(positionMatrix, (float) point.x, (float) point.y, (float) point.z)
                        .color(0.5f - progress * 0.5f, 1.0f, 0.5f + progress * 0.5f, 1.0f)
                        .next();
            }

            tessellator.draw();

            // 恢复渲染环境
            matrixStack.pop();
            // 【修复】恢复深度测试
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
        });
    }
}