package com.example.minebot;

import baritone.api.BaritoneAPI;
import baritone.api.command.manager.ICommandManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mod(ModConstants.MODID)
public class MineBotMod {

    // 例: "<Kiner5674> !mine" を検出
    private static final Pattern MINE_PATTERN = Pattern.compile("^\\s*<(?<name>[^>]+)>\\s*!mine\\b.*");

    public MineBotMod() {}

    @Mod.EventBusSubscriber(modid = "minebot", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ClientEvents {

        @SubscribeEvent
        public static void onChat(ClientChatReceivedEvent event) {
            Component msg = event.getMessage();
            String raw = msg.getString();

            Matcher m = MINE_PATTERN.matcher(raw);
            if (!m.matches()) return;

            String who = m.group("name");
            Minecraft mc = Minecraft.getInstance();
            ClientLevel level = mc.level;
            if (level == null) return;

            // 発言者のプレイヤーEntityを探す
            Optional<Player> speakerOpt = level.players().stream()
                    .filter(p -> p.getName().getString().equals(who))
                    .map(p -> (Player) p)
                    .findFirst();

            if (speakerOpt.isEmpty()) return;
            Player speaker = speakerOpt.get();

            double playerY = speaker.getY();
            // 基準Y = 発言者のいまの高さ
            int topY = (int)Math.floor(speaker.getY());

            // 発言者の現在チャンク
            int chunkX = speaker.chunkPosition().x;
            int chunkZ = speaker.chunkPosition().z;

            // チャンク座標→ブロック座標（0..15）
            int x0 = chunkX * 16;
            int z0 = chunkZ * 16;
            int x1 = x0 + 15;
            int z1 = z0 + 15;

            // 地表Y（上面）を代表点で取得（中央柱で算出：より厳密にやるなら全域の最大をとっても可）
            int cx = x0 + 8;
            int cz = z0 + 8;

            // 10層（surfaceY 〜 surfaceY-9）を掘削対象に
            int depth = 10;

            // Baritone コマンドを組み立て・実行
            ICommandManager cmd = BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager();

            int minY = speaker.level().getMinBuildHeight();            // -64（1.20.1の既定）
            int maxY = speaker.level().getMaxBuildHeight() - 1;        // 319

            cmd.execute("sel clear");
            cmd.execute(String.format("sel 1 %d %d %d", x0, topY, z0));
            cmd.execute(String.format("sel 2 %d %d %d", x1, topY, z1));
            cmd.execute(String.format("sel expand a down %d", topY - minY));
            cmd.execute(String.format("sel expand a up %d",   maxY - topY));
            cmd.execute("set buildInLayers true");
            cmd.execute("set layerOrder true");
            cmd.execute("sel ca");


            // 進行メッセージを自分に通知
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal(
                        String.format("[MineBot] %s のチャンク(%d,%d)を地表から%d層掘削開始", who, chunkX, chunkZ, depth)
                ), false);
            }
        }
    }
}
