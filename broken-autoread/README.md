# Netty autoRead bug for Kqueue and Epoll

## Background

Netty is supposed to not read channel data until requested explicitly by `ctx.read()` or enabling
`channel.config().setAutoRead(true)` .

This works well for Nio channels.  However, for Kqueue and Epoll, it works only when there is only listener and not
client running within the same instance.

## Test

### Usage

```shell
java -jar target/broken-autoread.jar channelimpl {client|clients|server|noreadserver|both|bothmulti|noreadboth|noreadbothmulti}
```

The first parameter specifies channel implementation, one of: nio, kqueue, epoll .
The second parameter specifies the test:
- client: run single client
- clients: run multiple clients in parallel
- server: run server
- noreadserver: run server but do not issue reading 
- both: run server and client
- bothmulti: run server and multiple clients
- noreadboth: run no-read server and client
- noreadbothmulti: run no-read server and client

### Nio test

```shell
java -jar target/broken-autoread.jar nio both
java -jar target/broken-autoread.jar nio noreadboth
```

The first test succeeds.  The second test gets correctly stuck forever as server never attempts to read.

### Kqueue test

```shell
java -jar target/broken-autoread.jar kqueue both
java -jar target/broken-autoread.jar kqueue noreadboth
```

Both runs succeed.  The second succeeds despite there is no attempt to read data on server.

```shell
java -jar target/broken-autoread.jar kqueue noreadserver
java -jar target/broken-autoread.jar kqueue client
```

Running only no-read server based on kqueue is more tricky.  Sending the full data via subsequent client command will
trigger the server handler despite no read issued.  Connecting via telnet or netcat and sending small unfinished data
does not trigger server activity.

### Epoll test

Epoll has similar result to Kqueue.

## Other issues

Unpredictable reads cause additional problems when channels are removed and added as the reads can then bubble through
pipeline while the handlers update are still not finished and may miss the newly added handler.  One solution is to use
FlowControlHandler but this may not work as well with explicit reads, may buffer more data and run out of memory and it
also does not support all messages, such as channel shutdown.

## Conclusion

Under certain conditions, the Kqueue and Epoll implementations ignore the autoRead settings and read data even when
disabled.  The Nio channels work correctly.  This seems like a bug in Netty.
