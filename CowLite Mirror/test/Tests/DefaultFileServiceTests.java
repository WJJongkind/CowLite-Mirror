package Tests;


import cowlite.mirror.DefaultFileService;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.After;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Test;

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

/**
 *
 * @author Wessel Jelle Jongkind
 */
public class DefaultFileServiceTests {
    
    // MARK: - Test contstants
    
    private static final class RelativePaths {
        public static final String fileInRoot = "file1.temp";
        public static final String folderInRoot = "folder1";
    }
    
    // MARK: - SUT
    
    private DefaultFileService sut;
    
    // MARK: - Setup & teardown
    
    @Before
    public void setUp() {
        sut = new DefaultFileService();
    }
    
    @After
    public void tearDown() {
        try {
            sut.delete(new File(RelativePaths.folderInRoot));
            sut.delete(new File(RelativePaths.fileInRoot));
        } catch(IOException e) { }
    }
    
    // MARK: - Tests
    
    @Test
    public void testCopyStringPaths() {
        try {
            File file = makeTestFileWithContent();
            File targetDirectory = new File(RelativePaths.folderInRoot);
            assertTrue(targetDirectory.mkdir());
            
            sut.copy(file.getAbsolutePath(), targetDirectory.getAbsolutePath() + File.separator + RelativePaths.fileInRoot);
            
            File expectedFile = new File(RelativePaths.folderInRoot + File.separator + RelativePaths.fileInRoot);
            assertTrue(expectedFile.exists());
            
            byte[] sourceBytes = Files.readAllBytes(Paths.get(file.toURI()));
            byte[] targetBytes = Files.readAllBytes(Paths.get(file.toURI()));
            assertEquals(sourceBytes.length, targetBytes.length);
            
            for(int i = 0; i < sourceBytes.length; i++) {
                assertEquals(sourceBytes[i], targetBytes[i]);
            }
        } catch(IOException e) {
            e.printStackTrace();
            fail("Unexpected exception occured");
        }
    }
    
    @Test
    public void testCopyStringPathsWithBuffer() {
        try {
            File file = makeTestFileWithContent();
            File targetDirectory = new File(RelativePaths.folderInRoot);
            assertTrue(targetDirectory.mkdir());
            
            sut.copy(file.getAbsolutePath(), targetDirectory.getAbsolutePath() + File.separator + RelativePaths.fileInRoot, 3);
            
            File expectedFile = new File(RelativePaths.folderInRoot + File.separator + RelativePaths.fileInRoot);
            assertTrue(expectedFile.exists());
            
            byte[] sourceBytes = Files.readAllBytes(Paths.get(file.toURI()));
            byte[] targetBytes = Files.readAllBytes(Paths.get(file.toURI()));
            assertEquals(sourceBytes.length, targetBytes.length);
            
            for(int i = 0; i < sourceBytes.length; i++) {
                assertEquals(sourceBytes[i], targetBytes[i]);
            }
        } catch(IOException e) {
            e.printStackTrace();
            fail("Unexpected exception occured");
        }
    }
    
    @Test
    public void testCopyFile() {
        try {
            File file = makeTestFileWithContent();
            File targetDirectory = new File(RelativePaths.folderInRoot);
            assertTrue(targetDirectory.mkdir());
            
            sut.copy(file, new File(targetDirectory.getAbsolutePath() + File.separator + RelativePaths.fileInRoot));
            
            File expectedFile = new File(RelativePaths.folderInRoot + File.separator + RelativePaths.fileInRoot);
            assertTrue(expectedFile.exists());
            
            byte[] sourceBytes = Files.readAllBytes(Paths.get(file.toURI()));
            byte[] targetBytes = Files.readAllBytes(Paths.get(file.toURI()));
            assertEquals(sourceBytes.length, targetBytes.length);
            
            for(int i = 0; i < sourceBytes.length; i++) {
                assertEquals(sourceBytes[i], targetBytes[i]);
            }
        } catch(IOException e) {
            e.printStackTrace();
            fail("Unexpected exception occured");
        }
    }
    
