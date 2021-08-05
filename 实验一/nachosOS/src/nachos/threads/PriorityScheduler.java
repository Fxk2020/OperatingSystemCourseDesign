package nachos.threads;

import nachos.machine.*;

import java.util.TreeSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * A scheduler that chooses threads based on their priorities.
 *
 * <p>
 * A priority scheduler associates a priority with each thread. The next thread
 * to be dequeued is always a thread with priority no less than any other
 * waiting thread's priority. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the thread
 * that has been waiting longest.
 *
 * <p>
 * Essentially, a priority scheduler gives access in a round-robin fassion to
 * all the highest-priority threads, and ignores all other threads. This has the
 * potential to starve a thread if there's always a thread waiting with higher
 * priority.
 *
 * <p>
 * A priority scheduler must partially solve the priority inversion problem; in
 * particular, priority must be donated through locks, and through joins.
 * 
 * 一个根据线程的优先级选择线程的调度程序。 优先级调度程序将优先级与每个线程相关联。 下一个要出队的线程始终是优先级不小于任何其他等待线程的优先级的线程。
 * 像循环调度程序一样，在具有相同（最高）优先级的所有线程中，被出队的线程是等待时间最长的线程。
 * 
 * 本质上，优先级调度程序以循环方式访问所有优先级最高的线程，而忽略所有其他线程。 如果始终有更高优先级的线程在等待，则有可能使线程饿死。
 * 
 * 优先级调度程序必须部分解决优先级反转问题； 特别是必须通过锁和联接来捐赠优先级。
 */
public class PriorityScheduler extends Scheduler {
	/**
	 * Allocate a new priority scheduler.
	 */
	public PriorityScheduler() {
	}

	/**
	 * Allocate a new priority thread queue.
	 *
	 * @param transferPriority <tt>true</tt> if this queue should transfer priority
	 *                         from waiting threads to the owning thread.
	 * @return a new priority thread queue.
	 */
	public ThreadQueue newThreadQueue(boolean transferPriority) {
		return new PriorityQueue(transferPriority);
	}

