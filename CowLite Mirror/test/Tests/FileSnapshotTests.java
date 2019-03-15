package Tests;

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

import cowlite.mirror.DefaultFileService;
import cowlite.mirror.FileService;
import cowlite.mirror.FileSnapshot;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author Wessel Jelle Jongkind
 */
public class FileSnapshotTests {

    // MARK: - Constants
    
    private static final class RelativePaths {
        public static final String sutRoot = "sut";
        public static final String comparableRoot = "comparable";
        public static final String fileInRoot = "/file1.temp";
        public static final String folderInRoot = "/folder1";
        public static final String fileInFolderInRoot = folderInRoot + "/file2.temp";
        public static final String folderInFolderInRoot = folderInRoot + "/folder2";
        public static final String folderInFolderInFolderInRoot = folderInFolderInRoot + "/folder3";
    }

    // MARK: - SUT
    
    private FileSnapshot sut;

    // MARK: - Collaborators
    
    private FileSnapshot snapshot;
    private ArrayList<FileSnapshot> added;
    private ArrayList<FileSnapshot> updated;
    private ArrayList<FileSnapshot> deleted;
    private ArrayList<FileSnapshot> missingFiles;
    private ArrayList<FileSnapshot> additionalFiles;
    
    // MARK: - Test-utilities
    
    FileService fileService = new DefaultFileService();

    // MARK: - Setup & teardown
    
    @Before
    public void setUp() {

        try {
            createTestFiles(RelativePaths.sutRoot);
            createTestFiles(RelativePaths.comparableRoot);
            sut = new FileSnapshot(Paths.get(new File(RelativePaths.sutRoot).toURI()));
            snapshot = new FileSnapshot(Paths.get(new File(RelativePaths.comparableRoot).toURI()));
            added = new ArrayList<>();
            updated = new ArrayList<>();
            deleted = new ArrayList<>();
            missingFiles = new ArrayList<>();
            additionalFiles = new ArrayList<>();
        } catch (Exception e) {
            e.printStackTrace();
            fail("Unexpected exception occured");
        }
    }

