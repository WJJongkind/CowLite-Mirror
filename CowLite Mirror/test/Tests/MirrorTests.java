package Tests;


import Mocks.FileSnapshotMock;
import cowlite.mirror.FileService;
import cowlite.mirror.FileSnapshot;
import cowlite.mirror.Mirror;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import org.junit.After;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
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
        FileSnapshot added1 = new FileSnapshot(new File(RelativePaths.sutRoot + RelativePaths.fileInFolderInRoot));
        FileSnapshot added2 = new FileSnapshot(new File(RelativePaths.sutRoot + RelativePaths.folderInFolderInFolderInRoot));
        origin.added.add(added1);
        origin.added.add(added2);
        
        //verify(fileService).;
    }
    
    @Test
    public void testWhenFileIsRemovedThenIsRemoved() {
        
    }
    
    @Test
    public void testWhenFileIsUpdatedThenIsUpdated() {
        
    }
    
    @Test
    public void testWhenFileIsMissingThenIsAddedToMirror() {
        
    }
    
    @Test
    public void testWhenFileIsUnknownThenIsRemvedFromMirror() {
        
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
