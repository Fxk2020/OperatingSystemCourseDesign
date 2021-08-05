package nachos.threads;

import nachos.machine.*;

/**
 * A KThread is a thread that can be used to execute Nachos kernel code. Nachos
 * allows multiple threads to run concurrently.
 *
 * To create a new thread of execution, first declare a class that implements
 * the <tt>Runnable</tt> interface. That class then implements the <tt>run</tt>
 * method. An instance of the class can then be allocated, passed as an argument
 * when creating <tt>KThread</tt>, and forked. For example, a thread that
 * computes pi could be written as follows:
 *
 * <p>
 * <blockquote>
 * 
 * <pre>
 * class PiRun implements Runnable {
 *     public void run() {
 *         // compute pi
 *         ...
 *     }
 * }
 * </pre>
 * 
 * </blockquote>
 * <p>
 * The following code would then create a thread and start it running:
 *
 * <p>
 * <blockquote>
 * 
 * <pre>
 * PiRun p = new PiRun();
 * new KThread(p).fork();
 * </pre>
 * 
 * </blockquote>
 */
public class KThread {
	/**
	 * Get the current thread.
	 *
	 * @return the current thread.
	 */
	public static KThread currentThread() {
		Lib.assertTrue(currentThread != null);
		return currentThread;
	}

	/**
	 * Allocate a new <tt>KThread</tt>. If this is the first <tt>KThread</tt>,
	 * create an idle thread as well.
	 */
	public KThread() {
		//1.1start
		// 抽象类由于java派生于抽象类的对象无法实例化，在运行时很可能出现空指针异常，该语句不可缺少
		waitJoinQueue = ThreadedKernel.scheduler.newThreadQueue(false);
		//1.1end
		if (currentThread != null) {
			tcb = new TCB();
		} else {
			readyQueue = ThreadedKernel.scheduler.newThreadQueue(false);
			readyQueue.acquire(this);

			currentThread = this;
			tcb = TCB.currentTCB();
			name = "main";
			restoreState();

			createIdleThread();
		}
	}

	/**
	 * Allocate a new KThread.
	 * 分配一个新的KThread。
	 *
	 * @param target the object whose <tt>run</tt> method is called.
	 */
	public KThread(Runnable target) {
//		this();
		this();
		this.target = target;
	}

	/**
	 * Set the target of this thread.
	 *
	 * @param target the object whose <tt>run</tt> method is called.
	 * @return this thread.
	 */
	public KThread setTarget(Runnable target) {
		Lib.assertTrue(status == statusNew);

		this.target = target;
		return this;
	}

	/**
	 * Set the name of this thread. This name is used for debugging purposes only.
	 *
	 * @param name the name to give to this thread.
	 * @return this thread.
	 */
	public KThread setName(String name) {
		this.name = name;
		return this;
	}

	/**
	 * Get the name of this thread. This name is used for debugging purposes only.
	 *
	 * @return the name given to this thread.
	 */
	public String getName() {
		return name;
	}

	/**
	 * Get the full name of this thread. This includes its name along with its
	 * numerical ID. This name is used for debugging purposes only.
	 *
	 * @return the full name given to this thread.
	 */
	public String toString() {
		return (name + " (#" + id + ")");
	}

	/**
	 * Deterministically and consistently compare this thread to another thread.
	 */
	public int compareTo(Object o) {
		KThread thread = (KThread) o;

		if (id < thread.id)
			return -1;
		else if (id > thread.id)
			return 1;
		else
			return 0;
	}

	/**
	 * Causes this thread to begin execution. The result is that two threads are
	 * running concurrently: the current thread (which returns from the call to the
	 * <tt>fork</tt> method) and the other thread (which executes its target's
	 * <tt>run</tt> method).
	 * 
	 * 使该线程开始执行。 结果是两个线程同时运行：
	 * 当前线程（从调用返回到<tt> fork </tt>方法）和另一个线程（执行目标的<tt> run </tt>方法） 。
	 */
	public void fork() {
		Lib.assertTrue(status == statusNew);
		Lib.assertTrue(target != null);

		Lib.debug(dbgThread, "Forking thread: " + toString() + " Runnable: " + target);

		boolean intStatus = Machine.interrupt().disable();

		tcb.start(new Runnable() {
			public void run() {
				runThread();
			}
		});

		ready();

		Machine.interrupt().restore(intStatus);
	}

