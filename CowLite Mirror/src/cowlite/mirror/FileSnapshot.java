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
 * MA 02110-1301 USA
 */
package cowlite.mirror;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.spi.FileSystemProvider;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/**
 * Objects of this class represent a 'snapshot' of a directory. The main
 * functionality is to check whether a directory has changed since the last time
 * the {@link #update(List, List, List)} method has been called and for
 * comparison to other directories by using the
 * {@link #compareTo(FileSnapshot, List, List)} method.
 *
 * <p>
 *
 * This class heavily relies on the {@code java.nio} package and also provides
 * some access to File-specific data that would otherwise be obtained through
 * that package. However, the main functionality of {@code FileSnapshot} remains
 * monitoring files and directories and their subdirectories, to see if files
 * have been added, altered or deleted.
 *
 * <p>
 *
 * {@code FileSnapshot}s can also be stored to a {@code File} so that they can
 * be used for comparison at a later time, either when the stored
 * {@code FileSnapshot} becomes unavailable in runtime or when the application
 * is temporarily shut down. This can be done with the {@link #store(File)}
 * method, which will then go through the snapshot and all subdirectories and
 * stores them. Currently, no functionality is implemented to decompile the
 * stored {@code FileSnapshot}s. However, this functionality will be implemented
 * soon.
 *
 * <h1>Basic usage</h1> {@code FileSnapshot} can be used to monitor directories
 * for changes. In the example below, the C:\ disk on a Windows machine is
 * monitored. In this example, we first initialize the {@code FileSnapshot}
 * object and then immediatly update it without recording any changes:
 *
 * <pre><i>
 *      FileSnapshot snapshot = new FileSnapshot(Paths.get("C:\\"));
 *
 *      // Update the snapshot, which will walk through all subdirectories.
 *      snapshot.update(null, null, null);
 * </i></pre>
 *
 * Then, we wait for 5 minutes and update the record with three
 * {@code ArrayList}s so that creation, alteration and deletion events are
 * recorded:
 *
 * <pre><i>
 *      try {
 *          Thread.sleep(300000);
 *      } catch(Exception e) {}
 *
 *      // Create lists in which all events are stored
 *      ArrayList&#60;FileSnapshot&#62; deleted = new ArrayList&#60;&#62;();
 *      ArrayList&#60;FileSnapshot&#62; added = new ArrayList&#60;&#62;();
 *      ArrayList&#60;FileSnapshot&#62; modified = new ArrayList&#60;&#62;();
 *
 *      // Update the snapshot and record events
 *      snapshot.update(deleted, added, modified);
 * </i></pre>
 *
 * The {@code FileSnapshot} can also be compared to another
 * {@code FileSnapshot}. If there is another disk, D:\, then we can compare the
 * difference in files between C:\ and D:\. In the example below we compare the
 * {@code FileSnapshot} of above to a new {@code FileSnapshot}:
 *
 * <pre><i>
 *      FileSnapshot otherSnap = new FileSnapshot(Paths.get("D:\\"));
 *
 *      // Update the snapshot, so that all subdirectories are added to the structure
 *      otherSnap.update(null, null, null);
 *
 *      // Lists to store the difference between the directories in
 *      // Missing is for FileSnapshots that are missing in the C:\ snapshot, garbage is
 *      // for FileSnapshots that are missing in D:\ otherSnap.
 *      ArrayList&#60;FileSnapshot&#62; missing = new ArrayList&#60;&#62;();
 *      ArrayList&#60;FileSnapshot&#62; garbage = new ArrayList&#60;&#62;();
 *
 *      // Compare the two FileSnapshots
 *      snapshot.compareTo(otherSnap, missing, garbage);
 * </i></pre>
 *
 * @author Wessel Jelle Jongkind
 * @version 2018-03-13 (yyyy/mm/dd)
 */
public class FileSnapshot {

    /**
     * Obtain one single FileSystemProvider for all {@code FileSnapshot}s. This
     * should reduce the memory footprint. The FileSystemProvider is used for
     * obtaining {@code FileAttribute}s such as filesize, last modified date and
     * creation date.
     */
    private static final FileSystemProvider FS = FileSystems.getDefault().provider();

    /**
     * The {@code Path} object that denotes the directory which is represented
     * by the {@code FileSnapshot}.
     */
    private final Path file;

    /**
     * The time at which the directory which is represented by the
     * {@code FileSnapshot} was last modified, as recorded by the last
     * {@link #update(List, List, List)} call.
     */
    private FileTime modifiedTime;

