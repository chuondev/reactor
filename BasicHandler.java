import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

/**
 * 单线程基本处理器，I/O 的读写以及业务的计算均由 Reactor 线程处理
 * 
 * @see Reactor
 * @author wskwbog
 */
public class BasicHandler implements Runnable {
	public static String LOG_PROMPT = "BasicHandler";
	
	private static final int MAXIN = 1024;
	private static final int MAXOUT = 1024;

	final SocketChannel socket;
	final SelectionKey sk;
	ByteBuffer input = ByteBuffer.allocate(MAXIN);
	ByteBuffer output = ByteBuffer.allocate(MAXOUT);
	static final int READING = 0, SENDING = 1;
	int state = READING;

	public BasicHandler(Selector sel, SocketChannel sc) throws IOException {
		socket = sc;
		sc.configureBlocking(false); // 设置非阻塞
		// Optionally try first read now
		sk = socket.register(sel, 0); // 注册通道
		sk.attach(this); // 将自身附加到 key
		sk.interestOps(SelectionKey.OP_READ); // 初始设置处理读取事件
		sel.wakeup(); // 唤醒 select() 方法，直接返回
		
		System.out.println(LOG_PROMPT + ": Register OP_READ " + sc.socket().getLocalSocketAddress() + " and wakeup Secletor");
	}
	
	@Override
	public void run() {
		try {
			if (state == READING)
				read(); // 此时通道已经准备好读取字节
			else if (state == SENDING)
				send(); // 此时通道已经准备好写入字节
		} catch (IOException ex) {
			ex.printStackTrace();
			// 关闭连接
			try {
				sk.channel().close();
			} catch (IOException ignore) {
			}
		}
	}
	
	/**
	 * 从通道读取字节，不同模式下需要注意的问题，n 为 read 返回值：<p>
	 * 非阻塞模式：没读取到内容就返回了，n 可能返回 0 或 -1 <br>
	 * 阻塞模式： n > 0 永远成立，至少读到一个字节才返回
	 */
	protected void read() throws IOException {
		int n = socket.read(input);
		System.out.println(LOG_PROMPT + ": Start reading ... ");
		if (inputIsComplete(n)) {
			System.out.println(LOG_PROMPT + ": End of reading and processing ...");
			process();
			System.out.println(LOG_PROMPT + ": Process end start sending ...");
			state = SENDING;
			// Normally also do first write now
			sk.interestOps(SelectionKey.OP_WRITE);
		}
	}
	
	// 缓存每次读取内容，在 process 方法会清空其内容
	StringBuilder request = new StringBuilder();
	/**
	 * 当读取到 \r\n 时表示结束
	 * @param bytes 读取的字节数，-1 通常是连接被关闭，0 非阻塞模式可能返回
	 */
	protected boolean inputIsComplete(int bytes) {
		// -1 关闭连接，此时 request 的长度为 0，内容为空 ""，后续处理会自动关闭
		if (bytes < 0) { return true; } 
		// 0，继续读取，不能操作缓冲区，放在 flip 之前
		if (bytes == 0) {return false;}
		
		input.flip();
		while (input.hasRemaining()) {
			byte ch = input.get();
			if (ch == '\r') { // continue
			} else if (ch == '\n') {
				// 读取到了 \r\n 读取结束
				return true;
			} else {
				request.append((char)ch);
			}
		}
		input.clear(); // 清空继续从通道读取，之前的输入已缓存
		return false;
	}
	
	// 缓存每次响应内容，在 outputIsComplete 方法会清空其内容
	StringBuilder response = new StringBuilder();
	/**
	 * 业务处理，这里直接将输入的内容返回
	 */
	protected void process() {
		response.append("Echo > ").append(request.toString());
		output.put(response.append("\r\n").toString().getBytes(StandardCharsets.UTF_8));
		
		// 清空接收缓冲区继续接收
		input.clear();
		request.delete(0, request.length());
		
		System.out.println(LOG_PROMPT + ": Response - [" + response.toString() + "]");
	}
	
	protected void send() throws IOException {
		output.flip();
		if (output.hasRemaining()) {
			socket.write(output);
			System.out.println(LOG_PROMPT + ": Send complete");
		} else {
			System.out.println(LOG_PROMPT + ": Nothing was send");
		}
		
		System.out.println(LOG_PROMPT + ": Check if disconnected ...");
		// 连接是否断开
		if (outputIsComplete()) {
			sk.channel().close();
			System.out.println(LOG_PROMPT + ": Channel close.");
		} else {
			// 否则继续读取
			state = READING;
			sk.interestOps(SelectionKey.OP_READ);
			System.out.println(LOG_PROMPT + ": Continue reading ...");
		}
			
	}

	/**
	 * output 的内容为 "Echo > \r\n" 关闭通道<br>
	 * 此时 request.length 为 0，有两种情况一是用户直接出入回车，二是socket读取返回-1，简单处理直接关闭
	 */
	protected boolean outputIsComplete() { 
		// 当只敲回车时（request长度为0） 断开连接
		if ("Echo > \r\n".equals(response.toString())) {
		    return true;	
		}
		
		// 清空旧数据，接着处理后续的请求
		output.clear();
		response.delete(0, response.length());
		return false;
	}
}
