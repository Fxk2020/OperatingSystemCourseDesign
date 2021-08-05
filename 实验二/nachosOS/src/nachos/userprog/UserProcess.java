package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.io.EOFException;
import java.util.LinkedList;

/**
 * Encapsulates the state of a user process that is not contained in its user
 * thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed.
 *
 *
 * 封装用户线程（或多个线程）中未包含的用户进程的状态。 
 * 这包括其地址转换状态，文件表以及有关正在执行的程序的信息。
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 *
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
	/**
	 * Allocate a new process.
	 */
	public UserProcess() {
		//2.3start
		pid=this.numberOfProcess++;
		this.numberOfProcess++;
		openfile[0]=UserKernel.console.openForReading();
		openfile[1]=UserKernel.console.openForWriting();
		//2.3end
		int numPhysPages = Machine.processor().getNumPhysPages();
		pageTable = new TranslationEntry[numPhysPages];
		for (int i = 0; i < numPhysPages; i++)
			pageTable[i] = new TranslationEntry(i, i, true, false, false, false);
	}

	/**
	 * Allocate and return a new process of the correct class. The class name is
	 * specified by the <tt>nachos.conf</tt> key <tt>Kernel.processClassName</tt>.
	 *
	 * @return a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
		return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to load
	 * the program, and then forks a thread to run it.
	 *
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the program was successfully executed.
	 */
	public boolean execute(String name, String[] args) {
		if (!load(name, args))
			return false;

		new UThread(this).setName(name).fork();

		return true;
	}

	/**
	 * Save the state of this process in preparation for a context switch. Called by
	 * <tt>UThread.saveState()</tt>.
	 * 
	 * 保存此过程的状态，以准备进行上下文切换。 由<tt> UThread.saveState（）</ tt>调用。
	 */
	public void saveState() {
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 * 
	 * 在上下文切换后恢复此过程的状态。 由<tt> UThread.restoreState（）</ tt>调用。
	 */
	public void restoreState() {
		Machine.processor().setPageTable(pageTable);
	}

	/**
	 * Read a null-terminated string from this process's virtual memory. Read at
	 * most <tt>maxLength + 1</tt> bytes from the specified address, search for the
	 * null terminator, and convert it to a <tt>java.lang.String</tt>, without
	 * including the null terminator. If no null terminator is found, returns
	 * <tt>null</tt>.
	 *
	 * 从该进程的虚拟内存中读取一个以空值结尾的字符串。 
	 * 从指定地址读取最多<tt> maxLength + 1 </ tt>个字节，搜索空终止符，
	 * 然后将其转换为<tt> java.lang.String </ tt>，不包括空终止符。 
	 * 如果未找到空终止符，则返回<tt> null </ tt>。
	 *
	 * @param vaddr     the starting virtual address of the null-terminated string.
	 * @param maxLength the maximum number of characters in the string, not
	 *                  including the null terminator.
	 * @return the string read, or <tt>null</tt> if no null terminator was found.
	 */
	public String readVirtualMemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength + 1];

		int bytesRead = readVirtualMemory(vaddr, bytes);

		for (int length = 0; length < bytesRead; length++) {
			if (bytes[length] == 0)
				return new String(bytes, 0, length);
		}

		return null;
	}

	/**
	 * Transfer data from this process's virtual memory to all of the specified
	 * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * 将数据从该进程的虚拟内存传输到所有指定的阵列。 
	 * 与<tt> readVirtualMemory（vaddr，data，0，data.length）</tt>相同。
	 *
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data  the array where the data will be stored.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from this process's virtual memory to the specified array. This
	 * method handles address translation details. This method must <i>not</i>
	 * destroy the current process if an error occurs, but instead should return the
	 * number of bytes successfully copied (or zero if no data could be copied).
	 * 将数据从该进程的虚拟内存传输到指定的阵列。 
	 * 此方法处理地址转换详细信息。 
	 * 如果发生错误，此方法必须<i>不</ i>销毁当前进程，
	 * 而应返回成功复制的字节数（如果无法复制数据，则返回零）。
	 *
	 * @param vaddr  the first byte of virtual memory to read.
	 * @param data   the array where the data will be stored.
	 * @param offset the first byte to write in the array.
	 * @param length the number of bytes to transfer from virtual memory to the
	 *               array.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();// 获取物理内存的引用

		// 2.2 start
		// 计算剩下的页表字节个数 (再次强调，vaddr:要读的虚拟内存的首字节)
		// length是要从虚拟内存读出到数组的字节数，
		// 总共剩下的字节 小于< length,那么length最大也就是剩下的字节数
		// 总共剩下的字节 > or = length,那么 length 就是 length
		if (length > (pageSize * numPages - vaddr))
			length = pageSize * numPages - vaddr;

		// 计算能够传输的数据的大小，如果data数组中存不下length，则减小length（传输字节数）
		// 疑问？前面不是已经保证 偏移量offset+长度length<=总的数据 数组 长度 了吗
//		if (data.length - offset < length)
//			length = data.length - offset;

		// 转换成功的字节数
		int transferredbyte = 0;
		do {
			// 计算页号
			int pageNum = Processor.pageFromAddress(vaddr + transferredbyte);
			// 页号大于 页表的长度 或者 为负 是异常情况
			if (pageNum < 0 || pageNum >= pageTable.length)
				return 0;
			// 计算页偏移量
			int pageOffset = Processor.offsetFromAddress(vaddr + transferredbyte);

			// 计算剩余页的容量
			int leftByte = pageSize - pageOffset;
			// 计算下一次传送的数量:剩余页容量和需要转移的字节数中较小者
			int amount = Math.min(leftByte, length - transferredbyte);
			// 计算物理内存的地址
			int realAddress = pageTable[pageNum].ppn * pageSize + pageOffset;
			// 将物理内存的东西传输到虚拟内存
			System.arraycopy(memory, realAddress, data, offset + transferredbyte, amount);
			// 修改传输成功的字节数
			transferredbyte = transferredbyte + amount;
		} while (transferredbyte < length);

		return transferredbyte;
		// 2.2 end

//		// for now, just assume that virtual addresses equal physical addresses
//		if (vaddr < 0 || vaddr >= memory.length)
//			return 0;
//
//		int amount = Math.min(length, memory.length - vaddr);
//		System.arraycopy(memory, vaddr, data, offset, amount);
//
//		return amount;
	}

	/**
	 * Transfer all data from the specified array to this process's virtual memory.
	 * Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 *
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data  the array containing the data to transfer.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory. This
	 * method handles address translation details. This method must <i>not</i>
	 * destroy the current process if an error occurs, but instead should return the
	 * number of bytes successfully copied (or zero if no data could be copied).
	 * 
	 * 将数据从指定的阵列传输到该进程的虚拟内存。 此方法处理地址转换详细信息。 如果发生错误，此方法必须<i>不</ i>销毁当前进程，
	 * 而应返回成功复制的字节数（如果无法复制数据，则返回零）。
	 *
	 * @param vaddr  the first byte of virtual memory to write.
	 * @param data   the array containing the data to transfer.
	 * @param offset the first byte to transfer from the array.
	 * @param length the number of bytes to transfer from the array to virtual
	 *               memory.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		// 2.2 start
		// 写内存的长度如果超过页剩余量,就要以小的页剩余量为准，不然要写入的length太长了，页剩余量不够，也写不进去啊
		if (length > (pageSize * numPages - vaddr))
			length = pageSize * numPages - vaddr;
		// 如果数组中要写的长度比给定的小，则给length减为数组剩余的长度
		// 同样的疑问？前面不是已经保证 偏移量offset+长度length<=总的数据 数组 长度 了吗
		if (data.length - offset < length)
			length = data.length - offset;

		// 转换成功的字节数
		int transferredbyte = 0;
		do {
			// 此函数返回给定地址的页号
			int pageNum = Processor.pageFromAddress(vaddr + transferredbyte);
			if (pageNum < 0 || pageNum >= pageTable.length)
				return 0;
			// 此函数返回给定地址的页偏移量
			int pageOffset = Processor.offsetFromAddress(vaddr + transferredbyte);

			// 页剩余的字节数
			int leftByte = pageSize - pageOffset;
			// 设置本次转移的数量
			int amount = Math.min(leftByte, length - transferredbyte);
			int realAddress = pageTable[pageNum].ppn * pageSize + pageOffset;
			// 从虚拟内存写入到物理内存
			System.arraycopy(data, offset + transferredbyte, memory, realAddress, amount);
			// 改变写成功的字节数
			transferredbyte = transferredbyte + amount;
		} while (transferredbyte < length);

		return transferredbyte;

		// 2.2 end

//		// for now, just assume that virtual addresses equal physical addresses
//		if (vaddr < 0 || vaddr >= memory.length)
//			return 0;
//
//		int amount = Math.min(length, memory.length - vaddr);
//		System.arraycopy(data, offset, memory, vaddr, amount);
//
//		return amount;
	}

	/**
	 * Load the executable with the specified name into this process, and prepare to
	 * pass it the specified arguments. Opens the executable, reads its header
	 * information, and copies sections and arguments into this process's virtual
	 * memory.
	 *
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the executable was successfully loaded.
	 */
	private boolean load(String name, String[] args) {
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

		OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
		if (executable == null) {
			Lib.debug(dbgProcess, "\topen failed");
			return false;
		}

		try {
			coff = new Coff(executable);
		} catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			return false;
		}

		// make sure the sections are contiguous and start at page 0
		numPages = 0;
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			if (section.getFirstVPN() != numPages) {
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
				return false;
			}
			numPages += section.getLength();
		}

		// make sure the argv array will fit in one page
		byte[][] argv = new byte[args.length][];
		int argsSize = 0;
		for (int i = 0; i < args.length; i++) {
			argv[i] = args[i].getBytes();
			// 4 bytes for argv[] pointer; then string plus one for null byte
			argsSize += 4 + argv[i].length + 1;
		}
		if (argsSize > pageSize) {
			coff.close();
			Lib.debug(dbgProcess, "\targuments too long");
			return false;
		}

		// program counter initially points at the program entry point
		initialPC = coff.getEntryPoint();

		// next comes the stack; stack pointer initially points to top of it
		numPages += stackPages;
		initialSP = numPages * pageSize;

		// and finally reserve 1 page for arguments
		numPages++;

		if (!loadSections())
			return false;

		// store arguments in last page
		int entryOffset = (numPages - 1) * pageSize;
		int stringOffset = entryOffset + args.length * 4;

		this.argc = args.length;
		this.argv = entryOffset;

		for (int i = 0; i < argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
			stringOffset += argv[i].length;
			Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
			stringOffset += 1;
		}

		return true;
	}

	/**
	 * Allocates memory for this process, and loads the COFF sections into memory.
	 * If this returns successfully, the process will definitely be run (this is the
	 * last step in process initialization that can fail).
	 * 
	 * 为此过程分配内存，并将COFF节加载到内存中。 如果此操作成功返回，则肯定会运行该进程（这是进程初始化的最后一步，可能会失败）。
	 *
	 * @return <tt>true</tt> if the sections were successfully loaded.
	 */
	protected boolean loadSections() {

		// 2.2 start
		UserKernel.allocateMemoryLock.acquire();// 获取分配的内存的锁
		// 2.2 end allocateMemoryLock

		if (numPages > Machine.processor().getNumPhysPages()) {// 页数量大于实际物理内存的页数量
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			return false;
		}

		// 2.2 start
		pageTable = new TranslationEntry[numPages];// 实例化页表
		// 一共numpages个页，要把页 装入到 物理地址
		for (int i = 0; i < numPages; i++) {
			// 从空闲物理页号链表中拿出一个
			int nextPage = (int) UserKernel.memoryLinkedList.remove();
			// virtual page number,physical page number,valid,readOnly,used,dirty
			pageTable[i] = new TranslationEntry(i, nextPage, true, false, false, false);
		}
		UserKernel.allocateMemoryLock.release();// 释放分配的内存的锁
		// 2.2 end

		// load sections
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess,
					"\tinitializing " + section.getName() + " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;
				// 2.2
				pageTable[vpn].readOnly = section.isReadOnly();// 标记为只读
				// for now, just assume virtual addresses=physical addresses
				// 装入物理页
				section.loadPage(i, pageTable[vpn].ppn);
				// 2.2

				// for now, just assume virtual addresses=physical addresses
//				section.loadPage(i, vpn);
			}
		}

		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 * 
	 * 释放资源
	 * 
	 */
	protected void unloadSections() {
	}

	/**
	 * Initialize the processor's registers in preparation for running the program
	 * loaded into this process. Set the PC register to point at the start function,
	 * set the stack pointer register to point at the top of the stack, set the A0
	 * and A1 registers to argc and argv, respectively, and initialize all other
	 * registers to 0.
	 * 
	 * 初始化处理器的寄存器，以准备运行加载到此进程中的程序。 将PC寄存器设置为指向启动功能，将堆栈指针寄存器设置为指向堆栈的顶部，
	 * 将A0和A1寄存器分别设置为argc和argv，并将所有其他寄存器初始化为0。
	 */
	public void initRegisters() {
		Processor processor = Machine.processor();

		// by default, everything's 0
		for (int i = 0; i < processor.numUserRegisters; i++)
			processor.writeRegister(i, 0);

		// initialize PC and SP according
		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);

		// initialize the first two argument registers to argc and argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
	}

	/**
	 * Handle the halt() system call. 处理halt（）系统调用。
	 */
	private int handleHalt() {

		// 2.1 start
		if (pid == 0)// 只有root进程才能停机
		// 2.1 end
			Machine.halt();

		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}

	// 2.1 start
	/*
	 * create(): 先从内存中将文件名读出，然后利用文件系统的打开文件。 
	 * 利用不存在就创建的方法，在物理磁盘中创建文件。
	 * 
	 * 文件名存在--打开文件数目没有到达16--可以create
	 */
	private int handleCreate(int fileAddress) {
		// 处理create()系统调用，创建一个文件，返回文件描述符
		// 读出文件名
		String filename = readVirtualMemoryString(fileAddress, 256);// 文件名长度不得超过256字符
		if (filename == null)
			return -1; // 文件名不存在，创建失败
		// 在openfile中找空的位置
		int fileDescriptor = findEmpty();
		if (fileDescriptor == -1)
			return -1; // fileDescriptor=-1进程打开文件数已经达到上限，无法创建并打开
		
		/* 执行到此处fileDescriptor=openfile为空位的下标，可以创建文件 */
		// 创建
		else {
			// 创建一个文件，并且将此时的openfile[fileDescriptor]中放入相应的描述符
			openfile[fileDescriptor] = ThreadedKernel.fileSystem.open(filename, true);// 文件不存在直接创建
			// 返回文件描述符
			return fileDescriptor;
		}
	}

	/*
	 * open（）： 先从内存中将文件名读出，然后利用文件系统的打开文件。 利用不存在直接返回null，仅打开
	 * 
	 * 文件名存在--打开文件数目没有到达16--可以open
	 */
	private int handleOpen(int fileAddress) {
		// 处理open()的系统调用，打开一个文件
		String filename = readVirtualMemoryString(fileAddress, 256);
		if (filename == null)
			return -1; // 文件名不存在
		// 在openfile中找空的位置
		int fileDescriptor = findEmpty();
		if (fileDescriptor == -1)
			return -1; // 打开文件数已经达到上限，无法打开
		/* 执行到此处说明可以打开文件 */
		else {
			openfile[fileDescriptor] = ThreadedKernel.fileSystem.open(filename, false);
			return fileDescriptor;// 打开成功返回文件描述符
		}
	}

	/*
	 * read(): 使用打开文件描述符，利用文件系统的读方法将数据 从文件中 读到数组中， 然后使用内存写操作，写入内存。返回写入的数量 int
	 * fileDescriptor:利用文件描述符-打开文件-读出length字节 int bufferAddress:使用内存写操作，写入内存 int
	 * length:要读取的字节数，要写入的字节数 return : 写入的字节数目。
	 */
	private int handleRead(int fileDescriptor, int bufferAddress, int length) {
		// 处理read()的系统调用，从文件中读出数据写入 指定虚拟地址
		// 检查给定的文件描述符
		if (fileDescriptor > 15 || fileDescriptor < 0 || openfile[fileDescriptor] == null)
			return -1; // 文件未打开，出错
		byte temp[] = new byte[length];
		// 读文件
		int readNumber = openfile[fileDescriptor].read(temp, 0, length);
		if (readNumber <= 0)
			return 0; // 没有读出数据
		int writeNumber = writeVirtualMemory(bufferAddress, temp);
		return writeNumber;
	}

	/*
	 * write(): 使用打开文件描述符，利用内存读操作将数据从内存中读到数组中， 然后使用文件写操作，写入文件。返回写入的数量
	 */
	private int handleWrite(int fileDescriptor, int bufferAddress, int length) {
		// 处理write()的系统调用，将指定虚拟内存地址的数据写入文件
		if (fileDescriptor > 15 || fileDescriptor < 0 || openfile[fileDescriptor] == null)
			return -1; // 文件未打开，出错
		byte temp[] = new byte[length];
		// 读出虚拟内存地址中的数据到temp中
		int readNumber = readVirtualMemory(bufferAddress, temp);
		// 数据读出后保存在temp中
		if (readNumber <= 0)
			return 0; // 未读出数据
		// 将Temp中的数据 写入文件
		int writeNumber = openfile[fileDescriptor].write(temp, 0, length);
		if (writeNumber < length)
			return -1;// 未完全写入，出错
		return writeNumber;
	}

	/*
	 * close(): 使用文件描述符，将文件描述符指向的文件 利用文件系统的关闭方法关闭
	 * 
	 */
	private int handleClose(int fileDescriptor) {
		// 处理close()的系统调用，用于关闭打开的文件
		if (fileDescriptor > 15 || fileDescriptor < 0 || openfile[fileDescriptor] == null)
			return -1; // 文件不存在，关闭出错
		openfile[fileDescriptor].close();
		openfile[fileDescriptor] = null;
		return 0;
	}

	/*
	 * unlink(): 先从内存中将文件名读出，利用文件系统的删除操作将文件从物理磁盘中删除
	 */
	private int handleUnlink(int fileAddress) {
		// 处理unlink的系统调用，用于删除文件
		// 获得文件名
		String filename = readVirtualMemoryString(fileAddress, 256);
		if (filename == null)
			return 0; // 文件不存在，不必删除
		if (ThreadedKernel.fileSystem.remove(filename))// 删除磁盘中实际的文件
			return 0;// 成功删除
		else
			return -1;
	}

	/**
	 * function: 从openfile中找到一个空的文件描述符位。
	 * 
	 * @return 数组下标,如果没有空的，则返回-1
	 */
	private int findEmpty() {
		for (int i = 0; i < 16; i++) {
			if (openfile[i] == null)
				return i;
		}
		return -1;
	}
	// 2.1 end

	/**
	 * 系统调用状态位
	 */
	private static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2, syscallJoin = 3, syscallCreate = 4,
			syscallOpen = 5, syscallRead = 6, syscallWrite = 7, syscallClose = 8, syscallUnlink = 9;

	/**
	 * Handle a syscall exception. Called by <tt>handleException()</tt>. The
	 * <i>syscall</i> argument identifies which syscall the user executed:
	 * 
	 * 处理系统调用异常。 由<tt> handleException（）</ tt>调用。 
	 * <i> syscall </ i>参数标识用户执行了哪个系统调用
	 *
	 * <table>
	 * <tr>
	 * <td>syscall#</td>
	 * <td>syscall prototype</td>
	 * </tr>
	 * <tr>
	 * <td>0</td>
	 * <td><tt>void halt();</tt></td>
	 * </tr>
	 * <tr>
	 * <td>1</td>
	 * <td><tt>void exit(int status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>2</td>
	 * <td><tt>int  exec(char *name, int argc, char **argv);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>3</td>
	 * <td><tt>int  join(int pid, int *status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>4</td>
	 * <td><tt>int  creat(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>5</td>
	 * <td><tt>int  open(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>6</td>
	 * <td><tt>int  read(int fd, char *buffer, int size);
	 *								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>7</td>
	 * <td><tt>int  write(int fd, char *buffer, int size);
	 *								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>8</td>
	 * <td><tt>int  close(int fd);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>9</td>
	 * <td><tt>int  unlink(char *name);</tt></td>
	 * </tr>
	 * </table>
	 * 
	 * @param syscall the syscall number.
	 * @param a0      the first syscall argument.
	 * @param a1      the second syscall argument.
	 * @param a2      the third syscall argument.
	 * @param a3      the fourth syscall argument.
	 * @return the value to be returned to the user.
	 */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		switch (syscall) {
		case syscallHalt:
			return handleHalt();
		case syscallExit:
			return handleExit(a0);
		case syscallExec :
			return handleExec(a0,a1,a2);
		case syscallJoin :
			return handleJoin(a0,a1);
		case syscallCreate :
			return handleCreate(a0);
		case syscallOpen :
			return handleOpen(a0);
		case syscallRead:
			return handleRead(a0,a1,a2);
		case syscallWrite:
			return handleWrite(a0,a1,a2);
		case syscallClose :
			return handleClose(a0);
		case syscallUnlink :
			return handleUnlink(a0);
		
		//不存在的系统调用
		default:
			Lib.debug(dbgProcess, "Unknown syscall " + syscall);
			Lib.assertNotReached("Unknown system call!");
		}
		return 0;
	}

	// 2.3 start
	// 实现创建子进程并执行的功能
	/**
	 * 
	 * @param fileAddress 文件地址
	 * @param argc        参数个数
	 * @param argvAddress 参数地址
	 * @return
	 */
	private int handleExec(int fileAddress, int argc, int argvAddress) {
		// 读虚拟内存获得文件名
		String filename = readVirtualMemoryString(fileAddress, 256);
		// 判断合法性:得到的文件名不能为空，参数个数不能小于0，地址不能小于0，地址不能超过总的容量
		if (filename == null || argc < 0 || argvAddress < 0 || argvAddress > numPages * pageSize)
			return -1;
		// args 参数数组:将由参数地址-物理地址-得到的内容-存到args里面
		String[] args = new String[argc];
		// 将文件内容读入虚拟内存
		for (int i = 0; i < argc; i++)// 对于 参数的数量 argc i=0~argc-1
		{
			// 先从argvAddress及其后续偏移地址分别取出 参数的地址
			// 放入argsAddress（byte类型数组中），再从该地址数组中拿地址取得相应参数得string值
			byte[] argsAddress = new byte[4];// ？

			// *4的原因:前四个字节存的是指针
			// argvAddress作为参数表数组的首址,读取虚拟内存地址
			if (readVirtualMemory(argvAddress + i * 4, argsAddress) > 0)// 从地址读出，存入argsAddress
				// argsAddress中保存了读出的参数
				// 依次读出每个参数
				args[i] = readVirtualMemoryString(Lib.bytesToInt(argsAddress, 0), 256);
		}
		// 创建子进程，将文件和参数标加载到子进程
		UserProcess process = UserProcess.newUserProcess();
		// 创建并执行子进程
		if (!process.execute(filename, args))// 文件打开失败，退出
			return -1;
		// 将这个子进程的父进程置为该进程
		process.parentProcess = this;// 将当前父进程信息赋予子进程
		// 将子进程加入到子进程表中（是此父进程的子进程表）
		childProcess.add(process);
		return process.pid;
	}

	private int handleJoin(int pid, int statusAddress) {
		UserProcess process = null;
		// 遍历子进程链表，确定join的进程是子进程
		for (int i = 0; i < childProcess.size(); i++) {
			if (pid == childProcess.get(i).pid)// 如果子进程是当前运行程序则返回
			{
				process = childProcess.get(i);
				break;
			}
		}
		if (process == null)
			return -1;
		// 获得join锁，让该进程休眠，直到子进程唤醒
		// 得到锁，保持互斥
		process.joinLock.acquire();// 得到锁
		// 在该线程
		process.joinCondition.sleep();// 进程睡眠，加入等待队列等待被唤醒
		process.joinLock.release();
		byte[] childstat = new byte[4];
		// 取出子进程的运行状态
		childstat = Lib.bytesFromInt(process.status);
		// 将子进程的状态存入内存中，判断当前进程是否正常结束
		int numWriteByte = writeVirtualMemory(statusAddress, childstat);
		if (process.normalExit && numWriteByte == 4)
			return 1;
		return 0;
	}

	private int handleExit(int status) {
		coff.close();// 关闭coff区，因为即将退出该进程
		// 判断是否在该子进程openfile中有打开的文件
		for (int i = 0; i < 16; i++) {
			if (openfile[i] != null) {
				// 无内容可以写，关闭
				openfile[i].close();// 如果有则关闭
				openfile[i] = null;// 并且把位置内容置为null
			}
		}

		this.status = status;// 把状态置入(打开或关闭)
		normalExit = true;// 正常退出
		// 如果有父进程，就从父进程的子进程链表删除，而且如果父进程中join子进程，则唤醒父进程
		if (parentProcess != null) {
			joinLock.acquire();// 实现互斥
			joinCondition.wake();
			joinLock.release();
			parentProcess.childProcess.remove(this);
		}
		// 释放内存
		unloadSections();
		if (numOfRunningProcess == 1)
			Machine.halt();
		numOfRunningProcess--;
		KThread.currentThread().finish();
		return 0;
	}

	// 2.3 end

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>.
	 * The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 *处理用户异常。 由<tt> UserKernel.exceptionHandler（）</ tt>调用。 
	 *<i> cause </ i>参数标识发生了哪个异常，请参见<tt> Processor.exceptionZZZ </ tt>常量。
	 * @param cause the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
		case Processor.exceptionSyscall:
			int result = handleSyscall(processor.readRegister(Processor.regV0), processor.readRegister(Processor.regA0),
					processor.readRegister(Processor.regA1), processor.readRegister(Processor.regA2),
					processor.readRegister(Processor.regA3));
			processor.writeRegister(Processor.regV0, result);
			processor.advancePC();
			break;

		default:
			Lib.debug(dbgProcess, "Unexpected exception: " + Processor.exceptionNames[cause]);
			Lib.assertNotReached("Unexpected exception");
		}
	}

	/**
	 * The program being run by this process. 该过程正在运行的程序
	 */
	protected Coff coff;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;// 程序的页表数目——物理页数目和虚拟页数目的转换
	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	//程序堆栈中的页数。
	protected final int stackPages = 8;

	private int initialPC, initialSP;
	private int argc, argv;

	private static final int pageSize = Processor.pageSize;
	private static final char dbgProcess = 'a';

	// 2.1 start
	protected int pid = 0;// 这是UserKernel中的相当于进程计数
	protected OpenFile openfile[] = new OpenFile[16];// 每个进程最多16个同时打开的文件
	// 2.1 end

	// 2.3 start
	/** UserProcess的父进程 */
	private UserProcess parentProcess;

	LinkedList<UserProcess> childProcess = new LinkedList<UserProcess>();
	private KThread thread;
	private Lock joinLock = new Lock();
	Condition joinCondition = new Condition(joinLock);
	private static int numOfRunningProcess = 0;
	public boolean normalExit;// 退出状态，是否正常退出
	public int status = 0;// 进程运行的状态 (打开/关闭)
	private static int numberOfProcess = 0;
	// 2.3 end

}
