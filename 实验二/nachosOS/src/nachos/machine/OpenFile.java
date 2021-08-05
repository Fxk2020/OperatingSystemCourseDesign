// PART OF THE MACHINE SIMULATION. DO NOT CHANGE.
//机器仿真的一部分。 不要换。

package nachos.machine;

import java.io.EOFException;

/**
 * A file that supports reading, writing, and seeking.
 * 支持读取，写入和查找的文件。
 */
public class OpenFile {
    /**
     * Allocate a new <tt>OpenFile</tt> object with the specified name on the
     * specified file system.
     * 将name和FileSystem进行关联
     * @param	fileSystem	the file system to which this file belongs.
     * @param	name		the name of the file, on that file system.
     */
    public OpenFile(FileSystem fileSystem, String name) {
	this.fileSystem = fileSystem;
	this.name = name;
    }

    /**
     * Allocate a new unnamed <tt>OpenFile</tt> that is not associated with any
     * file system.
     */
    public OpenFile() {
	this(null, "unnamed");
    }

    /**
     * Get the file system to which this file belongs.
     * 获取此文件所属的文件系统。
     *
     * @return	the file system to which this file belongs.
     */
    public FileSystem getFileSystem() {
	return fileSystem;
    }
    
    /**
     * Get the name of this open file.
     *
     * @return	the name of this open file.
     */
    public String getName() {
	return name;
    }
    
    /**
     * Read this file starting at the specified position and return the number
     * of bytes successfully read. If no bytes were read because of a fatal
     * error, returns -1
     *
     * 从指定位置开始读取此文件，并返回成功读取的字节数。 
     * 如果由于致命错误而没有读取任何字节，则返回-1
     *
     * @param	pos	the offset in the file at which to start reading.
     * @param	buf	the buffer to store the bytes in.
     * @param	offset	the offset in the buffer to start storing bytes.
     * @param	length	the number of bytes to read.
     * @return	the actual number of bytes successfully read, or -1 on failure.
     */    
    public int read(int pos, byte[] buf, int offset, int length) {
	return -1;
    }
    
    /**
     * Write this file starting at the specified position and return the number
     * of bytes successfully written. If no bytes were written because of a
     * fatal error, returns -1.
     * 从指定的位置开始写文件，返回成功写入的字节数，如果没有字节成功写入，则返回-1
     * @param	pos	the offset in the file at which to start writing.
     * @param	buf	the buffer to get the bytes from.
     * @param	offset	the offset in the buffer to start getting.
     * @param	length	the number of bytes to write.
     * @return	the actual number of bytes successfully written, or -1 on
     *		failure.
     */    
    public int write(int pos, byte[] buf, int offset, int length) {
	return -1;
    }

    /**
     * Get the length of this file.
     *
     * @return	the length of this file, or -1 if this file has no length.
     */
    public int length() {
	return -1;
    }

    /**
     * Close this file and release any associated system resources.
     */
    public void close() {
    }

    /**
     * Set the value of the current file pointer.
     * 设置当前文件指针的值。
     */
    public void seek(int pos) {
    }

    /**
     * Get the value of the current file pointer, or -1 if this file has no
     * pointer.
     */
    public int tell() {
	return -1;
    }

    /**
     * Read this file starting at the current file pointer and return the
     * number of bytes successfully read. Advances the file pointer by this
     * amount. If no bytes could be* read because of a fatal error, returns -1.
     *
     * @param	buf	the buffer to store the bytes in.
     * @param	offset	the offset in the buffer to start storing bytes.
     * @param	length	the number of bytes to read.
     * @return	the actual number of bytes successfully read, or -1 on failure.
     */    
    public int read(byte[] buf, int offset, int length) {
	return -1;
    }

    /**
     * Write this file starting at the current file pointer and return the
     * number of bytes successfully written. Advances the file pointer by this
     * amount. If no bytes could be written because of a fatal error, returns
     * -1.
     *
     * @param	buf	the buffer to get the bytes from.
     * @param	offset	the offset in the buffer to start getting.
     * @param	length	the number of bytes to write.
     * @return	the actual number of bytes successfully written, or -1 on
     *		failure.
     */    
    public int write(byte[] buf, int offset, int length) {
	return -1;
    }

    private FileSystem fileSystem;
    private String name;
}
