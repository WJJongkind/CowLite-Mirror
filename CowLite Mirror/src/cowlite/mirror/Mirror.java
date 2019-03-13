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

import filedatareader.FileDataReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This is the 'backbone' of the project. Objects of this class imitate disk
 * mirror functionality. They check if new files have been added to the source
 * folder/disk, if files have been updated, removed etc.. It also checks a
 * temporary folder which resembles the folder structure of the source. This
 * temporary folder can be used with TeamViewer file transfer for eaier access.
 *
 * Note: Files on the target-folder/disk (the mirror) will be kept in sync with
 * the source. This means that any files that exist on the mirror but not on the
 * source will be removed.
 *
 * @author Wessel Jelle Jongkind
 * @version 2019-03-11 (yyyy-mm-dd)
 */
public class Mirror {
    
    // MARK: - Properties

    /**
     * The source disk/folder which has to be duplicated.
     */
    private final FileSnapshot originSnapshot;

    /**
     * The target disk/folder, also called the mirror. Is a duplicate of the
     * source disk/folder.
     */
    private final FileSnapshot mirrorSnapshot;

    /**
     * FileService used for copying, deleting and creating files/folders.
     */
    private final FileService fileService;
    /**
     * Multiplier of the byte-buffer for reading/writing files.
     */
    private final int bufferSize;

    /**
     * The interval at which this checker should run in seconds.
     */
    private final int interval;

    /**
     * The maximum file size that will be mirrored.
     */
    private final long maxFileSize;

    /**
     * The name of the mirror.
     */
    private final String mirrorName;
    
    /**
     * Whether or not the file checker is already running.
     */
    private boolean bussy = false;
    
    /**
     * Lock used to lock the Mirror checkFiles() method so that it can only be ran serially.
     */
    private final Lock lock = new ReentrantLock();
    
    // MARK: - Object lifecycle

    /**
     * Instantiates a new Mirror object and if available, load the library
     * for this Mirror.
     *
     * @param origin File that denotes the path to the source/origin folder.
     * @param target File that denotes the path to the mirror-folder.
     * @param fileService The service which can be used to create, copy & delete files/folders.
     * @param bufferSize The size of the buffer used for file-transfers in megabytes.
     * @param interval The interval at which the file checker should run.
     * @param maxFileSize The maximum size of files that are to be checked.
     * @throws java.io.IOException When an IO exception occurs.
     * @throws java.security.NoSuchAlgorithmException When the mirror could not be instantiated.
     */
    public Mirror(FileSnapshot origin, FileSnapshot target, FileService fileService,  int bufferSize, int interval, long maxFileSize) throws IOException, NoSuchAlgorithmException, IllegalArgumentException  {
        this.bufferSize = bufferSize;
        this.maxFileSize = maxFileSize;
        this.interval = interval;
        this.fileService = fileService;

        if (this.bufferSize < 1) {
            throw new IllegalArgumentException("Buffer multiplier should be atleast 1.");
        }

        // Initialize all FileSnapshots and immediatly update them because we want all subdirectories to be added to the structure
        this.originSnapshot = origin;
        this.mirrorSnapshot = target;
        this.mirrorName = makeMirrorName();

        if (!this.originSnapshot.isDirectory() || !mirrorSnapshot.isDirectory()) {
            throw new IllegalArgumentException("One or more of the selected filepaths is invalid.");
        }
        
        mirrorSnapshot.update(null, null, null);
        if(new File(getLibraryPath()).exists()) { // If no known stored library of the mirror exists, then we dont want to update Origin.
            this.originSnapshot.update(null, null, null);
        }  
        
        loadLibrary();
    }
    
    /**
     * Constructs & returns the name of the mirror. The name of the mirror can is made by
     * concatenating the origin filepath with the mirror filepath, with a dash in the middle.
     * For example: mirror = "C:\\users" and mirror = "C:\\mirror", then the concatenated String is
     * "C:\\users-C:\\mirror". 
     * 
     * Of this concatenated String, the SHA-256 hash is calculated and base64 encoded. The base64 String utf8
     * representation is then returned. In this String, '/' is replaced with 'slash', '+' is replaced with 'plus' and
     * '=' is replaced with 'equals'.
     * 
     * @return The name of the mirror.
     * @throws NoSuchAlgorithmException 
     */
    private String makeMirrorName() throws NoSuchAlgorithmException {
        String unhashedName = originSnapshot.getFile().toFile().getAbsolutePath()+ "-" + mirrorSnapshot.getFile().toFile().getAbsolutePath();
        
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(unhashedName.getBytes(StandardCharsets.UTF_8));
        return new String(Base64.getEncoder().encode(hash)).replace("/", "slash").replace("+", "plus").replace("=", "equals");
    }

