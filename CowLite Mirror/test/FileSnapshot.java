/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */


import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import static java.nio.file.Files.size;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/**
 *
 * @author Wessel
 */
public class FileSnapshot 
{
    private final Path file;
    private static final FileSystemProvider provider = FileSystems.getDefault().provider();
    private BasicFileAttributes attributes;
    private FileTime creationTime;
    private FileTime modifiedTime;
    private long size;
    private boolean directory;
    private final HashMap<Path, FileSnapshot> children;
    private final Path name;
    
    public static void main(String[] args) throws Exception {
        Path p = Paths.get("E:\\");
        Files.getLastModifiedTime(p);
        FileSystemProvider provider = FileSystems.getDefault().provider();
        BasicFileAttributes attributes = provider.readAttributes(p, BasicFileAttributes.class);
       // Files.isDirectory(p, options)
    }
    
    public FileSnapshot(Path p) throws IOException {
        this.file = p;
        this.children = new HashMap<>();
        this.attributes = provider.readAttributes(p, BasicFileAttributes.class);
        this.creationTime = attributes.creationTime();
        this.modifiedTime = attributes.lastModifiedTime();
        this.name = p.getFileName();
        this.directory = attributes.isDirectory();
        this.size = attributes.size();
    }
    
    public void update(List<FileSnapshot> deleted, List<FileSnapshot> added, List<FileSnapshot> updated) throws IOException  {
        /*
            The file represented by this snapshot doesn't exist, add it to the deleted list.
            Children don't have to be added to the deleted list, as this can easily be deduced.
        */  
        if(!checkAccess()) {
            deleted.add(this);
            return;
        }
        
        // Obtain the new file metadata
        FileTime newCreation = attributes.creationTime();
        FileTime newModified = attributes.lastModifiedTime();
        
        // Did the file represented by the snapshot change? If so, add it to the modified list.
        if(!newCreation.equals(creationTime) || !newModified.equals(modifiedTime)) {
            if(updated != null) {
                updated.add(this);
            }
            
            // The file has changed, update metadata
            creationTime = newCreation;
            modifiedTime = newModified;
            directory = attributes.isDirectory();
            size = attributes.size();
        }
        
        // If this snapshot represents a directory, check it's children
        if(directory) {
            checkChildren(deleted, added, updated);
        } else if(!children.isEmpty() && deleted != null) {
            /*
                The snapshot does not represent a directory anymore (usually happens
                when the directory was deleted and replaced by a file that is named
                identically)
            */
            deleted.addAll(children.values());
            children.clear();
        }
    }
    
    private boolean checkAccess() {
        try {
            provider.checkAccess(file);
            return true;
        } catch(Exception e) {
            return false;
        }
    }
    
    private void checkChildren(List<FileSnapshot> deleted, List<FileSnapshot> added, List<FileSnapshot> updated) throws IOException {
        // This list is used  to keep track of the subdirectories that were deleted
        List<FileSnapshot> deletedChildren = new ArrayList<>(children.values());
        
        DirectoryStream<Path> stream = null;
        
        try {
            stream = Files.newDirectoryStream(file);
            for(Path p : stream) {
                Path fileName = p.getFileName();
                FileSnapshot snapshot = children.get(fileName);
                
                // If the snapshot exists, update it. If it does not, create a new one.
                if(snapshot != null) {
                    snapshot.update(deleted, added, updated);
                    deletedChildren.remove(snapshot);
                } else {
                    FileSnapshot newSnapshot = new FileSnapshot(p);
                    children.put(newSnapshot.getName(), newSnapshot);
                    newSnapshot.update(deleted, added, updated);
                    
                    if(added != null) {
                        added.add(newSnapshot);
                    }
                }
            }
        } catch(IOException e) {
            if(stream != null) {
                stream.close();
            }
            throw e;
        } finally {
            if(stream != null) {
                stream.close();
            }
        }
        
        // Remove items from the children HashMap that were not found with java.io.File's listFiles() method
        removeChildren(deletedChildren);
        
        // Add the removed items to the deleted list, if it exists.
        if(deleted != null) {
            deleted.addAll(deletedChildren);
        }
    }
    
    private void removeChildren(List<FileSnapshot> remove) {
        for(FileSnapshot snapshot : remove) {
            children.remove(snapshot.getName());
        }
    }
    
    /**
     * Compares this FileSnapshot to the given FileSnapshot.
     * @param other The FileSnapshot to compare with.
     * @param missing A list in which the FileSnapshots are stored that are present 
     *                in the given FileSnapshot's subdirectories but not in 
     *                this FileSnapshot's subdirectories. When two FileSnapshots that represent
     *                the same file are not equal, they are being added to this list as well.
     * @param garbage A list in which the FileSnapshots are stored that are present 
     *                in this FileSnapshot's subdirectories, but not present in the given FileSnapshot's subdirectories.
     */
    public void compareTo(FileSnapshot other, List<FileSnapshot> missing, List<FileSnapshot> garbage) {
        // No need to run the method if there are no children to this FileSnapshot
        if(children.isEmpty()) {
            return;
        }
        
        // Required for iteration
        Set<Path> childrenNames = children.keySet();
        
        // Make a soft-copy so we can keep track of which FileSnapshots are missing
        HashMap<Path, FileSnapshot> otherChildren = new HashMap<>(other.getChildren());
        
        for(Path p : childrenNames) {
            FileSnapshot child = otherChildren.get(p);
            if(child != null) {
                FileSnapshot myChild = children.get(p);
                
                // FileSnapshots are not the same, add to missing list.
                if(myChild.isDirectory() != child.isDirectory() || myChild.getSize() != child.getSize()) {
                    missing.add(child);
                }
                
                myChild.compareTo(child, missing, garbage);
                otherChildren.remove(p);
            } else {
                garbage.add(children.get(p));
            }
        }
        
        // Add all missing Filesnapshots to the missing list
        missing.addAll(otherChildren.values());
    }
    
    public void store(File f) throws FileNotFoundException, IOException {
        PrintWriter out = null;
        
        try {
            out = new PrintWriter(f);
            
            out.println(file + "||" + modifiedTime.toMillis() + "||" + attributes.size());
            
            for(FileSnapshot child : children.values()) {
                child.store(out);
            }
        } catch(FileNotFoundException e) {
            throw e;
        } finally {
            if(out != null) {
                out.close();
            }
        }
    }
    
    private void store(PrintWriter out) {
        out.println(file + "||" + modifiedTime.toMillis() + "||" + attributes.size());
        
        for(FileSnapshot child : children.values()) {
            child.store(out);
        }
    }

    public Path getFile() {
        return file;
    }

    public boolean isDirectory() {
        return directory;
    }

    public Path getName() {
        return name;
    }
    
    public long getSize() {
        return size;
    }

    public HashMap<Path, FileSnapshot> getChildren() {
        return children;
    }
}
