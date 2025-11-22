package com.xiaohashu.second.NIO;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class EventHandler {
    private static final int BUFFER_SIZE = 1024;
    private final KryoProtocolDecoder decoder = new KryoProtocolDecoder();
    private final KryoProtocolEncoder encoder = new KryoProtocolEncoder();
    private final Map<SocketChannel, ClientConnection> connections = new ConcurrentHashMap<>();

    /**
     * 处理连接事件（枚举ACCEPT_EVENT）
     */
    public void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = serverChannel.accept();
        if (clientChannel != null) {
            clientChannel.configureBlocking(false);
            ClientConnection connection = new ClientConnection(clientChannel);
            // 注册读事件（使用枚举的OP_CODE）
            clientChannel.register(key.selector(), NioConstant.READ_EVENT.getOpCode(), connection);
            connections.put(clientChannel, connection);
            System.out.println("新客户端连接: " + clientChannel.getRemoteAddress());
        }
    }

    /**
     * 处理读事件（枚举READ_EVENT）
     */
// EventHandler的handleRead方法
    public void handleRead(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        ClientConnection connection = (ClientConnection) key.attachment();
        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
        buffer.clear(); // 关键修复：清空临时缓冲区，避免残留旧数据

        int bytesRead = channel.read(buffer);
        if (bytesRead == -1) {
            closeConnection(key);
            return;
        }

        if (bytesRead > 0) {
            // 关键：将读取到的有效数据写入连接的接收缓冲区前，先确保buffer是读模式
            buffer.flip();
            connection.appendReceiveBuffer(buffer);
            // 解析消息
            List<BusinessMessage> messages = decoder.decode(connection.getReceiveBuffer());
            for (BusinessMessage message : messages) {
                processMessage(connection, message);
            }
        }
    }

    /**
     * 处理写事件（枚举WRITE_EVENT）
     */
    public void handleWrite(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        ClientConnection connection = (ClientConnection) key.attachment();
        ByteBuffer sendBuffer = connection.getSendBuffer();

        sendBuffer.flip(); // 切换为读模式
        if (sendBuffer.hasRemaining()) {
            channel.write(sendBuffer);
            connection.updateLastActiveTime();
        }
        sendBuffer.compact(); // 压缩缓冲区

        // 发送完毕，取消写事件（避免重复触发）
        if (!sendBuffer.hasRemaining()) {
            key.interestOps(key.interestOps() & ~NioConstant.WRITE_EVENT.getOpCode());
        }
    }

    /**
     * 业务消息处理
     */
    private void processMessage(ClientConnection connection, BusinessMessage message) {
        switch (message.getMessageType()) {
            case 1: // 心跳消息
                System.out.println("收到心跳消息: " + message.getMessageId());
                sendHeartbeatResponse(connection, message);
                break;
            case 2: // 业务消息
                System.out.println("收到业务消息: " + message.getContent());
                break;
            default:
                System.err.println("未知消息类型: " + message.getMessageType());
        }
    }

    /**
     * 发送心跳响应
     */
    private void sendHeartbeatResponse(ClientConnection connection, BusinessMessage request) {
        BusinessMessage response = new BusinessMessage(
                request.getMessageId() + 1,
                "心跳响应",
                System.currentTimeMillis(),
                1
        );
        sendMessage(connection, response);
    }

    /**
     * 异步发送消息
     */
    public void sendMessage(ClientConnection connection, BusinessMessage message) {
        try {
            ByteBuffer encodedData = encoder.encode(message);
            connection.appendSendBuffer(encodedData);
            Selector selector = connection.getChannel().keyFor(null).selector();
            SelectionKey key = connection.getChannel().keyFor(selector);

            if (key != null && key.isValid()) {
                key.interestOps(key.interestOps() | NioConstant.WRITE_EVENT.getOpCode());
                selector.wakeup(); // 唤醒Selector
            }
        } catch (Exception e) {
            System.err.println("发送消息失败: " + e.getMessage());
        }
    }

    /**
     * 关闭连接
     */
    private void closeConnection(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        connections.remove(channel);
        channel.close();
        key.cancel();
        System.out.println("客户端断开连接: " + channel.getRemoteAddress());
    }
}