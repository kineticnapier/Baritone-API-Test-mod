package com.example.minebot;

import baritone.api.BaritoneAPI;
import baritone.api.command.manager.ICommandManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
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

    @Mod.EventBusSubscriber(modid = ModConstants.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
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
                    .findFirst();

            if (speakerOpt.isEmpty()) return;
            Player speaker = speakerOpt.get();

            // 発言者の現在チャンク
            int chunkX = speaker.chunkPosition().x;
            int chunkZ = speaker.chunkPosition().z;

            // チャンク座標→ブロック座標（0..15）
            int x0 = chunkX * 16;
            int z0 = chunkZ * 16;
            int x1 = x0 + 15;
            int z1 = z0 + 15;

            // チャンク中央の高さを取得
            int cx = x0 + 8;
            int cz = z0 + 8;
            int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, cx, cz);

            // 10層（surfaceY 〜 surfaceY-9）を掘削対象に
            int depth = 10;
            int bottomY = Math.max(surfaceY - depth + 1, level.getMinBuildHeight());

            // Baritone コマンドを組み立て・実行
            ICommandManager cmd = BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager();

            cmd.execute("sel clear");
            cmd.execute(String.format("sel 1 %d %d %d", x0, surfaceY, z0));
            cmd.execute(String.format("sel 2 %d %d %d", x1, bottomY, z1));
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
