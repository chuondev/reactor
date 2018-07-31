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
 * @author wskwbog
 */
public class MultithreadHandler extends BasicHandler {
	
	static Executor workPool = Executors.newFixedThreadPool(5);
	static {
		LOG_PROMPT = "MultithreadHandler";
	}
	static final int PROCESSING = 3;
	private Object lock = new Object();
	
	public MultithreadHandler(Selector sel, SocketChannel sc) throws IOException {
		super(sel, sc);
	}

	@Override
	public void read() throws IOException {
		synchronized (lock) {
			int n = socket.read(input);
			System.out.println(LOG_PROMPT + ": Start reading ... ");
			if (inputIsComplete(n)) {
				System.out.println(LOG_PROMPT + ": End of reading and Submit to thread pool processing ...");
				
				// 读取完毕后将后续的处理交给
				state = PROCESSING;
				workPool.execute(new Processer());
			}
		}
	}
	
	private void processAndHandOff() {
		synchronized (lock) {
			process();
			System.out.println(LOG_PROMPT + ": Process end start sending ...");
			
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
			System.out.println(LOG_PROMPT + ".Processer: Process and handoff");
			processAndHandOff();
		}
	}
}
