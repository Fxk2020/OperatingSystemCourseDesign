package nachos.threads;

import nachos.machine.*;

/**
 * A <tt>Lock</tt> is a synchronization primitive that has two states,具有两个状态的同步原语
 * <i>busy</i> and <i>free</i>. There are only two operations allowed on a
 * lock:
 *
 * <ul>
 * <li><tt>acquire()</tt>: atomically wait until the lock is <i>free</i> and
 * then set it to <i>busy</i>.
 * <li><tt>release()</tt>: set the lock to be <i>free</i>, waking up one
 * waiting thread if possible.
 * </ul>
 *
 * <p>
 * Also, only the thread that acquired a lock may release it. As with
 * semaphores, the API does not allow you to read the lock state (because the
 * value could change immediately after you read it).
 */
public class Lock {
    /**
     * Allocate a new lock. The lock will initially be <i>free</i>.
     */
    public Lock() {
    }

    /**
     * Atomically acquire this lock. The current thread must not already hold
     * this lock.
     * 
     * 以原子方式获取此锁。 当前线程必须尚未拥有此锁。
     */
    public void acquire() {
	Lib.assertTrue(!isHeldByCurrentThread());

	boolean intStatus = Machine.interrupt().disable();
	KThread thread = KThread.currentThread();

	if (lockHolder != null) {
	    waitQueue.waitForAccess(thread);
	    KThread.sleep();
	}
	else {
	    waitQueue.acquire(thread);
	    lockHolder = thread;
	}

	Lib.assertTrue(lockHolder == thread);

	Machine.interrupt().restore(intStatus);
    }

    /**
     * Atomically release this lock, allowing other threads to acquire it.
     * 
     * 以原子方式释放此锁，允许其他线程获取它。
     */
    public void release() {
	Lib.assertTrue(isHeldByCurrentThread());

	boolean intStatus = Machine.interrupt().disable();

	if ((lockHolder = waitQueue.nextThread()) != null)
	    lockHolder.ready();
	
	Machine.interrupt().restore(intStatus);
    }

    /**
     * Test if the current thread holds this lock.
     *
     * @return	true if the current thread holds this lock.
     */
    public boolean isHeldByCurrentThread() {
	return (lockHolder == KThread.currentThread());
    }

    private KThread lockHolder = null;
    private ThreadQueue waitQueue =
	ThreadedKernel.scheduler.newThreadQueue(true);
}