    @Test
    public void testCopyFileWithBuffer() {
        try {
            File file = makeTestFileWithContent();
            File targetDirectory = new File(RelativePaths.folderInRoot);
            assertTrue(targetDirectory.mkdir());
            
            sut.copy(file, new File(targetDirectory.getAbsolutePath() + File.separator + RelativePaths.fileInRoot), 3);
            
            File expectedFile = new File(RelativePaths.folderInRoot + File.separator + RelativePaths.fileInRoot);
            assertTrue(expectedFile.exists());
            
            byte[] sourceBytes = Files.readAllBytes(Paths.get(file.toURI()));
            byte[] targetBytes = Files.readAllBytes(Paths.get(file.toURI()));
            assertEquals(sourceBytes.length, targetBytes.length);
            
            for(int i = 0; i < sourceBytes.length; i++) {
                assertEquals(sourceBytes[i], targetBytes[i]);
            }
        } catch(IOException e) {
            e.printStackTrace();
            fail("Unexpected exception occured");
        }
    }
    
    @Test
    public void testDeleteStringPath() {
        try {
            File directory = new File(RelativePaths.folderInRoot);
            assertTrue(directory.mkdir());
            
            File file = new File(RelativePaths.folderInRoot + File.separator + RelativePaths.fileInRoot);
            assertTrue(file.createNewFile());
            
            sut.delete(directory.getAbsolutePath());
            assertFalse(directory.exists());
            assertFalse(file.exists());
        } catch(IOException e) {
            e.printStackTrace();
            fail("Unexpected exception occured");
        }
    }
    
    @Test
    public void testDeleteFile() {
        try {
            File directory = new File(RelativePaths.folderInRoot);
            assertTrue(directory.mkdir());
            
            File file = new File(RelativePaths.folderInRoot + File.separator + RelativePaths.fileInRoot);
            assertTrue(file.createNewFile());
            
            sut.delete(directory);
            assertFalse(directory.exists());
            assertFalse(file.exists());
        } catch(IOException e) {
            e.printStackTrace();
            fail("Unexpected exception occured");
        }
    }
    
    @Test
    public void testDeletePath() {
        try {
            File directory = new File(RelativePaths.folderInRoot);
            assertTrue(directory.mkdir());
            
            File file = new File(RelativePaths.folderInRoot + File.separator + RelativePaths.fileInRoot);
            assertTrue(file.createNewFile());
            
            sut.delete(Paths.get(directory.toURI()));
            assertFalse(directory.exists());
            assertFalse(file.exists());
        } catch(IOException e) {
            e.printStackTrace();
            fail("Unexpected exception occured");
        }
    }
    
    @Test
    public void testCreateFileFromStringPath() {
        try {
            File file = new File(RelativePaths.folderInRoot + File.separator + RelativePaths.fileInRoot);
            assertFalse(new File(RelativePaths.folderInRoot).exists());
            
            sut.createFile(file.getAbsolutePath());
            
            assertTrue(file.exists());
        } catch(IOException e) {
            e.printStackTrace();
            fail("Unexpected exception occured");
        }
    }
    
    @Test
    public void testCreateFile() {
        try {
            File file = new File(RelativePaths.folderInRoot + File.separator + RelativePaths.fileInRoot);
            assertFalse(new File(RelativePaths.folderInRoot).exists());
            
            sut.createFile(file);
            
            assertTrue(file.exists());
        } catch(IOException e) {
            e.printStackTrace();
            fail("Unexpected exception occured");
        }
    }
    
    @Test
    public void testCreateDirectoryFromStringPath() {
        File targetDirectory = new File(RelativePaths.folderInRoot + File.separator + RelativePaths.folderInRoot);
        assertFalse(new File(RelativePaths.folderInRoot).exists());

        sut.createDirectory(targetDirectory.getAbsolutePath());

        assertTrue(targetDirectory.exists());
    }
    
    @Test
    public void  testCreateDirectory() {
        File targetDirectory = new File(RelativePaths.folderInRoot + File.separator + RelativePaths.folderInRoot);
        assertFalse(new File(RelativePaths.folderInRoot).exists());

        sut.createDirectory(targetDirectory);

        assertTrue(targetDirectory.exists());
    }
    
    // MARK: - Helper methods
    
    private File makeTestFileWithContent() throws IOException {
        File file = new File(RelativePaths.fileInRoot);
        assertTrue(file.createNewFile());
        
        PrintWriter out = null;
        
        try {
            out = new PrintWriter(file);
            out.print("Ohi there");
            out.flush();
        } catch(IOException e) {
            throw e;
        } finally {
            if (out != null) {
                out.close();
            }
        }
        
        return file;
    }
}
