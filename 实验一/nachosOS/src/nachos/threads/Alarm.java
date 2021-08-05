package nachos.threads;

import java.util.LinkedList;

import nachos.machine.*;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time. 使用硬件计时器提供优先权，并允许线程休眠到一定时间。
 */
public class Alarm {
	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 *
	 * <p>
	 * <b>Note</b>: Nachos will not function correctly with more than one alarm.
	 */
	public Alarm() {
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() {
				timerInterrupt();
			}
		});
	}

	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current thread
	 * to yield, forcing a context switch if there is another thread that should be
	 * run.
	 * 
	 * Timer 每过500个clock ticks执行timerInterrupt()
	 * 
	 * 使当前线程屈服，如果有另一个线程应运行，则强制进行上下文切换。
	 * 
	 * 检查等待队列上的线程有没有到唤醒时间？
     * 到唤醒时间的线程 从 waitlist中移除   加入ready队列！！！
	 */
	public void timerInterrupt() {

		// 1.3start
		boolean preState = Machine.interrupt().disable();// 关中断

		Waiter waiter;
		// 将等待队列上应该唤醒的队列加入到就绪队列
		for (int i = 0; i < waitlist.size(); i++) {

			waiter = waitlist.getFirst();
			if (waiter.wakeTime <= Machine.timer().getTime()) {// 如果达到唤醒时间，将其从waitlist中移除并唤醒该线程
				System.out.println("唤醒线程：" + waiter.thread.getName() + ",时间为：" + Machine.timer().getTime());
				waitlist.removeFirst();// 移除这个已到唤醒时间的线程并加入ready
				waiter.thread.ready();// 加入ready,线程进入就绪状态
			}
		}

		Machine.interrupt().restore(preState);// 恢复中断
		// 1.3end

		KThread.currentThread().yield();
	}

	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks, waking it up in
	 * the timer interrupt handler. The thread must be woken up (placed in the
	 * scheduler ready set) during the first timer interrupt where
	 * 
	 * 使当前线程至少进入<i> x </ i>滴答声，然后在计时器中断处理程序中将其唤醒。 
	 * 在第一个计时器中断期间，必须唤醒线程（将其放在调度程序就绪集中）
	 *
	 * <p>
	 * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
	 *
	 * @param x the minimum number of clock ticks to wait.
	 *
	 * @see nachos.machine.Timer#getTime()
	 */
	public void waitUntil(long x) {
		// for now, cheat just to get something working (busy waiting is bad)
		long wakeTime = Machine.timer().getTime() + x;
//		while (wakeTime > Machine.timer().getTime())
//			KThread.yield();
		
		 //1.3start
		boolean preState = Machine.interrupt().disable();//关中断
	    
		Waiter waiter = new Waiter(KThread.currentThread(), wakeTime);
		waitlist.add(waiter);//加入到等待列表waitlist
		System.out.println(KThread.currentThread().getName()+"线程休眠，时间为："+Machine.timer().getTime()+",应在"+wakeTime+"时间醒来。");
		KThread.sleep();//该线程休眠！！
		
		Machine.interrupt().restore(preState);//恢复中断 
	    //1.3end
	}

// 1.3start
	private LinkedList<Waiter> waitlist = new LinkedList();/* !!!注意new */

	/**
	 * 存储等待线程的信息，包括线程号和唤醒时间 内部类,存放线程信息
	 */
	private class Waiter {
		private KThread thread;// 等待线程
		private long wakeTime;// 唤醒时间

		public Waiter(KThread thread, long wakeTime) {
			this.thread = thread;
			this.wakeTime = wakeTime;
		}

		public KThread getThread() {
			return thread;
		}

		public void setThread(KThread thread) {
			this.thread = thread;
		}

		public long getWakeTime() {
			return wakeTime;
		}

		public void setWakeTime(long time) {
			this.wakeTime = wakeTime;
		}
	}
//1.3end

}
