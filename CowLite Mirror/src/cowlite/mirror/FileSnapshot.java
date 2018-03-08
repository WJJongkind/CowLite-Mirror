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
        if(!file.exists()) {
            deleted.add(this);
        }
        
        long newSize = file.length();
        long newModified = file.lastModified();
        boolean newIsFolder = file.isDirectory();
        
        if(newIsFolder) {
            checkChildren(deleted, added, updated);
        } else if(directory && deleted != null) {
            deleted.addAll(children.values());
            children.clear();
        }
        
        if(newSize != size || lastModified != newModified || newIsFolder != directory) {
            if(updated != null) {
                updated.add(this);
            }
        }
        
        size = newSize;
        lastModified = newModified;
        directory = newIsFolder;
    }
    
    private void checkChildren(List<FileSnapshot> deleted, List<FileSnapshot> added, List<FileSnapshot> updated) {
        List<FileSnapshot> deletedChildren = new ArrayList<>(children.values());
        
        File[] files = file.listFiles();
        if(files != null) {
            for(File f : files) {
                FileSnapshot snapshot = children.get(f.getName());
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
        
        removeChildren(deletedChildren);
        
        if(deleted != null) {
            deleted.addAll(deletedChildren);
        }
    }
    
    private void removeChildren(List<FileSnapshot> remove) {
        for(FileSnapshot snapshot : remove) {
            children.remove(snapshot.getName());
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
    
    public void compareTo(FileSnapshot other, List<FileSnapshot> missing, List<FileSnapshot> garbage) {
        if(children.isEmpty()) {
            return;
        }
        
        Set<String> childrenNames = children.keySet();
        HashMap<String, FileSnapshot> otherChildren = new HashMap<>(other.getChildren());
        
        for(String s : childrenNames) {
            FileSnapshot child = otherChildren.get(s);
            if(child != null) {
                FileSnapshot myChild = children.get(s);
                
                if(myChild.isDirectory() != child.isDirectory() || myChild.getSize() != child.getSize()) {
                    missing.add(child);
                }
                
                myChild.compareTo(child, missing, garbage);
                otherChildren.remove(s);
            } else {
                garbage.add(children.get(s));
            }
        }
        
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
}
