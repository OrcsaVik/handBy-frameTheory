package com.xiaohashu.second.NIO;

import com.esotericsoftware.kryo.Kryo;

import java.nio.channels.SelectionKey;

public enum NioConstant {
    // NIO事件类型枚举
    ACCEPT_EVENT(SelectionKey.OP_ACCEPT, "客户端连接事件"),
    READ_EVENT(SelectionKey.OP_READ, "客户端读事件"),
    WRITE_EVENT(SelectionKey.OP_WRITE, "客户端写事件"),

    // Kryo序列化类注册（类标识ID + 对应Class）
    BUSINESS_MESSAGE(1, BusinessMessage.class, "业务消息类");

    // NIO事件相关属性
    private final int opCode;
    private final String eventDesc;

    // Kryo序列化相关属性
    private final int registerId;
    private final Class<?> registerClass;

    // NIO事件枚举构造器
    NioConstant(int opCode, String eventDesc) {
        this.opCode = opCode;
        this.eventDesc = eventDesc;
        this.registerId = -1;
        this.registerClass = null;
    }

    // Kryo序列化枚举构造器
    NioConstant(int registerId, Class<?> registerClass, String eventDesc) {
        this.registerId = registerId;
        this.registerClass = registerClass;
        this.opCode = -1;
        this.eventDesc = eventDesc;
    }

    /**
     * 注册Kryo序列化类（统一注册入口）
     */
    public static void registerKryoClasses(Kryo kryo) {
        for (NioConstant constant : NioConstant.values()) {
            if (constant.registerClass != null) {
                kryo.register(constant.registerClass, constant.registerId);
            }
        }
        kryo.setRegistrationRequired(true); // 强制注册，禁止动态类注册
        kryo.setReferences(true); // 支持循环引用
    }

    // Getter方法
    public int getOpCode() {
        return opCode;
    }

    public String getEventDesc() {
        return eventDesc;
    }

    public int getRegisterId() {
        return registerId;
    }

    public Class<?> getRegisterClass() {
        return registerClass;
    }
}