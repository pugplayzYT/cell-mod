package com.example.intertnet;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Locale;

public class CellTowerDebugRenderer {

    public static void onRenderLevelStage(final RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || !player.isCreative() || !mc.getDebugOverlay().showDebugScreen()) {
            return;
        }

        List<CellTowerBlockEntity> towers = SignalManager.getActiveTowerEntities(player.level());
        if (towers.isEmpty()) {
            return;
        }

        String simNet = DeliveryManager.findPlayerSimNetwork(player);
        Vec3 camPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer lines = bufferSource.getBuffer(RenderType.lines());

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        Vec3 playerTargetPos = player.position().add(0.0, 1.1, 0.0);

        for (CellTowerBlockEntity tower : towers) {
            SignalManager.TowerSignalEval eval = SignalManager.evaluateTower(player.level(), player.getEyePosition(), tower, simNet);
            BlockPos tPos = tower.getBlockPos();
            Vec3 tCenter = Vec3.atCenterOf(tPos);
            boolean powered = eval.powered();
            int maxRange = Math.max(16, eval.maxRange());

            // 1. Highlight the Cell Tower block itself + vertical antenna mast line
            float tr = powered ? 0.1f : 0.95f;
            float tg = powered ? 0.95f : 0.25f;
            float tb = powered ? 0.65f : 0.25f;
            LevelRenderer.renderLineBox(poseStack, lines, new AABB(tPos).inflate(0.03), tr, tg, tb, 1.0f);
            drawSegment(poseStack, lines, tCenter, tCenter.add(0, 16, 0), tr, tg, tb, 0.9f);

            // Draw 3D Wireless Network Link Beams to any peer Cell Towers linked in the chain!
            List<CellTowerBlockEntity> linkedPeers = TowerNetworkManager.getConnectedNetworkTowers(player.level(), tPos);
            for (CellTowerBlockEntity peer : linkedPeers) {
                if (peer != tower && peer.getBlockPos().compareTo(tPos) > 0) {
                    Vec3 peerCenter = Vec3.atCenterOf(peer.getBlockPos());
                    drawSegment(poseStack, lines, tCenter.add(0, 0.8, 0), peerCenter.add(0, 0.8, 0), 0.22f, 0.74f, 0.98f, 1.0f);
                    drawSegment(poseStack, lines, tCenter.add(0, 16.0, 0), peerCenter.add(0, 16.0, 0), 0.85f, 0.35f, 0.98f, 0.95f);
                }
            }

            // 2. Draw 4 Concentric Signal Zone Rings
            if (powered) {
                drawCircleHorizontal(poseStack, lines, tCenter, maxRange * 0.25, 48, 0.06f, 0.73f, 0.51f, 0.85f);
                drawCircleHorizontal(poseStack, lines, tCenter, maxRange * 0.50, 56, 0.52f, 0.80f, 0.09f, 0.85f);
                drawCircleHorizontal(poseStack, lines, tCenter, maxRange * 0.75, 64, 0.92f, 0.70f, 0.03f, 0.85f);
                drawCircleHorizontal(poseStack, lines, tCenter, maxRange * 1.00, 80, 0.98f, 0.45f, 0.09f, 1.00f);
                drawCircleVerticalXY(poseStack, lines, tCenter, maxRange, 64, 0.22f, 0.74f, 0.97f, 0.45f);
                drawCircleVerticalZY(poseStack, lines, tCenter, maxRange, 64, 0.22f, 0.74f, 0.97f, 0.45f);
            } else {
                drawCircleHorizontal(poseStack, lines, tCenter, maxRange, 64, 0.94f, 0.27f, 0.27f, 0.65f);
            }

            // 3. Draw Straight-Line Signal Ray from Tower to Player + Occluding Block Wireframes
            if (eval.rawDistance() <= maxRange + 32) {
                float lr = eval.bars() > 0 ? 0.1f : 0.95f;
                float lg = eval.bars() > 0 ? 0.9f : 0.2f;
                float lb = eval.bars() > 0 ? 0.95f : 0.2f;
                drawSegment(poseStack, lines, tCenter, playerTargetPos, lr, lg, lb, 0.95f);

                for (BlockPos occPos : eval.occludedPositions()) {
                    LevelRenderer.renderLineBox(poseStack, lines, new AABB(occPos).inflate(0.01), 1.0f, 0.2f, 0.2f, 0.9f);
                }
            }
        }

