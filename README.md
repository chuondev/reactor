## Reactor

依据 Doug Lea 的 [Scalable IO in Java](http://gee.cs.oswego.edu/dl/cpjslides/nio.pdf) 基于 NIO 实现的 Reacotr 模式的回显服务器

- BasicHandler: 单线程处理器
- MultiReactor: 主从 Reactor
- MultithreadHandler: 线程池处理器
- Reactor: 接收连接，I/O 读写

Reactor 模型的说明：https://www.cnblogs.com/chuonye/p/10725372.html

## Getting started

**编译**

```java
javac -encoding utf-8 *.java
```

**运行**

```java
java Reactor
java MultiReactor
```

**测试**

使用 telnet 命令进行测试，在 windows 下记得打开回显（ctrl+] -> set localecho -> Enter）

```bash
D:\>telnet 127.0.0.1 10393
Implementation of Reactor Design Partten by tonwu.net
reactor> Hello World!
Hello World!
reactor>

遗失对主机的连接。
D:\>
```
