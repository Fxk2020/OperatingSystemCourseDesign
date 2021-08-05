package nachos.vm;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedList;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

/**
 * A kernel that can support multiple demand-paging user processes.
 */
public class VMKernel extends UserKernel {
	/**
	 * Allocate a new VM kernel.
	 */
	public VMKernel() {
		super();

		//Initialize the coremap by physical page number
		for (int i = 0; i < coremap.length; i++)
			coremap[i] = new MemoryEntry(i);
	}

	/**
	 * Initialize this kernel.
	 */
	@Override
	public void initialize(String[] args) {
		super.initialize(args);
		memoryLock = new Lock();
		allPinned = new Condition(memoryLock);
		swap = new Swap();
	}

	/**
	 * Start running user programs.
	 */
	@Override
	public void run() {
		super.run();
	}

	/**
	 * Terminate this kernel. Never returns.
	 */
	@Override
	public void terminate() {
		// Delete the swap file
		swap.cleanup();
		
		super.terminate();
	}

	/**
	 * LRU置换算法-二次机会算法
	 * @return ppn
	 */
	private MemoryEntry clockAlgorithm() {
		memoryLock.acquire();
		while (pinnedCount == coremap.length) allPinned.sleep();

		/*
		 * We only need to flush before we start the search.
		 */
		propagateAndFlushTLB(false);
		
		// When we get here, there MUST be a non-pinned page to find
		while (true) {
			clockHand = (clockHand+1) % coremap.length;
			MemoryEntry page = coremap[clockHand];

			// 跳过固定页面
			if (page.pinned)
				continue;

			// 优先选择无效页面
			if (page.processID == -1 || page.translationEntry.valid == false)
				break;

			// 如果最近使用继续
			if (page.translationEntry.used) {
				page.translationEntry.used = false;
			}
			// Otherwise use this page!
			else {
				if (page.translationEntry.valid) {
					
				}
			    
				break;
			}
		}

		MemoryEntry me = coremap[clockHand];
		pinnedCount++;//我们不必检查它是否已固定，因为此时仅应将其取消固定
		me.pinned = true;

		//对TLB更新
		invalidateTLBEntry(clockHand);

		//从页表中删除映射
		MemoryEntry me1 = null;
		if (me.processID > -1)
			me1 = invertedPageTable.remove(new TableKey(me.translationEntry.vpn, me.processID));

		memoryLock.release();

		//如果页面已存在于内存中，请在此处换出页面
		if (me1 != null) swap.swapOut(me);

		return me;
	}

	/**
	 * 使用第n次机会算法来释放物理页面，执行必要的交换。
     * 将适当的映射插入coremap和反向页面表中。
     * 该页面将被固定。
	 * @param vpn
	 * @param pid
	 * @return TranslationEntry of the newly freed page
	 */
	TranslationEntry requestFreePage(int vpn, int pid) {
		// 选择交换页面
		MemoryEntry page = clockAlgorithm();

		// 给交换页面清零
		int pageBeginAddress = Processor.makeAddress(page.translationEntry.ppn, 0);
		Arrays.fill(Machine.processor().getMemory(), pageBeginAddress, pageBeginAddress + Processor.pageSize, (byte) 0);

		
		page.translationEntry.vpn = vpn;
		page.translationEntry.valid = true;
		page.processID = pid;

		// 加入反向页表
		insertIntoTable(vpn, pid, page);

		return page.translationEntry;
	}

	//插入到反向页表
	private void insertIntoTable(int vpn, int pid, MemoryEntry page) {
		memoryLock.acquire();
		invertedPageTable.put(new TableKey(vpn, pid), page);
		memoryLock.release();
	}

	/**
	 *当请求的页面不再位于反向页面表中时调用。
  	 *将该页面交换到内存（如果存在）中，并更新coremap并反转
  	 *页表以反映此更改。
	 *该页面将被固定。
	 * The page will be pinned.
	 * @param vpn
	 * @param pid
	 * @return TranslationEntry of the newly swapped page, or null if not found
	 */
	TranslationEntry pageFault(int vpn, int pid) {
		if (!swap.pageInSwap(vpn, pid))
			return null;
		TranslationEntry te = requestFreePage(vpn, pid);
		swap.swapIn(vpn, pid, te.ppn);

		return te;
	}

