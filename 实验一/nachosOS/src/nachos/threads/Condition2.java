package nachos.threads;

import nachos.machine.*;

/**
 * An implementation of condition variables that disables interrupt()s for
 * synchronization.
 *
 * <p>
 * You must implement this.
 *
 * @see nachos.threads.Condition
 */
public class Condition2 {
	/**
	 * Allocate a new condition variable.
	 *
	 * @param conditionLock the lock associated with this condition variable. The
	 *                      current thread must hold this lock whenever it uses
	 *                      <tt>sleep()</tt>, <tt>wake()</tt>, or
	 *                      <tt>wakeAll()</tt>.
	 */
	public Condition2(Lock conditionLock) {
		this.conditionLock = conditionLock;
		// 初始化队列
		// 1.2 start
		waitQueue = ThreadedKernel.scheduler.newThreadQueue(false);
		// 1.2 end
	}

	/**
	 * Atomically release the associated lock and go to sleep on this condition
	 * variable until another thread wakes it using <tt>wake()</tt>. The current
	 * thread must hold the associated lock. The thread will automatically reacquire
	 * the lock before <tt>sleep()</tt> returns.
	 * 
	 * 以原子方式释放关联的锁，并在此条件变量上进入睡眠状态，
	 * 直到另一个线程使用<tt> wake（）</ tt>将其唤醒。 
	 * 当前线程必须持有关联的锁。 
	 * 该线程将在<tt> sleep（）</ tt>返回之前自动重新获得该锁。
	 */
	public void sleep() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());// 检测当前进程是否获得锁

		conditionLock.release();
		// 1.2 start
		boolean preState = Machine.interrupt().disable();// 关中断
		waitQueue.waitForAccess(KThread.currentThread());// 将当前线程加入到waitQueue中
		KThread.currentThread().sleep();// 让当前线程睡眠,返回就是有线程使用wake唤醒
		Machine.interrupt().restore(preState);// 恢复中断
		// 1.2 end

		conditionLock.acquire();// 重新获得锁才能执行
	}

	/**
	 * Wake up at most one thread sleeping on this condition variable. The current
	 * thread must hold the associated lock.
	 */
	public void wake() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		// 1.2 start
		boolean preState = Machine.interrupt().disable();// 关中断
		KThread thread = waitQueue.nextThread();
		if (!(thread == null))
			thread.ready();// 只是将线程加入就绪队列但不释放锁
		Machine.interrupt().restore(preState);// 恢复中断
		// 1.2 end

	}

	/**
	 * Wake up all threads sleeping on this condition variable. The current thread
	 * must hold the associated lock.
	 * 唤醒所有在此条件变量上休眠的线程。
	 * 当前线程必须持有关联的锁。
	 */
	public void wakeAll() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());

		// 1.2 start
		boolean preState = Machine.interrupt().disable();// 关中断
		KThread thread = waitQueue.nextThread();
		while (thread != null) {// 将waitQueue中的所有线程均唤醒
			thread.ready();
			thread = waitQueue.nextThread();
		}
		Machine.interrupt().restore(preState);// 恢复中断
		// 1.2 end

	}

	private Lock conditionLock;
	// 声明变量
	// 1.2 start
	private ThreadQueue waitQueue = null;
	// 1.2 end

}