    /**
     * The size of the directory or file that is represented by the
     * {@code FileSnapshot}, as recorded by the last
     * {@link #update(List, List, List)} call.
     */
    private long size;

    /**
     * {@code true} if the {@code FileSnapshot} represents a directory,
     * {@code false} if the {@code FileSnapshot} represents a file.
     */
    private boolean isDirectory;

    /**
     * All subdirectories of the directory that is referenced by the
     * {@code FileSnapshot} represented as a {@code Map}.
     */
    private final HashMap<Path, FileSnapshot> children;

    /**
     * The {@code Path} object that denotes the directory or file that is being
     * monitored by the {@code FileSnapshot}.
     */
    private final Path name;

    /**
     * Instantiates a new {@code FileSnapshot} object which only obtains data of
     * the {@code Path} object that was passed as a parameter. Use
     * {@link #update(List, List, List)} to also index all subdirectories of the
     * {@code Path} that is passed as aparameter.
     *
     * @param p The {@code Path} object of which a {@code FileSnapshot} should
     * be created.
     * @throws IOException When the directory denoted by the {@code Path} does
     * not exist or IO errors occur.
     * @throws IllegalArgumentException When the {@code Path} that is given as a
     * parameter is null.
     */
    public FileSnapshot(Path p) throws IOException, IllegalArgumentException {
        if (p == null) {
            throw new IllegalArgumentException("Path can not be null.");
        }

        this.file = p;
        this.name = p.getFileName();
        this.children = new HashMap<>();

        BasicFileAttributes attributes = FS.readAttributes(p, BasicFileAttributes.class);
        this.modifiedTime = attributes.lastModifiedTime();
        this.isDirectory = attributes.isDirectory();
        this.size = attributes.size();
    }

    public FileSnapshot(String path) throws IOException, IllegalArgumentException {
        this(Paths.get(new File(path).toURI()));
    }

    public FileSnapshot(File f) throws IOException, IllegalArgumentException {
        this(Paths.get(f.toURI()));
    }

    /**
     * Updates the {@code FileSnapshot} and all subdirectories so that their
     * metadata such as filesize, last modified date and creationdate are up to
     * date. If new folders have been added to the directory or one of the
     * subdirectories monitored by the {@code FileSnapshot} then these
     * directories are added to the monitoring-structure as well.
     * <p>
     * Any changes that occured in the monitored directory or subdirectories can
     * also be recorded by passing on {@code java.util.Lists} as parameters.
     *
     * @param deleted The list in which deletion events are recorded by adding
     * {@code FileSnapshot}s to the list of which the monitored directory or
     * file has been removed. If this parameter is null, then deletion events
     * are not recorded.
     * @param added The list in which creation events are recorded by adding
     * {@code FileSnapshot}s to the list of files/directories that have been
     * newly discovered during {@link #update(List, List, List)} calls. If this
     * parameter is null, then creation events are not recorded.
     * @param updated The list in which modification events are recorded by
     * adding {@code FileSnapshot}s to the list of which the monitored directory
     * or file has been altered. If this parameter is null, then modification
     * events are not recorded. If a file has turned into a folder or vice
     * versa, then it is also added to this list.
     * @throws IOException When the {@code FileSnapshot} could not be updated.
     * @see #checkChildren(List, List, List)
     */
    public void update(List<FileSnapshot> deleted, List<FileSnapshot> added, List<FileSnapshot> updated) throws IOException {
        /*
            The file represented by this snapshot doesn't exist. This means
            that it has most likely been removed. Adding only this file to the
            deleted list will suffice, as we can infer from that that the children
            are also deleted.
         */
        if (!checkAccess() && deleted != null) {
            deleted.add(this);
            return;
        }

        // Obtain the new file metadata
        BasicFileAttributes attributes = FS.readAttributes(file, BasicFileAttributes.class);
        FileTime modifiedTime = attributes.lastModifiedTime();
        boolean isDirectory = attributes.isDirectory();
        long newSize = attributes.size();

        if (isDirectory) {
            if (!this.isDirectory && updated != null) {
                updated.add(this);
            }

            updateChildren(deleted, added, updated);
        } else if (!this.modifiedTime.equals(modifiedTime) || newSize != size) {
            if (this.isDirectory) {
                children.clear();
            }

            if (updated != null) {
                updated.add(this);
            }
        }

        this.isDirectory = isDirectory;
        this.modifiedTime = modifiedTime;
        this.size = newSize;
    }

