package com.xiaohashu.second.NIO;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.pool.KryoPool;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public class KryoSerializer {
    private static volatile KryoSerializer instance;
    private final KryoPool kryoPool;

    private KryoSerializer() {
        // 基于枚举注册Kryo序列化类
        KryoPool.Builder builder = new KryoPool.Builder(() -> {
            Kryo kryo = new Kryo();
            NioConstant.registerKryoClasses(kryo); // 调用枚举的注册方法
            return kryo;
        });
        this.kryoPool = builder.build();
        // 软引用池，平衡性能与内存
    }

    public static KryoSerializer getInstance() {
        if (instance == null) {
            synchronized (KryoSerializer.class) {
                if (instance == null) {
                    instance = new KryoSerializer();
                }
            }
        }
        return instance;
    }

    /**
     * 序列化：对象转字节数组
     */
    public byte[] serialize(Object obj) {
        if (obj == null) {
            throw new IllegalArgumentException("序列化对象不能为空");
        }
        Kryo kryo = kryoPool.borrow();
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             Output output = new Output(baos)) {
            kryo.writeClassAndObject(output, obj);
            output.flush();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Kryo序列化失败", e);
        } finally {
            kryoPool.release(kryo);
        }
    }

    /**
     * 反序列化：字节数组转对象
     */
    @SuppressWarnings("unchecked")
    public <T> T deserialize(byte[] bytes, Class<T> clazz) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("反序列化字节数组不能为空");
        }
        Kryo kryo = kryoPool.borrow();
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
             Input input = new Input(bais)) {
            Object obj = kryo.readClassAndObject(input);
            return (T) obj;
        } catch (Exception e) {
            throw new RuntimeException("Kryo反序列化失败", e);
        } finally {
            kryoPool.release(kryo);
        }
    }
}