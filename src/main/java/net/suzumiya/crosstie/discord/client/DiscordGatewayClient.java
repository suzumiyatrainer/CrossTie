package net.suzumiya.crosstie.discord.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.ssl.SslHandler;
import net.suzumiya.crosstie.CrossTie;
import net.suzumiya.crosstie.discord.DiscordConfig;
import net.suzumiya.crosstie.discord.DiscordMessageQueue;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import java.net.URI;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Netty 4.0 を使用した、Discord Gateway v10 (WebSocket) 双方向チャット受信用クライアント。
 * 外部ライブラリを追加せず、Minecraft 組み込みの Netty と Gson のみで動作。
 */
public final class DiscordGatewayClient {

    private static final String GATEWAY_HOST = "gateway.discord.gg";
    private static final int GATEWAY_PORT = 443;
    private static final String GATEWAY_URI_STR = "wss://gateway.discord.gg/?v=10&encoding=json";

    private static final Gson GSON = new Gson();
    @SuppressWarnings("deprecation")
    private static final JsonParser PARSER = new JsonParser();

    private EventLoopGroup group;
    private Channel channel;
    private volatile boolean running = true;

    private String sessionId = "";
    private String resumeGatewayUrl = null;
    private Integer lastSequence = null;
    private ScheduledFuture<?> heartbeatTask;
    @SuppressWarnings("unused")
    private long lastHeartbeatAck = 0;

    public void start() {
        this.running = true;
        this.group = new NioEventLoopGroup(1, r -> {
            Thread t = new Thread(r, "CrossTie-DiscordGateway");
            t.setDaemon(true);
            return t;
        });

        connectAsync(0);
    }

    public void stop() {
        this.running = false;
        if (heartbeatTask != null) {
            heartbeatTask.cancel(true);
        }
        if (channel != null && channel.isOpen()) {
            channel.close();
        }
        if (group != null) {
            group.shutdownGracefully();
        }
    }

