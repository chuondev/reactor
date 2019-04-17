import java.io.EOFException;
import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * 多线程基本处理器，I/O 的读写由 Reactor 线程处理，业务的处理交给线程池
 * 
 * @see Reactor
 * @see BasicHandler
 * @author tongwu.net
 */
public class MultithreadHandler extends BasicHandler {
	
	static Executor workPool = Executors.newFixedThreadPool(5);
	static final int PROCESSING = 4;
	private Object lock = new Object();
	
	public MultithreadHandler(Selector sel, SocketChannel sc) throws IOException {
		super(sel, sc);
	}

	@Override
	public void read() throws IOException {
		// 为什么要同步？Processer 线程处理时通道还有可能有读事件发生
		// 保护 input ByteBuffer 不会重置和状态的可见性
		// 应该是这样
		synchronized (lock) {
			input.clear();
			int n = socket.read(input);
			if (inputIsComplete(n)) {
				
				// 读取完毕后将后续的处理交给
				state = PROCESSING;
				workPool.execute(new Processer());
			}
		}
	}
	
	private void processAndHandOff() {
		synchronized (lock) {
			try {
				process();
			} catch (EOFException e) {
				// 直接关闭连接
				try {
					sk.channel().close();
				} catch (IOException e1) {}
				return;
			}
			
			// 最后的发送还是交给 Reactor 线程处理
			state = SENDING;
			sk.interestOps(SelectionKey.OP_WRITE);
			
			// 这里需要唤醒 Selector，因为当把处理交给 workpool 时，Reactor 线程已经阻塞在 select() 方法了， 注意
			// 此时该通道感兴趣的事件还是 OP_READ，这里将通道感兴趣的事件改为 OP_WRITE，如果不唤醒的话，就只能在
			// 下次select 返回时才能有响应了，当然了也可以在 select 方法上设置超时
			sk.selector().wakeup();
		}
	}
	
	class Processer implements Runnable {
		@Override
		public void run() {
			processAndHandOff();
		}
	}
}
