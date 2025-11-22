package com.xiaohashu.second.NIO;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NioSocketServer {
    private static final int DEFAULT_PORT = 9000;
    private static final int BUFFER_SIZE = 1024;
    private Selector selector;
    private ServerSocketChannel serverChannel;
    private volatile boolean running = false;

    private static final EventHandler eventHandler = new EventHandler();
    private final Map<SocketChannel, ClientConnection> connections = new ConcurrentHashMap<>();

    public void start() throws IOException {
        start(DEFAULT_PORT);
    }

    public void start(int port) throws IOException {
        // 初始化Selector和服务端通道
        selector = Selector.open();
        serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.bind(new InetSocketAddress(port));

        // 注册ACCEPT事件（使用枚举的OP_CODE）
        serverChannel.register(selector, NioConstant.ACCEPT_EVENT.getOpCode());
        running = true;
        System.out.println("NIO服务器启动，监听端口: " + port);

        eventLoop();
    }

    private void eventLoop() {
        while (running) {
            try {
                // 阻塞等待事件（超时1秒）
                int readyChannels = selector.select(1000);
                if (readyChannels == 0) {
                    handleTimeout(); // 超时处理（心跳检测）
                    continue;
                }

                // 处理就绪事件
                Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    keyIterator.remove(); // 避免重复处理

                    if (!key.isValid()) {
                        continue;
                    }

                    // 基于枚举分发事件
                    if (key.isAcceptable()) {
                        eventHandler.handleAccept(key);
                    } else if (key.isReadable()) {
                        eventHandler.handleRead(key);
                    } else if (key.isWritable()) {
                        eventHandler.handleWrite(key);
                    }
                }
            } catch (IOException e) {
                System.err.println("事件循环异常: " + e.getMessage());
                break;
            }
        }
    }

    /**
     * 超时处理：清理超时连接
     */
    private void handleTimeout() {
        long currentTime = System.currentTimeMillis();
        connections.entrySet().removeIf(entry -> {
            ClientConnection conn = entry.getValue();
            if (currentTime - conn.getLastActiveTime() > 300000) { // 5分钟超时
                try {
                    entry.getKey().close();
                    System.out.println("清理超时连接: " + entry.getKey().getRemoteAddress());
                    return true;
                } catch (IOException e) {
                    e.printStackTrace();
                    return true;
                }
            }
            return false;
        });
    }

    /**
     * 停止服务器
     */
    public void stop() {
        running = false;
        if (selector != null) {
            selector.wakeup();
            try {
                serverChannel.close();
                selector.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) throws IOException {
        NioSocketServer server = new NioSocketServer();
        server.start();

        // 优雅关闭
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
    }
}