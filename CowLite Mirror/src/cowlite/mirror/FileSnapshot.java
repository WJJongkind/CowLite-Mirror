/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cowlite.mirror;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

/**
 *
 * @author Wessel
 */
public class FileSnapshot 
{
    private final File file;
    private final HashMap<String, FileSnapshot> children;
    private long size;
    private long lastModified;
    private boolean directory;
    private final String name;
    
    public FileSnapshot(File f) {
        this.file = f;
        children = new HashMap<>();
        size = f.length();
        lastModified = f.lastModified();
        directory = f.isDirectory();
        name = f.getName();
    }
    
    public void update(List<FileSnapshot> deleted, List<FileSnapshot> added, List<FileSnapshot> updated) {
        /*
            The file represented by this snapshot doesn't exist, add it to the deleted list.
            Children don't have to be added to the deleted list, as this can easily be deduced.
        */  
        if(!file.exists()) {
            deleted.add(this);
            return;
        }
        
        // Obtain the new file metadata
        long newSize = file.length();
        long newModified = file.lastModified();
        boolean newIsFolder = file.isDirectory();
        
        // Did the file represented by the snapshot change? If so, add it to the modified list.
        if(newSize != size || lastModified != newModified || newIsFolder != directory) {
            if(updated != null) {
                updated.add(this);
            }
        }
        
        // Update the snapshot's fields
        size = newSize;
        lastModified = newModified;
        directory = newIsFolder;
        
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
    
    private void checkChildren(List<FileSnapshot> deleted, List<FileSnapshot> added, List<FileSnapshot> updated) {
        // This list is used  to keep track of the subdirectories that were deleted
        List<FileSnapshot> deletedChildren = new ArrayList<>(children.values());
        
        File[] files = file.listFiles();
        if(files != null) {
            // Iterate over subdirectories / files
            for(File f : files) {
                FileSnapshot snapshot = children.get(f.getName());
                
                // If the snapshot exists, update it. If it does not, create a new one.
                if(snapshot != null) {
                    snapshot.update(deleted, added, updated);
                    deletedChildren.remove(snapshot);
                } else {
                    FileSnapshot newSnapshot = new FileSnapshot(f);
                    children.put(newSnapshot.getName(), newSnapshot);
                    newSnapshot.update(deleted, added, updated);
                    
                    if(added != null) {
                        added.add(newSnapshot);
                    }
                }
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
        Set<String> childrenNames = children.keySet();
        
        // Make a soft-copy so we can keep track of which FileSnapshots are missing
        HashMap<String, FileSnapshot> otherChildren = new HashMap<>(other.getChildren());
        
        for(String s : childrenNames) {
            FileSnapshot child = otherChildren.get(s);
            if(child != null) {
                FileSnapshot myChild = children.get(s);
                
                // FileSnapshots are not the same, add to missing list.
                if(myChild.isDirectory() != child.isDirectory() || myChild.getSize() != child.getSize()) {
                    missing.add(child);
                }
                
                myChild.compareTo(child, missing, garbage);
                otherChildren.remove(s);
            } else {
                garbage.add(children.get(s));
            }
        }
        
        // Add all missing Filesnapshots to the missing list
        missing.addAll(otherChildren.values());
    }
    
    public void store(File f) throws FileNotFoundException, IOException {
        PrintWriter out = null;
        
        try {
            out = new PrintWriter(f);
            
            out.println(file.getAbsolutePath() + "||" + lastModified + "||" + size);
            
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
        out.println(file.getAbsolutePath() + "||" + lastModified + "||" + size);
        
        for(FileSnapshot child : children.values()) {
            child.store(out);
        }
    }

    public File getFile() {
        return file;
    }

    public long getSize() {
        return size;
    }

    public long getLastModified() {
        return lastModified;
    }

    public boolean isDirectory() {
        return directory;
    }

    public String getName() {
        return name;
    }

    public HashMap<String, FileSnapshot> getChildren() {
        return children;
    }
}