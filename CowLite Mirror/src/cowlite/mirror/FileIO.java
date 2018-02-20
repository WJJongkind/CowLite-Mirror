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
import java.util.HashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class is used for file input and output (such as copying files, deletingfiles). Numerous
 * methods are offered for copying and deleting files, creating new files, creating a folder with all
 * subdirectories etc.
 * @author Wessel Jongkind
 * @version 2018-02-18
 */
public class FileIO {
    /**
     * The default and minimum size of the buffer. It is highly recommended that this
     * buffer size or a multiple of it is used.
     */
    public static final int DEFAULT_BUFFER_SIZE = 1024;
    
    /**
     * Maximum amount of attempts to remove files.
     */
    private static final int MAX_ATTEMPTS = 10;
    
    /**
     * Copy a file from the given input filepath to the given output filepath. If
     * the output filepath is in a directory that does not yet exist, all subdirectories are
     * also automatically created.
     * @param source Path to the file that should be copied.
     * @param target Path to the location of where the file should be copied to.
     */
    public static void copy(String source, String target) {
        copy(new File(source), new File(target));
    }
    
    /**
     * Copy a file from the given input filepath to the given output filepath. If
     * the output filepath is in a directory that does not yet exist, all subdirectories are
     * also automatically created.
     * @param source Path to the file that should be copied.
     * @param target Path to the location of where the file should be copied to.
     * @param buff Multiplier for the buffer that should be used. The buffer has a size of
     *             8192 bytes. A multiplier of 2 will  mean the buffer size will be 16384 bytes.
     */
    public static void copy(String source, String target, int buff) {
        copy(new File(source), new File(target), buff);
    }
    
    /**
     * Copy a file from the given source file to the given target file. If
     * the target filepath is in a directory that does not yet exist, all subdirectories are
     * also automatically created.
     * @param source The file that should be copied.
     * @param target The target to which the source file should be copied.
     */
    public static void copy(File source, File target) {
        copy(source, target, DEFAULT_BUFFER_SIZE);
    }
    
    /**
     * Copy a file from the given source file to the given target file. If
     * the target filepath is in a directory that does not yet exist, all subdirectories are
     * also automatically created.
     * @param source The file that should be copied.
     * @param target The target to which the source file should be copied.
     * @param buff Multiplier for the buffer that should be used. The buffer has a size of
     *             8192 bytes. A multiplier of 2 will  mean the buffer size will be 16384 bytes.
     */
    public static void copy(File source, File target, int buff) {
        FileInputStream is = null;
        FileOutputStream os = null;
        FileChannel fci = null;
        FileChannel fco = null;
        try{
            is = new FileInputStream(source);
            os = new FileOutputStream(target);

            fci = is.getChannel();
            fco = os.getChannel();

            ByteBuffer buffer = ByteBuffer.allocate(buff * DEFAULT_BUFFER_SIZE);

            while (true) {
                int read = fci.read(buffer);

                if (read == -1)
                    break;
                buffer.flip();
                fco.write(buffer);
                buffer.clear();
            }
        }catch(Exception e){
            e.printStackTrace();
        } finally {
            if(is != null)
                try {
                    is.close();
            } catch (IOException ex) {
                Logger.getLogger(FileIO.class.getName()).log(Level.SEVERE, null, ex);
            }
            if(os != null)
                try {
                    os.close();
            } catch (IOException ex) {
                Logger.getLogger(FileIO.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    
    /**
     * Delete the file at the given filepath. Does nothing if there is no file at
     * the given filepath.
     * @param path Path to the file that should be deleted.
     */
    public static void delete(String path) {
        delete(new File(path));
    }
    
    /**
     * Delete the given file. Does nothing if the file does not exist.
     * @param file The file to be deleted.
     */
    public static void delete(File file) {
        for(int i = 0; i < MAX_ATTEMPTS && file.exists(); i++) {
            file.delete();
        }
    }
    
    /**
     * Create a folder to which the given paths lead and all subdirectories if needed.
     * @param path Path to the folder that should be createde.
     */
    public static void createDirectory(String path) {
        createDirectory(new File(path));
    }
    
    /**
     * Create a folder with the given file. All subdirectories are created if needed.
     * @param file File that references the folder that should be created.
     */
    public static void createDirectory(File file) {
        file.mkdirs();
    }
    
    private static class FastReader extends Thread {
        private final FileInputStream in;
        private final HashMap<byte[], Integer> blocks;
        private final ConcurrentLinkedQueue<byte[]> queue;
        private final ConcurrentLinkedQueue<byte[]> empty;
        private FastWriter writer;
        private final CountDownLatch latch;
        
        public FastReader(HashMap<byte[], Integer> blocks, ConcurrentLinkedQueue<byte[]> queue, ConcurrentLinkedQueue<byte[]> empty, FileInputStream in, CountDownLatch latch) {
            this.in = in;
            this.queue = queue;
            this.empty = empty;
            this.blocks = blocks;
            this.latch = latch;
        }
        
        @Override
        public void run() {
            int length = 0;
            do{
                try{
                    while(empty.isEmpty()) {
                        if(writer.getState() == Thread.State.WAITING) {
                            synchronized(writer) {
                                writer.notify();
                            }
                        } else {
                            synchronized(this) {
                                wait();
                            }
                        }
                    }
                    
                    byte[] buffer = empty.peek();
                    length = in.read(buffer);
                    empty.remove(buffer);
                    blocks.replace(buffer, length);
                    queue.add(buffer);
                    

                    synchronized(writer) {
                        if(writer.getState() == Thread.State.WAITING)
                            writer.notify();
                    }
                }catch(Exception e) {
                    length = 0;
                    e.printStackTrace();
                }
            }while(length > 0);
            latch.countDown();
        }
        
        public void setFastWriter(FastWriter writer) {
            this.writer = writer;
        }
    }
    
    private static class FastWriter extends Thread {
        private final HashMap<byte[], Integer> blocks;
        private final ConcurrentLinkedQueue<byte[]> queue;
        private final ConcurrentLinkedQueue<byte[]> empty;
        private final FileOutputStream out;
        private FastReader reader;
        private final CountDownLatch latch;
        
        public FastWriter(HashMap<byte[], Integer> blocks, ConcurrentLinkedQueue<byte[]> queue, ConcurrentLinkedQueue<byte[]> empty, FileOutputStream out, CountDownLatch latch) {
            this.blocks = blocks;
            this.out = out;
            this.queue = queue;
            this.empty = empty;
            this.latch = latch;
        }
        
        @Override
        public void run() {
            int length = 0;
            do{
                try{
                    while(queue.isEmpty()) {
                        if(reader.getState() == Thread.State.WAITING) {
                            synchronized(reader) {
                                reader.notify();
                            }
                        } else {
                            synchronized(this) {
                                wait();
                            }
                        }
                    }
                    
                    //System.out.println("q" + queue.isEmpty());
                    byte[] buffer = queue.peek();
                    length = blocks.get(buffer);
                    
                    if(length > 0) {
                        out.write(buffer, 0, blocks.get(buffer));
                        queue.remove(buffer);
                        empty.add(buffer);
                    }
                    
                    synchronized(reader) {
                        if(reader.getState() == Thread.State.WAITING)
                            reader.notify();
                    }
                }catch(Exception e) {
                    e.printStackTrace();
                    length = 0;
                }
            }while(length > 0);
            latch.countDown();
        }
        
        public void setFastReader(FastReader reader) {
            this.reader = reader;
        }
    }
}