    /**
     * Loads in the last known library for this mirror.
     *
     * @throws Exception FileDataReader error.
     */
    private void loadLibrary() throws IOException {
        if (!new File(getLibraryPath()).exists()) {
            return;
        }

        HashMap<String, String[]> stored = new HashMap<>();

        FileDataReader red = new FileDataReader();
        red.setPath(getLibraryPath());

        //Library exists! Parse the lines in the library to usable formats.
        for (String s : red.getDataStringLines()) {
            String[] params = s.split("\\|\\|");
            stored.put(params[0], params);
        }

        // Compare the FileSnapshots to the data that was stored
        ArrayList<FileSnapshot> updated = new ArrayList<>();
        crossReferenceLibrary(stored, originSnapshot, updated);

        for (FileSnapshot s : updated) {
            copyToMirror(s);
        }

        if (!stored.keySet().isEmpty() || !updated.isEmpty()) {
            storeLibrary();
        }
    }

    /**
     * With this method, the data from stored {@code FileSnapshot} files are
     * cross-referenced newly initialized {@code FileSnapshot}s. Any changes in
     * the data from the stored {@code FileSnapshot} file are added to the
     * {@code List} in which the chnages are stored.
     *
     * @param stored {@code Map} containing the stored filepaths as keys, and
     * the filepath, last modified date (in UNIX Epoch) and filesize in
     * {@code String} array respectively.
     * @param snapshot The {@code FileSnapshot} to which the stored data should
     * be compared.
     * @param updated The {@code List} in which changes should be stored.
     */
    private void crossReferenceLibrary(Map<String, String[]> stored, FileSnapshot snapshot, List<FileSnapshot> updated) {
        String[] data = stored.get(snapshot.getFile().toString());

        if (data != null) {
            stored.remove(snapshot.getFile().toString());
            if (Long.parseLong(data[1]) != snapshot.getModifiedTime() || Long.parseLong(data[2]) != snapshot.getSize()) {
                updated.add(snapshot);
            }
        } else {
            updated.add(snapshot);
        }

        for (FileSnapshot s : snapshot.getChildren()) {
            crossReferenceLibrary(stored, s, updated);
        }
    }

