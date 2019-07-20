package minirpc.remoting.impl.nettyhttp2impl.client;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http2.*;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslContext;
import minirpc.invoker.DefaultRPCInvokerFactory;
import minirpc.utils.RPCException;
import minirpc.utils.serialize.Serializer;

import java.net.InetSocketAddress;

public class NettyHttp2ClientInitializer extends ChannelInitializer<SocketChannel> {

    private static final Http2FrameLogger logger = new Http2FrameLogger(LogLevel.INFO, NettyHttp2ClientInitializer.class);

    private final SslContext sslContext;
    private final int maxContentLength;
    private HttpToHttp2ConnectionHandler connectionHandler;
    private NettyHttp2ClientHandler http2ClientHandler;
    private Http2SettingsHandler settingsHandler;

    private final DefaultRPCInvokerFactory factory;
    private final Serializer serializer;

    NettyHttp2ClientInitializer(SslContext sslContext, int maxContentLength, DefaultRPCInvokerFactory factory, Serializer serializer) {
        this.sslContext = sslContext;
        this.maxContentLength = maxContentLength;
        this.factory = factory;
        this.serializer = serializer;
    }

    @Override
    protected void initChannel(SocketChannel socketChannel) throws Exception {
        final Http2Connection connection = new DefaultHttp2Connection(false);
        connectionHandler = new HttpToHttp2ConnectionHandlerBuilder()  // here http <-> http2
                .frameListener(new DelegatingDecompressorFrameListener(
                        connection,
                        new InboundHttp2ToHttpAdapterBuilder(connection)
                                .maxContentLength(maxContentLength)
                                .propagateSettings(true)
                                .build()))
                .frameLogger(logger)
                .connection(connection)
                .build();
        this.http2ClientHandler = new NettyHttp2ClientHandler(factory,serializer);
        settingsHandler = new Http2SettingsHandler(socketChannel.newPromise());
        if(sslContext != null){
            configureSsl(socketChannel);
        }else {
            configureClearText(socketChannel);
        }
    }

    Http2SettingsHandler settingsHandler(){
        return settingsHandler;
    }

    private void configureEndOfPipeline(ChannelPipeline pipeline) {
        pipeline.addLast(settingsHandler, http2ClientHandler);
    }

    private void configureSsl(SocketChannel ch){
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast(sslContext.newHandler(ch.alloc()));
        pipeline.addLast(new ApplicationProtocolNegotiationHandler("") {
            @Override
            protected void configurePipeline(ChannelHandlerContext ctx, String protocol) throws Exception {
                if(ApplicationProtocolNames.HTTP_2.equals(protocol)){
                    ChannelPipeline p = ctx.pipeline();
                    p.addLast(connectionHandler);
                    configureEndOfPipeline(p);
                    return;
                }
                ctx.close();
                throw new RPCException("Netty Http2Client: unsupported protocol: " + protocol);
            }
        });
    }

    private void configureClearText(SocketChannel ch) {
        HttpClientCodec sourceCodec = new HttpClientCodec();
        Http2ClientUpgradeCodec upgradeCodec = new Http2ClientUpgradeCodec(connectionHandler);
        HttpClientUpgradeHandler upgradeHandler = new HttpClientUpgradeHandler(sourceCodec, upgradeCodec, 65536);

        ch.pipeline().addLast(
                sourceCodec,
                upgradeHandler,
                new UpgradeRequestHandler(),
                new UserEventLogger());
    }

    private final class UpgradeRequestHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            DefaultFullHttpRequest upgradeRequest =
                    new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,"/");

            InetSocketAddress remote = (InetSocketAddress) ctx.channel().remoteAddress();
            String hostString = remote.getHostString();
            if(hostString == null)
                hostString = remote.getAddress().getHostAddress();
            upgradeRequest.headers().set(HttpHeaderNames.HOST, hostString+":"+remote.getPort());

            ctx.writeAndFlush(upgradeRequest);

            ctx.fireChannelActive();

            ctx.pipeline().remove(this);

            configureEndOfPipeline(ctx.pipeline());
        }
    }

    private static class UserEventLogger extends ChannelInboundHandlerAdapter{
        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            System.out.println("User Event Triggered: "+evt);
            ctx.fireUserEventTriggered(evt);
        }
    }

}
