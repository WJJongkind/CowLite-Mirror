/* 
 * Copyright (C) 2018 Wessel Jelle Jongkind.
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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedList;

/**
 * This class is used for file input and output (such as copying files, deleting
 * files). Numerous methods are offered for copying and deleting files, creating
 * new files and creating a folder with all subdirectories.
 *
 * @author Wessel Jelle Jongkind
 * @version 2018-03-13 (yyyy-mm-dd)
 */
public class FileIO {

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
    public static void copy(String source, String target) throws IOException {
        copy(new File(source), new File(target));
    }

    /**
     * Copy a file from the given input filepath to the given output filepath.
     * If the output filepath is in a directory that does not yet exist, all
     * subdirectories are also automatically created.
     *
     * @param source Path to the file that should be copied.
     * @param target Path to the location of where the file should be copied to.
     * @param buff Multiplier for the buffer that should be used. The buffer has
     * a default size of 1024 bytes. A multiplier of 2 will mean the buffer size
     * will be 2048 bytes.
     * @throws java.io.IOException When the file could not be copied due to IO
     * errors.
     */
    public static void copy(String source, String target, double buff) throws IOException {
        copy(new File(source), new File(target), buff);
    }

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
    public static void copy(File source, File target) throws IOException {
        copy(source, target, DEFAULT_BUFFER_SIZE);
    }

    /**
     * Copy a file from the given source file to the given target file. If the
     * target filepath is in a directory that does not yet exist, all
     * subdirectories are also automatically created. This method uses
     * {@code java.nio.ByteBuffer} and is made for making the copying of files
     * easier.
     *
     * @param source The file that should be copied.
     * @param target The target to which the source file should be copied.
     * @param buff Multiplier for the buffer that should be used. The buffer has
     * a default size of 1024 bytes. A multiplier of 2 will mean the buffer size
     * will be 2048 bytes.
     *
     * @throws java.io.IOException When the file could not be copied due to IO
     * errors.
     */
    public static void copy(File source, File target, double buff) throws IOException {
        FileInputStream is = null;
        FileOutputStream os = null;
        FileChannel fci = null;
        FileChannel fco = null;
        try {
            createFile(target);
            is = new FileInputStream(source);
            os = new FileOutputStream(target);

            fci = is.getChannel();
            fco = os.getChannel();

            ByteBuffer buffer = ByteBuffer.allocate((int) Math.round(buff * DEFAULT_BUFFER_SIZE));

            while (true) {
                int read = fci.read(buffer);

                if (read == -1) {
                    break;
                }
                buffer.flip();
                fco.write(buffer);
                buffer.clear();
            }
        } catch (IOException e) {
            throw e;
        } finally {
            if (fci != null) {
                fci.close();
            }
            if (fco != null) {
                fco.close();
            }
            try {
                if (is != null) {
                    is.close();
                }
                if (os != null) {
                    os.close();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    /**
     * Delete the file or directory at the given {@code Path}. Does nothing if
     * there is no file at the given {@code Path}.
     *
     * @param f Path to the file that should be deleted.
     * @throws java.io.IOException When IO errors occur.
     */
    public static void delete(File f) throws IOException {
        delete(f.getAbsolutePath());
    }

    /**
     * Delete the file or directory at the given {@code Path}. Does nothing if
     * there is no file at the given {@code Path}.
     *
     * @param path Path to the file that should be deleted.
     * @throws java.io.IOException When IO errors occur.
     */
    public static void delete(String path) throws IOException {
        delete(Paths.get(path));
    }

    /**
     * Delete the given file or directory and all subdirectories. Does nothing
     * if the file does not exist.
     *
     * @param path The path that denotes the file/folder that has to be removed.
     * @throws java.io.IOException When IO errors occur.
     */
    public static void delete(Path path) throws IOException {
        LinkedList<Path> remove = new LinkedList<>();

        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                remove.add(0, dir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path dir, BasicFileAttributes attrs) throws IOException {
                remove.add(0, dir);
                return FileVisitResult.CONTINUE;
            }
        });

        for (Path p : remove) {
            Files.delete(p);
        }
    }

    /**
     * Create a folder to which the given paths lead and all subdirectories if
     * needed.
     *
     * @param path Path to the folder that should be createde.
     */
    public static void createDirectory(String path) {
        createDirectory(new File(path));
    }

    /**
     * Create a folder with the given file. All subdirectories are created if
     * needed.
     *
     * @param file File that references the folder that should be created.
     */
    public static void createDirectory(File file) {
        file.mkdirs();
    }

    /**
     * Creates the file to which the given path is pointing. Does nothing if the
     * file already exists. If a path to the file does not yet exist, then all parent
     * directories are first made.
     *
     * @param path The path at which the new file should be created.
     * @throws IOException When the file could not be created.
     */
    public static void createFile(String path) throws IOException {
        createFile(new File(path));
    }

    /**
     * Creates the file to which the given File object is pointing. Does nothing
     * if the file already exists. If a path to the file does not yet exist, then all parent
     * directories are first made.
     *
     * @param file The file that has to be created.
     * @throws IOException If the file could not be created.
     */
    public static void createFile(File file) throws IOException {
        createDirectory(file.getParentFile());
        file.createNewFile();
    }
}
