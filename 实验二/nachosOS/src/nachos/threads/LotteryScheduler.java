package nachos.threads;

import nachos.machine.*;
import nachos.threads.PriorityScheduler.PriorityQueue;
import nachos.threads.PriorityScheduler.ThreadState;

import java.util.TreeSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;

/**
 * 彩票
 * A scheduler that chooses threads using a lottery.
 * 使用彩票选择线程的调度程序。
 *
 * <p>
 * A lottery scheduler associates a number of tickets with each thread. When a
 * thread needs to be dequeued, a random lottery is held, among all the tickets
 * of all the threads waiting to be dequeued. The thread that holds the winning
 * ticket is chosen.
 *
 * <p>
 * Note that a lottery scheduler must be able to handle a lot of tickets
 * (sometimes billions), so it is not acceptable to maintain state for every
 * ticket.
 *
 * <p>
 * A lottery scheduler must partially solve the priority inversion problem; in
 * particular, tickets must be transferred through locks, and through joins.
 * Unlike a priority scheduler, these tickets add (as opposed to just taking
 * the maximum).
 */
public class LotteryScheduler extends PriorityScheduler {
    /**
     * Allocate a new lottery scheduler.
     */
    public LotteryScheduler() {
    }
    
    /**
     * Allocate a new lottery thread queue.
     *
     * @param	transferPriority	<tt>true</tt> if this queue should
     *					transfer tickets from waiting threads
     *					to the owning thread.
     * @return	a new lottery thread queue.
     */
    public ThreadQueue newThreadQueue(boolean transferPriority) {
    	return new PriorityQueue(transferPriority);
    }
	/**
	 * Return the scheduling state of the specified thread.
	 *
	 * @param thread the thread whose scheduling state to return.
	 * @return the scheduling state of the specified thread.
	 */
	public ThreadState getThreadState(KThread thread) {
		if (thread.schedulingState == null)
			thread.schedulingState = new ThreadState(thread);

		return (ThreadState) thread.schedulingState;
	}

	/**
	 * 
	 */
	public class PriorityQueue extends PriorityScheduler.PriorityQueue
    {

        PriorityQueue(boolean transferPriority)
        {
        	super(transferPriority);
            this.transferPriority = transferPriority;
            waitQueue = new LinkedList<>();
            holder = null;
        }

        @Override
        public ThreadState pickNextThread()
        {
            ThreadState r = null;
            
            //temp : waitQueue的每一个线程的彩票总数之和
            int temp = 0;
            for(int i = 0; i < waitQueue.size(); ++i)
            {
                temp += waitQueue.get(i).effectivePriority;
            }
            
            //彩票总数不为0
            if(temp != 0)
            {
                int rand = random.nextInt(temp) + 1; //随机抽取的彩票数是rand
                temp = 0;
                
                /*再次对 waitQueue的每一个线程的彩票数，开始相加
                 * 直到 rand < 不断相加的彩票总数,那么当前线程就是获得彩票的那个(即下一个要执行的)线程。
                 */
                for(int i = 0; i < waitQueue.size(); ++i)
                {
                	temp += waitQueue.get(i).effectivePriority;
                    if(rand <= temp)
                    {
                    	//r 就是获得彩票的那个ThreadState,即下一个要执行的程序。
                        r = waitQueue.remove(i);
                        break;
                    }                  
                }
            }
          //每去掉（执行）一个线程，都要改变holder（如果有holder的话）的EffectivePriority
            if(holder != null)
            {
                holder.holdingQueues.remove(this);
                holder.changeEffectivePriority();
                holder = r;
            }
          //r 就是获得彩票的那个ThreadState,即下一个要执行的。
            if(r != null)
                r.waitQueue = null;
            
            return r;
        }

        protected LinkedList<ThreadState> waitQueue;
        protected LotteryScheduler.ThreadState holder;
}


public class ThreadState extends PriorityScheduler.ThreadState
    {
        public ThreadState(KThread thread) {
        	super(thread);
            holdingQueues = new LinkedList<>();
            this.thread = thread;
            setPriority(priorityDefault);
            super.effectivePriority = priorityDefault;
        }

		@Override
        public void waitForAccess(PriorityScheduler.PriorityQueue wait)
        {
            PriorityQueue p = (PriorityQueue)wait;
            boolean oldStatus = Machine.interrupt().disable();
            
            this.waitQueue = p;
            p.waitQueue.add(this);
            
            //每增加waitForAccess一个线程，
            //都要改变holder（如果有holder的话）的EffectivePriority
            if(p.holder != null)
                p.holder.changeEffectivePriority();
            
            Machine.interrupt().restore(oldStatus);
        }

        @Override
        public void acquire(PriorityScheduler.PriorityQueue wait)
        {
            boolean oldStatus = Machine.interrupt().disable();
            PriorityQueue p = (PriorityQueue)wait;
            if(p.transferPriority)
            {
                holdingQueues.add(p);
                p.holder = this;
            }
            Machine.interrupt().restore(oldStatus);
        }

        protected void changeEffectivePriority()
        {
            effectivePriority = priority;
            if(holdingQueues == null)
            {
                System.out.println();
            }
            
            /* this线程 的 有效彩票数 = 自身的彩票数priority + 等待队列里所有线程的彩票数目
             * 一开始是effectivePriority = priority
             * 然后遍历holdingQueues的 几个PriorityQueue，
             * 对于每一个PriorityQueue，遍历其中的线程，
             * 不断加遍历的线程的彩票数，
             * 最终遍历完holdingQueues，所加得的effectivePriority就是 this线程 的 有效彩票数
             * */
            for(PriorityQueue p : holdingQueues)
            {
                for(int i = 0; i < p.waitQueue.size(); ++i)
                {
                    Lib.assertTrue(effectivePriority + p.waitQueue.get(i).effectivePriority < priorityMaximum);
                    effectivePriority += p.waitQueue.get(i).effectivePriority;
                }
            }
            //this线程 的 有效彩票数effectivePriority修改了，
            //如若waitQueue != null && waitQueue.holder != null，
            //那么waitQueue.holder也要跟着修改EffectivePriority
            if(waitQueue != null && waitQueue.holder != null)
                waitQueue.holder.changeEffectivePriority();
        }
        
        public int getE() {
        	return effectivePriority;
        }

        protected LinkedList<PriorityQueue> holdingQueues;
        protected PriorityQueue waitQueue;
    }

	
	/**
	 * The default priority for a new thread. Do not change this value.
	 */
	public static final int priorityDefault = 1;
	/**
	 * The minimum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMinimum = 1;
	/**
	 * The maximum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMaximum = Integer.MAX_VALUE;
	public static Random random = new Random();

	
}
