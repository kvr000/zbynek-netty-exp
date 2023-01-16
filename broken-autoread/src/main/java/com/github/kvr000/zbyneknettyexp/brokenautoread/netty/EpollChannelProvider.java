package com.github.kvr000.zbyneknettyexp.brokenautoread.netty;

import io.netty.channel.ChannelFactory;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollDomainSocketChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerDomainSocketChannel;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DuplexChannel;
import io.netty.channel.unix.DomainSocketAddress;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;


public class EpollChannelProvider implements ChannelProvider
{
	static {
		EpollEventLoopGroup events = new EpollEventLoopGroup();
		events.shutdownGracefully(0, 0, TimeUnit.SECONDS);
	}

	@Override
	public EventLoopGroup createEventLoopGroup()
	{
		return new EpollEventLoopGroup();
	}

	@Override
	public ChannelFactory<? extends ServerChannel> getServerChannel(SocketAddress address)
	{
		if (address instanceof InetSocketAddress) {
			return () -> new EpollServerSocketChannel(NettyEngine.getNettyProtocolByAddress(((InetSocketAddress)address).getAddress()));
		}
		else if (address instanceof DomainSocketAddress) {
			return EpollServerDomainSocketChannel::new;
		}
		else {
			throw new UnsupportedOperationException("Unsupported socket address: class="+address.getClass());
		}
	}

	@Override
	public ChannelFactory<? extends DuplexChannel> getStreamChannel(SocketAddress address)
	{
		if (address instanceof InetSocketAddress) {
			return () -> new EpollSocketChannel(NettyEngine.getNettyProtocolByAddress(((InetSocketAddress)address).getAddress()));
		}
		else if (address instanceof DomainSocketAddress) {
			return EpollDomainSocketChannel::new;
		}
		else {
			throw new UnsupportedOperationException("Unsupported socket address: class="+address.getClass());
		}
	}

	@Override
	public ChannelFactory<? extends DatagramChannel> getDatagramChannel(SocketAddress socketAddress)
	{
		return EpollDatagramChannel::new;
	}
}