	private void runThread() {
		begin();
		target.run();
		finish();
	}

	private void begin() {
		Lib.debug(dbgThread, "Beginning thread: " + toString());

		Lib.assertTrue(this == currentThread);

		restoreState();

		Machine.interrupt().enable();
	}

	/**
	 * Finish the current thread and schedule it to be destroyed when it is safe to
	 * do so. This method is automatically called when a thread's <tt>run</tt>
	 * method returns, but it may also be called directly.
	 *
	 * The current thread cannot be immediately destroyed because its stack and
	 * other execution state are still in use. Instead, this thread will be
	 * destroyed automatically by the next thread to run, when it is safe to delete
	 * this thread.
	 */
	public static void finish() {
		
		Lib.debug(dbgThread, "Finishing thread: " + currentThread.toString());

		Machine.interrupt().disable();// 系统关中断

		Machine.autoGrader().finishingCurrentThread();// 当前进程运行结束的标志

		Lib.assertTrue(toBeDestroyed == null);
		toBeDestroyed = currentThread;

		currentThread.status = statusFinished;// 当前进程的状态修改为运行结束

		// 1.1 START
		KThread waitThread = currentThread.waitJoinQueue.nextThread(); // 调用等待队列上的第一个进程;
		while (waitThread != null) // while等待队列上有进程
		{
			waitThread.ready(); // 唤醒等待队列上所有被阻塞的进程
			waitThread = currentThread.waitJoinQueue.nextThread(); // 调用等待队列上的下一个进程
		}
		// 1.1 END

		
		sleep();
		
	}

	/**
	 * Relinquish the CPU if any other thread is ready to run. If so, put the
	 * current thread on the ready queue, so that it will eventually be rescheuled.
	 *
	 * <p>
	 * Returns immediately if no other thread is ready to run. Otherwise returns
	 * when the current thread is chosen to run again by
	 * <tt>readyQueue.nextThread()</tt>.
	 *
	 * <p>
	 * Interrupts are disabled, so that the current thread can atomically add itself
	 * to the ready queue and switch to the next thread. On return, restores
	 * interrupts to the previous state, in case <tt>yield()</tt> was called with
	 * interrupts disabled.
	 * 
	 * 如果准备好运行任何其他线程，则放弃CPU。 如果是这样，请将当前线程放在就绪队列中，以便最终将其重新调度。
	 * 
	 * 如果没有其他线程可以运行，则立即返回。在选择了当前线程再次运行，否则返回
	 * 
	 * 禁用中断，以便当前线程可以自动将自身添加到就绪队列中并切换到下一个线程。 返回时，如果在禁用中断的情况下调用<tt> yield（）</
	 * tt>，则将中断恢复到先前的状态。
	 */
	public static void yield() {
		Lib.debug(dbgThread, "Yielding thread: " + currentThread.toString());

		Lib.assertTrue(currentThread.status == statusRunning);

		boolean intStatus = Machine.interrupt().disable();

		currentThread.ready();

		runNextThread();

		Machine.interrupt().restore(intStatus);
	}

	/**
	 * Relinquish the CPU, because the current thread has either finished or it is
	 * blocked. This thread must be the current thread.
	 *
	 * <p>
	 * If the current thread is blocked (on a synchronization primitive, i.e. a
	 * <tt>Semaphore</tt>, <tt>Lock</tt>, or <tt>Condition</tt>), eventually some
	 * thread will wake this thread up, putting it back on the ready queue so that
	 * it can be rescheduled. Otherwise, <tt>finish()</tt> should have scheduled
	 * this thread to be destroyed by the next thread to run.
	 */
	public static void sleep() {
		Lib.debug(dbgThread, "Sleeping thread: " + currentThread.toString());

		Lib.assertTrue(Machine.interrupt().disabled());

		if (currentThread.status != statusFinished)
			currentThread.status = statusBlocked;

		runNextThread();
	}