	/**
	 * 清除内存中的所有页面。
	 * <p>
	 * Purge all swapped pages from the given process from the swap file.
	 * @param vpn
	 * @param pid
	 */
	void freePages(int pid, int maxVPN) {
		memoryLock.acquire();
		for (MemoryEntry page : coremap)
			if (page.processID == pid) {
				// Remove from inverted page table
				invertedPageTable.remove(new TableKey(page.translationEntry.vpn, page.processID));

				// Invalidate coremap entry
				page.processID = -1;
				page.translationEntry.valid = false;
			}

		memoryLock.release();
		
		swap.freePages(maxVPN, pid);
	}

	/**
	 * 取消固定与物理页码相对应的页面。
	 * @param ppn
	 */
	void unpin(int ppn) {
		memoryLock.acquire();
		MemoryEntry me = coremap[ppn];

		if (me.pinned)
			pinnedCount--;

		me.pinned = false;

		allPinned.wake();

		memoryLock.release();
	}

	/**
	 * 将页面固定（如果存在）。
	 * @param vpn
	 * @param pid
	 * @return true if the page exists (and was pinned)
	 */
	TranslationEntry pinIfExists(int vpn, int pid) {
		MemoryEntry me = null;
		memoryLock.acquire();

		if ((me = invertedPageTable.get(new TableKey(vpn, pid))) != null) {
			
			if (!me.pinned)
				pinnedCount++;
			me.pinned = true;
		}

		memoryLock.release();

		if (me == null)
			return null;
		else
			return me.translationEntry;
	}

	/**
	 * 将TLB中的已用位和脏位传播到相应的物理页面中。
  		<p>
  			通过将所有条目标记为无效来刷新TLB。
  		<p>
      必须在禁用中断的情况下调用。
	 */
	void propagateAndFlushTLB(boolean flush) {
		for (int i = 0; i < Machine.processor().getTLBSize(); i++) {
			TranslationEntry te = Machine.processor().readTLBEntry(i);

			if (te.valid) {
				TranslationEntry translationEntry = coremap[te.ppn].translationEntry;
				if (translationEntry.valid && translationEntry.vpn == te.vpn) {
					
					translationEntry.used |= te.used;
					translationEntry.dirty |= te.dirty;
				}
			}

			if (flush) {
				te.valid = false;
				Machine.processor().writeTLBEntry(i, te);
			}
		}
	}

	/**
	 * 为给定的ppn设置TLB条目的无效位
	 */
	void invalidateTLBEntry(int ppn) {
		for (int i = 0; i < Machine.processor().getTLBSize(); i++) {
			TranslationEntry te = Machine.processor().readTLBEntry(i);
			if (te.valid && te.ppn == ppn) {
				te.valid = false;
				Machine.processor().writeTLBEntry(i, te);
				break;
			}
		}
	}
	
	//传播
	void propagateEntry(int ppn, boolean used, boolean dirty) {
		memoryLock.acquire();
		TranslationEntry te = coremap[ppn].translationEntry;
		te.used |= used;
		te.dirty |= dirty;
		memoryLock.release();
	}

	private static final char dbgVM = 'v';

	/** 物理内存页号标号的内存条目coremap */
	private MemoryEntry[] coremap = new MemoryEntry[Machine.processor().getNumPhysPages()];

	/** 在时钟算法中使用的当前页面的持久索引 */
	private int clockHand = 0;

	/** 从vaddr，PID到PPN的映射的反向页表 */
	private Hashtable<TableKey,MemoryEntry> invertedPageTable = new Hashtable<TableKey,MemoryEntry>();

	/** A lock  */
	private Lock memoryLock;

	/** 固定的内存条目数。 */
	private int pinnedCount;

	/** 如果没有未固定的页面，则所有进程都要等待的条件。*/
	private Condition allPinned;

	/**一个内部类，用作反向页面表的键。 */
	private static class TableKey {
		TableKey(int vpn1, int pid1) {
			vpn = vpn1;
			pid = pid1;
		}

		@Override
		public int hashCode() {
			return Processor.makeAddress(vpn, pid );
		}

		@Override
		public boolean equals(Object x) {
			if (this == x)
				return true;
			else if (x instanceof TableKey) {
				TableKey xCasted = (TableKey)x;
				return vpn.equals(xCasted.vpn) && pid.equals(xCasted.pid);
			} else {
				return false;
			}
		}

		private Integer vpn, pid;
	}

