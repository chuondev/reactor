import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

/**
 * Reactor 接收连接，直接负责 I/O 的读写，拿到字节后有单线程和多线程两种处理器：
 * <p>
 * 单线程处理器：业务处理也由 Reactor 线程来做
 * <br>
 * 多线程处理器：业务处理由线程池线程来做
 * 
 * @see BasicHandler
 * @see MultithreadHandlerold
 * @author wskwbog
 */
public class Reactor implements Runnable {
	public static void main(String[] args) {
		try {
			Thread th = new Thread(new Reactor(10393));
			th.setName("Reactor");
			th.start();
			th.join();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	public static final String LOG_PROMPT = "Reactor";
	final Selector selector;
	final ServerSocketChannel serverSocket;

	public Reactor(int port) throws IOException {
		selector = Selector.open(); // 选择器，为多个通道提供服务
		serverSocket = ServerSocketChannel.open();
		serverSocket.socket().bind(new InetSocketAddress(port)); // 绑定端口
		serverSocket.configureBlocking(false); // 设置成非阻塞模式
		SelectionKey sk = serverSocket.register(selector, SelectionKey.OP_ACCEPT); // 注册到 选择器 并设置处理 socket 连接事件
		// 添加一个附加对象，关注的事件发生时用于处理
		sk.attach(new Acceptor());
		
		System.out.println(LOG_PROMPT + ": Listening on port " + port);
	}

	@Override
	public void run() { // normally in a new Thread
		try {
			while (!Thread.interrupted()) { // 死循环
				selector.select(); // 阻塞，直到有通道事件就绪
				Set<SelectionKey> selected = selector.selectedKeys(); // 拿到就绪通道 SelectionKey 的集合
				Iterator<SelectionKey> it = selected.iterator();
				while (it.hasNext()) {
					SelectionKey skTmp = it.next();
					
					String action = ""; // 此字段用于日志输出，便于理解，无其他含义
					if (skTmp.isReadable()) {
						action = "OP_READ";
					} else if (skTmp.isWritable()) {
						action = "OP_WRITE";
					} else if (skTmp.isAcceptable()) {
						action = "OP_ACCEPT";
					} else if (skTmp.isConnectable()) {
						action = "OP_CONNECT";
					}
					System.out.println();
					System.out.println(LOG_PROMPT + ": Action - " + action);
					
					dispatch(skTmp); // 分发
				}
				selected.clear(); // 清空就绪通道的 key
			}
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	void dispatch(SelectionKey k) {
		Runnable r = (Runnable) (k.attachment()); // 拿到通道注册时附加的对象
		if (r != null) r.run();
	}
	
	/**
	 * 处理连接建立事件
	 * 
	 * @author wskwbog
	 */
	class Acceptor implements Runnable {
		@Override
		public void run() {
			try {
				SocketChannel sc = serverSocket.accept(); // 接收连接，非阻塞模式下，没有连接直接返回 null
				if (sc != null) {
					System.out.println(LOG_PROMPT + ": Accept and handler - " + sc.socket().getLocalSocketAddress());
					new BasicHandler(selector, sc); // 单线程处理连接
//					new MultithreadHandler(selector, sc); // 线程池处理连接
				}
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
	}
}
