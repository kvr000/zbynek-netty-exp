package com.github.kvr000.zbyneknettyexp.brokenautoread.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.socket.DuplexChannel;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.resolver.InetNameResolver;
import io.netty.resolver.dns.DnsNameResolverBuilder;
import io.netty.util.concurrent.Future;
import org.apache.commons.lang3.SystemUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ProtocolFamily;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;


public class NettyEngine
{
	private final ChannelProvider channelProvider;

	private EventLoopGroup bossGroup ;
	private EventLoopGroup workerGroup;

	private final InetNameResolver inetNameResolver;

	public NettyEngine(ChannelProvider channelProvider)
	{
		this.channelProvider = channelProvider;
		bossGroup = channelProvider.createEventLoopGroup();
		workerGroup = channelProvider.createEventLoopGroup();
		inetNameResolver = new DnsNameResolverBuilder()
			.eventLoop(workerGroup.next())
			.channelFactory(channelProvider.getDatagramChannel(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0)))
			.build();
	}

	public CompletableFuture<SocketAddress> resolve(SocketAddress address)
	{
		if (address instanceof InetSocketAddress address1 && address1.isUnresolved()) {
			if (address1.getHostString().equals("*")) {
				return CompletableFuture.completedFuture(new InetSocketAddress(address1.getPort()));
			}
			Future<InetAddress> future =
				inetNameResolver.resolve(((InetSocketAddress)address).getHostString());
			return new CompletableFuture<SocketAddress>() {
				{
					future.addListener((f) -> {
						try {
							complete(new InetSocketAddress((InetAddress) f.get(), ((InetSocketAddress)address).getPort()));
						}
						catch (Throwable ex) {
							completeExceptionally(ex);
						}
					});
				}
			};
		}
		else {
			return CompletableFuture.completedFuture(address);
		}
	}

	public CompletableFuture<ServerChannel> listen(SocketAddress listen, ChannelInitializer<DuplexChannel> channelInitializer)
	{
		try {
			return new CompletableFuture<ServerChannel>() {
				ChannelFuture bindFuture;

				private synchronized void stepBind(SocketAddress address)
				{
					ServerBootstrap b = new ServerBootstrap();
					b.group(bossGroup, workerGroup)
						.channelFactory(channelProvider.getServerChannel(address))
						.option(ChannelOption.SO_BACKLOG, Integer.MAX_VALUE)
						.childHandler(channelInitializer)
						.childOption(ChannelOption.AUTO_READ, false)
						.childOption(ChannelOption.ALLOW_HALF_CLOSURE, true);
					if (!SystemUtils.IS_OS_MAC_OSX) {
						b.childOption(ChannelOption.SO_KEEPALIVE, true);
					}

					bindFuture = b.bind(address);

					bindFuture.addListener((f) -> {
						try {
							try {
								f.get();
							}
							catch (ExecutionException ex) {
								throw ex.getCause();
							}
							if (!complete((ServerChannel) bindFuture.channel())) {
								bindFuture.channel().close();
							}
						}
						catch (IOException ex) {
							completeExceptionally(new UncheckedIOException("Failed to bind to: "+address+" : "+ex.getMessage(), ex));
						}
						catch (Throwable ex) {
							completeExceptionally(new IOException("Failed to bind to: "+address, ex));
						}
					});
				}

				{
					resolve(listen)
						.thenAccept(this::stepBind)
						.whenComplete((v, ex) -> {
							if (ex != null) {
								completeExceptionally(ex);
							}
						});
				}

				@Override
				public synchronized boolean cancel(boolean interrupt)
				{
					return bindFuture.cancel(interrupt);
				}
			};
		}
		catch (Throwable ex) {
			return CompletableFuture.failedFuture(ex);
		}
	}

	public CompletableFuture<Channel> connect(SocketAddress address, ChannelHandler channelInitializer)
	{
		return new CompletableFuture<Channel>() {
			private ChannelFuture future;

			{
				resolve(address)
					.whenComplete((v, ex) -> {
						if (ex != null) {
							completeExceptionally(ex);
						}
						else {
							stepConnect(v);
						}
					});
			}

			private synchronized void stepConnect(SocketAddress resolved)
			{
				if (isDone())
					return;
				try {
					Bootstrap b = new Bootstrap();
					future = b.group(workerGroup)
						.channelFactory(channelProvider.getStreamChannel(resolved))
						.option(ChannelOption.AUTO_READ, false)
						.option(ChannelOption.ALLOW_HALF_CLOSURE, true)
						.handler(channelInitializer)
						.connect(resolved);

					future.addListener((f) -> {
						try {
							try {
								f.get();
							}
							catch (ExecutionException ex) {
								throw ex.getCause();
							}
							complete((DuplexChannel)future.channel());
						}
						catch (IOException ex) {
							completeExceptionally(new UncheckedIOException("Failed to connect to: "+address+" : "+ex.getMessage(), ex));
						}
						catch (Throwable ex) {
							completeExceptionally(new IOException("Failed to connect to: "+address, ex));
						}
					});
				}
				catch (Throwable ex) {
					completeExceptionally(ex);
				}
			}

			@Override
			public synchronized boolean cancel(boolean interrupt)
			{
				if (future != null)
					return future.cancel(interrupt);
				return super.cancel(interrupt);
			}
		};
	}

	public static ProtocolFamily getProtocolByAddress(InetAddress address)
	{
		return address instanceof Inet6Address ? StandardProtocolFamily.INET6 : StandardProtocolFamily.INET;
	}

	public static InternetProtocolFamily getNettyProtocolByAddress(InetAddress address)
	{
		return address instanceof Inet6Address ? InternetProtocolFamily.IPv6 : InternetProtocolFamily.IPv4;
	}
}
