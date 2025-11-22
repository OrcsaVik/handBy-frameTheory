package com.xiaohashu.second.NIO;

import lombok.Data;

@Data
public class BusinessMessage {
    private long messageId;      // 消息唯一ID
    private String content;      // 消息内容
    private long sendTime;       // 发送时间戳
    private int messageType;     // 消息类型（如心跳、业务请求）

    // 无参构造函数（Kryo默认需要，可通过配置跳过）
    public BusinessMessage() {}

    // 全参构造函数、getter/setter
    public BusinessMessage(long messageId, String content, long sendTime, int messageType) {
        this.messageId = messageId;
        this.content = content;
        this.sendTime = sendTime;
        this.messageType = messageType;
    }

}