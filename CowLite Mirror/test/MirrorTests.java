
import cowlite.mirror.FileService;
import cowlite.mirror.Mirror;
import java.io.File;
import java.io.IOException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

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
    
    @Mock
    private FileService fileService;
    
    // MARK: - Setup & Teardown
    
    @Before
    public void setUp() {
        //mirror = new M
    }
    
    @After
    public void tearDown() {
        
    }
    
    // MARK: - Tests
    
    @Test
    public void testWhenFileIsAddedThenIsMirrored() {
        
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