	public int getPriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());

		return getThreadState(thread).getPriority();
	}

	public int getEffectivePriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());

		return getThreadState(thread).getEffectivePriority();
	}

	public void setPriority(KThread thread, int priority) {
		Lib.assertTrue(Machine.interrupt().disabled());

		Lib.assertTrue(priority >= priorityMinimum && priority <= priorityMaximum);

		getThreadState(thread).setPriority(priority);
	}

	public boolean increasePriority() {
		boolean intStatus = Machine.interrupt().disable();

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMaximum)
			return false;

		setPriority(thread, priority + 1);

		Machine.interrupt().restore(intStatus);
		return true;
	}

	public boolean decreasePriority() {
		boolean intStatus = Machine.interrupt().disable();

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMinimum)
			return false;

		setPriority(thread, priority - 1);

		Machine.interrupt().restore(intStatus);
		return true;
	}

	/**
	 * The default priority for a new thread. Do not change this value.
	 */
	public static final int priorityDefault = 1;
	/**
	 * The minimum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMinimum = 0;
	/**
	 * The maximum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMaximum = 7;

	/**
	 * Return the scheduling state of the specified thread.
	 *
	 * @param thread the thread whose scheduling state to return.
	 * @return the scheduling state of the specified thread.
	 */
	protected ThreadState getThreadState(KThread thread) {
		if (thread.schedulingState == null)
			thread.schedulingState = new ThreadState(thread);

		return (ThreadState) thread.schedulingState;
	}

	/**
	 * A <tt>ThreadQueue</tt> that sorts threads by priority. 通过优先级储存进程
	 */
	protected class PriorityQueue extends ThreadQueue {
		PriorityQueue(boolean transferPriority) {
			this.transferPriority = transferPriority;
//			this.transferPriority = true;
		}

		// 将参数中线程加入到this队列中
		public void waitForAccess(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			getThreadState(thread).waitForAccess(this);
		}

		// 参数中的线程会成为this队列的队列头，但并不在队列里面
		public void acquire(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			getThreadState(thread).acquire(this);
		}

		// 决定this队列中下一个最可能执行的线程【如果this是readyQueue，则选择是下一个执行的线程】，
		// 最可能执行的线程是队列中有效优先级最高的线程。
		public KThread nextThread() {
			Lib.assertTrue(Machine.interrupt().disabled());
			// implement me
			// 1.5 start
			ThreadState x = pickNextThread();// 下一个选择的线程
			if (x == null)// 如果为null,则返回null
				return null;
			else {
				KThread thread = x.thread;
				x.acquire(this);// 将得到的线程改为this线程队列的队列头
				return thread;// 将该线程返回
			}
			// 1.5 end

		}

		/**
		 * Return the next thread that <tt>nextThread()</tt> would return, without
		 * modifying the state of this queue.
		 *
		 * @return the next thread that <tt>nextThread()</tt> would return.
		 */
		// 选择下一个有效优先级最高的线程；
		// 根据题意如果有效优先级相同，则应该选择等待时间最长的线程。
		// 对于优先级相同的线程只要优先选择该链表前面的线程即可解决这个问题
		protected ThreadState pickNextThread() {
			// implement me
			// 1.5 start
			Iterator i = waitList.iterator();
			KThread nextthread;
			if (i.hasNext()) {
				nextthread = (KThread) i.next();// 取出下一个线程
				KThread x = null;
				while (i.hasNext()) {// 比较线程的有效优先级，选出最大的，如果优先级相同，则选择等待时间最长的
					x = (KThread) i.next();
					int a = getThreadState(nextthread).getEffectivePriority();
//					System.out.println(nextthread.getName()+"**的优先级为："+a);
					int b = getThreadState(x).getEffectivePriority();
//					System.out.println(x.getName()+"**的优先级为："+b);
					if (a < b) {
//						System.out.println(x.getName()+"的优先级高于"+nextthread.getName());
						nextthread = x;
						
					}
				}
				return getThreadState(nextthread);
			} else {
				return null;
			}
			// 1.5 end
		}

		public void print() {
			Lib.assertTrue(Machine.interrupt().disabled());
			// implement me (if you want)
		}

		/**
		 * <tt>true</tt> if this queue should transfer priority from waiting threads to
		 * the owning thread.
		 * 
		 * 此队列是否应将优先级从等待线程转移到拥有线程。
		 */
		public boolean transferPriority;
		// 1.5 start
		protected KThread lockHolder = null; // 队列头
		protected LinkedList<KThread> waitList = new LinkedList<KThread>();
		// 1.5 end
		public boolean isTransferPriority() {
			return transferPriority;
		}

		public void setTransferPriority(boolean transferPriority) {
			this.transferPriority = transferPriority;
		}

	}

	/**
	 * The scheduling state of a thread. This should include the thread's priority,
	 * its effective priority, any objects it owns, and the queue it's waiting for,
	 * if any. 包含进程的优先级
	 * 
	 * @see nachos.threads.KThread#schedulingState
	 */
	protected class ThreadState {
		/**
		 * Allocate a new <tt>ThreadState</tt> object and associate it with the
		 * specified thread.
		 *
		 * @param thread the thread this state belongs to.
		 */
		public ThreadState(KThread thread) {
			this.thread = thread;

			setPriority(priorityDefault);
		}

		/**
		 * Return the priority of the associated thread.
		 *
		 * @return the priority of the associated thread.
		 */
		public int getPriority() {
			return priority;
		}

		/**
		 * Return the effective priority of the associated thread.
		 *
		 * @return the effective priority of the associated thread.
		 * 
		 *         该方法通过比较this线程的acquired中所有线程的优先级，将最高优先级捐献给this线程。
		 *         acquired为一个HashSet容器，该容器中装有所有等待该线程的队列，这些队列包括
		 *         锁队列（如果该线程持有几把锁，那么等待该锁的线程队列便会在里面），
		 *         waitForJoin队列（如果A线程join了该线程，则A线程会进入该线程waitForJoin队列）等， 队列里放着等待的线程
		 */
		public int getEffectivePriority() {
			// 1.5 start
			Lib.assertTrue(Machine.interrupt().disabled());

			// ThreadState 有一个PriorityQueue类型的 waitQueue, PriorityQueue 中有个LinkedList的waitList

			// 该优先级线程队列存在优先级捐赠吗？？
			if (effectivePriority == invalidPriority && !acquired.isEmpty()) {
				effectivePriority = priority;// 先将自己的优先级赋给有效优先级

				for (Iterator i = acquired.iterator(); i.hasNext();) {// 比较acquired中的所有等待队列中的所有线程的优先级

					int index = 0;
					for (Iterator j = ((PriorityQueue) i.next()).waitList.iterator(); j.hasNext();) {
						ThreadState ts = getThreadState((KThread) j.next());
						if (ts.priority > effectivePriority) {
							
							effectivePriority = ts.priority;
						}
					}
				}
				return effectivePriority;
			} else {
				if (effectivePriority == -2) { // 表明该优先级线程队列不存在优先级捐赠
					return priority;
				} else
					return effectivePriority;// 如果该线程没有执行，那么它之前算的有效优先级不必重新再算一遍
			}
			// 1.5 end
		}

		/**
		 * Set the priority of the associated thread to the specified value.
		 *
		 * @param priority the new priority.
		 */
		public void setPriority(int priority) {
			if (this.priority == priority)
				return;

			this.priority = priority;

			// implement me
		}

		/**
		 * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is the
		 * associated thread) is invoked on the specified priority queue. The associated
		 * thread is therefore waiting for access to the resource guarded by
		 * <tt>waitQueue</tt>. This method is only called if the associated thread
		 * cannot immediately obtain access.
		 *
		 * @param waitQueue the queue that the associated thread is now waiting on.
		 *
		 * @see nachos.threads.ThreadQueue#waitForAccess
		 */
		public void waitForAccess(PriorityQueue waitQueue) {
			// implement me
			// 1.5 start
			waitQueue.setTransferPriority(true);
			waitQueue.waitList.add(this.thread);// 将this调用线程加入到等待队列
			acquired.add(waitQueue);
			// 1.5 end

		}

		/**
		 * Called when the associated thread has acquired access to whatever is guarded
		 * by <tt>waitQueue</tt>. This can occur either as a result of
		 * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
		 * <tt>thread</tt> is the associated thread), or as a result of
		 * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
		 *
		 * acquired获得
		 *
		 * @see nachos.threads.ThreadQueue#acquire
		 * @see nachos.threads.ThreadQueue#nextThread
		 */
		public void acquire(PriorityQueue waitQueue) {
			// implement me
			// 1.5 start
			waitQueue.setTransferPriority(true);
			waitQueue.waitList.remove(this.thread);// 如果这个队列中存在该线程，删除
			waitQueue.lockHolder = this.thread;// 对于readyQueue来讲，lockHolder为执行线程；对于Lock类的waitQueue来讲，lockHolder为持锁者；对于waitForJoin队列来讲，lockHolder为执行join方法的线程。
			if (waitQueue.transferPriority) {// 如果存在优先级翻转，则执行下面操作
//				this.effectivePriority = invalidPriority;
				this.effectivePriority = invalidPriority;
				acquired.add(waitQueue);// 将等待该线程的队列加入该线程的等待队列集合中
			}
			// 1.5 end
		}


		/** The thread with which this object is associated. */
		protected KThread thread;
		/** The priority of the associated thread. */
		protected int priority;
		// 1.5 start
		protected int effectivePriority = -2;// 有效优先级初始化为-2
		protected final int invalidPriority = -1;// 无效优先级初始化为-1
		// 等待该线程的所有优先队列（每个优先队列里有等待线程）,包括等待锁，等待join方法的队列
		protected HashSet<PriorityQueue> acquired = new HashSet<PriorityQueue>();
		protected PriorityQueue waitQueue = new PriorityQueue(true);
		// 1.5 end

	}
}
