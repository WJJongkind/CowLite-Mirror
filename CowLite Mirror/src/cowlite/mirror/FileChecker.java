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

import cowlite.mirror.DirectoryWatcher.DirectoryEvent;
import filedatareader.FileDataReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    private final File ORIGIN;
    
    /**
     * The target disk/folder, also called the mirror. Is a duplicate of the source disk/folder.
     */
    private final File MIRROR;
    
    /**
     * The temporary folder which can also be used to add files to the mirror. Prevents
     * people from accidentally putting files in the mirror folder rather than the source folder.
     */
    private final File TEMP_FOLDER;
    
    /**
     * Multiplier of the byte-buffer for reading/writing files.
     */
    private final int BUFFER_MULTIPLIER;
    
    /**
     * The interval at which this checker should run in seconds.
     */
    private final int INTERVAL;
    
    private final int AGE_BUFFER;
    
    
    /**
     * The lock file. This file is read multiple times when the filechecker is run
     * in order to make sure that the source folder/disk still exists or is alive.
     */
    private final File LOCK;
    
    /**
     * Keeps references to the files in the source-folder. Allows for more efficient
     * data processing.
     */
    private HashMap<String, FileData> sourceLibrary;
    
    /**
     * Whether or not the file checker is already running.
     */
    private boolean bussy;
    
    private boolean monitoring = false;
    
    /**
     * Instantiates a new FileChecker object and if available, load the library for this filechecker.
     * @param pathOrigin Path to the source for the mirror: the folder/disk that should be duplicated.
     * @param pathMirror Path to the mirror: the folder/disk that is a duplicate.
     * @param tempPath Path to the folder which can also be used to upload files to the mirror.
     * @param bufferMultiplier Multiplier for the buffer block-size. Default buffer block-size is 8192.
     *                         A multiplier size of 2 will make the block-size 16384 bytes.
     * @param interval The interval at which the file checker should run.
     * @param ageBuffer The mean maximum age in timed event queues.
     * @throws Exception When the lock-file could not be created or the origin, mirror or temp_folder are not existing folders.
     */
    public FileChecker(String pathOrigin, String pathMirror, String tempPath, int bufferMultiplier, int interval, int ageBuffer) throws Exception {
        ORIGIN = new File(pathOrigin);
        MIRROR = new File(pathMirror);
        TEMP_FOLDER = new File(tempPath);
        
        if(!ORIGIN.isDirectory() || !MIRROR.isDirectory() || !TEMP_FOLDER.isDirectory()) {
            throw new IllegalArgumentException("One or more of the selected filepaths is invalid.");
        }
        
        BUFFER_MULTIPLIER = bufferMultiplier;
        AGE_BUFFER = ageBuffer;
        
        if(BUFFER_MULTIPLIER < 1) {
            throw new IllegalArgumentException("Buffer multiplier should be atleast 1.");
        }
        
        INTERVAL = interval;
        sourceLibrary = new HashMap<>();
        bussy = false;
        
        loadLibrary();
        
        //Create a lock file which is used to quit the application if a folder should not be accessible.
        LOCK = new File(ORIGIN.getAbsolutePath() + "\\" + new File(getLibraryPath()).getName() + ".lock");
        LOCK.createNewFile();
    }
    
    /**
     * Loads in the last known library for this mirror.
     * @throws Exception FileDataReader error.
     */
    private void loadLibrary() throws Exception {
        if(!new File(getLibraryPath()).exists())
            return;
        
        FileDataReader red = new FileDataReader();
        red.setPath(getLibraryPath());
        
        //Library exists! Parse the lines in the library to usable formats.
        for(String s : red.getDataStringLines()){
            String[] params = s.split("\\|\\|");
            sourceLibrary.put(params[0], new FileData(params[0], Long.parseLong(params[1]), Long.parseLong(params[2])));
        }
    }
    
    public void startMonitoring(boolean threaded) throws Exception {
        if(threaded) {
            Thread t = new Thread(new Runnable(){
                @Override
                public void run() {
                    try {
                        startWatching();
                    }catch(Exception e){
                       e.printStackTrace();
                    }
                }
            });
            
            t.start();
        } else {
            startWatching();
        }
    }
    
    private void startWatching() throws Exception {
        monitoring = true;
        DirectoryWatcher watcher = new DirectoryWatcher(ORIGIN.getAbsolutePath());
        watcher.start();
        
        while(monitoring) {
            List<DirectoryEvent> events = watcher.takeEvents(AGE_BUFFER, true);
            
            for(DirectoryEvent e : events) {
                System.out.println(e.getType() + "   " + e.getFile().getAbsolutePath());
                String relativePath = getPath(e.getFile(), ORIGIN.getAbsolutePath());
                if(e.getType() == DirectoryEvent.FILE_CREATE || e.getType() == DirectoryEvent.FILE_MODIFY) {
                    if(e.getFile().isDirectory()) {
                        FileIO.createDirectory(MIRROR.getAbsolutePath() + relativePath);
                    } else {
                        FileIO.copy(e.getFile(), new File(MIRROR.getAbsolutePath() + relativePath));
                    }
                    
                    sourceLibrary.put(relativePath, new FileData(relativePath, e.getFile().lastModified(), e.getFile().length()));
                } else if(e.getType() == DirectoryEvent.FILE_DELETE) {
                    FileIO.delete(MIRROR.getAbsolutePath() + relativePath);
                }
            }
        }
    }
    
    /**
     * Main function call: this method will make sure a number of things are done.
     * First, it checks if the object is not already doing a file-check. If not,
     * it will remove any files that exist on the mirror and don't exist on the source
     * disk/folder. Then, it will copy new files and missing data to the mirror. Any
     * files in the temp folder are also added to both the source folder/disk and the mirror.
     */
    public void fullFileCheck() {
        if(!bussy) {
            // Lock the file checker
            bussy = true;
            
            // Used to see if the source disk/folder was changed.
            boolean change = false;
            
            // Copy temp folder files to the source disk and mirror.
            HashMap<String, FileData> tempFolders = new HashMap<>();
            copyUnaddedFiles(tempFolders);
            
            // Index the source folder files, mirror files, newly added files etc.
            HashMap<String, FileData> newLibrary = new HashMap<>();
            HashMap<String, FileData> emptyFolders = new HashMap<>();
            ArrayList<FileData> newFiles = new ArrayList<>();
            
            // Used to copy the source disk/folder structure to the temp folder without the actual files
            HashMap<String, FileData> folders = new HashMap<>();
            buildLibrary(newLibrary, newFiles, folders, emptyFolders, ORIGIN, ORIGIN.getAbsolutePath());
            
            // Check if the source disk/folder was changed. If so, a new library has to be stored.
            change = newLibrary.size() != sourceLibrary.size() || newFiles.size() > 0;
            
            // The mirror.
            HashMap<String, FileData> mirror = new HashMap<>();
            buildLibrary(mirror, null, null, emptyFolders, MIRROR, MIRROR.getAbsolutePath());

            // Do the actual mirroring.
            copyNewFiles(mirror, newFiles);
            HashMap<String, FileData> missing = cleanData(mirror, newLibrary, emptyFolders);
            copyMissingData(missing);
            updateTempFolder(tempFolders, folders);
            
            // Make it easier for GC to garbage collect...
            sourceLibrary.clear();
            sourceLibrary = newLibrary;
            mirror.clear();
            newFiles.clear();
            folders.clear();
            tempFolders.clear();
            tempFolders = null;
            folders = null;
            newLibrary = null;
            newFiles = null;
            mirror = null;
            
            // Store the new library if changes were made.
            if(change)
                storeLibrary();
            
            // Unlock the file checker.
            bussy = false;
        }
    }
    
    /**
     * 
     * @param newLibrary
     * @param newFiles
     * @param folders
     * @param emptyDirectories
     * @param f
     * @param root 
     */
    private void buildLibrary(HashMap<String, FileData> newLibrary, ArrayList<FileData> newFiles, HashMap<String, FileData> folders, HashMap<String, FileData> emptyDirectories, File f, String root) {
        if(f.isDirectory()){
            File[] files = f.listFiles();
            if(files != null){
                if(files.length > 0) {
                    if(folders != null && !root.equals(f.getAbsolutePath()) && !folders.containsKey(getPath(f, root)))
                        folders.put(getPath(f, root), new FileData(getPath(f, root), 0, 0));
                    
                    for(File file : files)
                        buildLibrary(newLibrary, newFiles, folders, emptyDirectories, file, root);
                } else {
                    if(!root.equals(f.getAbsolutePath())) {
                        String path = getPath(f, root);
                        if(emptyDirectories != null && !emptyDirectories.containsKey(path))
                            emptyDirectories.put(path, new FileData(path, 0, 0));
                    }
                }
            }
        }
        else {
            addPath(newLibrary, newFiles, f, root);
        }
    }
    
    /**
     * Update the temporary files folder which is used for safely 'uploading' files
     * to the mirror.
     * @param tempFolder Folders found in the temp folder
     * @param folders Folders that should exist in the temp folder
     */
    private void updateTempFolder(HashMap<String, FileData> tempFolder, HashMap<String, FileData> folders) {
        // Remove any folders that should not exist.
        for(String path : tempFolder.keySet()) {
            if(!folders.containsKey(path)) {
                FileIO.delete(TEMP_FOLDER.getAbsolutePath() + path);
            }
        }
        
        // If both sizes are equal, we don't need to do any more checks.
        if(tempFolder.size() == folders.size()) {
            return;
        }
        
        // Copy new folders to temp folder.
        for(String path : folders.keySet()) {
            if(!tempFolder.containsKey(path)) {
                FileIO.createDirectory(TEMP_FOLDER + path);
            }
        }
    }
    
    /**
     * Copy any unadded files from the temp folder to the source disk/folder of the mirror.
     * @param folders Subdirectories in the temp folder.
     */
    private void copyUnaddedFiles(HashMap<String, FileData> folders) {
        // If the lock file doesn't exist then it is not safe to continu execution. Exit program.
        if(!LOCK.exists())
            System.exit(0);
            
        // Build a library with the files that were added to the temp folder after the previous file check.
        HashMap<String, FileData> newFiles = new HashMap<>();
        buildLibrary(newFiles, null, folders, null, TEMP_FOLDER, TEMP_FOLDER.getAbsolutePath());
        
        // Copy files from temp folder to the source of the mirror, delete them from temp folder.
        for(String path : newFiles.keySet()) {
            FileIO.copy(TEMP_FOLDER.getAbsolutePath() + path, ORIGIN.getAbsolutePath() + path);
            FileIO.delete(TEMP_FOLDER.getAbsolutePath() + path);
        }
    }
    
    /**
     * Copy any data that is missing on the mirror to the mirror. 
     * @param mirror Map containing references to all files that exist on the mirror.
     * @param newLibrary Map containing all the files currently known to be on the source disk/folder
     * @param newFiles Files of which we are certain that they are not on the mirror, as they were newly added or updated.
     */
    private void copyMissingData(HashMap<String, FileData> missing) {
        // No lock available means it is not safe to continue execution. Exit program.
        if(!LOCK.exists())
            System.exit(0);

        // Copy missing files to the mirror.
        for(String path : missing.keySet()) {
            FileIO.copy(ORIGIN.getAbsolutePath() + path, MIRROR.getAbsolutePath() + path);
        }
    }
    
    /**
     * Copy newly added or updated data from the source disk/folder to the mirror.
     * @param newFiles Files of which we are certain that they are newly added or updated.
     */
    private void copyNewFiles(HashMap<String, FileData> target, ArrayList<FileData> newFiles) {
        // No lock available means it is not safe to continue execution. Exit program.
        if(!LOCK.exists())
            System.exit(0);

        // Copy the new files to the mirror.
        for(FileData nf : newFiles) {
            FileIO.copy(ORIGIN.getAbsolutePath() + nf.getPath(), MIRROR.getAbsolutePath() + nf.getPath(), BUFFER_MULTIPLIER * FileIO.DEFAULT_BUFFER_SIZE);
            
            if(!target.containsKey(nf.getPath())) {
                target.put(nf.getPath(), nf);
            }
        }
    }
    
    /**
     * Clean the mirror by removing any files that shouldn't exist on the mirror
     * and by removing one layer of empty folders.
     * @param mirror Map containing references to all files on the mirror.
     * @param newLibrary Map containing references to all files on the source disk/folder.
     * @param emptyFolders All folders which were found to be empty.
     */
    private HashMap<String, FileData> cleanData(HashMap<String, FileData> mirror, HashMap<String, FileData> newLibrary, HashMap<String, FileData> emptyFolders) {
        // For efficiency, find out what files are missing on the mirror
        HashMap<String, FileData> missing = new HashMap<>(newLibrary);

        // No lock available means it is not safe to continue execution. Exit program.
        if(!LOCK.exists())
            System.exit(0);

        // Remove data that should not exist on the mirror.
        for(FileData fd : mirror.values()) {
            FileData inLib = newLibrary.get(fd.getPath());
            if(inLib == null && LOCK.exists()) {
                FileIO.delete(new File(MIRROR.getAbsolutePath() + fd.getPath()));
            } else if(LOCK.exists() && inLib != null){
                missing.remove(inLib.getPath());
            }
        }

        // Remove folders that are empty.
        for(FileData fd : emptyFolders.values()) {
            FileIO.delete(new File(MIRROR.getAbsolutePath() + fd.getPath()));
            FileIO.delete(new File(ORIGIN.getAbsolutePath() + fd.getPath()));
        }
        
        return(missing);
    }
    
    /**
     * Store the library to the disk. This significantly improves first-iteration performance
     * when the program is restarted.
     */
    private void storeLibrary()
    {
        PrintStream out = null;

        try {
            // Set the output stream to the correct folder.
            String path = getLibraryPath();
            out = new PrintStream(new File(path + ".tmp"));

            // Print the library values.
            for(FileData dat : sourceLibrary.values())
                out.println(dat.toString());

            out.close();
            
            // Remove the old library file.
            File nf = new File(path);
            if(nf.exists())
                FileIO.delete(nf);
            
            // Rename the temporary library file.
            File f = new File(path + ".tmp");
            f.renameTo(nf);
        } catch (FileNotFoundException ex) {
            Logger.getLogger(FileChecker.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            if(out != null)
                out.close();
        }
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
        String originLetter = Paths.get(ORIGIN.getAbsolutePath()).getRoot().toString();
        String mirrorLetter = Paths.get(MIRROR.getAbsolutePath()).getRoot().toString();
        String path = fw.getDefaultDirectory().getAbsolutePath() 
                + "\\CowLite Mirror\\" + ORIGIN.getAbsolutePath().replace(originLetter, "").replace("\\", ".") 
                + "-" + MIRROR.getAbsolutePath().replace(mirrorLetter, "").replace("\\", ".");
        
        return path;
    }
    
    /**
     * Add a reference to the given file in the library map and if needed, add it to the newfiles list.
     * @param newLibrary The library folder to which the reference has to be added.
     * @param newFiles Folder containing the newly found files, or null if not needed.
     * @param f The file which has to be referenced.
     * @param root Path to the root.
     */
    private void addPath(HashMap<String, FileData> newLibrary, ArrayList<FileData> newFiles, File f, String root) {
        String path = getPath(f, root);
        FileData found = new FileData(path, f.lastModified(), f.length());
        newLibrary.put(found.getPath(), found);
        
        if(newFiles != null) {
            FileData known = sourceLibrary.get(found.getPath());
            if(known == null || !known.equals(found))
                newFiles.add(found);
        }
    }
    
    /**
     * Obtain the relative path to a file from a root.
     * @param f The file to which a relative path has to be obtained.
     * @param root Root at which the path should start.
     * @return Relative path to a file from a root.
     */
    private String getPath(File f, String root) {
        String path = f.getAbsolutePath().replace(root, "");
        if(!(path.charAt(0) + "").equals("\\"))
            path = "\\" + path;
        
        return path;
    }
    
    /**
     * The interval in seconds at which the FileChecker should check the mirror.
     * @return The interval at which the FileChecker should be run (in seconds).
     */
    public int getInterval() {
        return INTERVAL;
    }
    
    /**
     * Class used for storing file references and check if files have been updated.
     */
    private class FileData
    {
        /**
         * Relative path to the file that is referenced.
         */
        private String path;
        
        /**
         * Last modified time in seconds from Unix (1970-01-01).
         */
        private long lastModified;
        
        /**
         * Size of the file that is referenced.
         */
        private long fileSize;
        
        /**
         * Creates a new reference to a file.
         * @param path Relative path to the file that is referenced.
         * @param lastModified Last Unix timestamp the file was modified.
         * @param fileSize Size of the referenced file.
         */
        public FileData(String path, long lastModified, long fileSize)
        {
            this.path = path;
            this.lastModified = lastModified;
            this.fileSize = fileSize;
        }

        /**
         * Returns the relative path to the referenced file.
         * @return Relative path to the file.
         */
        public String getPath() {
            return path;
        }

        /**
         * Returns the last modified timestamp (in Unix) of the referenced file.
         * @return The last modified timestamp (in Unix) of teh referenced file.
         */
        public long getLastModified() {
            return lastModified;
        }

        /**
         * Returns the file size of the referenced file.
         * @return The file size of the referenced file.
         */
        public long getFileSize() {
            return fileSize;
        }
        
        /**
         * Checks if the given FileData is equal to this FileData.
         * @param otherData The other Filedata that has to be compared.
         * @return True if this and the given FileData are equal.
         */
        public boolean equals(FileData otherData){
            return otherData.getPath().equals(getPath()) && 
                   otherData.getLastModified() == getLastModified() &&
                   otherData.getFileSize() == getFileSize();
        }
        
        @Override
        public String toString() {
            return path + "||" + lastModified + "||" + fileSize;
        }
    }
}