    /**
     * A stripped-down version of the
     * {@code java.nio.Files#exists(Path, LinkOption...)} method. As
     * checkAccess() will be called often, a slight performance boost was
     * obtained by making a stripped down version of the previously described
     * method.
     *
     * @return {@code true} if the file/directory that is monitored still
     * exists.
     */
    private boolean checkAccess() {
        try {
            FS.checkAccess(file);
            return true;
        } catch (Exception e) {
            System.out.println(new Date() + "-------" + "Access DENIED!      " + file);
            e.printStackTrace();
            return false;
        }
    }

    /**
     * This method is called when the {@code FileSnapshot} is monitoring a
     * directory. All subdirectories and files of which {@code FileSnapshot}s
     * exist are updated. For subdirectories and files of which no
     * {@code FileSnapshot}s exist, new {@code FileSnapshot}s are created and
     * immediatly updated.
     *
     * @param deleted The list in which deletion events are recorded by adding
     * {@code FileSnapshot}s to the list of which the monitored directory or
     * file has been removed. If this parameter is null, then deletion events
     * are not recorded.
     * @param added The list in which creation events are recorded by adding
     * {@code FileSnapshot}s to the list of files/directories that have been
     * newly discovered during {@link #update(List, List, List)} calls. If this
     * parameter is null, then creation events are not recorded.
     * @param updated The list in which modification events are recorded by
     * adding {@code FileSnapshot}s to the list of which the monitored directory
     * or file has been altered. If this parameter is null, then modification
     * events are not recorded.
     * @throws IOException When the {@code FileSnapshot} could not be updated.
     * @see #update(List, List, List)
     */
    private void updateChildren(List<FileSnapshot> deleted, List<FileSnapshot> added, List<FileSnapshot> updated) throws IOException {
        // This list is used  to keep track of the subdirectories that were deleted
        HashMap<Path, FileSnapshot> deletedChildren = new HashMap<>(children);
        DirectoryStream<Path> stream = null;

        try {
            stream = Files.newDirectoryStream(file);
            for (Path p : stream) {
                Path fileName = p.getFileName();
                FileSnapshot snapshot = children.get(fileName);

                // If the snapshot exists, update it. If it does not, create a new one.
                if (snapshot != null) {
                    snapshot.update(deleted, added, updated);
                    deletedChildren.remove(fileName); // The child is still there, therefore we can  remove it from deletedChildren
                } else {
                    FileSnapshot newSnapshot = new FileSnapshot(p);
                    children.put(newSnapshot.getName(), newSnapshot);

                    if (added != null) {
                        added.add(newSnapshot);
                    }

                    newSnapshot.update(deleted, added, updated);
                }
            }
        } catch (IOException e) {
            throw e;
        } finally {
            if (stream != null) {
                stream.close();
            }
        }

        // Remove items from the children HashMap that were not found with java.io.File's listFiles() method
        Collection<FileSnapshot> deletedSet = deletedChildren.values();
        children.values().removeAll(deletedSet);

        // Add the removed items to the deleted list, if it exists.
        if (deleted != null) {
            deleted.addAll(deletedSet);
        }
    }

    /**
     * Compares this FileSnapshot to the given FileSnapshot.
     *
     * @param other The FileSnapshot to compare with.
     * @param missing A list in which the FileSnapshots are stored that are
     * present in the given FileSnapshot's subdirectories but not in this
     * FileSnapshot's subdirectories. When two FileSnapshots that represent the
     * same file are not equal, they are being added to this list as well.
     * @param garbage A list in which the FileSnapshots are stored that are
     * present in this FileSnapshot's subdirectories, but not present in the
     * given FileSnapshot's subdirectories.
     */
    public void compareTo(FileSnapshot other, List<FileSnapshot> missing, List<FileSnapshot> garbage) {
        // No need to run the method if there are no children to this FileSnapshot
        if (children.isEmpty()) {
            missing.addAll(other.children.values());
        }

        // Required for iteration
        Set<Path> childrenNames = children.keySet();

        // Make a soft-copy so we can keep track of which FileSnapshots are missing
        HashMap<Path, FileSnapshot> otherChildren = new HashMap<>(other.children);

        for (Path p : childrenNames) {
            FileSnapshot child = otherChildren.get(p);
            if (child != null) {
                FileSnapshot myChild = children.get(p);

                // FileSnapshots are not the same, add to missing list.
                if (myChild.isDirectory() != child.isDirectory() || myChild.getSize() != child.getSize()) {
                    System.out.println("Adding " + child.getFile() + " as missing");
                    missing.add(child);
                }

                myChild.compareTo(child, missing, garbage);
                otherChildren.remove(p);
            } else {
                System.out.println("Adding " + p + " as garbage");
                garbage.add(children.get(p));
            }
        }

        // Add all missing Filesnapshots to the missing list
        missing.addAll(otherChildren.values());
    }

