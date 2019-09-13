package com.chorifa.minirpc.remoting;

import com.chorifa.minirpc.remoting.impl.nettyhttp2impl.client.NettyHttp2Client;
import com.chorifa.minirpc.remoting.impl.nettyhttp2impl.server.NettyHttp2Server;
import com.chorifa.minirpc.remoting.impl.nettyhttpimpl.client.NettyHttpClient;
import com.chorifa.minirpc.remoting.impl.nettyhttpimpl.server.NettyHttpServer;
import com.chorifa.minirpc.remoting.impl.nettyimpl.client.NettyClient;
import com.chorifa.minirpc.remoting.impl.nettyimpl.server.NettyServer;

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
