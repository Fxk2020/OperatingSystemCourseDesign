// PART OF THE MACHINE SIMULATION. DO NOT CHANGE.

package nachos.machine;

/**
 * A read-only <tt>OpenFile</tt> backed by a byte array.
 * 由字节数组支持的只读<tt> OpenFile </ tt>。
 */
public class ArrayFile extends OpenFileWithPosition {
    /**
     * Allocate a new <tt>ArrayFile</tt>.
     *
     * @param	array	the array backing this file.
     */
    public ArrayFile(byte[] array) {
	this.array = array;
    }

    public int length() {
	return array.length;
    }

    public void close() {
	array = null;
    }

    public int read(int position, byte[] buf, int offset, int length) {
	Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= buf.length);
	
	//位置不合法
	if (position < 0 || position >= array.length)
	    return 0;

	length = Math.min(length, array.length-position);
	System.arraycopy(array, position, buf, offset, length);

	return length;
    }

    public int write(int position, byte[] buf, int offset, int length) {
	return 0;
    }

    private byte[] array;
}