	/**
	 * Moves this thread to the ready state and adds this to the scheduler's ready
	 * queue.
	 * 
	 * 将此线程移到就绪状态，并将其添加到调度程序的就绪队列中。
	 */
	public void ready() {
		Lib.debug(dbgThread, "Ready thread: " + toString());

		Lib.assertTrue(Machine.interrupt().disabled());
		Lib.assertTrue(status != statusReady);

		status = statusReady;
		if (this != idleThread)
			readyQueue.waitForAccess(this);

		Machine.autoGrader().readyThread(this);
	}

	/**
	 * Waits for this thread to finish. If this thread is already finished, return
	 * immediately. This method must only be called once; the second call is not
	 * guaranteed to return. This thread must not be the current thread.
	 * 
	 * 等待线程结束，如果线程结束立刻返回。
	 */
	public void join() {
		Lib.debug(dbgThread, "Joining to thread: " + toString());

		Lib.assertTrue(this != currentThread);
		// 1.1 start
		joinCount++;
		if (joinCount > 1) {
			System.out.println("提示:join函数只能被调用1次!");
			return;
		}

		boolean intStatus = Machine.interrupt().disable();// 系统关中断(关中断是指在此中断处理完成前，不处理其它中断)
		if (status != statusFinished) {
			// 让this线程(join方法调用之前正在运行的线程)成为waitForJoinQueue队列的头，
			//表明只有先执行this线程，才会去执行队列里的线程
			waitJoinQueue.acquire(this);//this线程获得资源
			waitJoinQueue.waitForAccess(currentThread);//// 将当前线程加入到waitJoinQueue队列里，暂时阻塞
			currentThread.sleep();// 当前进程睡眠等待被调用进程结束
		}
		Machine.interrupt().enable();// 系统开中断
		// 1.1 end

	}

	/**
	 * Create the idle thread. Whenever there are no threads ready to be run, and
	 * <tt>runNextThread()</tt> is called, it will run the idle thread. The idle
	 * thread must never block, and it will only be allowed to run when all other
	 * threads are blocked.
	 *
	 * <p>
	 * Note that <tt>ready()</tt> never adds the idle thread to the ready set.
	 */
	private static void createIdleThread() {
		Lib.assertTrue(idleThread == null);

		idleThread = new KThread(new Runnable() {
			public void run() {
				while (true)
					yield();
			}
		});
		idleThread.setName("idle");

		Machine.autoGrader().setIdleThread(idleThread);

		idleThread.fork();
	}

	/**
	 * Determine the next thread to run, then dispatch the CPU to the thread using
	 * <tt>run()</tt>.
	 */
	private static void runNextThread() {
		KThread nextThread = readyQueue.nextThread();
		if (nextThread == null)
			nextThread = idleThread;

		nextThread.run();
	}

	/**
	 * Dispatch the CPU to this thread. Save the state of the current thread, switch
	 * to the new thread by calling <tt>TCB.contextSwitch()</tt>, and load the state
	 * of the new thread. The new thread becomes the current thread.
	 *
	 * <p>
	 * If the new thread and the old thread are the same, this method must still
	 * call <tt>saveState()</tt>, <tt>contextSwitch()</tt>, and
	 * <tt>restoreState()</tt>.
	 *
	 * <p>
	 * The state of the previously running thread must already have been changed
	 * from running to blocked or ready (depending on whether the thread is sleeping
	 * or yielding).
	 *
	 * @param finishing <tt>true</tt> if the current thread is finished, and should
	 *                  be destroyed by the new thread.
	 */
	private void run() {
		Lib.assertTrue(Machine.interrupt().disabled());

		Machine.yield();

		currentThread.saveState();

		Lib.debug(dbgThread, "Switching from: " + currentThread.toString() + " to: " + toString());

		currentThread = this;

		tcb.contextSwitch();

		currentThread.restoreState();
	}

	/**
	 * 重新run进程 Prepare this thread to be run. Set <tt>status</tt> to
	 * <tt>statusRunning</tt> and check <tt>toBeDestroyed</tt>.
	 */
	protected void restoreState() {
		Lib.debug(dbgThread, "Running thread: " + currentThread.toString());

		Lib.assertTrue(Machine.interrupt().disabled());
		Lib.assertTrue(this == currentThread);
		Lib.assertTrue(tcb == TCB.currentTCB());

		Machine.autoGrader().runningThread(this);

		status = statusRunning;

		if (toBeDestroyed != null) {
			toBeDestroyed.tcb.destroy();
			toBeDestroyed.tcb = null;
			toBeDestroyed = null;
		}
	}

