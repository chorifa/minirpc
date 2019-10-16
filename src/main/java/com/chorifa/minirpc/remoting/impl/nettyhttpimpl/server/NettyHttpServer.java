package com.chorifa.minirpc.remoting.impl.nettyhttpimpl.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.timeout.IdleStateHandler;
import com.chorifa.minirpc.provider.DefaultRPCProviderFactory;
import com.chorifa.minirpc.remoting.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class NettyHttpServer extends Server {

    private static Logger logger = LoggerFactory.getLogger(NettyHttpServer.class);

    private Thread thread;

    @Override
    public void start(DefaultRPCProviderFactory providerFactory) {
        thread = new Thread(() -> {

            final ExecutorService executorService = Executors.newFixedThreadPool(100,
                    (r) -> new Thread(r, "rpc-Netty Http Server-execPool: " + r.hashCode()));

            EventLoopGroup bossGroup = new NioEventLoopGroup(1);
            EventLoopGroup workGroup = new NioEventLoopGroup();
            try {
                ServerBootstrap sbs = new ServerBootstrap();
                sbs.group(bossGroup, workGroup).channel(NioServerSocketChannel.class)
                        .option(ChannelOption.SO_BACKLOG, 1024)
                        .childOption(ChannelOption.SO_KEEPALIVE, true)
                        .childHandler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel socketChannel) throws Exception {
                                socketChannel.pipeline()
                                        .addLast(new IdleStateHandler(0, 0, 10, TimeUnit.MINUTES))
                                        .addLast(new HttpServerCodec())
                                        .addLast(new HttpObjectAggregator(1024 * 1024))
                                        .addLast(new NettyHttpServerHandler(providerFactory, executorService));
                            }
                        });
                ChannelFuture channelFuture = sbs.bind(providerFactory.getPort()).sync();
                logger.info("NettyHttpServer bind port:[{}] succeed",providerFactory.getPort());
                beforeStart(); // start call back

                channelFuture.channel().closeFuture().sync();
            }catch (Exception e){
                if(e instanceof InterruptedException)
                    logger.info("NettyHttpServer interrupted >>> shut down...");
                else
                    logger.error("NettyHttpServer encounter error...");
            }finally {
                executorService.shutdown(); // close thread pool
                bossGroup.shutdownGracefully();
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
