package nachos.vm;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Random;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {
	/**
	 * Allocate a new process.
	 */
	public VMProcess() {
		super();
		if (kernel == null) {
			try {
				kernel = (VMKernel) ThreadedKernel.kernel;
			} catch (ClassCastException cce) {
				
			}
		}
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	@Override
	public void saveState() {
		//Propagate the effect of the memory accesses
		kernel.propagateAndFlushTLB(true);
	}

	/**
	 * We need this override so it doesn't push the page table back 
	 * into the processor.
	 */
	@Override
	public void restoreState() {}

	/**
	 * Initializes page tables for this process so that the executable can be
	 * demand-paged.
	 * 
	 * Thunk the <tt>CoffSection</tt>s and the stack pages. The arguments are thunked in a later method.
	 * 
	 * @return <tt>true</tt> if successful.
	 */
	@Override
	protected boolean loadSections() {
		//装入coff sections
		int topVPN = 0;
		for (int sectionNumber = 0; sectionNumber < coff.getNumSections(); sectionNumber++) {
			CoffSection section = coff.getSection(sectionNumber);

			CoffConstructor constructor;

			
			topVPN += section.getLength();
			for (int i = section.getFirstVPN(); i < topVPN; i++) {
				constructor = new CoffConstructor(section, i);
				thunkedSections.put(i, constructor);
			}
		}

		//加载所有的物理页
		for (; topVPN < numPages - 1; topVPN++)
			thunkedSections.put(topVPN, new StackConstructor(topVPN));

		return true;
	}
	
	@Override
	protected void unloadSections() {
		kernel.freePages(PID, numPages);
	}

	@Override
	protected void loadArguments(int entryOffset, int stringOffset, byte[][] argv) {
		thunkedSections.put(numPages - 1, new ArgConstructor(entryOffset, stringOffset, argv));
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exception</tt> constants.
	 * 
	 * @param cause
	 *            the user exception that occurred.
	 */
	@Override
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
		case Processor.exceptionTLBMiss:
			//并在processor.regBadVAddr寄存器中读到发生页错误的虚拟内存页号
			handleTLBMiss(processor.readRegister(processor.regBadVAddr));
			break;
		default:
			super.handleException(cause);
			break;
		}
	}

	/**
	 * 处理与虚拟地址关联的TLB未命中。
	 * @param bad vaddr
	 */
	public void handleTLBMiss(int vaddr) {
		
		if (!validAddress(vaddr)) {
			
		} else {
			
			TranslationEntry retrievedTE = retrievePage(Processor.pageFromAddress(vaddr));
			
//			System.out.println("我在监听是否发生页面置换");
			
			boolean unwritten = true;
			//使所有指向新物理页面的条目无效
			//最好查找一个无效（即为空）条目并将其替换。 否则，请使用随机替换。
			Processor p = Machine.processor();
			for (int i = 0; i < p.getTLBSize() && unwritten; i++) {
				TranslationEntry tlbTranslationEntry = p.readTLBEntry(i);

				//如果条目匹配则使该条目无效
				if (tlbTranslationEntry.ppn == retrievedTE.ppn) {
					if (unwritten) {
						p.writeTLBEntry(i, retrievedTE);
						unwritten = false;
					} else if (tlbTranslationEntry.valid) {
						tlbTranslationEntry.valid = false;
						p.writeTLBEntry(i, tlbTranslationEntry);
					}
				} else if (unwritten && !tlbTranslationEntry.valid) {
					p.writeTLBEntry(i, retrievedTE);
					unwritten = false;
				}
			}

			//如果我们尚未将其写入TLB，请使用随机替换政策
			if (unwritten) {
				int randomIndex = generator.nextInt(p.getTLBSize());
				TranslationEntry oldEntry = p.readTLBEntry(randomIndex);
				
				//将信息传播到内存中				
				if (oldEntry.dirty || oldEntry.used)
					//什么时候置脏位
					kernel.propagateEntry(oldEntry.ppn, oldEntry.used, oldEntry.dirty);
				
				p.writeTLBEntry(randomIndex, retrievedTE);
			}
			
			//取消固定物理页面
			kernel.unpin(retrievedTE.ppn);
		}
	}

	/** 用于为TLB替换生成随机数的生成器。 */
	public Random generator = new Random();

	/**
	* 当进程需要访问一个内存地址时，
	* 通过retrievePage这个方法来获取内存页条目。
	* 先从内核中判断虚拟内存页号是否已经存在在页表中。
	* 如果存在，就标记占用并返回，若不存在则页错误，抛出异常
	 */
	public TranslationEntry retrievePage(int vpn) {
		TranslationEntry returnEntry = null;

		
		if (thunkedSections.containsKey(vpn))
			returnEntry = thunkedSections.get(vpn).execute();
		else if ((returnEntry = kernel.pinIfExists(vpn, PID)) == null)
			
			returnEntry = kernel.pageFault(vpn, PID);

		Lib.assertTrue(returnEntry != null);
		return returnEntry;
	}

	@Override
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		int bytesRead = 0;
		LinkedList<VMMemoryAccess> memoryAccesses = createMemoryAccesses(vaddr, data, offset, length, AccessType.READ, true);

		//Execute them if they were successfully created
		if (memoryAccesses != null) {
			int temp;
			for (VMMemoryAccess vma : memoryAccesses) {
				temp = vma.executeAccess();
				if (temp == 0)
					break;
				else
					bytesRead += temp;
			}
		}

		return bytesRead;
	}

	@Override
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		return writeVirtualMemory(vaddr, data, offset, length, true);
	}

	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length, boolean unpin) {
		int bytesWritten = 0;
		LinkedList<VMMemoryAccess> memoryAccesses = createMemoryAccesses(vaddr, data, offset, length, AccessType.WRITE, unpin);

		//Execute them if they were successfully created
		if (memoryAccesses != null) {
			int temp;
			for (VMMemoryAccess vma : memoryAccesses) {
				temp = vma.executeAccess();
				if (temp == 0)
					break;
				else
					bytesWritten += temp;
			}
		}

		return bytesWritten;
	}

	public int writeVirtualMemory(int vaddr, byte[] data, boolean unpin) {
		return VMProcess.this.writeVirtualMemory(vaddr, data, 0, data.length, unpin);
	}

	public LinkedList<VMMemoryAccess> createMemoryAccesses(int vaddr, byte[] data, int offset, int length, AccessType accessType, boolean unpin) {
		Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);
		LinkedList<VMMemoryAccess> returnList  = null;

		if (validAddress(vaddr)) {
			returnList = new LinkedList<VMMemoryAccess>();

			while (length > 0) {
				int vpn = Processor.pageFromAddress(vaddr);

				int potentialPageAccess = Processor.pageSize - Processor.offsetFromAddress(vaddr);
				int accessSize = length < potentialPageAccess ? length : potentialPageAccess;

				returnList.add(new VMMemoryAccess(accessType, data, vpn, offset, Processor.offsetFromAddress(vaddr), accessSize, unpin));
				length -= accessSize;
				vaddr += accessSize;
				offset += accessSize;
			}
		}

		return returnList;
	}

	public static final int pageSize = Processor.pageSize;
	public static final char dbgProcess = 'a';
	public static final char dbgVM = 'v';

	/** 为方便起见，对VMKernel的引用。 */
	public static VMKernel kernel = null; 

	public HashMap<Integer,Constructor> thunkedSections = new HashMap<Integer,Constructor>();

	protected class VMMemoryAccess extends UserProcess.MemoryAccess {
		VMMemoryAccess(AccessType at, byte[] d, int _vpn, int dStart, int pStart, int len, boolean _unpin) {
			super(at,d,_vpn,dStart,pStart,len);
			unpin = _unpin;
		}

		@Override
		public int executeAccess() {
			//用新的覆盖entryEntry
			translationEntry = retrievePage(vpn);//页面应固定

			int bytesAccessed = super.executeAccess();

			//完成后取消固定页面
			if (unpin)
				kernel.unpin(translationEntry.ppn);

			return bytesAccessed;
		}

		/** A bit to indicate whether the access should unpin the page when it is finished. */
		public boolean unpin;
	}

	/**
	 * 用来初始化页面初始化的类
	 */
	public abstract class Constructor {
		abstract TranslationEntry execute();
	}

	public class CoffConstructor extends Constructor {
		CoffConstructor(CoffSection ce, int vpn1) {
			coffSection = ce;
			vpn = vpn1;
		}

		@Override
		TranslationEntry execute() {
			
			int sectionNumber = vpn - coffSection.getFirstVPN();
			Lib.assertTrue(thunkedSections.remove(vpn) != null);
			
			//获取空的页面
			TranslationEntry returnEntry = kernel.requestFreePage(vpn, PID);
			coffSection.loadPage(sectionNumber, returnEntry.ppn);
			
			returnEntry.readOnly = coffSection.isReadOnly() ? true : false;

			return returnEntry;
		}

		public CoffSection coffSection;
		public int vpn;
	}

	public class StackConstructor extends Constructor {
		StackConstructor(int vpn1) {
			vpn = vpn1;
		}

		@Override
		TranslationEntry execute() {
			
			Lib.assertTrue(thunkedSections.remove(vpn) != null);

			TranslationEntry te = kernel.requestFreePage(vpn, PID);
			te.readOnly = false;
			return te;
		}

		public int vpn;
	}

	public class ArgConstructor extends Constructor {
		ArgConstructor(int _entryOffset, int _stringOffset, byte[][] _argv) {
			entryOffset = _entryOffset; stringOffset = _stringOffset; argv = _argv;
		}

		@Override
		TranslationEntry execute() {
			Lib.assertTrue(thunkedSections.remove(numPages - 1) != null);

			TranslationEntry te = kernel.requestFreePage(numPages - 1, PID);//get a free page

			//该页面已固定，因此只需使用writeVM即可加载信息
			for (int i = 0; i < argv.length; i++) {
				byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
				Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes, false) == 4);
				entryOffset += 4;
				Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i], false) == argv[i].length);
				stringOffset += argv[i].length;
				Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }, false) == 1);
				stringOffset += 1;
			}
			
			te.readOnly = true;
			
			return te;
		}

		public int entryOffset, stringOffset;
		public byte[][] argv;
	}
}
