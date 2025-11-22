package com.xiaohashu.second.NIO;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Scanner;
import java.util.UUID;

public class NioClient {
    private SocketChannel socketChannel;
    private ByteBuffer readBuffer = ByteBuffer.allocate(8192);
    private ByteBuffer writeBuffer = ByteBuffer.allocate(8192);
    private final KryoSerializer kryoSerializer = KryoSerializer.getInstance();
    private final String clientId; // 客户端唯一标识，区分三个客户端
    public static final Scanner scanner = new Scanner(System.in);
    public NioClient(String clientId) {
        this.clientId = clientId;
    }

    /**
     * 连接服务端
     */
    public void connect(String host, int port) throws Exception {
        socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(false); // 非阻塞模式
        // 连接服务端
        boolean connected = socketChannel.connect(new InetSocketAddress(host, port));
        if (!connected) {
            // 非阻塞连接需要等待finishConnect
            while (!socketChannel.finishConnect()) {
                System.out.println(clientId + "：正在连接服务端...");
                Thread.sleep(100);
            }
        }
        System.out.println(clientId + "：成功连接服务端，地址：" + socketChannel.getRemoteAddress());
    }

    /**
     * 发送消息（心跳/业务）
     */
    public void sendMessage(BusinessMessage message) throws Exception {
        // 1. Kryo序列化消息体，拼接服务端协议头（8字节：4总长度+4消息类型）
        byte[] bodyBytes = kryoSerializer.serialize(message);
        int totalLength = 8 + bodyBytes.length;

        // 2. 写入协议头和消息体到缓冲区
        writeBuffer.clear();
        writeBuffer.putInt(totalLength); // 总长度
        writeBuffer.putInt(message.getMessageType()); // 消息类型
        writeBuffer.put(bodyBytes); // Kryo序列化后的消息体
        writeBuffer.flip();

        // 3. 非阻塞写入通道
        while (writeBuffer.hasRemaining()) {
            socketChannel.write(writeBuffer);
        }
        System.out.println(clientId + "：发送消息成功，ID：" + message.getMessageId() + "，内容：" + message.getContent());
    }

    /**
     * 接收服务端响应（轮询读取）
     */
    public void receiveResponse() throws Exception {
        readBuffer.clear();
        int bytesRead = socketChannel.read(readBuffer);
        if (bytesRead > 0) {
            readBuffer.flip();
            // 解析服务端协议：先读8字节头，再读消息体
            while (readBuffer.remaining() >= 8) {
                int totalLength = readBuffer.getInt();
                int messageType = readBuffer.getInt();
                int bodyLength = totalLength - 8;
                byte[] bodyBytes = new byte[bodyLength];
                readBuffer.get(bodyBytes);

                // Kryo反序列化响应消息
                BusinessMessage response = kryoSerializer.deserialize(bodyBytes, BusinessMessage.class);
                System.out.println(clientId + "：收到服务端响应，ID：" + response.getMessageId() + "，内容：" + response.getContent());
            }
        } else if (bytesRead == -1) {
            // 服务端关闭连接
            System.out.println(clientId + "：服务端断开连接");
            close();
        }
    }
    private long messageId = 1L;

    public void interactiveSend() {
        while (true) {
            try {
                // 打印交互式菜单
                System.out.println("\n===== " + clientId + " 消息发送菜单 =====");
                System.out.println("1. 发送心跳消息");
                System.out.println("2. 发送业务消息");
                System.out.println("3. 退出客户端");
                System.out.print("请选择操作（1/2/3）：");
                String choice = scanner.nextLine().trim();


                switch (choice) {
                    case "1":
                        // 发送心跳消息（使用全局messageId，避免局部变量覆盖）
                        BusinessMessage heartbeatMsg = new BusinessMessage(
                                messageId, // 直接使用全局自增ID
                                clientId + " 心跳请求（手动发送）",
                                System.currentTimeMillis(),
                                1 // 心跳消息类型
                        );
                        sendMessage(heartbeatMsg);
                        Thread.sleep(500); // 短暂等待后接收响应
                        receiveResponse();
                        messageId++; // 心跳消息发送后，ID自增（关键修复）
                        break;
                    case "2":
                        // 发送业务消息（手动输入内容）
                        System.out.print("请输入业务消息内容：");
                        String businessContent = scanner.nextLine();
                        // 防御性判断：避免null和空字符串（修复潜在空指针）
                        if (businessContent == null || businessContent.trim().isEmpty()) {
                            businessContent = clientId + " 业务数据：" + UUID.randomUUID().toString().substring(0, 8);
                        } else {
                            businessContent = businessContent.trim();
                        }
                        // 直接使用全局messageId，无需定义messageId2（删除冗余变量）
                        BusinessMessage businessMsg = new BusinessMessage(
                                messageId,
                                businessContent,
                                System.currentTimeMillis(),
                                2 // 业务消息类型
                        );
                        sendMessage(businessMsg);
                        Thread.sleep(500);
                        receiveResponse();
                        messageId++; // 业务消息发送后，ID自增
                        break;
                    case "3":
                        // 退出客户端：优雅关闭资源（补充socketChannel判空）
                        System.out.println(clientId + "：准备退出，关闭连接...");
                        close();
                        if (scanner != null) {
                            scanner.close();
                        }
                        return;
                    default:
                        System.out.println("无效的选择，请输入1/2/3！");
                        break; // 补充break，规范switch语法
                }
            } catch (Exception e) {
                System.err.println(clientId + "：消息发送异常：" + e.getMessage());
            }
        }
    }

    public void close() throws Exception {
        if (socketChannel != null) {
            socketChannel.close();
        }
        System.out.println(clientId + "：客户端连接已关闭");
    }

    // 测试入口
    public static void main(String[] args) throws Exception {
        // 客户端唯一标识（启动三个实例时分别改为Client-1、Client-2、Client-3）
        String clientId = "Client-2";
        NioClient client = new NioClient(clientId);

        // 1. 连接服务端
        client.connect("127.0.0.1", 9000);

        // 2. 启动交互式消息发送
        client.interactiveSend();
    }
}