        poseStack.popPose();
        bufferSource.endBatch(RenderType.lines());
    }

    public static void onDebugText(final CustomizeGuiOverlayEvent.DebugText event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || !player.isCreative() || !mc.getDebugOverlay().showDebugScreen()) {
            return;
        }
        String simNet = DeliveryManager.findPlayerSimNetwork(player);
        SignalManager.SignalStatus sig = SignalManager.getBestSignal(player, simNet);
        List<CellTowerBlockEntity> towers = SignalManager.getActiveTowerEntities(player.level());

        event.getLeft().add("");
        event.getLeft().add("§b[Internet Cellular F3 Telemetry]§r");
        event.getLeft().add("Active Cell Towers in Level: §a" + towers.size() + "§r | Inserted SIM: §e" + (simNet != null ? simNet : "NONE") + "§r");
        if (sig.bars() > 0 && sig.connectedTowerPos() != null) {
            event.getLeft().add(String.format(Locale.ROOT,
                    "%s: §a%s§r | Signal: §a%d/4 Bars (%d dBm)§r | Dist: §f%.1fm§r (Eff: §e%.1fm / %dm§r, Occluded: §c%d blks§r)",
                    sig.connectedViaGlobalSatellite() ? "Satellite Relay" : "Connected Tower",
                    sig.connectedTowerPos().toShortString(),
                    sig.bars(),
                    sig.getSignalDbm(),
                    sig.rawDistance(),
                    sig.effectiveDistance(),
                    sig.maxRange(),
                    sig.occludingBlocks()
            ));
        } else {
            event.getLeft().add("Cellular Status: §cNO SIGNAL§r"
                    + (simNet == null ? " §e(No SIM Card Inserted)§r" : (sig.missingOrWrongSim() ? " §c(SIM Network Mismatch)§r" : ""))
                    + (sig.satelliteSkyBlockedNearby() ? " §6(Satellite Sky Blocked)§r" : "")
                    + (sig.unpoweredTowerNearby() ? " §6(Nearby Tower Out of Fuel)§r" : ""));
        }
    }

    private static void drawCircleHorizontal(PoseStack poseStack, VertexConsumer consumer, Vec3 center, double radius, int segments, float r, float g, float b, float a) {
        for (int i = 0; i < segments; i++) {
            double a1 = (2.0 * Math.PI * i) / segments;
            double a2 = (2.0 * Math.PI * (i + 1)) / segments;
            Vec3 p1 = new Vec3(center.x + Math.cos(a1) * radius, center.y + 0.1, center.z + Math.sin(a1) * radius);
            Vec3 p2 = new Vec3(center.x + Math.cos(a2) * radius, center.y + 0.1, center.z + Math.sin(a2) * radius);
            drawSegment(poseStack, consumer, p1, p2, r, g, b, a);
        }
    }

    private static void drawCircleVerticalXY(PoseStack poseStack, VertexConsumer consumer, Vec3 center, double radius, int segments, float r, float g, float b, float a) {
        for (int i = 0; i < segments; i++) {
            double a1 = (2.0 * Math.PI * i) / segments;
            double a2 = (2.0 * Math.PI * (i + 1)) / segments;
            Vec3 p1 = new Vec3(center.x + Math.cos(a1) * radius, center.y + Math.sin(a1) * radius, center.z);
            Vec3 p2 = new Vec3(center.x + Math.cos(a2) * radius, center.y + Math.sin(a2) * radius, center.z);
            drawSegment(poseStack, consumer, p1, p2, r, g, b, a);
        }
    }

    private static void drawCircleVerticalZY(PoseStack poseStack, VertexConsumer consumer, Vec3 center, double radius, int segments, float r, float g, float b, float a) {
        for (int i = 0; i < segments; i++) {
            double a1 = (2.0 * Math.PI * i) / segments;
            double a2 = (2.0 * Math.PI * (i + 1)) / segments;
            Vec3 p1 = new Vec3(center.x, center.y + Math.sin(a1) * radius, center.z + Math.cos(a1) * radius);
            Vec3 p2 = new Vec3(center.x, center.y + Math.sin(a2) * radius, center.z + Math.cos(a2) * radius);
            drawSegment(poseStack, consumer, p1, p2, r, g, b, a);
        }
    }

    private static void drawSegment(PoseStack poseStack, VertexConsumer consumer, Vec3 from, Vec3 to, float r, float g, float b, float a) {
        PoseStack.Pose pose = poseStack.last();
        Matrix4f mat = pose.pose();
        Vec3 dir = to.subtract(from).normalize();
        float nx = (float) dir.x;
        float ny = (float) dir.y;
        float nz = (float) dir.z;
        consumer.addVertex(mat, (float) from.x, (float) from.y, (float) from.z).setColor(r, g, b, a).setNormal(pose, nx, ny, nz);
        consumer.addVertex(mat, (float) to.x, (float) to.y, (float) to.z).setColor(r, g, b, a).setNormal(pose, nx, ny, nz);
    }
}
