package com.xiaohashu.second.NIO;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class KryoProtocolDecoder {
    private static final int HEADER_LENGTH = 8; // 4字节总长度 + 4字节消息类型
    private static final int MAX_MESSAGE_LENGTH = 1024 * 1024; // 最大1MB
    private final KryoSerializer kryoSerializer = KryoSerializer.getInstance();

    public List<BusinessMessage> decode(ByteBuffer buffer) {
        List<BusinessMessage> messages = new ArrayList<>();
        // 关键修复1：先将缓冲区切换为读模式，确保读取的是有效数据
        buffer.flip();

        // 循环解析前，先校验缓冲区剩余字节是否至少包含完整的协议头
        while (buffer.remaining() >= HEADER_LENGTH) {
            buffer.mark(); // 标记当前位置，解析失败时回滚
            int totalLength = buffer.getInt(); // 读取总长度

            // 关键修复2：增加总长度的合法性校验，排除0或负数
            if (totalLength <= 0 || totalLength < HEADER_LENGTH || totalLength > MAX_MESSAGE_LENGTH) {
                buffer.reset(); // 回滚标记位
                buffer.position(buffer.position() + HEADER_LENGTH); // 跳过无效数据，避免死循环
                System.err.println("无效的消息长度: " + totalLength + "，跳过无效数据");
                continue;
            }

            // 校验缓冲区剩余字节是否足够解析完整消息
            if (buffer.remaining() < (totalLength - 4)) { // 已读取4字节总长度，剩余需解析：totalLength -4
                buffer.reset(); // 消息不完整，回滚标记位
                break;
            }

            // 解析协议头和消息体
            int messageType = buffer.getInt(); // 读取4字节消息类型
            int bodyLength = totalLength - HEADER_LENGTH;
            byte[] bodyBytes = new byte[bodyLength];
            buffer.get(bodyBytes); // 读取消息体

            // Kryo反序列化
            try {
                BusinessMessage message = kryoSerializer.deserialize(bodyBytes, BusinessMessage.class);
                message.setMessageType(messageType);
                messages.add(message);
            } catch (Exception e) {
                System.err.println("Kryo反序列化失败: " + e.getMessage());
            }
        }

        // 关键修复3：压缩缓冲区时，先判断是否有未解析的剩余数据
        buffer.compact(); // 保留未解析的数据，压缩到缓冲区头部
        return messages;
    }
}

/**
 * Kryo协议编码器：长度前缀+Kryo序列化
 */
class KryoProtocolEncoder {
    private static final int HEADER_LENGTH = 8;
    private final KryoSerializer kryoSerializer = KryoSerializer.getInstance();

    public ByteBuffer encode(BusinessMessage message) {
        // Kryo序列化消息体
        byte[] bodyBytes = kryoSerializer.serialize(message);
        int totalLength = HEADER_LENGTH + bodyBytes.length;

        // 拼接协议头
        ByteBuffer buffer = ByteBuffer.allocate(totalLength);
        buffer.putInt(totalLength); // 总长度
        buffer.putInt(message.getMessageType()); // 消息类型
        buffer.put(bodyBytes); // 消息体
        buffer.flip(); // 切换为读模式

        return buffer;
    }
}