	/**
	 * 进程放弃cpu进入等待队列 Prepare this thread to give up the processor. Kernel threads do
	 * not need to do anything here.
	 */
	protected void saveState() {
		Lib.assertTrue(Machine.interrupt().disabled());
		Lib.assertTrue(this == currentThread);
	}

	private static class PingTest implements Runnable {
		PingTest(int which) {
			this.which = which;
		}

		public void run() {
			for (int i = 0; i < 5; i++) {
				System.out.println("*** thread " + which + " looped " + i + " times");
//				System.out.println("*** " + currentThread.getName() + " looped " + i + " times");
				currentThread.yield();
			}
		}

		private int which;
	}

	/**
	 * Tests whether this module is working.
	 */
	public static void selfTest() {

		Lib.debug(dbgThread, "Enter KThread.selfTest");
		
//		testBoat();
//		testPriority();
//		testCommunicator();
//		testAlarm();
//		testCondition2();
//		testJoin();
//		new KThread(new PingTest(1)).setName("forked thread").fork();
//		new PingTest(0).run();
		testLottery();

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


	// 自我检测
	// 1.1test start
	public static void testJoin() {
		final KThread b = new KThread(new PingTest(1));
		System.out.println("thread b 启动");
		b.fork();
		System.out.println("调用join方法，当前线程阻塞，thread b 执行结束后,thread a 再执行(thread a 为主线程)");
		b.join();//如果不使用join方法，a，b线程会交替执行
//		b.join();
		System.out.println("thread a 开始执行");
		new PingTest(0).run();
	}
	// 1.1test end
	// 1.2test start
	public static void testCondition2() {
		Lib.debug(dbgThread, "Enter KThread.selfTest");
		Lock lock = new Lock();
		Condition2 con = new Condition2(lock);
		KThread thread_A = new KThread(new Runnable() {
			public void run() {
				lock.acquire();

				System.out.println("-----Now we begin to testCondition2()-----");
				System.out.println("thread_A will sleep");
				con.sleep();// 暂时释放锁

				System.out.println("thread_A is waked up");
//						cdt.wake();
				lock.release();
				System.out.println("thread_A execute successful!");
			}
		});

		KThread thread_B = new KThread(new Runnable() {
			public void run() {
				lock.acquire();
				System.out.println("thread_B will sleep");
				con.sleep();// 暂时释放锁
				// KThread.currentThread.yield();
				System.out.println("thread_B is waked up");
				lock.release();
				System.out.println("thread_B execute successful!");
			}
		});
		KThread thread_MM = new KThread(new Runnable() {
			public void run() {
				lock.acquire();
				System.out.println("Thread_Wake:I will wake up all of the threads");
//				con.wake();
				con.wakeAll();
				lock.release();
				// KThread.currentThread.yield();
				System.out.println("thread_Wake execute successful!");
//						System.out.println("successful!");
			}
		});
		thread_A.fork();
		thread_B.fork();
		thread_MM.fork();
//				thread_A.join();

	}

	// 1.2test end
//1.3test start	
	public static void testAlarm() {
		KThread alarmThread_1 = new KThread(new Runnable() {
			int wait = 10;

			public void run() {
				System.out.println("-----Now we begin to testAlarm()-----");
				System.out.println("alarmThread_1进入睡眠,时间:" + Machine.timer().getTime() + "等待时间:" + wait);
				ThreadedKernel.alarm.waitUntil(wait);
				System.out.println("alarmThread_1执行结束后的系统时间:" + Machine.timer().getTime());
			}
		}).setName("alarmThread_1");

		KThread alarmThread_2 = new KThread(new Runnable() {
			int wait = 1125;

			public void run() {
				System.out.println("alarmThread_2进入睡眠,时间:" + Machine.timer().getTime() + "等待时间:" + wait);
				ThreadedKernel.alarm.waitUntil(wait);
				System.out.println("alarmThread_2执行结束后的系统时间:" + Machine.timer().getTime());
			}
		}).setName("alarmThread_2");
		
//		KThread alarmThread_3 = new KThread(new Runnable() {
//			int wait = 15;
//
//			public void run() {
//				System.out.println("alarmThread_3进入睡眠,时间:" + Machine.timer().getTime() + "等待时间:" + wait);
//				ThreadedKernel.alarm.waitUntil(wait);
//				System.out.println("alarmThread_3执行结束后的系统时间:" + Machine.timer().getTime());
//			}
//		}).setName("alarmThread_3");

		alarmThread_1.fork();
		alarmThread_2.fork();
//		alarmThread_3.fork();
		System.out.println("successful");
	}

//1.3 test end!	
//1.4 test start
	public static void testCommunicator() {
		System.out.println("-----Now we begin to testCommunicator()-----");
		Communicator c = new Communicator();
		new KThread(new Runnable() {

			@Override
			public void run() {
				for (int i = 0; i < 3; ++i) {
					System.out.println("speaker 大声说出 : " + i);
					c.speak(i);
					KThread.yield();
				}

			}
		}).setName("Speaker").fork();
		
		new KThread(new Runnable() {

			@Override
			public void run() {
				for (int i = 0; i < 2; ++i) {
					System.out.println("speaker 大声说出 : " + (i+3));
					c.speak(i);
					KThread.yield();
				}

			}
		}).setName("Speaker2").fork();

		for (int i = 0; i < 5; ++i) {
//			try {
//				Thread.sleep(1000);//发现对话确实是一对一对话的
//			} catch (InterruptedException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
			System.out.println("我是listener" + i + ",我来听speaker说话.");
			int x = c.listen();
			System.out.println("listener 听到了, word = " + x);
			KThread.yield();
		}
	}

//1.4 test end!
//1.5 test start
	public static void testPriority() {
		System.out.println("-----Now begin the testPriority()-----");
		boolean status = Machine.interrupt().disable();// 关中断，setPriority()函数中要求关中断

		final KThread a = new KThread(new PingTest(1)).setName("thread1");
		new PriorityScheduler().setPriority(a, 3);
		System.out.println("thread1的优先级为：" + new PriorityScheduler().getThreadState(a).priority);
		KThread b = new KThread(new PingTest(2)).setName("thread2");
		new PriorityScheduler().setPriority(b, 4);
		System.out.println("thread2的优先级为：" + new PriorityScheduler().getThreadState(b).priority);
		KThread c = new KThread(new Runnable() {
			public void run() {
				for (int i = 0; i < 5; i++) {
					if (i == 2)
						a.join();//并没有发生优先级翻转！！！！！！！！！！
					System.out.println("*** thread 3 looped " + i + " times");
					KThread.currentThread().yield();
				}
			}
		}).setName("thread3");
		new PriorityScheduler().setPriority(c, 5);
		System.out.println("thread3的优先级为：" + new PriorityScheduler().getThreadState(c).priority);
		a.fork();
		b.fork();
		c.fork();

		Machine.interrupt().restore(status);

	}
//1.5test end!
//1.6test start
	public static void testBoat() {
		System.out.println("-----Now we begin to testBoat()-----");
		Boat.selfTest();
	}
//1.6test end!
	private static final char dbgThread = 't';

	/**
	 * Additional state used by schedulers.
	 * 调度程序使用的其他状态。
	 * @see nachos.threads.PriorityScheduler.ThreadState
	 * 
	 */
	public Object schedulingState = null;

	private static final int statusNew = 0;
	private static final int statusReady = 1;
	private static final int statusRunning = 2;
	private static final int statusBlocked = 3;
	private static final int statusFinished = 4;

	/**
	 * The status of this thread. A thread can either be new (not yet forked), ready
	 * (on the ready queue but not running), running, or blocked (not on the ready
	 * queue and not running).
	 */
	private int status = statusNew;
	private String name = "(unnamed thread)";
	private Runnable target;
	private TCB tcb;

	/**
	 * Unique identifer for this thread. Used to deterministically compare threads.
	 */
	private int id = numCreated++;
	/** Number of times the KThread constructor was called. */
	private static int numCreated = 0;

	private static ThreadQueue readyQueue = null;
	private static KThread currentThread = null;
	private static KThread toBeDestroyed = null;
	private static KThread idleThread = null;

	// 定义变量
	private static ThreadQueue waitJoinQueue = null;
	private int joinCount = 0;// 定义调用join的次数

}
