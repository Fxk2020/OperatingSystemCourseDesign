package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

/**
 * A UThread is KThread that can execute user program code inside a user
 * process, in addition to Nachos kernel code.
 * 
 * UThread是KThread，除了Nachos内核代码外，它还可以在用户进程内执行用户程序代码。
 */ 
public class UThread extends KThread {
    /**
     * Allocate a new UThread.
     */
    public UThread(UserProcess process) {
	super();

	setTarget(new Runnable() {
		public void run() {
		    runProgram();
		}
	    });

	this.process = process;
    }

    private void runProgram() {
	process.initRegisters();
	process.restoreState();

	Machine.processor().run();
	
	Lib.assertNotReached();
    }
    
    /**
     * Save state before giving up the processor to another thread.
     * 在将处理器放弃到另一个线程之前，请保存状态。
     */
    protected void saveState() {
	process.saveState();

	for (int i=0; i<Processor.numUserRegisters; i++)
	    userRegisters[i] = Machine.processor().readRegister(i);

	super.saveState();
    }

    /**
     * Restore state before receiving the processor again.
     * 恢复状态，然后再次接收处理器。
     */      
    protected void restoreState() {
	super.restoreState();
	
	for (int i=0; i<Processor.numUserRegisters; i++)
	    Machine.processor().writeRegister(i, userRegisters[i]);
	
	process.restoreState();
    }

    /**
     * Storage for the user register set.
     *
     * <p>
     * A thread capable of running user code actually has <i>two</i> sets of
     * CPU registers: one for its state while executing user code, and one for
     * its state while executing kernel code. While this thread is not running,
     * its user state is stored here.
     * 
     * 能够运行用户代码的线程实际上具有<i>两组</ i> CPU寄存器：一组用于执行用户代码时的状态，
     * 另一组用于执行内核代码时的状态。
     * 当此线程未运行时，其用户状态存储在此处。
     */
    public int userRegisters[] = new int[Processor.numUserRegisters];

    /**
     * The process to which this thread belongs.
     * 该线程所属的进程。
     */
    public UserProcess process;
}
