package com.louloulin.apix.graalvm;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * GraalVM Feature to register classes for reflection at build time
 */
public class RuntimeReflectionRegistrationFeature implements Feature {
    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        try {

            // Register VertxHandler for reflection
            Class<?> vertxHandlerClass = access.findClassByName("io.vertx.core.net.impl.VertxHandler");
            if (vertxHandlerClass != null) {
                RuntimeReflection.register(vertxHandlerClass);
                RuntimeReflection.register(vertxHandlerClass.getDeclaredConstructors());
                RuntimeReflection.register(vertxHandlerClass.getDeclaredMethods());

                // Register parent class methods
                Class<?> parentClass = vertxHandlerClass.getSuperclass();
                if (parentClass != null) {
                    RuntimeReflection.register(parentClass);
                    RuntimeReflection.register(parentClass.getDeclaredConstructors());
                    RuntimeReflection.register(parentClass.getDeclaredMethods());
                }

                // Register interfaces
                for (Class<?> iface : vertxHandlerClass.getInterfaces()) {
                    RuntimeReflection.register(iface);
                    RuntimeReflection.register(iface.getDeclaredMethods());
                }

                // Register specific methods that are missing
                Class<?> channelHandlerContextClass = access.findClassByName("io.netty.channel.ChannelHandlerContext");
                Class<?> channelPromiseClass = access.findClassByName("io.netty.channel.ChannelPromise");

                // Register methods from ChannelHandler interface
                Class<?> channelHandlerClass = access.findClassByName("io.netty.channel.ChannelHandler");
                if (channelHandlerClass != null) {
                    RuntimeReflection.register(channelHandlerClass);
                    try {
                        Method userEventTriggeredMethod = channelHandlerClass.getDeclaredMethod(
                                "userEventTriggered", channelHandlerContextClass, Object.class);
                        RuntimeReflection.register(userEventTriggeredMethod);
                    } catch (NoSuchMethodException e) {
                        // Method might be in a different interface or class
                    }

                    try {
                        Method exceptionCaughtMethod = channelHandlerClass.getDeclaredMethod(
                                "exceptionCaught", channelHandlerContextClass, Throwable.class);
                        RuntimeReflection.register(exceptionCaughtMethod);
                    } catch (NoSuchMethodException e) {
                        // Method might be in a different interface or class
                    }

                    try {
                        Method closeMethod = channelHandlerClass.getDeclaredMethod(
                                "close", channelHandlerContextClass, channelPromiseClass);
                        RuntimeReflection.register(closeMethod);
                    } catch (NoSuchMethodException e) {
                        // Method might be in a different interface or class
                    }
                }
            }

            // Register other Netty handler classes
            registerNettyClass(access, "io.netty.channel.ChannelHandler");
            registerNettyClass(access, "io.netty.channel.ChannelHandlerAdapter");
            registerNettyClass(access, "io.netty.channel.ChannelInboundHandlerAdapter");
            registerNettyClass(access, "io.netty.channel.ChannelOutboundHandlerAdapter");
            registerNettyClass(access, "io.netty.channel.ChannelDuplexHandler");
            registerNettyClass(access, "io.netty.handler.timeout.IdleStateHandler");
            registerNettyClass(access, "io.vertx.core.http.impl.Http1xUpgradeToH2CHandler");
            registerNettyClass(access, "io.vertx.core.http.impl.Http1xOrH2CHandler");
            registerNettyClass(access, "io.vertx.core.http.impl.HttpServerWorker");
            registerNettyClass(access, "io.netty.handler.codec.ByteToMessageDecoder");
            registerNettyClass(access, "io.netty.channel.ChannelHandlerContext");
            registerNettyClass(access, "io.netty.channel.ChannelPromise");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void registerNettyClass(BeforeAnalysisAccess access, String className) {
        try {
            Class<?> clazz = access.findClassByName(className);
            if (clazz != null) {
                RuntimeReflection.register(clazz);
                RuntimeReflection.register(clazz.getDeclaredConstructors());
                RuntimeReflection.register(clazz.getDeclaredMethods());
                RuntimeReflection.register(clazz.getDeclaredFields());

                // Register parent class
                Class<?> parentClass = clazz.getSuperclass();
                if (parentClass != null && !parentClass.equals(Object.class)) {
                    RuntimeReflection.register(parentClass);
                    RuntimeReflection.register(parentClass.getDeclaredConstructors());
                    RuntimeReflection.register(parentClass.getDeclaredMethods());
                }

                // Register interfaces
                for (Class<?> iface : clazz.getInterfaces()) {
                    RuntimeReflection.register(iface);
                    RuntimeReflection.register(iface.getDeclaredMethods());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
