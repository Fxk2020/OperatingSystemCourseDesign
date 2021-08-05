package nachos.threads;

import nachos.ag.BoatGrader;
import nachos.machine.Lib;

/**
 * 实验一 test6
 * 
 * 
 * @author 26292
 *
 */

public class Boat {
	static BoatGrader bg;

	public static void selfTest() {
		BoatGrader b = new BoatGrader();

//		System.out.println("\n ***Testing Boats with only 2 children***");
//		begin(0, 2, b);

//	System.out.println("\n ***Testing Boats with 2 children, 1 adult***");
//  	begin(1, 2, b);

  	System.out.println("\n ***Testing Boats with 3 children, 3 adults***");
  	begin(3, 3, b);
	}

	public static void begin(int adults, int children, BoatGrader b) {
		// 一、将全局变量实例化；二、为每个大人或小孩创建线程，并将线程启动。
		// Store the externally generated autograder in a class
		// variable to be accessible by children.
		bg = b;
		Lib.assertTrue(children >= 2);// 小孩>=2个

		// Instantiate global variables here

		// Create threads here. See section 3.4 of the Nachos for Java
		// Walkthrough linked from the projects page.
		// 1.6 start
		num_children_O = children;
		num_alduts_O = adults;
		num_alduts_M = 0;
		num_children_M = 0;

		lock = new Lock();
		children_condition_o = new Condition(lock);
		children_condition_m = new Condition(lock);
		alduts_condition_o = new Condition(lock);

		boatInO = true;
		is_pilot = true;
		is_adult_go = false;
		gameover = false;

		// 为每位大人和小孩创建线程，并启动
		for (int i = 0; i < adults; i++) {// 每个大人为一个线程
			new KThread(new Runnable() {
				public void run() {
					AdultItinerary();
				}
			}).fork();
			;
		}

		for (int i = 0; i < children; i++) {// 每个小孩为一个线程
			new KThread(new Runnable() {
				public void run() {
					ChildItinerary();
				}
			}).fork();
			;
		}
		// 1.6 end

//	Runnable r = new Runnable() {
//	    public void run() {
//                SampleItinerary();
//            }
//        };
//        KThread t = new KThread(r);
//        t.setName("Sample Boat Thread");
//        t.fork();

	}

	static void AdultItinerary() {
		// 该方法是大人线程启动后调用的方法
		bg.initializeAdult();
		// Required for autograder interface. Must be the first thing called.
		// DO NOT PUT ANYTHING ABOVE THIS LINE.

		// 1.6 start
		lock.acquire();// 申请锁

		// 如果大人不满足过河条件或者船不在O岛，大人在O岛不走睡眠
		if (!(is_adult_go && boatInO)) {
			alduts_condition_o.sleep();
		}
		// 否则大人划至M岛
		bg.AdultRowToMolokai();
		num_alduts_M++;// M岛的大人数量+1
		num_alduts_O--;// O岛的大人数量—1
		boatInO = false;// 船改至M岛
		children_condition_m.wake();// 唤醒M岛的孩子线程
		is_adult_go = false;// 下一次船再到O岛时，必定是小孩走

		lock.release();// 释放锁
		// 1.6 end

	}

	static void ChildItinerary() {
		// 该方法是小孩线程启动后调用的方法
		bg.initializeChild(); // Required for autograder interface. Must be the first thing called.
		// DO NOT PUT ANYTHING ABOVE THIS LINE.

		// 1.6 start
		lock.acquire();// 申请锁
		while (!gameover) {
			//先将所有孩子运输到M岛
			if (boatInO) {// 如果船在O岛
				if (is_adult_go) {// 如果大人满足过河条件，能走，则将O岛的大人线程唤醒，O岛的孩子线程睡眠
					alduts_condition_o.wake();
					children_condition_o.sleep();
				}
				if (is_pilot) {// 如果是第一个小孩，则设为舵手
					bg.ChildRowToMolokai();
					num_children_O--;// O岛小孩数量-1
					num_children_M++;// M岛小孩数+1
					is_pilot = false;// 将舵手设为false
					children_condition_o.wake();// 唤醒O岛的其他小孩线程
					children_condition_m.sleep();// 让自己睡眠在M岛
				} else {// 如果是第二个小孩，则设为游客
					bg.ChildRideToMolokai();
					boatInO = false;// 将船改为在M岛
					num_children_O--;// O岛的小孩数量-1
					num_children_M++;// M岛的小孩数量+1
					is_pilot = true;// 将舵手设为true
					if (num_alduts_O == 0 && num_children_O == 0) {
						gameover = true;// 如果O岛的孩子和大人数量均为0，则游戏结束
					}
					if (gameover) {// 如果游戏结束，则打印成功过河
						System.out.println("成功过河！！！");
						children_condition_o.sleep();
					}
					if (num_alduts_O != 0 && num_children_O == 0) {// 如果O岛的大人还有，但小孩线程为0，则大人可走
						is_adult_go = true;
					}
					children_condition_m.wake();// 将M岛的其他孩子线程唤醒
					children_condition_m.sleep();// 将自己睡眠在M岛
				}
			} else// 如果船在M岛
			{
				bg.ChildRowToOahu();
				boatInO = true;// 设置船在O岛
				num_children_O++;// O岛孩子数量+1
				num_children_M--;// M岛孩子线程数量-1
				if (is_adult_go) {// 如果大人可以走，则将O岛的大人线程唤醒
					alduts_condition_o.wake();
				} else {// 否则，唤醒O岛的孩子线程
					children_condition_o.wake();
				}
				children_condition_o.sleep();// 让自己睡眠在O岛
			}

		}
		lock.release();// 释放锁

		// 1.6 end

	}

	static void SampleItinerary() {
		// Please note that this isn't a valid solution (you can't fit
		// all of them on the boat). Please also note that you may not
		// have a single thread calculate a solution and then just play
		// it back at the autograder -- you will be caught.
		System.out.println("\n ***Everyone piles on the boat and goes to Molokai***");
		bg.AdultRowToMolokai();
		bg.ChildRideToMolokai();
		bg.AdultRideToMolokai();
		bg.ChildRideToMolokai();
	}

	// 1.6 start
	// 声明变量
	static int num_children_O; // 在Oahu岛上孩子的数量
	static int num_alduts_O;// 在Oahu岛上大人的数量
	static int num_children_M;// 在Molokai岛上孩子的数量
	static int num_alduts_M;// 在Molokai岛上大人的数量

	static Lock lock;
	static Condition children_condition_o;// 孩子在Oahu岛上的条件变量
	static Condition children_condition_m;// 孩子在Molokai岛上的条件变量
	static Condition alduts_condition_o; // 大人在Oahu岛上的条件变量

	static boolean boatInO;// 船在O岛吗？
	static boolean is_pilot;// 是舵手吗？
	static boolean is_adult_go;// 大人要过河（O->M）吗
	static boolean gameover;// 所有人都过河了吗？
//1.6 end

}