    /**
     * Main function call: this method will make sure a number of things are
     * done. First, it checks if the object is not already doing a file-check.
     * If not, it will remove any files that exist on the mirror and don't exist
     * on the source disk/folder. Then, it will copy new files and missing data
     * to the mirror. Any files in the temp folder are also added to both the
     * source folder/disk and the mirror.
     */
    public final void checkFiles() {
        try {
            lock.lock();
            bussy = true;

            // ArrayLists that keep track of all changes
            ArrayList<FileSnapshot> deleted = new ArrayList<>();
            ArrayList<FileSnapshot> added = new ArrayList<>();
            ArrayList<FileSnapshot> updated = new ArrayList<>();

            // Update origin directory tree
            try {
                originSnapshot.update(deleted, added, updated);
            } catch (IOException e) {
                return;
            }

            // Send updates to the mirror directory
            List<FileSnapshot> filesToCopy = new ArrayList<>();
            filesToCopy.addAll(added);
            filesToCopy.addAll(updated);

            for (FileSnapshot s : filesToCopy) {
                try {
                    copyToMirror(s);
                } catch (Exception e) {} // If file copying fails, still attempt to copy other files.
            }

            for (FileSnapshot s : deleted) {
                try {
                    deleteSourceSnapshotFromMirror(s);
                } catch (Exception e) { } // If file deletion fails, still attempt to remove the other files.
            }

            // For validating the mirror directory
            ArrayList<FileSnapshot> missing = new ArrayList<>();
            ArrayList<FileSnapshot> garbage = new ArrayList<>();

            // Update the mirror directory
            try {
                mirrorSnapshot.update(null, null, null);
            } catch (Exception e) {
                return;
            }

            mirrorSnapshot.compareTo(originSnapshot, missing, garbage);

            for (FileSnapshot s : missing) {
                try {
                    copyToMirror(s);
                } catch (Exception e) { } // If file copying fails, still attempt to copy other files.
            }

            for (FileSnapshot s : garbage) {
                try {
                    secureDelete(s.getFile().toFile());
                } catch (IOException e) { } // If file deletion fails, still attempt to remove the other files.
            }

            if (!added.isEmpty() || !updated.isEmpty() || !deleted.isEmpty()) {
                try {
                    storeLibrary();
                } catch (IOException e) { }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Copy the given {@code File} that is present in the source-directory or
     * one of the subdirectories to the mirror directory. The relative path from
     * the root-folder is kept:
     *
     * For example; C:\Test\ is the root folder that is being mirrored, and
     * C:\Mirror\ is the root for the mirror. When copying
     * C:\Test\foldera\folderb\file.mp4 to the mirror, it will be copied to
     * C:\Mirror\foldera\folderb\file.mp4.
     *
     * @param f The {@code File} to be copied to the mirror.
     */
    private void copyToMirror(File f) {
        if (!f.exists() || f.length() > maxFileSize) {
            return;
        }

        securityCheck();
        try {
            File newFile = new File(mirrorSnapshot.getFile().toString() + getRelativePath(f, originSnapshot.getFile().toString()));
            
            if(newFile.exists()) {
                try { fileService.delete(newFile); } catch (IOException e) { /* We dont care if deletion fails, it probably means the file already got deleted or does not exist */}
            }

            if (f.isFile()) {
                fileService.copy(f, newFile, bufferSize);
            } else {
                fileService.createDirectory(newFile);
            }
        } catch (IOException ex) { }
    }

    /**
     * Copy the given {@code File} that is present in the source-directory or
     * one of the subdirectories to the mirror directory. The relative path from
     * the root-folder is kept:
     *
     * For example; C:\Test\ is the root folder that is being mirrored, and
     * C:\Mirror\ is the root for the mirror. When copying
     * C:\Test\foldera\folderb\file.mp4 to the mirror, it will be copied to
     * C:\Mirror\foldera\folderb\file.mp4.
     *
     * @param s The {@code FileSnapshot} to be copied to the mirror.
     */
    private void copyToMirror(FileSnapshot s) {
        if (s.isDirectory()) {
            for(FileSnapshot child : s.getChildren()) {
                copyToMirror(child);
            }
        } else {
            copyToMirror(new File(s.getFile().toString()));
        }
    }

    /**
     * Delete the mirror of the file which the given {@code FileSnapshot}
     * denotes.
     *
     * For example; C:\Test\ is the root folder that is being mirrored, and
     * C:\Mirror\ is the root for the mirror. When deleting the mirrored file of
     * C:\Test\foldera\folderb\file.mp4, then C:\Mirror\foldera\folderb\file.mp4
     * will be removed.
     *
     * @param s The file to be removed to the mirror.
     */
    private void deleteSourceSnapshotFromMirror(FileSnapshot s) {
        Mirror.this.deleteSourceSnapshotFromMirror(new File(s.getFile().toString()));
    }

    /**
     * Delete the mirror of the file which the given {@code File}
     * denotes.
     *
     * For example; C:\Test\ is the root folder that is being mirrored, and
     * C:\Mirror\ is the root for the mirror. When deleting the mirrored file of
     * C:\Test\foldera\folderb\file.mp4, then C:\Mirror\foldera\folderb\file.mp4
     * will be removed.
     *
     * @param f 
     */
    private void deleteSourceSnapshotFromMirror(File f) {
        try {
            secureDelete(new File(mirrorSnapshot.getFile().toString() + getRelativePath(f, originSnapshot.getFile().toString())));
        } catch (IOException e) { }
    }

    /**
     * First check if the origin folder is still accessible. If so, then it is safe
     * to remove the file. If not, the origin may have gone offline due to hardware failure
     * or any other number of reasons. At that point, no files should be removed from the mirror
     * just to be safe.
     * @param f
     * @throws IOException 
     */
    private void secureDelete(File f) throws IOException {
        securityCheck();
        fileService.delete(f);
    }

    /**
     * Checks if both the mirror & origin are still accessible. If not, the application
     * is instantly shut down.
     */
    private void securityCheck() {
        if (!originSnapshot.getFile().toFile().exists()) {
            System.err.println("Could not find source folder.");
            System.exit(0);
        }

        if (!mirrorSnapshot.getFile().toFile().exists()) {
            System.err.println("Could not find mirror folder.");
            System.exit(0);
        }
    }

    /**
     * Store the library to the disk. This significantly improves
     * first-iteration performance when the program is restarted.
     *
     * @throws IOException When the library could not be stored, due to lack of
     * access to the output file.
     */
    private void storeLibrary() throws IOException {
        // Set the output stream to the correct folder.
        String path = getLibraryPath();
        File out = new File(path + ".tmp");
        out.getParentFile().mkdirs();
        out.createNewFile();

        // Print the library values.
        originSnapshot.store(out);

        // Remove the old library file.
        File nf = new File(path);
        if (nf.exists()) {
            nf.delete();
        }

        // Rename the temporary library file.
        out.renameTo(nf);
    }

    /**
     * Obtain the path to the library file.
     *
     * @return Library to the library file.
     */
    private String getLibraryPath() {
        return "mirrors" + File.separator + mirrorName + ".cm";
    }

    /**
     * Obtain the relative path to a file from a root.
     *
     * @param f The file to which a relative path has to be obtained.
     * @param root Root at which the path should start.
     * @return Relative path to a file from a root.
     */
    private String getRelativePath(File f, String root) {
        String path = f.getAbsolutePath().replace(root, "");
        if (path.equals("")) {
            return path;
        }

        if (!(path.charAt(0) + "").equals("/")) {
            path = File.separator + path;
        }

        return path;
    }

    /**
     * Returns the interval (in seconds) at which this {@code Mirror}
     * should be updated.
     *
     * @return The interval (in seconds) at which this {@code Mirror}
     * should be updated.
     */
    public int getInterval() {
        return interval;
    }
    
    /**
     * Returns true of the Mirror is already running.
     * @return True if the Mirror is already running, false otherwise.
     */
    public boolean isBussy() {
        return bussy;
    }
}
