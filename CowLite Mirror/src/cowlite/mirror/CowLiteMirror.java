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
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileSystemView;

/**
 * Default class for CowLite Mirror. Loads in all settings that were previously stored,
 * such as previously configured mirrors. Currently, it automatically starts all mirrors
 * but this should change soon when a GUI will be added to the project.
 * 
 * @author Wessel Jongkind
 * @version 2018-02-17
 */
public class CowLiteMirror {

    /**
     * The timer that is used to start mirror checks.
     */
    public static IntervalTimer timer;
    
    /**
     * @param args the command line arguments
     * @throws java.lang.Exception When start settings could not be found or loaded.
     */
    public static void main(String[] args) throws Exception {
        //Load in pre-configured mirrors
        FileDataReader in = new FileDataReader();
        JFileChooser fr = new JFileChooser();
        FileSystemView fw = fr.getFileSystemView();
        in.setPath(fw.getDefaultDirectory().getAbsolutePath() + "\\CowLite Mirror\\mirrorsettings.cm");
        
        //Used for making sure no mirrors conflict with each other
        ArrayList<String> roots = new ArrayList<>();
        ArrayList<FileChecker> checkers = new ArrayList<>();
        
        //Launch settings...
        ArrayList<String> launchSettings = in.getDataStringLines();
        int maxcores = Integer.parseInt(launchSettings.get(0));
        launchSettings.remove(0);
        
        //Iterate over the pre-configured mirrors and store them.
        for(String s : launchSettings) {
            //Current line is a mirror configuration. Obtain the settings
            String[] params = s.split("\\|\\|");
            String origin = params[0];
            String mirror = params[1];
            double bufferMultiplier = Double.parseDouble(params[2]);
            int timerInterval = Integer.parseInt(params[3]);
            String tempPath = params[4];
            
            //Make sure the mirror does not conflict
            boolean legal = true;
            for(String src : roots)
                if(src.contains(origin) || origin.contains(src) || src.contains(mirror) || mirror.contains(src) || src.contains(tempPath) || tempPath.contains(src))
                    legal = false;
            
            //Only legal mirrors are added
            if(legal) {
                roots.add(origin);
                roots.add(mirror);
                roots.add(tempPath);
                checkers.add(new FileChecker(origin, mirror, tempPath, bufferMultiplier, timerInterval));
            }
        }
        
        // Start the timer, which will run the filecheckers.
        timer = new IntervalTimer(checkers, maxcores);
        
        // To prevent the application from stopping prematurely...
        CountDownLatch latch = new CountDownLatch(1);
        latch.await();
    }
    
}
