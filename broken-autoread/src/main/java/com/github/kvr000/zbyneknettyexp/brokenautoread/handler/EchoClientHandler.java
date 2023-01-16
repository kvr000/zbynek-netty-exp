package com.github.kvr000.zbyneknettyexp.brokenautoread.handler;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.channel.socket.DuplexChannel;
import io.netty.channel.socket.DuplexChannelConfig;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;


@Log4j2
public class EchoClientHandler extends ChannelInboundHandlerAdapter
{
	public static final String TEST_STRING = "Hello World\n";
	public static final byte[] TEST_BYTES = TEST_STRING.getBytes(StandardCharsets.UTF_8);
	public static final int REPEAT = 1000;
	public static final String EXPECTED = StringUtils.repeat(TEST_STRING, REPEAT);

	private StringBuilder sb = new StringBuilder();

	private final CompletableFuture<Void> closedPromise;

	private final AtomicInteger pending;

	private int counter = REPEAT;

	public EchoClientHandler(CompletableFuture<Void> closedPromise, DuplexChannel channel, AtomicInteger pending)
	{
		this.closedPromise = closedPromise;
		((DuplexChannelConfig) channel.config()).setAutoRead(false);
		((DuplexChannelConfig) channel.config()).setAllowHalfClosure(true);
		channel.closeFuture().addListener(f -> {
			if (f.isSuccess())
				closedPromise.completeExceptionally(new IOException("Unexpected close before test completion"));
			else
				closedPromise.completeExceptionally(f.cause());
		});
		this.pending = pending;
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx)
	{
		ctx.read();
		writeData(ctx);
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg)
	{
		sb.append((String) msg);
		ReferenceCountUtil.release(msg);
		ctx.read();
	}

	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
		if (evt instanceof ChannelInputShutdownEvent) {
			try {
				if (!sb.toString().equals(EXPECTED)) {
					closedPromise.completeExceptionally(new IllegalStateException(
						"Output does not match expected: [" + sb.toString() + "] expected [" + EXPECTED + "]"));
				}
			}
			catch (Throwable ex) {
				closedPromise.completeExceptionally(ex);
			}
			finally {
				log.info("Finished client, pending={}", pending);
				closedPromise.complete(null);
				ctx.close();
			}
		}
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
		throws Exception {
		log.error("Exception in client:", cause);
		closedPromise.completeExceptionally(cause);
		ctx.close();
	}

	private void writeData(ChannelHandlerContext ctx)
	{
		if (--counter >= 0) {
			ctx.writeAndFlush(Unpooled.wrappedBuffer(TEST_BYTES)).addListener(f -> {
				if (f.isSuccess()) {
					writeData(ctx);
				}
				else {
					closedPromise.completeExceptionally(f.cause());
					ctx.close();
				}
			});
		}
		else {
			((DuplexChannel) ctx.channel()).shutdownOutput().addListener(f -> {
				if (!f.isSuccess()) {
					closedPromise.completeExceptionally(f.cause());
				}
			});
		}
	}
}
