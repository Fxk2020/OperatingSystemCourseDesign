package nachos.threads;

import java.util.LinkedList;

import nachos.machine.*;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>, and multiple
 * threads can be waiting to <i>listen</i>. But there should never be a time
 * when both a speaker and a listener are waiting, because the two threads can
 * be paired off at this point.
 * 
 * <i> communicator </ i>允许线程同步交换32位消息。 多个线程可能正在等待<i>说话</ i>，并且多个线程可能正在等待<i>收听</
 * i>。 但是，永远不要让说话者和听者都在等待，因为此时两个线程可以配对。
 */
public class Communicator {
	/**
	 * Allocate a new communicator.
	 */
	public Communicator() {
		// 1.4 start
		lock = new Lock();
		words = new LinkedList<Integer>();
		speaker = new Condition2(lock);
		listener = new Condition2(lock);
		speakerNum = 0;
		listenerNum = 0;
		// 1.4 end
	}

	/**
	 * Wait for a thread to listen through this communicator, and then transfer
	 * <i>word</i> to the listener.
	 *
	 * <p>
	 * Does not return until this thread is paired up with a listening thread.
	 * Exactly one listener should receive <i>word</i>.
	 *
	 * @param word the integer to transfer.
	 */
	public void speak(int word) {
		// 1.4 start
		boolean preState = Machine.interrupt().disable();// 关中断
		lock.acquire();// 拿到锁
		words.add(word);// 说者的word加入words链表

		if (listenerNum == 0) {// 没有听者，说者睡眠，队列存储说者的话
			System.out.println("暂时没有收听者，等待收听");
			speakerNum++;// 说者人数+1
			speaker.sleep();//sleep释放锁，直至重新获得锁才返回
			listenerNum--;
		} else {// 有听者
			System.out.println("有收听者，作为speaker我要直接唤醒它，且word传给它！");
			speakerNum++;// 说者人数+1
			listener.wake();
			listenerNum--;
		}
		lock.release();
		Machine.interrupt().restore(preState);// 开中断
		return;
		// 1.4 end
	}

	/**
	 * Wait for a thread to speak through this communicator, and then return the
	 * <i>word</i> that thread passed to <tt>speak()</tt>.
	 *
	 * @return the integer transferred.
	 */
	public int listen() {
		// 1.4 start

		// 如果有人说话,则让说者说话，得到word，听者返回word；否则听者等待
		boolean preState = Machine.interrupt().disable();// 关中断
		lock.acquire();// 拿到锁

		if (speakerNum == 0) {
			System.out.println("暂时没有说话者，等待说话");
			listenerNum++;// listener++
			listener.sleep();
			speakerNum--;
		} else {
			System.out.println("有说者，我要唤醒它！");
			listenerNum++;// listener++
			speaker.wake();// 如果说话者不为0，则wake一个speaker说话
			speakerNum--;
		}
		lock.release();// 释放锁
		Machine.interrupt().restore(preState);// 开中断
		return words.removeLast();

		// 1.4 end

	}

	// 1.4 start
	private Lock lock;// 互斥锁
	private int speakerNum;// 说者数量
	private int listenerNum;// 听者数量
	private LinkedList<Integer> words;// 保存说者话语
	Condition2 listener;// 说者条件变量
	Condition2 speaker;// 听者条件变量
	// 1.4 end

}
