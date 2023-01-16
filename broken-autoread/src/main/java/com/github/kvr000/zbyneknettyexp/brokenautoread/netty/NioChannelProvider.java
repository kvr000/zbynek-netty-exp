package com.github.kvr000.zbyneknettyexp.brokenautoread.netty;

import io.netty.channel.ChannelFactory;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DuplexChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProtocolFamily;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;


public class NioChannelProvider implements ChannelProvider
{
	@Override
	public EventLoopGroup createEventLoopGroup()
	{
		return new NioEventLoopGroup();
	}

	@Override
	public ChannelFactory<? extends ServerChannel> getServerChannel(SocketAddress address)
	{
		if (address instanceof InetSocketAddress) {
			return () -> new NioServerSocketChannel(SelectorProvider.provider(),
				NettyEngine.getNettyProtocolByAddress(((InetSocketAddress)address).getAddress()));
		}
		else if (address instanceof UnixDomainSocketAddress) {
			return () -> new NioServerSocketChannel(new DelegatedSelectorProvider(SelectorProvider.provider())
			{
				@Override
				public ServerSocketChannel openServerSocketChannel() throws IOException
				{
					ProtocolFamily protocol = StandardProtocolFamily.UNIX;
					return super.openServerSocketChannel(protocol);
				}
			});
		}
		else {
			throw new UnsupportedOperationException("Unsupported socket address: class="+address.getClass());
		}
	}

	@Override
	public ChannelFactory<? extends DuplexChannel> getStreamChannel(SocketAddress address)
	{

		if (address instanceof InetSocketAddress) {
			return () -> new NioSocketChannel(new DelegatedSelectorProvider(SelectorProvider.provider())
			{
				@Override
				public SocketChannel openSocketChannel() throws IOException
				{
					ProtocolFamily protocol =
						NettyEngine.getProtocolByAddress(((InetSocketAddress)address).getAddress());
					return super.openSocketChannel(protocol);
				}
			});
		}
		else if (address instanceof UnixDomainSocketAddress) {
			return () -> new NioSocketChannel(new DelegatedSelectorProvider(SelectorProvider.provider())
			{
				@Override
				public SocketChannel openSocketChannel() throws IOException
				{
					ProtocolFamily protocol = StandardProtocolFamily.UNIX;
					return super.openSocketChannel(protocol);
				}
			});
		}
		else {
			throw new UnsupportedOperationException("Unsupported socket address: class="+address.getClass());
		}
	}

	@Override
	public ChannelFactory<? extends DatagramChannel> getDatagramChannel(SocketAddress address)
	{
		if (address instanceof InetSocketAddress) {
			return () -> new NioDatagramChannel(new DelegatedSelectorProvider(SelectorProvider.provider())
			{
				@Override
				public java.nio.channels.DatagramChannel openDatagramChannel() throws IOException
				{
					ProtocolFamily protocol =
						NettyEngine.getProtocolByAddress(((InetSocketAddress)address).getAddress());
					return super.openDatagramChannel(protocol);
				}
			});
		}
		else {
			return NioDatagramChannel::new;
		}
	}

	@RequiredArgsConstructor
	public static class DelegatedSelectorProvider extends SelectorProvider
	{
		@Delegate
		private final SelectorProvider delegate;
	}
}
