package com.github.kvr000.zbyneknettyexp.brokenautoread.netty;

import io.netty.channel.ChannelFactory;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DuplexChannel;

import java.net.SocketAddress;


public interface ChannelProvider
{
	EventLoopGroup createEventLoopGroup();

	ChannelFactory<? extends ServerChannel> getServerChannel(SocketAddress address);

	ChannelFactory<? extends DuplexChannel> getStreamChannel(SocketAddress address);

	ChannelFactory<? extends DatagramChannel> getDatagramChannel(SocketAddress address);
}
