// PART OF THE MACHINE SIMULATION. DO NOT CHANGE.

package nachos.machine;

/**
 * A file system that allows the user to create, open, and delete files.
 * 一个允许用户创建，打开和删除文件的文件系统。
 */
public interface FileSystem {
    /**
     * Atomically open a file, optionally creating it if it does not 
     * already exist. If the file does not
     * already exist and <tt>create</tt> is <tt>false</tt>, returns
     * <tt>null</tt>. If the file does not already exist and <tt>create</tt>
     * is <tt>true</tt>, creates the file with zero length. If the file already
     * exists, opens the file without changing it in any way.
     *
     * @param	name		the name of the file to open.
     * @param	create		<tt>true</tt> to create the file if it does not
     *				already exist.
     * @return	an <tt>OpenFile</tt> representing a new instance of the opened
     *		file, or <tt>null</tt> if the file could not be opened.
     */
    public OpenFile open(String name, boolean create);

    /**
     * Atomically remove an existing file. After a file is removed, it cannot
     * be opened until it is created again with <tt>open</tt>. If the file is
     * already open, it is up to the implementation to decide whether the file
     * can still be accessed or if it is deleted immediately.
     * 
     * 以原子方式删除现有文件。 
     * 删除文件后，除非使用<tt> open </ tt>重新创建文件，否则无法将其打开。 
     * 如果文件已经打开，则由实施来决定是否仍可以访问该文件或是否立即删除该文件。
     *
     * @param	name	the name of the file to remove.
     * @return	<tt>true</tt> if the file was successfully removed.
     */
    public boolean remove(String name);
}
