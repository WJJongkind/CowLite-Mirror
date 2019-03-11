/* 
 * Copyright (C) 2019 Wessel Jelle Jongkind.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package cowlite.mirror;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * This interface defines several methods for file input and output (such as copying files, deleting
 * files). Numerous methods are offered for copying and deleting files, creating
 * new files and creating a folder with all subdirectories.
 *
 * @author Wessel Jelle Jongkind
 * @version 2019-03-10 (yyyy-mm-dd)
 */
public interface FileService {
    
    /**
     * The default and minimum size of the buffer. The size of this buffer can
     * be multiplied by any double, however it is recommended that only whole
     * numbers are used.
     */
    public static final int DEFAULT_BUFFER_SIZE = 1024;
    
    /**
     * Copy a file from the given input filepath to the given output filepath.
     * If the output filepath is in a directory that does not yet exist, all
     * subdirectories are also automatically created.
     *
     * @param source Path to the file that should be copied.
     * @param target Path to the location of where the file should be copied to.
     * @throws java.io.IOException When the file could not be copied due to IO
     * errors.
     */
    public void copy(String source, String target) throws IOException;

    /**
     * Copy a file from the given input filepath to the given output filepath.
     * If the output filepath is in a directory that does not yet exist, all
     * subdirectories are also automatically created.
     *
     * @param source Path to the file that should be copied.
     * @param target Path to the location of where the file should be copied to.
     * @param bufferSize The size of the buffer that is used for transferring the file, in megabytes.
     * @throws java.io.IOException When the file could not be copied due to IO
     * errors.
     */
    public void copy(String source, String target, int bufferSize) throws IOException;

    /**
     * Copy a file from the given source file to the given target file. If the
     * target filepath is in a directory that does not yet exist, all
     * subdirectories are also automatically created.
     *
     * @param source The file that should be copied.
     * @param target The target to which the source file should be copied.
     * @throws java.io.IOException When the file could not be copied due to IO
     * errors.
     */
    public void copy(File source, File target) throws IOException;

    /**
     * Copy a file from the given source file to the given target file. If the
     * target filepath is in a directory that does not yet exist, all
     * subdirectories are also automatically created. This method uses
     * {@code java.nio.ByteBuffer} and is made for making the copying of files
     * easier.
     *
     * @param source The file that should be copied.
     * @param target The target to which the source file should be copied.
     * @param bufferSize The size of the buffer that is used for transferring the file, in megabytes.
     *
     * @throws java.io.IOException When the file could not be copied due to IO
     * errors.
     */
    public void copy(File source, File target, int bufferSize) throws IOException;

    /**
     * Delete the file or directory at the given {@code Path}. Does nothing if
     * there is no file at the given {@code Path}.
     *
     * @param f Path to the file that should be deleted.
     * @throws java.io.IOException When IO errors occur.
     */
    public void delete(File f) throws IOException;

    /**
     * Delete the file or directory at the given {@code Path}. Does nothing if
     * there is no file at the given {@code Path}.
     *
     * @param path Path to the file that should be deleted.
     * @throws java.io.IOException When IO errors occur.
     */
    public void delete(String path) throws IOException;

    /**
     * Delete the given file or directory and all subdirectories. Does nothing
     * if the file does not exist.
     *
     * @param path The path that denotes the file/folder that has to be removed.
     * @throws java.io.IOException When IO errors occur.
     */
    public void delete(Path path) throws IOException;

    /**
     * Create a folder to which the given paths lead and all subdirectories if
     * needed.
     *
     * @param path Path to the folder that should be createde.
     */
    public void createDirectory(String path);

    /**
     * Create a folder with the given file. All subdirectories are created if
     * needed.
     *
     * @param file File that references the folder that should be created.
     */
    public void createDirectory(File file);

    /**
     * Creates the file to which the given path is pointing. Does nothing if the
     * file already exists. If a path to the file does not yet exist, then all parent
     * directories are first made.
     *
     * @param path The path at which the new file should be created.
     * @throws IOException When the file could not be created.
     */
    public void createFile(String path) throws IOException;

    /**
     * Creates the file to which the given File object is pointing. Does nothing
     * if the file already exists. If a path to the file does not yet exist, then all parent
     * directories are first made.
     *
     * @param file The file that has to be created.
     * @throws IOException If the file could not be created.
     */
    public void createFile(File file) throws IOException;
}
