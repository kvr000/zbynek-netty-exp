package com.github.kvr000.zbyneknettyexp.brokenautoread;


import com.github.kvr000.zbyneknettyexp.brokenautoread.handler.EchoClientHandler;
import com.github.kvr000.zbyneknettyexp.brokenautoread.handler.EchoServerHandler;
import com.github.kvr000.zbyneknettyexp.brokenautoread.handler.EchoServerNoReadHandler;
import com.github.kvr000.zbyneknettyexp.brokenautoread.netty.ChannelProvider;
import com.github.kvr000.zbyneknettyexp.brokenautoread.netty.EpollChannelProvider;
import com.github.kvr000.zbyneknettyexp.brokenautoread.netty.KqueueChannelProvider;
import com.github.kvr000.zbyneknettyexp.brokenautoread.netty.NettyEngine;
import com.github.kvr000.zbyneknettyexp.brokenautoread.netty.NioChannelProvider;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ServerChannel;
import io.netty.channel.socket.DuplexChannel;
import io.netty.handler.codec.string.StringDecoder;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;


public class Main
{
	private InetSocketAddress listenAddress = InetSocketAddress.createUnresolved("localhost", 40400);

	private NettyEngine nettyEngine;

	public static void main(String[] args)
	{
		System.exit(new Main().run(args));
	}

	public int run(String[] args)
	{
		if (args.length != 2) {
			System.err.println("Usage: brokenautoread channelimpl {client|clients|server|noreadserver|both|bothmulti|noreadboth|noreadbothmulti}");
			return 121;
		}

		nettyEngine = new NettyEngine(setupChannelImpl(args[0]));

		return executeTask(args[1]);
	}

	public int executeTask(String task)
	{
		switch (task) {
		case "server":
			return executeServer();

		case "noreadserver":
			return executeNoReadServer();

		case "client":
			return executeClient();

		case "clients":
			return executeClients();

		case "both":
			return executeBoth();

		case "bothmulti":
			return executeBothMulti();

		case "noreadboth":
			return executeNoReadBoth();

		case "noreadbothmulti":
			return executeNoReadBothMulti();

		default:
			throw new UnsupportedOperationException("Unknown task, only {client|clients|server|noreadserver|both|bothmulti|noreadboth|noreadbothmulti} supported: " + task);
		}
	}

	public int executeServer()
	{
		Channel server = launchServer().join();
		server.closeFuture().syncUninterruptibly();
		return 0;
	}

	public int executeNoReadServer()
	{
		Channel server = launchNoReadServer().join();
		server.closeFuture().syncUninterruptibly();
		return 0;
	}

	public int executeClient()
	{
		Void result = launchClient(new AtomicInteger()).join();
		return 0;
	}

	public int executeClients()
	{
		Void result = launchClients().join();
		return 0;
	}

	public int executeBoth()
	{
		Channel server = launchServer().join();
		Void result = launchClient(new AtomicInteger()).join();
		server.close().syncUninterruptibly();
		return 0;
	}

	public int executeNoReadBoth()
	{
		Channel server = launchNoReadServer().join();
		Void result = launchClient(new AtomicInteger()).join();
		server.close().syncUninterruptibly();
		return 0;
	}

	public int executeBothMulti()
	{
		Channel server = launchServer().join();
		Void result = launchClients().join();
		server.close().syncUninterruptibly();
		return 0;
	}

	public int executeNoReadBothMulti()
	{
		Channel server = launchNoReadServer().join();
		Void result = launchClients().join();
		server.close().syncUninterruptibly();
		return 0;
	}

	public CompletableFuture<ServerChannel> launchServer()
	{
		return nettyEngine.listen(
			listenAddress,
			new ChannelInitializer<DuplexChannel>()
			{
				@Override
				protected void initChannel(DuplexChannel ch) throws Exception
				{
					ch.pipeline().addLast(
						new EchoServerHandler(ch)
					);
				}
			}
		);
	}

	public CompletableFuture<ServerChannel> launchNoReadServer()
	{
		return nettyEngine.listen(
			listenAddress,
			new ChannelInitializer<DuplexChannel>()
			{
				@Override
				protected void initChannel(DuplexChannel ch) throws Exception
				{
					ch.pipeline().addLast(
						new EchoServerNoReadHandler(ch)
					);
				}
			}
		);
	}

	public CompletableFuture<Void> launchClients()
	{
		AtomicInteger remaining = new AtomicInteger(1000);
		AtomicInteger pending = new AtomicInteger();
		List<CompletableFuture<Void>> executions = new ArrayList<>();
		for (int i = 0; i < Runtime.getRuntime().availableProcessors()*2+1; ++i) {
			CompletableFuture<Void> execution = (new Function<Void, CompletableFuture<Void>>()
			{
				Function<Void, CompletableFuture<Void>> this0 = this;
				@Override
				public CompletableFuture<Void> apply(Void unused)
				{
					if (remaining.getAndDecrement() > 0) {
						return launchClient(pending)
							.thenCompose(this0);
					}
					else {
						return CompletableFuture.completedFuture(null);
					}
				}
			}).apply(null);
			executions.add(execution);
		}
		return CompletableFuture.allOf(executions.toArray(new CompletableFuture[0]));
	}

	public CompletableFuture<Void> launchClient(AtomicInteger pending)
	{
		CompletableFuture<Void> result = new CompletableFuture<>();
		nettyEngine.connect(
			listenAddress,
			new ChannelInitializer<DuplexChannel>()
			{
				@Override
				protected void initChannel(DuplexChannel ch) throws Exception
				{
					ch.pipeline().addLast(
						new StringDecoder(),
						new EchoClientHandler(result, ch, pending)
					);
				}
			}
		)
			.whenComplete((v, ex) -> {
				if (ex != null) {
					result.completeExceptionally(ex);
				}
			});
		return result;
	}

	private ChannelProvider setupChannelImpl(String name)
	{
		switch (name) {
		case "nio":
			return new NioChannelProvider();

		case "kqueue":
			return new KqueueChannelProvider();

		case "epoll":
			return new EpollChannelProvider();

		default:
			throw new UnsupportedOperationException("Unknown channel implementation, only {nio|kqueue|epoll} supported: " + name);
		}
	}
}
