package minirpc.remoting.impl.nettyhttp2impl.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.*;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import minirpc.provider.DefaultRPCProviderFactory;
import minirpc.remoting.Server;
import minirpc.utils.RuntimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NettyHttp2Server extends Server {

    private static Logger logger = LoggerFactory.getLogger(NettyHttp2Server.class);

    private Thread thread;

    private static final boolean useSSL = OpenSsl.isAlpnSupported() | RuntimeUtil.JDK_VERSION >= 9;

    @Override
    public void start(DefaultRPCProviderFactory providerFactory) {
        thread = new Thread(() -> {
            final ExecutorService executorService = Executors.newFixedThreadPool(100,
                    (r) -> new Thread(r, "rpc-Netty Http2 Server-execPool: " + r.hashCode()));

            final SslContext sslCtx;
            EventLoopGroup bossGroup = null;
            EventLoopGroup workGroup = null;

            try {
                if (useSSL) {
                    // JDK9 and above support ALPN
                    SslProvider provider = OpenSsl.isAlpnSupported()? SslProvider.OPENSSL : SslProvider.JDK;

                    // self certificate : not support for web browser
                    SelfSignedCertificate ssc = new SelfSignedCertificate();
                    sslCtx = SslContextBuilder.forServer(ssc.certificate(),ssc.privateKey())
                            .sslProvider(provider)
                            .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                            .applicationProtocolConfig(new ApplicationProtocolConfig(
                                    ApplicationProtocolConfig.Protocol.ALPN,
                                    ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                                    ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                                    ApplicationProtocolNames.HTTP_2,
                                    ApplicationProtocolNames.HTTP_1_1))
                            .build();
                }else sslCtx = null;

                bossGroup = new NioEventLoopGroup();
                workGroup = new NioEventLoopGroup();

                ServerBootstrap sbs = new ServerBootstrap();
                sbs.option(ChannelOption.SO_BACKLOG,1024);
                sbs.group(bossGroup, workGroup).channel(NioServerSocketChannel.class)
                        .handler(new LoggingHandler(LogLevel.INFO))
                        .childHandler(new Http2ServerInitializer(sslCtx, providerFactory, executorService));

                int port = providerFactory.getPort();
                Channel ch = sbs.bind(port).sync().channel();

                System.out.println("Open your HTTP/2-enabled web browser and navigate to " +
                        (useSSL? "https" : "http") + "://127.0.0.1:" + port + '/');

                ch.closeFuture().sync();

            }catch (Exception e){
                if(e instanceof InterruptedException)
                    System.out.println("interrupted >>> shut down...");
                else
                    System.err.println("other exception!!!");
            }finally {
                if(bossGroup != null)
                    bossGroup.shutdownGracefully();
                if(workGroup != null)
                    workGroup.shutdownGracefully();
            }

        });

        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void stop() {

        if(thread != null && thread.isAlive())
            thread.interrupt();

        afterStop();

        logger.info("NettyServer stopped...");

    }

}
