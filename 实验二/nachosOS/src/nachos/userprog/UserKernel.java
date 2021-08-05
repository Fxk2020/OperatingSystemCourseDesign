package nachos.userprog;

import java.util.LinkedList;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

/**
 * A kernel that can support multiple user processes.
 * 
 * 可以支持多个用户进程的内核。
 */
public class UserKernel extends ThreadedKernel {
	/**
	 * Allocate a new user kernel.
	 */
	public UserKernel() {
		super();
	}

	/**
	 * Initialize this kernel. Creates a synchronized console and sets the
	 * processor's exception handler.
	 */
	public void initialize(String[] args) {

		// 2.2 start
		allocateMemoryLock = new Lock();
		// 初始化的时候，使memoryLinkedList包含所有的页号
		memoryLinkedList = new LinkedList<Integer>();
		for (int i = 0; i < 1024; i++) {
			memoryLinkedList.add(i);
		}
		// 2.2 end

		super.initialize(args);

		console = new SynchConsole(Machine.console());

		Machine.processor().setExceptionHandler(new Runnable() {
			public void run() {
				exceptionHandler();
			}
		});
	}

	/**
	 * Test the console device.
	 */
	public void selfTest() {
//		super.selfTest();
//		System.out.println("start!");
//        testLottery();
//		System.out.println("Testing the console device. Typed characters");
//		System.out.println("将回显直到键入q：");
//
//		char c;
//
//		do {
//			c = (char) console.readByte(true);
//			console.writeByte(c);
//		} while (c != 'q');
//
//		System.out.println("");
	}

// 2.4test start
	public static void testLottery() {
		System.out.println("-----Now we begin to testLottery()-----");
		boolean oldStatus = Machine.interrupt().disable();

		ThreadQueue q11 = ThreadedKernel.scheduler.newThreadQueue(true);
		ThreadQueue q12 = ThreadedKernel.scheduler.newThreadQueue(true);
		LotteryScheduler.PriorityQueue q = (LotteryScheduler.PriorityQueue) q11;
		LotteryScheduler.PriorityQueue q1 = (LotteryScheduler.PriorityQueue) q12;

		KThread k1 = new KThread().setName("k1");
		KThread k2 = new KThread().setName("k2");
		KThread k3 = new KThread().setName("k3");

		q.acquire(k1);
		System.out.println("k1得到q队列,q队列:[k1]");
		System.out.println("k1 effectivePriority = " + ThreadedKernel.scheduler.getEffectivePriority(k1));
		System.out.println();

		q.waitForAccess(k2);
		System.out.println("k2加入q队列,q队列:[k1],k2");
		System.out.println("k1 effectivePriority = " + ThreadedKernel.scheduler.getEffectivePriority(k1));
		System.out.println("k2 effectivePriority = " + ThreadedKernel.scheduler.getEffectivePriority(k2));
		System.out.println();

		q1.acquire(k2);
		System.out.println("k2得到q1队列,q1队列:[k2]");
		q1.waitForAccess(k3);
		System.out.println("k3加入q1队列,q1队列:[k2],k3");
		System.out.println("k1 effectivePriority = " + ThreadedKernel.scheduler.getEffectivePriority(k1));
		System.out.println("k2 effectivePriority = " + ThreadedKernel.scheduler.getEffectivePriority(k2));

		Machine.interrupt().restore(oldStatus);
	}
// 2.4test end

	/**
	 * Returns the current process.
	 *
	 * @return the current process, or <tt>null</tt> if no process is current.
	 */
	public static UserProcess currentProcess() {
		if (!(KThread.currentThread() instanceof UThread))
			return null;

		return ((UThread) KThread.currentThread()).process;
	}

	/**
	 * The exception handler. This handler is called by the processor whenever a
	 * user instruction causes a processor exception.
	 *
	 * <p>
	 * When the exception handler is invoked, interrupts are enabled, and the
	 * processor's cause register contains an integer identifying the cause of the
	 * exception (see the <tt>exceptionZZZ</tt> constants in the <tt>Processor</tt>
	 * class). If the exception involves a bad virtual address (e.g. page fault, TLB
	 * miss, read-only, bus error, or address error), the processor's BadVAddr
	 * register identifies the virtual address that caused the exception.
	 */
	public void exceptionHandler() {
		Lib.assertTrue(KThread.currentThread() instanceof UThread);

		UserProcess process = ((UThread) KThread.currentThread()).process;
		int cause = Machine.processor().readRegister(Processor.regCause);
		process.handleException(cause);
	}

	/**
	 * Start running user programs, by creating a process and running a shell
	 * program in it. The name of the shell program it must run is returned by
	 * <tt>Machine.getShellProgramName()</tt>.
	 *
	 * @see nachos.machine.Machine#getShellProgramName
	 */
	public void run() {
		super.run();

		UserProcess process = UserProcess.newUserProcess();

		String shellProgram = Machine.getShellProgramName();
		Lib.assertTrue(process.execute(shellProgram, new String[] {}));

		KThread.currentThread().finish();
	}

	/**
	 * Terminate this kernel. Never returns.
	 */
	public void terminate() {
		super.terminate();
	}

	/**
	 * Globally accessible reference to the synchronized console. 全局可访问的对同步控制台的引用。
	 */
	public static SynchConsole console;

	// dummy variables to make javac smarter
	// 虚拟变量使Javac更智能
	private static Coff dummy1 = null;

	// 2.2start
	public static Lock allocateMemoryLock = null;
	public static LinkedList memoryLinkedList = null;
	// 2.2end
}
