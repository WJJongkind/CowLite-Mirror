package Tests;


import Mocks.FileSnapshotMock;
import cowlite.mirror.FileService;
import cowlite.mirror.FileSnapshot;
import cowlite.mirror.Mirror;
import java.io.File;
import java.io.IOException;
import org.junit.After;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import static org.mockito.Mockito.verify;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author Wessel Jelle Jongkind
 */
public class MirrorTests {
    
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
    
    // MARK: - sut
    
    private Mirror mirror;
    
    // MARK: - collaborators
    
    private FileSnapshotMock origin;
    private FileSnapshotMock target;
    @Mock
    private FileService fileService;
    
    // MARK: - Setup & Teardown
    
    @Before
    public void setUp() {//FileSnapshot origin, FileSnapshot target, FileService fileService,  int bufferSize, int interval, long maxFileSize
        try {
            createTestFiles(RelativePaths.sutRoot);
            createTestFiles(RelativePaths.comparableRoot);
            origin = new FileSnapshotMock(new File(RelativePaths.sutRoot));
            target = new FileSnapshotMock(new File(RelativePaths.comparableRoot));
            fileService = Mockito.mock(FileService.class);
            
            mirror = new Mirror(origin, target, fileService, 1, 1, 100000);
        } catch(Exception e) {
            e.printStackTrace();
            fail("Unexpected error occured");
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
    public void testWhenFileIsAddedThenIsMirrored() throws IOException {
        // Configure the mocks
        FileSnapshot added1 = new FileSnapshot(new File(RelativePaths.sutRoot + RelativePaths.fileInRoot));
        FileSnapshot added2 = new FileSnapshot(new File(RelativePaths.sutRoot + RelativePaths.folderInFolderInFolderInRoot));
        origin.added.add(added1);
        origin.added.add(added2);
        
        // Run the mirror so that mock is triggered to copy files
        mirror.checkFiles();
        
        // The new files we expect to be copied
        File newFile = new File(RelativePaths.comparableRoot + RelativePaths.fileInRoot).getAbsoluteFile();
        File folderInFolderInNewDirectory = new File(RelativePaths.comparableRoot + RelativePaths.folderInFolderInFolderInRoot).getAbsoluteFile();
        
        
        // Verify that the methods were called correctly
        verify(fileService).copy(added1.getFile().toFile(), newFile, 1);
        verify(fileService).createDirectory(folderInFolderInNewDirectory);
        assertTrue(origin.storeCalled);
    }
    
    @Test
    public void testWhenFileIsRemovedThenIsRemoved() throws Exception {
        // Configure the mocks
        FileSnapshot removed = new FileSnapshot(new File(RelativePaths.sutRoot + RelativePaths.folderInRoot));
        origin.deleted.add(removed);
        
        // We expect this file to be deleted
        File expectDeleted = new File(RelativePaths.comparableRoot + RelativePaths.folderInRoot).getAbsoluteFile();
        
        // Run the mirror, so that it attempts to delete the file
        mirror.checkFiles();
        
        verify(fileService).delete(expectDeleted);
        assertTrue(origin.storeCalled);
    }
    
    @Test
    public void testWhenFileIsUpdatedThenIsUpdated() throws Exception {
        // Configure the mocks
        FileSnapshot updated = new FileSnapshot(new File(RelativePaths.sutRoot + RelativePaths.fileInRoot));
        origin.updated.add(updated);
        
        // Run the mirror so that mock is triggered to copy files
        mirror.checkFiles();
        
        // The new files we expect to be copied
        File updatedFile = new File(RelativePaths.comparableRoot + RelativePaths.fileInRoot).getAbsoluteFile();
        
        // Verify that the methods were called correctly
        verify(fileService).copy(updated.getFile().toFile(), updatedFile, 1);
        assertTrue(origin.storeCalled);
    }
    
    @Test
    public void testWhenFileIsMissingThenIsAddedToMirror() throws Exception {
        // Configure the mocks
        FileSnapshot missing = new FileSnapshot(new File(RelativePaths.sutRoot + RelativePaths.fileInRoot));
        target.missing.add(missing);
        
        // Run the mirror so that mock is triggered to copy files
        mirror.checkFiles();
        
        // The new files we expect to be copied
        File missingFile = new File(RelativePaths.comparableRoot + RelativePaths.fileInRoot).getAbsoluteFile();
        
        // Verify that the methods were called correctly
        verify(fileService).copy(missing.getFile().toFile(), missingFile, 1);
        assertFalse(origin.storeCalled);
    }
    
    @Test
    public void testWhenFileIsUnknownThenIsRemvedFromMirror() throws Exception {
        // Configure the mocks
        FileSnapshot additional = new FileSnapshot(new File(RelativePaths.comparableRoot + RelativePaths.fileInRoot));
        target.additional.add(additional);
        
        // Run the mirror so that mock is triggered to copy files
        mirror.checkFiles();
        
        // The new files we expect to be copied
        File additionalFile = new File(RelativePaths.comparableRoot + RelativePaths.fileInRoot).getAbsoluteFile();
        
        // Verify that the methods were called correctly
        verify(fileService).delete(additionalFile);
        assertFalse(origin.storeCalled);
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
}