    private void connectAsync(int delaySeconds) {
        if (!running)
            return;

        group.schedule(() -> {
            if (!running)
                return;
            try {
                String targetUriStr = GATEWAY_URI_STR;
                String targetHost = GATEWAY_HOST;
                int targetPort = GATEWAY_PORT;

                if (!sessionId.isEmpty() && resumeGatewayUrl != null && !resumeGatewayUrl.isEmpty()) {
                    try {
                        URI resumeUri = new URI(resumeGatewayUrl);
                        targetHost = resumeUri.getHost();
                        targetPort = resumeUri.getPort() == -1 ? 443 : resumeUri.getPort();
                        String path = resumeUri.getPath();
                        if (path == null || path.isEmpty() || path.equals("/")) {
                            path = "/";
                        }
                        targetUriStr = "wss://" + targetHost + (targetPort == 443 ? "" : ":" + targetPort) + path + "?v=10&encoding=json";
                    } catch (Throwable e) {
                        CrossTie.LOGGER.warn("[Discord] Failed to parse resume_gateway_url: {}, falling back to default.", resumeGatewayUrl);
                        targetUriStr = GATEWAY_URI_STR;
                        targetHost = GATEWAY_HOST;
                        targetPort = GATEWAY_PORT;
                    }
                }

                URI uri = new URI(targetUriStr);
                SSLContext sslContext = SSLContext.getDefault();
                SSLEngine sslEngine = sslContext.createSSLEngine(targetHost, targetPort);
                sslEngine.setUseClientMode(true);
                // Netty 4.0.10 の SslHandler は TLS 1.3 の状態遷移に対応していないため TLS 1.2 を明示
                sslEngine.setEnabledProtocols(new String[]{"TLSv1.2"});

                // Cloudflare / SNI (Server Name Indication) を有効化
                SSLParameters sslParams = sslEngine.getSSLParameters();
                sslParams.setEndpointIdentificationAlgorithm("HTTPS");
                sslEngine.setSSLParameters(sslParams);

                SslHandler sslHandler = new SslHandler(sslEngine);
                sslHandler.setHandshakeTimeout(30, TimeUnit.SECONDS);

                HttpHeaders headers = new DefaultHttpHeaders();
                headers.add(HttpHeaders.Names.USER_AGENT, "CrossTie-Discord/1.0");

                WebSocketClientHandshaker handshaker = WebSocketClientHandshakerFactory.newHandshaker(uri,
                        WebSocketVersion.V13, null, false, headers, 1280000);

                Bootstrap b = new Bootstrap();
                final String finalHost = targetHost;
                final int finalPort = targetPort;
                b.group(group).channel(NioSocketChannel.class).handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast("ssl", sslHandler);
                        p.addLast("http-codec", new HttpClientCodec());
                        p.addLast("aggregator", new HttpObjectAggregator(1048576));
                        p.addLast("ws-handler", new GatewayWebSocketHandler(handshaker, sslHandler));
                    }
                });

                CrossTie.LOGGER.info("[Discord] Connecting to Discord Gateway ({})...", targetHost);
                b.connect(finalHost, finalPort).addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        channel = future.channel();
                    } else {
                        CrossTie.LOGGER.warn("[Discord] Failed to connect to Gateway: {}. Retrying in 10s...",
                                future.cause().getMessage());
                        connectAsync(10);
                    }
                });
            } catch (Throwable t) {
                CrossTie.LOGGER.warn("[Discord] Gateway setup error: {}. Retrying in 15s...", t.getMessage());
                connectAsync(15);
            }
        }, delaySeconds, TimeUnit.SECONDS);
    }

    private final class GatewayWebSocketHandler extends SimpleChannelInboundHandler<Object> {

        private final WebSocketClientHandshaker handshaker;
        private final SslHandler sslHandler;

        GatewayWebSocketHandler(WebSocketClientHandshaker handshaker, SslHandler sslHandler) {
            this.handshaker = handshaker;
            this.sslHandler = sslHandler;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            // Netty 4.0 では channelActive が SSL ハンドシェイク完了前に発火するため、
            // handshakeFuture の完了を待ってから WebSocket ハンドシェイクを開始する
            sslHandler.handshakeFuture().addListener(future -> {
                if (future.isSuccess()) {
                    handshaker.handshake(ctx.channel());
                } else {
                    CrossTie.LOGGER.warn("[Discord] SSL Handshake failed: {}", future.cause().getMessage());
                    ctx.close();
                }
            });
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            if (heartbeatTask != null) {
                heartbeatTask.cancel(true);
            }
            if (running) {
                CrossTie.LOGGER.info("[Discord] Gateway connection lost. Reconnecting in 5s...");
                connectAsync(5);
            }
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
            if (!handshaker.isHandshakeComplete()) {
                if (msg instanceof FullHttpResponse) {
                    handshaker.finishHandshake(ctx.channel(), (FullHttpResponse) msg);
                    CrossTie.LOGGER.info("[Discord] Gateway handshake completed.");
                    return;
                }
            }

            if (msg instanceof TextWebSocketFrame) {
                String text = ((TextWebSocketFrame) msg).text();
                handleGatewayMessage(ctx.channel(), text);
            } else if (msg instanceof CloseWebSocketFrame) {
                ctx.channel().close();
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            CrossTie.LOGGER.warn("[Discord] Gateway exception: {}", cause.getMessage());
            ctx.close();
        }
    }

    private void handleGatewayMessage(Channel ch, String text) {
        try {
            @SuppressWarnings("deprecation")
            JsonObject json = PARSER.parse(text).getAsJsonObject();
            int op = json.get("op").getAsInt();

            if (json.has("s") && !json.get("s").isJsonNull()) {
                lastSequence = json.get("s").getAsInt();
            }

            switch (op) {
            case 10: // Hello
                handleHello(ch, json.getAsJsonObject("d"));
                break;
            case 11: // Heartbeat ACK
                lastHeartbeatAck = System.currentTimeMillis();
                break;
            case 1: // Heartbeat Request
                sendHeartbeat(ch);
                break;
            case 7: // Reconnect
                CrossTie.LOGGER.info("[Discord] Gateway requested reconnect.");
                ch.close();
                break;
            case 9: // Invalid Session
                boolean resumable = json.get("d").getAsBoolean();
                CrossTie.LOGGER.warn("[Discord] Invalid session (resumable={}).", resumable);
                if (!resumable) {
                    sessionId = "";
                    resumeGatewayUrl = null;
                    lastSequence = null;
                }
                ch.close();
                break;
            case 0: // Dispatch
                handleDispatch(json);
                break;
            }
        } catch (Throwable t) {
            CrossTie.LOGGER.warn("[Discord] Error parsing Gateway payload: {}", t.getMessage());
        }
    }

    private void handleHello(Channel ch, JsonObject data) {
        int interval = data.get("heartbeat_interval").getAsInt();
        lastHeartbeatAck = System.currentTimeMillis();

        if (heartbeatTask != null) {
            heartbeatTask.cancel(true);
        }

        // Heartbeat 定期送信スケジュール
        heartbeatTask = group.scheduleAtFixedRate(() -> sendHeartbeat(ch), interval, interval, TimeUnit.MILLISECONDS);

        // Resume または Identify
        if (!sessionId.isEmpty() && lastSequence != null) {
            sendResume(ch);
        } else {
            sendIdentify(ch);
        }
    }

    private void sendHeartbeat(Channel ch) {
        if (ch != null && ch.isOpen()) {
            JsonObject hb = new JsonObject();
            hb.addProperty("op", 1);
            if (lastSequence != null) {
                hb.addProperty("d", lastSequence);
            } else {
                hb.add("d", null);
            }
            ch.writeAndFlush(new TextWebSocketFrame(GSON.toJson(hb)));
        }
    }

    private void sendIdentify(Channel ch) {
        JsonObject payload = new JsonObject();
        payload.addProperty("op", 2);

        JsonObject d = new JsonObject();
        d.addProperty("token", DiscordConfig.resolvedBotToken);
        d.addProperty("intents", 33280); // GUILD_MESSAGES (512) | MESSAGE_CONTENT (32768)

        JsonObject props = new JsonObject();
        props.addProperty("os", "minecraft");
        props.addProperty("browser", "CrossTie");
        props.addProperty("device", "CrossTie");
        d.add("properties", props);

        payload.add("d", d);
        ch.writeAndFlush(new TextWebSocketFrame(GSON.toJson(payload)));
        CrossTie.LOGGER.info("[Discord] Sent Gateway Identify.");
    }

    private void sendResume(Channel ch) {
        JsonObject payload = new JsonObject();
        payload.addProperty("op", 6);

        JsonObject d = new JsonObject();
        d.addProperty("token", DiscordConfig.resolvedBotToken);
        d.addProperty("session_id", sessionId);
        d.addProperty("seq", lastSequence);

        payload.add("d", d);
        ch.writeAndFlush(new TextWebSocketFrame(GSON.toJson(payload)));
        CrossTie.LOGGER.info("[Discord] Sent Gateway Resume.");
    }

    private void handleDispatch(JsonObject json) {
        String eventType = json.get("t").getAsString();
        JsonObject d = json.getAsJsonObject("d");

        if ("READY".equals(eventType)) {
            sessionId = d.get("session_id").getAsString();
            if (d.has("resume_gateway_url") && !d.get("resume_gateway_url").isJsonNull()) {
                resumeGatewayUrl = d.get("resume_gateway_url").getAsString();
            }
            CrossTie.LOGGER.info("[Discord] Gateway READY. Session ID: {}", sessionId);
        } else if ("RESUMED".equals(eventType)) {
            CrossTie.LOGGER.info("[Discord] Gateway session successfully RESUMED.");
        } else if ("MESSAGE_CREATE".equals(eventType)) {
            handleMessageCreate(d);
        }
    }

    private void handleMessageCreate(JsonObject d) {
        // ギルドIDが設定されている場合はギルドIDの一致も検証
        if (DiscordConfig.guildId != null && !DiscordConfig.guildId.isEmpty()) {
            if (!d.has("guild_id") || !DiscordConfig.guildId.equals(d.get("guild_id").getAsString())) {
                return;
            }
        }

        String channelId = d.get("channel_id").getAsString();
        if (!channelId.equals(DiscordConfig.channelId)) {
            return;
        }

        // Bot 自身、または Webhook 発言を除外 (無限エコーバック防止)
        if (d.has("webhook_id") && !d.get("webhook_id").isJsonNull()) {
            return;
        }
        if (d.has("author")) {
            JsonObject author = d.getAsJsonObject("author");
            if (author.has("bot") && author.get("bot").getAsBoolean()) {
                return;
            }

            String authorName = author.get("username").getAsString();
            // サーバーニックネームがある場合は優先
            if (d.has("member") && !d.get("member").isJsonNull()) {
                JsonObject member = d.getAsJsonObject("member");
                if (member.has("nick") && !member.get("nick").isJsonNull()) {
                    authorName = member.get("nick").getAsString();
                }
            }

            String content = d.has("content") ? d.get("content").getAsString() : "";
            if (content.isEmpty() && d.has("attachments") && d.getAsJsonArray("attachments").size() > 0) {
                content = "[添付ファイル]";
            }

            if (!content.isEmpty()) {
                String formatted = DiscordConfig.formatDiscordToMc.replace("%name%", authorName).replace("%message%",
                        content);
                DiscordMessageQueue.enqueueInbound(formatted);
            }
        }
    }
}
