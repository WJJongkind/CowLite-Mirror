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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileSystemView;

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
 * @version 2018-03-14
 */
public class FileChecker {
    
    // MARK: - Properties

    /**
     * The source disk/folder which has to be duplicated.
     */
    private final FileSnapshot ORIGIN;

    /**
     * The target disk/folder, also called the mirror. Is a duplicate of the
     * source disk/folder.
     */
    private final FileSnapshot MIRROR;

    /**
     * Multiplier of the byte-buffer for reading/writing files.
     */
    private final double BUFFER_MULTIPLIER;

    /**
     * The interval at which this checker should run in seconds.
     */
    private final int INTERVAL;

    /**
     * The maximum file size that will be mirrored.
     */
    private final long MAX_SIZE;

    /**
     * The name of the mirror.
     */
    private final String MIRROR_NAME;
    
    /**
     * Whether or not the file checker is already running.
     */
    private boolean bussy;
    
    // MARK: - Object lifecycle

    /**
     * Instantiates a new FileChecker object and if available, load the library
     * for this filechecker.
     *
     * @param pathOrigin Path to the source for the mirror: the folder/disk that
     * should be duplicated.
     * @param pathMirror Path to the mirror: the folder/disk that is a
     * duplicate.
     * @param bufferMultiplier Multiplier for the buffer block-size. Default
     * buffer block-size is 8192. A multiplier size of 2 will make the
     * block-size 16384 bytes.
     * @param interval The interval at which the file checker should run.
     * @param maxFileSize The maximum size of files that are to be checked.
     * @throws Exception When the lock-file could not be created or the origin,
     * mirror or temp_folder are not existing folders.
     */
    public FileChecker(File origin, File mirror, double bufferMultiplier, int interval, long maxFileSize) throws Exception {
        BUFFER_MULTIPLIER = bufferMultiplier;
        MAX_SIZE = maxFileSize;

        if (BUFFER_MULTIPLIER < 0.01) {
            throw new IllegalArgumentException("Buffer multiplier should be atleast 0.01.");
        }

        // Initialize all FileSnapshots and immediatly update them because we want all subdirectories to be added to the structure
        ORIGIN = new FileSnapshot(Paths.get(origin.getAbsolutePath()));
        MIRROR = new FileSnapshot(Paths.get(mirror.getAbsolutePath()));

        if (!ORIGIN.isDirectory() || !MIRROR.isDirectory()) {
            throw new IllegalArgumentException("One or more of the selected filepaths is invalid.");
        }
        
        MIRROR.update(null, null, null);
        ORIGIN.update(null, null, null);
        
        MIRROR_NAME = makeMirrorName();

        INTERVAL = interval;
        bussy = false;
        
        loadLibrary();
    }
    
    private String makeMirrorName() throws NoSuchAlgorithmException {
        String unhashedName = ORIGIN.getFile().toFile().toString() + "-" + MIRROR.getFile().toFile().toString();
        
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(unhashedName.getBytes(StandardCharsets.UTF_8));
        return new String(Base64.getEncoder().encode(hash));
    }

    /**
     * Loads in the last known library for this mirror.
     *
     * @throws Exception FileDataReader error.
     */
    private void loadLibrary() throws Exception {
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
        crossReferenceLibrary(stored, ORIGIN, updated);

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
    public void checkFiles() {
        if (!bussy) {
            // Lock the file checker
            bussy = true;

            // ArrayLists that keep track of all changes
            ArrayList<FileSnapshot> deleted = new ArrayList<>();
            ArrayList<FileSnapshot> added = new ArrayList<>();
            ArrayList<FileSnapshot> updated = new ArrayList<>();

            // Update origin directory tree
            try {
                ORIGIN.update(deleted, added, updated);
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }

            // Send updates to the mirror directory
            List<FileSnapshot> filesToCopy = new ArrayList<>();
            filesToCopy.addAll(added);
            filesToCopy.addAll(updated);

            for (FileSnapshot s : filesToCopy) {
                try {
                    copyToMirror(s);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            for (FileSnapshot s : deleted) {
                try {
                    deleteSourceSnapshotFromMirror(s);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            // For validating the mirror directory
            ArrayList<FileSnapshot> missing = new ArrayList<>();
            ArrayList<FileSnapshot> garbage = new ArrayList<>();

            // Update the mirror directory
            try {
                MIRROR.update(null, null, null);
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }

            MIRROR.compareTo(ORIGIN, missing, garbage);

            for (FileSnapshot s : missing) {
                try {
                    copyToMirror(s);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            for (FileSnapshot s : garbage) {
                try {
                    secureDelete(s.getFile().toFile());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            if (!added.isEmpty() || !updated.isEmpty() || !deleted.isEmpty()) {
                try {
                    storeLibrary();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        bussy = false;
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
        if (!f.exists() || f.length() > MAX_SIZE) {
            return;
        }

        securityCheck();
        try {
            File newFile = new File(MIRROR.getFile().toString() + getRelativePath(f, ORIGIN.getFile().toString()));
            
            if(newFile.exists()) {
                try { FileIO.delete(newFile); } catch (Exception e) { /* We dont care if deletion fails, it probably means the file already got deleted or does not exist */}
            }

            if (f.isFile()) {
                FileIO.copy(f, newFile, BUFFER_MULTIPLIER);
            } else {
                FileIO.createDirectory(newFile);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
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
     * denotes from the mirror.
     *
     * For example; C:\Test\ is the root folder that is being mirrored, and
     * C:\Mirror\ is the root for the mirror. When deleting the mirrored file of
     * C:\Test\foldera\folderb\file.mp4, then C:\Mirror\foldera\folderb\file.mp4
     * will be removed.
     *
     * @param s The file to be removed to the mirror.
     */
    private void deleteSourceSnapshotFromMirror(FileSnapshot s) {
        FileChecker.this.deleteSourceSnapshotFromMirror(new File(s.getFile().toString()));
    }

    private void deleteSourceSnapshotFromMirror(File f) {
        try {
            secureDelete(new File(MIRROR.getFile().toString() + getRelativePath(f, ORIGIN.getFile().toString())));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void secureDelete(File f) throws IOException {
        securityCheck();
        FileIO.delete(f);
    }

    private void securityCheck() {
        if (!ORIGIN.getFile().toFile().exists()) {
            System.err.println("Could not find source folder.");
            System.exit(0);
        }

        if (!MIRROR.getFile().toFile().exists()) {
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

        // Print the library values.
        ORIGIN.store(out);

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
        // TODO memory leak?
        // Obtain documents folder. Possibly this way causes memory leaks?
        JFileChooser fr = new JFileChooser();
        FileSystemView fw = fr.getFileSystemView();

        // Generate path string
        String originLetter = Paths.get(ORIGIN.getFile().toString()).getRoot().toString();
        String mirrorLetter = Paths.get(MIRROR.getFile().toString()).getRoot().toString();
        String path = fw.getDefaultDirectory().toString()
                + File.separator + "CowLite Mirror" + File.separator + MIRROR_NAME;

        return path;
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
     * Returns the interval (in seconds) at which this {@code FileChecker}
     * should be updated.
     *
     * @return The interval (in seconds) at which this {@code FileChecker}
     * should be updated.
     */
    public int getInterval() {
        return INTERVAL;
    }
}
