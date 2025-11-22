package com.xiaohashu.second.NIO;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class ClientConnection {
    private final SocketChannel channel;
    private final ByteBuffer receiveBuffer; // 接收缓冲区
    private final ByteBuffer sendBuffer;    // 发送缓冲区
    private long lastActiveTime;            // 最后活跃时间

    public ClientConnection(SocketChannel channel) {
        this.channel = channel;
        this.receiveBuffer = ByteBuffer.allocate(8192); // 8KB
        this.sendBuffer = ByteBuffer.allocate(8192);
        this.lastActiveTime = System.currentTimeMillis();
    }

    // 追加接收数据
    public void appendReceiveBuffer(ByteBuffer buffer) {
        receiveBuffer.put(buffer);
    }

    // 追加发送数据
    public void appendSendBuffer(ByteBuffer buffer) {
        sendBuffer.put(buffer);
    }

    // 更新活跃时间
    public void updateLastActiveTime() {
        this.lastActiveTime = System.currentTimeMillis();
    }

    // Getter & Setter
    public SocketChannel getChannel() { return channel; }
    public ByteBuffer getReceiveBuffer() { return receiveBuffer; }
    public ByteBuffer getSendBuffer() { return sendBuffer; }
    public long getLastActiveTime() { return lastActiveTime; }
}