	/** 表示coremap中条目的类 */
	private static class MemoryEntry {
		MemoryEntry (int ppn) {
			translationEntry = new TranslationEntry(-1, ppn, false, false, false, false);
		}

		TranslationEntry translationEntry;
		int processID = -1;
		boolean pinned = false;
	}

	/**
	 * @return 打开交换文件
	 */
	protected OpenFile openSwapFile() {
		return fileSystem.open("swapfile", true);
	}

	private class Swap {
		Swap() {
			swapFile = openSwapFile();
		}

		/** 
		 * 将物理页面写入交换文件（如果尚未交换）或脏文件
         * 注意：为安全起见，应固定物理页
		 */
		void swapOut(MemoryEntry me) {
			if (me.translationEntry.valid) {
				
				SwapEntry swapEntry = null;
				TableKey tk = new TableKey(me.translationEntry.vpn, me.processID);

				swapLock.acquire();
				if (me.translationEntry.dirty || !swapTable.containsKey(tk)) {
					// 使用空缺职位（如果有）
					if (freeList.size() > 0) {
						swapEntry = freeList.removeFirst();
						swapEntry.readOnly = me.translationEntry.readOnly;
					}
					// 否则扩展交换文件
					else {
//						System.out.println("发生页面置换");
						swapEntry = new SwapEntry(maxTableEntry++, me.translationEntry.readOnly); 
					}

					swapTable.put(tk, swapEntry);
				}
				swapLock.release();

				if (swapEntry != null) {
					// 写物理页面
					Lib.assertTrue(swapFile.write(swapEntry.swapPageNumber * Processor.pageSize,
							Machine.processor().getMemory(),
							me.translationEntry.ppn * Processor.pageSize,
							Processor.pageSize) == Processor.pageSize);
				}
			}
		}
		
		private int maxTableEntry = 0;

		/** 
		 * 从交换文件读取虚拟页面并将其写入物理内存
         * 注意：为安全起见，应固定物理页
		 */
		void swapIn(int vpn, int pid, int ppn) {
			swapLock.acquire();
			SwapEntry swapEntry = swapTable.get(new TableKey(vpn, pid));
			swapLock.release();
			
			if (swapEntry != null) {
				//读物理页
				Lib.assertTrue(swapFile.read(swapEntry.swapPageNumber * Processor.pageSize,
						Machine.processor().getMemory(),
						ppn * Processor.pageSize,
						Processor.pageSize) == Processor.pageSize);

				
				coremap[ppn].translationEntry.readOnly = swapEntry.readOnly;
			}
		}

		/**
		 * @return 如果给定页面在交换文件中，则为True
		 */
		boolean pageInSwap(int vpn, int pid) {
			swapLock.acquire();
			boolean retBool = swapTable.containsKey(new TableKey(vpn, pid));
			swapLock.release();
			return retBool;
		}

		/**
		 * 与该过程关联的交换文件中的空闲页面条目，因此可以重复使用。
         *（免费列表上的“位置”页面）
         * @param maxVPN：进程中最高的VPN + 1
		 */
		void freePages(int maxVPN, int pid) {
			swapLock.acquire();
			SwapEntry freeEntry;
			for (int i = 0; i < maxVPN; i++)
				if ((freeEntry = swapTable.get(new TableKey(i, pid))) != null)
					freeList.add(freeEntry);
			swapLock.release();
		}

		/**
		 * 关闭并删除交换文件
		 */
		void cleanup() {
			swapFile.close();
			fileSystem.remove(swapFile.getName());
		}

		/** 对包含换出页面的文件的引用
         * 该文件分为页面大小的块，这些块由SwapEntries索引
		 */
		private OpenFile swapFile;

		/** 交换文件中当前未使用的列表 */
		private LinkedList<SwapEntry> freeList = new LinkedList<SwapEntry>();

		/** 流程页面之间的映射以及它们在交换文件中的位置 */
		private HashMap<TableKey, SwapEntry> swapTable = new HashMap<TableKey, SwapEntry>();

		/** A <tt>Lock</tt> */
		private Lock swapLock = new Lock();
		
		/** 表示交换文件中交换页面位置的类 */
		private class SwapEntry {
			SwapEntry (int spn, boolean ro) {
				swapPageNumber = spn;
				readOnly = ro;
			}
			int swapPageNumber;
			boolean readOnly;
		}
	}

	private Swap swap;
}
