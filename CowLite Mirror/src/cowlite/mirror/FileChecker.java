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

import filedatareader.FileDataReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileSystemView;

/**
 * This is the 'backbone' of the project. Objects of this class imitate disk mirror
 * functionality. They check if new files have been added to the source folder/disk,
 * if files have been updated, removed etc.. It also checks a temporary folder which
 * resembles the folder structure of the source. This temporary folder can be used with
 * TeamViewer file transfer for eaier access. 
 * 
 * Note: Files on the target-folder/disk (the mirror) will be kept in sync with the source.
 * This means that any files that exist on the mirror but not on the source will be removed.
 * 
 * @author Wessel Jongkind
 * @version 2018-02-17
 */
public class FileChecker {
    /**
     * The source disk/folder which has to be duplicated.
     */
    private final FileSnapshot ORIGIN;
    
    /**
     * The target disk/folder, also called the mirror. Is a duplicate of the source disk/folder.
     */
    private final FileSnapshot MIRROR;
    
    /**
     * The temporary folder which can also be used to add files to the mirror. Prevents
     * people from accidentally putting files in the mirror folder rather than the source folder.
     */
    private final FileSnapshot TEMP_FOLDER;
    
    /**
     * Multiplier of the byte-buffer for reading/writing files.
     */
    private final double BUFFER_MULTIPLIER;
    
    /**
     * The interval at which this checker should run in seconds.
     */
    private final int INTERVAL;
    
    /**
     * The lock file. This file is read multiple times when the filechecker is run
     * in order to make sure that the source folder/disk still exists or is alive.
     */
    private final File LOCK;
    
    /**
     * Whether or not the file checker is already running.
     */
    private boolean bussy;
    
    /**
     * Instantiates a new FileChecker object and if available, load the library for this filechecker.
     * @param pathOrigin Path to the source for the mirror: the folder/disk that should be duplicated.
     * @param pathMirror Path to the mirror: the folder/disk that is a duplicate.
     * @param tempPath Path to the folder which can also be used to upload files to the mirror.
     * @param bufferMultiplier Multiplier for the buffer block-size. Default buffer block-size is 8192.
     *                         A multiplier size of 2 will make the block-size 16384 bytes.
     * @param interval The interval at which the file checker should run.
     * @throws Exception When the lock-file could not be created or the origin, mirror or temp_folder are not existing folders.
     */
    public FileChecker(String pathOrigin, String pathMirror, String tempPath, double bufferMultiplier, int interval) throws Exception {
        BUFFER_MULTIPLIER = bufferMultiplier;
        
        if(BUFFER_MULTIPLIER < 0.01) {
            throw new IllegalArgumentException("Buffer multiplier should be atleast 0.01.");
        }
        
        ORIGIN = new FileSnapshot(new File(pathOrigin));
        MIRROR = new FileSnapshot(new File(pathMirror));
        TEMP_FOLDER = new FileSnapshot(new File(tempPath));
        ORIGIN.update(null, null, null);
        MIRROR.update(null, null, null);
        TEMP_FOLDER.update(null, null, null);
        
        if(!ORIGIN.isDirectory() || !MIRROR.isDirectory() || !TEMP_FOLDER.isDirectory()) {
            throw new IllegalArgumentException("One or more of the selected filepaths is invalid.");
        }
        
        INTERVAL = interval;
        bussy = false;
        
        loadLibrary();
        
        //Create a lock file which is used to quit the application if a folder should not be accessible.
        LOCK = new File(ORIGIN.getFile().getAbsolutePath() + "\\" + new File(getLibraryPath()).getName() + ".lock");
        LOCK.createNewFile();
    }
    
    /**
     * Loads in the last known library for this mirror.
     * @throws Exception FileDataReader error.
     */
    private void loadLibrary() throws Exception {
        if(!new File(getLibraryPath()).exists())
            return;
        
        HashMap<String, String[]> stored = new HashMap<>();
        
        FileDataReader red = new FileDataReader();
        red.setPath(getLibraryPath());
        
        //Library exists! Parse the lines in the library to usable formats.
        for(String s : red.getDataStringLines()){
            String[] params = s.split("\\|\\|");
            stored.put(params[0], params);
        }
        
        ArrayList<FileSnapshot> updated = new ArrayList<>();
        
        FileSnapshot snapshot = ORIGIN;
        
        crossReferenceLibrary(stored, snapshot, updated);
        
        for(String path : stored.keySet()) {
            deleteFromMirror(path);
        }
        
        for(FileSnapshot s : updated) {
            copyToMirror(s);
        }
        
        if(!stored.keySet().isEmpty() || !updated.isEmpty()) {
            storeLibrary();
        }
    }
    
    private void  crossReferenceLibrary(Map<String, String[]> stored, FileSnapshot snapshot, List<FileSnapshot> updated) {
        String[] data = stored.get(snapshot.getFile().getAbsolutePath());
        
        if(data != null) {
            stored.remove(snapshot.getFile().getAbsolutePath());
            if(Long.parseLong(data[1]) != snapshot.getLastModified() || Long.parseLong(data[2]) != snapshot.getSize()) {
                updated.add(snapshot);
            }
        } else {
            updated.add(snapshot);
        }
        
        for(FileSnapshot s : snapshot.getChildren().values()) {
            crossReferenceLibrary(stored, s, updated);
        }
    }
    
