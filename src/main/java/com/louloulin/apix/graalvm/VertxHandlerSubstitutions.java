package com.louloulin.apix.graalvm;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.Feature.BeforeAnalysisAccess;

/**
 * Substitutions for Vert.x Handler to fix missing methods in native image
 */
@TargetClass(className = "io.vertx.core.net.impl.VertxHandler")
final class Target_io_vertx_core_net_impl_VertxHandler {

    @Substitute
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        ctx.fireUserEventTriggered(evt);
    }

    @Substitute
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        ctx.close(promise);
    }

    @Substitute
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.fireExceptionCaught(cause);
    }
}

/**
 * Substitutions for HttpServerWorker$1 inner class
 */
@TargetClass(className = "io.vertx.core.http.impl.HttpServerWorker$1")
final class Target_io_vertx_core_http_impl_HttpServerWorker$1 {

    @Substitute
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        ctx.fireUserEventTriggered(evt);
    }
}

public class VertxHandlerSubstitutions implements Feature {
    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        // This is a hook for GraalVM to recognize this as a feature
    }
}
