package com.github.kvr000.zbyneknettyexp.brokenautoread.handler;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.channel.socket.DuplexChannel;
import io.netty.channel.socket.DuplexChannelConfig;
import lombok.extern.log4j.Log4j2;


@Log4j2
public class EchoServerHandler extends ChannelInboundHandlerAdapter
{
	public EchoServerHandler(DuplexChannel channel)
	{
		((DuplexChannelConfig) channel.config()).setAutoRead(false);
		((DuplexChannelConfig) channel.config()).setAllowHalfClosure(true);
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx)
	{
		ctx.read();
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg)
	{
		ctx.writeAndFlush(msg);
		ctx.read();
	}

	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
		if (evt instanceof ChannelInputShutdownEvent) {
			ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener((ChannelFuture f) -> {
				ChannelFuture newFuture = f;
				if (f.isSuccess()) {
					newFuture = ((DuplexChannel) ctx.channel()).shutdownOutput();
				}
				newFuture.addListener(ChannelFutureListener.CLOSE);
			});
		}
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		log.error("Exception in server:", cause);
		ctx.close();
	}
}