    /**
     * Main function call: this method will make sure a number of things are done.
     * First, it checks if the object is not already doing a file-check. If not,
     * it will remove any files that exist on the mirror and don't exist on the source
     * disk/folder. Then, it will copy new files and missing data to the mirror. Any
     * files in the temp folder are also added to both the source folder/disk and the mirror.
     */
    public void checkFiles() throws IOException {
        if(!bussy) {
            // Lock the file checker
            bussy = true;
            
            // ArrayLists that keep track of all changes
            ArrayList<FileSnapshot> deleted = new ArrayList<>();
            ArrayList<FileSnapshot> added = new ArrayList<>();
            ArrayList<FileSnapshot> updated = new ArrayList<>();
            
            // Update origin directory tree
            ORIGIN.update(deleted, added, updated);
            
            // Send updates to the mirror directory
            for(FileSnapshot s : added) {
                copyToMirror(s);
            }
            
            for(FileSnapshot s : updated) {
                copyToMirror(s);
            }
            
            for(FileSnapshot s : deleted) {
                deleteFromMirror(s);
            }
            
            // For validating the mirror directory
            ArrayList<FileSnapshot> missing = new ArrayList<>();
            ArrayList<FileSnapshot> garbage = new ArrayList<>();
            
            // Update the mirror directory, check which files are still missing or shouldn't be there
            MIRROR.update(null, null, null);
            MIRROR.compareTo(ORIGIN, missing, garbage);
            
            for(FileSnapshot s : missing) {
                copyToMirror(s);
            }
            
            for(FileSnapshot s : garbage) {
                deleteFromMirror(s);
            }
            
            if(!added.isEmpty() || !updated.isEmpty() || !deleted.isEmpty()) {
                storeLibrary();
            }
        }
        
        bussy = false;
    }
    
    private void copyToMirror(File f) {
        if(f.isFile()) {
            FileIO.copy(f, new File(MIRROR.getFile().getAbsolutePath() + getPath(f, ORIGIN.getFile().getAbsolutePath())), BUFFER_MULTIPLIER);
        } else {
            FileIO.createDirectory(new File(MIRROR.getFile().getAbsolutePath() + getPath(f, ORIGIN.getFile().getAbsolutePath())));
        }
    }
    
    private void copyToMirror(FileSnapshot s) {
        copyToMirror(s.getFile());
    }
    
    private void deleteFromMirror(FileSnapshot s) {
        deleteFromMirror(s.getFile());
    }
    
    private void deleteFromMirror(String s) {
        deleteFromMirror(new File(s));
    }
    
    private void deleteFromMirror(File f) {
        FileIO.delete(MIRROR.getFile().getAbsolutePath() + getPath(f, ORIGIN.getFile().getAbsolutePath()));
    }
    
    /**
     * Store the library to the disk. This significantly improves first-iteration performance
     * when the program is restarted.
     */
    private void storeLibrary() throws IOException
    {
        // Set the output stream to the correct folder.
        String path = getLibraryPath();
        File out = new File(path + ".tmp");
        System.out.println(out);

        // Print the library values.
        ORIGIN.store(out);

        // Remove the old library file.
        File nf = new File(path);
        if(nf.exists())
            FileIO.delete(nf);

        // Rename the temporary library file.
        out.renameTo(nf);
    }
    
    /**
     * Obtain the path to the library file.
     * @return Library to the library file.
     */
    private String getLibraryPath()
    {
        // TODO memory leak?
        // Obtain documents folder. Possibly this way causes memory leaks?
        JFileChooser fr = new JFileChooser();
        FileSystemView fw = fr.getFileSystemView();
        
        // Generate path string
        String originLetter = Paths.get(ORIGIN.getFile().getAbsolutePath()).getRoot().toString();
        String mirrorLetter = Paths.get(MIRROR.getFile().getAbsolutePath()).getRoot().toString();
        String path = fw.getDefaultDirectory().getAbsolutePath() 
                + "\\CowLite Mirror\\" + ORIGIN.getFile().getAbsolutePath().replace(originLetter, "").replace("\\", ".") 
                + "-" + MIRROR.getFile().getAbsolutePath().replace(mirrorLetter, "").replace("\\", ".");
        
        return path;
    }
    
    /**
     * Obtain the relative path to a file from a root.
     * @param f The file to which a relative path has to be obtained.
     * @param root Root at which the path should start.
     * @return Relative path to a file from a root.
     */
    private String getPath(File f, String root) {
        String path = f.getAbsolutePath().replace(root, "");
        if(path.equals("")) {
            return path;
        }
        
        if(!(path.charAt(0) + "").equals("\\"))
            path = "\\" + path;
        
        return path;
    }

    public int getInterval() {
        return INTERVAL;
    }
}
