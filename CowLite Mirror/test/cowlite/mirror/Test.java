/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cowlite.mirror;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import static java.lang.Math.log;

/**
 *
 * @author Wessel
 */
public class Test {
    
    public static void main(String[] args) {
        System.out.println(isCompletelyWritten(new File("C:\\Users\\Wessel\\Documents\\iktg_gia_paige_lc042715_1080p_12000.mp4")));
    }
    private static boolean isCompletelyWritten(File file) {
    RandomAccessFile stream = null;
    try {
        stream = new RandomAccessFile(file, "rw");
        return true;
    } catch (Exception e) {
        e.printStackTrace();
    } finally {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    return false;
}
}