    /**
     * Stores the {@code FileSnapshot} and all {@code FileSnapshot}s that
     * represent subdirectories and their files to the specified {@code File}.
     * The data is compiled by writing the data of a single {@code FileSnapshot}
     * to each line of the file. Each line is formatted as [absolute
     * path]||[last modified date]||[file length]. These values are equal to
     * those returned by {@link #getFile()}, {@link #getModifiedTime()} and
     * {@link #getSize()}.
     *
     * For decompiling files, see {@link #decompile(File)}.
     *
     * @param f The file to which to compile the {@code FileSnapshot}
     * @throws FileNotFoundException When the file to which the
     * {@code FileSnapshot} has to be stored can not be found or the parent
     * folder does not exist.
     * @throws IOException When IO exceptions occur.
     * @see #decompile(File)
     */
    public void store(File f) throws FileNotFoundException, IOException {
        PrintWriter out = null;

        try {
            out = new PrintWriter(f, "UTF-8");

            out.println(file + "||" + modifiedTime.toMillis() + "||" + size);

            for (FileSnapshot child : children.values()) {
                child.store(out);
            }
        } catch (FileNotFoundException e) {
            throw e;
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }

    /**
     * Store the {@code FileSnapshot} if a {@code PrintWriter} has already been
     * created for storing the file.
     *
     * @param out The {@code PrintWriter} used to write lines to the output
     * file.
     */
    private void store(PrintWriter out) {
        out.println(file + "||" + modifiedTime.toMillis() + "||" + size);

        for (FileSnapshot child : children.values()) {
            child.store(out);
        }
    }

    /**
     * Decompiles the given {@code File} to a {@code FileSnapshot} structure or
     * throws a {@code IOException} if the given file could not be decompiled.
     *
     * @param f The file to be decompiled.
     * @return A {@code FileSnapshot} which contains all subdirectories that
     * were found in the given file.
     */
    public static FileSnapshot decompile(File f) {
        throw new UnsupportedOperationException("Still has to be implemented...");
    }

    /**
     * Returns the {@code Path} object which is being monitored by the
     * {@code FileSnapshot} object that this method is being called on.
     *
     * @return The {@code Path} object which is being monitored by the
     * {@code FileSnapshot} object that this method is being called on.
     */
    public Path getFile() {
        return file;
    }

    /**
     * Returns {@code true} if the {@code FileSnapshot} is monitoring a
     * directory.
     *
     * @return {@code true} if the {@code FileSnapshot} is monitoring a
     * directory.
     */
    public boolean isDirectory() {
        return isDirectory;
    }

    /**
     * Returns the {@code Path} object that denotes the name of the file or
     * folder that is being monitored by the {@code FileSnapshot} object.
     *
     * @return The {@code Path} object that denotes the name of the file or
     * folder that is being monitored by the {@code FileSnapshot} object.
     */
    public Path getName() {
        return name;
    }

    /**
     * Returns the last known size of the object that is being monitored. This
     * is the size that was obtained during the last
     * {@link #update(List, List, List)} call.
     *
     * @return The last known size of the object that is being monitored. This
     * is the size that was obtained during the last
     * {@link #update(List, List, List)} call.
     */
    public long getSize() {
        return size;
    }

    /**
     * Returns the time at which the file that is represented by the
     * {@code FileSnapshot} was last modified prior to the previous
     * {@link #update(List, List, List)} call in milliseconds.
     *
     * @return The time at which the file that is represented by the
     * {@code FileSnapshot} was last modified prior to the previous
     * {@link #update(List, List, List)} call in milliseconds.
     */
    public long getModifiedTime() {
        return modifiedTime.toMillis();
    }

    /**
     * Returns the children of the {@code FileSnapshot}, which are
     * subdirectories and files contained by the directory that the
     * {@code FileSnapshot} represents.
     *
     * @return The children of the {@code FileSnapshot}, which are
     * subdirectories and files contained by the directory that the
     * {@code FileSnapshot} represents.
     */
    public Collection<FileSnapshot> getChildren() {
        return children.values();
    }
}
