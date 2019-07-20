package minirpc.remoting;

import minirpc.remoting.impl.nettyhttp2impl.client.NettyHttp2Client;
import minirpc.remoting.impl.nettyhttp2impl.server.NettyHttp2Server;
import minirpc.remoting.impl.nettyhttpimpl.client.NettyHttpClient;
import minirpc.remoting.impl.nettyhttpimpl.server.NettyHttpServer;
import minirpc.remoting.impl.nettyimpl.client.NettyClient;
import minirpc.remoting.impl.nettyimpl.server.NettyServer;

public enum RemotingType {
    NETTY_HTTP2(NettyHttp2Client.class, NettyHttp2Server.class),
    NETTY_HTTP(NettyHttpClient.class, NettyHttpServer.class),
    NETTY(NettyClient.class, NettyServer.class);

    private Class<? extends Client> client;
    private Class<? extends Server> server;

    RemotingType(Class<? extends Client> client, Class<? extends Server> server){
        this.client = client;
        this.server = server;
    }

    public Class<? extends Client> getClient(){
        return client;
    }

    public Class<? extends Server> getServer(){
        return server;
    }

}