    @After
    public void tearDown() {
        try {
            fileService.delete(new File(RelativePaths.sutRoot));
            fileService.delete(new File(RelativePaths.comparableRoot));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // MARK: - Tests
    
    @Test
    public void testInitialUpdate() {
        try {
            sut.update(deleted, added, updated);

            List<String> pathStrings = convertToStringList(added);
            assertTrue(pathStrings.contains(new File(RelativePaths.sutRoot + RelativePaths.fileInRoot).getAbsolutePath()));
            assertTrue(pathStrings.contains(new File(RelativePaths.sutRoot + RelativePaths.folderInRoot).getAbsolutePath()));
            assertTrue(pathStrings.contains(new File(RelativePaths.sutRoot + RelativePaths.fileInFolderInRoot).getAbsolutePath()));
            assertTrue(pathStrings.contains(new File(RelativePaths.sutRoot + RelativePaths.folderInFolderInRoot).getAbsolutePath()));
            assertTrue(pathStrings.contains(new File(RelativePaths.sutRoot + RelativePaths.folderInFolderInFolderInRoot).getAbsolutePath()));
        } catch (Exception e) {
            e.printStackTrace();
            fail("unexpected exception occured"); // Fail test
        }
    }

    @Test
    public void testWhenFileIsAddedThenSnapshotAddsToList() {
        try {
            File f = new File(RelativePaths.sutRoot + RelativePaths.fileInRoot);
            assertTrue(f.delete());
            sut.update(null, null, null);
            assertTrue(f.createNewFile());

            sut.update(deleted, added, updated);
            assertEquals(added.size(), 1);

            List<String> converted = convertToStringList(added);
            assertTrue(converted.contains(f.getAbsolutePath()));
        } catch (Exception e) {
            e.printStackTrace();
            fail("unexpected exception occured"); // Fail test
        }
    }

    @Test
    public void testWhenFileIsDeletedThenSnapshotAddsToList() {
        try {
            sut.update(null, null, null);
            File f = new File(RelativePaths.sutRoot + RelativePaths.fileInRoot);
            assertTrue(f.delete());

            sut.update(deleted, added, updated);
            assertEquals(1, deleted.size());

            List<String> converted = convertToStringList(deleted);
            assertTrue(converted.contains(f.getAbsolutePath()));
        } catch (Exception e) {
            e.printStackTrace();
            fail("unexpected exception occured"); // Fail test
        }
    }

    @Test
    public void testWhenFileIsUpdatedThenSnapshtoAddsToList() {
        try {
            sut.update(null, null, null);
            File f = new File(RelativePaths.sutRoot + RelativePaths.fileInFolderInRoot);
            PrintWriter out = new PrintWriter(f);
            out.print("This is some juicy updated text");
            out.close();

            sut.update(deleted, added, updated);
            assertEquals(1, updated.size());

            List<String> converted = convertToStringList(updated);
            assertTrue(converted.contains(f.getAbsolutePath()));
        } catch (Exception e) {
            e.printStackTrace();
            fail("unexpected exception occured"); // Fail test
        }
    }

    @Test
    public void testWhenRootIsDeletedThenAddsSelfToList() {
        try {
            sut.update(null, null, null);
            File f = new File(RelativePaths.sutRoot);
            fileService.delete(f);

            sut.update(deleted, added, updated);
            assertEquals(1, deleted.size());

            List<String> converted = convertToStringList(deleted);
            assertTrue(converted.contains(f.getAbsolutePath()));
        } catch (Exception e) {
            e.printStackTrace();
            fail("unexpected exception occured"); // Fail test
        }
    }

    @Test
    public void testWhenFileTurnsIntoFolderThenIsAddedToUpdated() {
        try {
            sut.update(null, null, null);
            File f = new File(RelativePaths.sutRoot + RelativePaths.fileInFolderInRoot);
            assertTrue(f.delete());
            assertTrue(f.mkdir());

            sut.update(deleted, added, updated);
            assertEquals(1, updated.size());

            List<String> converted = convertToStringList(updated);
            assertTrue(converted.contains(f.getAbsolutePath()));
        } catch (Exception e) {
            e.printStackTrace();
            fail("unexpected exception occured"); // Fail test
        }
    }

    @Test
    public void testWhenFolderTurnsIntoFileThenIsAddedToUpdated() {
        try {
            sut.update(null, null, null);
            File f = new File(RelativePaths.sutRoot + RelativePaths.folderInFolderInFolderInRoot);
            assertTrue(f.delete());
            assertTrue(f.createNewFile());

            sut.update(deleted, added, updated);
            assertEquals(1, updated.size());

            List<String> converted = convertToStringList(updated);
            assertTrue(converted.contains(f.getAbsolutePath()));
        } catch (Exception e) {
            e.printStackTrace();
            fail("unexpected exception occured"); // Fail test
        }
    }

    @Test
    public void testCompareToWithMissingFile() {
        try {
            File f = new File(RelativePaths.sutRoot + RelativePaths.fileInFolderInRoot);
            assertTrue(f.delete());
            sut.update(null, null, null);
            snapshot.update(null, null, null);

            sut.compareTo(snapshot, missingFiles, additionalFiles);
            assertEquals(1, missingFiles.size());

            List<String> converted = convertToStringList(missingFiles);
            assertTrue(converted.contains(new File(RelativePaths.comparableRoot + RelativePaths.fileInFolderInRoot).getAbsolutePath()));
        } catch (Exception e) {
            e.printStackTrace();
            fail("unexpected exception occured"); // Fail test
        }
    }

    @Test
    public void testCompareToWithUnknownFile() {
        try {
            File f = new File(RelativePaths.comparableRoot + RelativePaths.fileInFolderInRoot);
            assertTrue(f.delete());
            sut.update(null, null, null);
            snapshot.update(null, null, null);

            sut.compareTo(snapshot, missingFiles, additionalFiles);
            assertEquals(1, additionalFiles.size());

            List<String> converted = convertToStringList(additionalFiles);
            assertTrue(converted.contains(new File(RelativePaths.sutRoot + RelativePaths.fileInFolderInRoot).getAbsolutePath()));
        } catch (Exception e) {
            e.printStackTrace();
            fail("unexpected exception occured"); // Fail test
        }
    }

    // MARK: - Helper methods
    
    private void createTestFiles(String rootFolder) throws IOException {
        File f = new File(rootFolder);
        f.mkdirs();

        f = new File(rootFolder + RelativePaths.fileInRoot);
        f.createNewFile();

        f = new File(rootFolder + RelativePaths.folderInRoot);
        f.mkdir();

        f = new File(rootFolder + RelativePaths.fileInFolderInRoot);
        f.createNewFile();

        f = new File(rootFolder + RelativePaths.folderInFolderInRoot);
        f.mkdir();

        f = new File(rootFolder + RelativePaths.folderInFolderInFolderInRoot);
        f.mkdir();
    }

    private List<String> convertToStringList(List<FileSnapshot> source) {
        ArrayList<String> result = new ArrayList<>();

        for (FileSnapshot s : source) {
            result.add(s.getFile().toFile().getAbsolutePath());
        }

        return result;
    }
}
