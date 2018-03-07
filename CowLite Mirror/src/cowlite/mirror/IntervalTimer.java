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


import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Semaphore;
import javax.swing.JFileChooser;
import javax.swing.Timer;

/**
 * This class can be used to time the intervals at which filecheckers should be activated.
 * It does this multithreaded if requested. When too many threads are being used, the filecheckers
 * are added to a queue.
 * @author Wessel Jongkind
 * @version 2018-02-18
 */
public class IntervalTimer implements ActionListener
{
    /**
     * Map containing the checkers and the amount of ticks that have been registered for them.
     * One tick equals one second.
     */
    private static final HashMap<FileChecker, Integer> CHECKERS = new HashMap<>();
    
    /**
     * Queue if a filechecker could not be run because too many threads are
     * alive.
     */
    private static final LinkedList<FileChecker> QUEUE = new LinkedList<>();
    
    /**
     * Semaphore which is used to keep track of how many threads are alive.
     */
    private static Semaphore THREADS;
    
    /**
     * Temporary solution for forcibly exiting the program without the use of
     * task manager.
     */
    private static final File EXIT = new File(new JFileChooser().getFileSystemView().getDefaultDirectory().getAbsolutePath() + "\\CowLite Mirror\\stop.txt");
    
    /**
     * Timer used for timing the file checkers.
     */
    private static Timer timer;
    
    /**
     * Amount of cores on the machine this code is being run on.
     */
    private static int CORE_COUNT;
    
    /**
     * Instantiates and starts a new IntervalTimer.
     * @param checkers FileCheckers that should be timed.
     * @param maxcores Maximum amount of cores that should be used. A value of 0
     * will use one core for each FileChecker, or the maximum amount of system cores available if
     * there are too many file checkers.
     */
    public IntervalTimer(List<FileChecker> checkers, int maxcores)
    {
        /*
            Adding the checkers to the hashmap which is used to track the amount of ticks that they had since their last run.
        */
        for(FileChecker checker : checkers) {
            CHECKERS.put(checker, 0);
        }
        
        // Decide the number of threads that should be used.
        if(THREADS == null) {
            int cores = Runtime.getRuntime().availableProcessors();
            if(maxcores == 0)
                CORE_COUNT = cores;
            else
                CORE_COUNT = Math.min(cores, Math.min(maxcores, checkers.size()));

            THREADS = new Semaphore(CORE_COUNT);
        }
        
        // Start the timer if it has not been created and started yet.
        if(timer == null) {
            timer = new Timer(1000, this);
            timer.start();
        }
    }
    @Override
    public void actionPerformed(ActionEvent e) 
    {
        //Check if the program should stop checking.
        if(EXIT.exists())
            System.exit(0);
        
        // Clear the queue
        for(FileChecker checker : QUEUE) {
            if(THREADS.tryAcquire())
                runChecker(checker);
            else
                return;
        }
        
        /* 
            Iterate over all checkers and add one tick to their associated ticks.
            If a checker has gained enough ticks, it will be initiated to check files.
        */
        for(FileChecker checker : CHECKERS.keySet()) {
            CHECKERS.replace(checker, CHECKERS.get(checker) + 1);
            System.out.println(checker.getInterval() + "    " + CHECKERS.get(checker));
            
            // Try to a acquire a thread
            if(CHECKERS.get(checker) == checker.getInterval() && THREADS.tryAcquire()) {
                runChecker(checker);
            } else if(CHECKERS.get(checker) >= checker.getInterval()) {
                // No thread was available, add checker to the queue
                if(!QUEUE.contains(checker)) {
                    QUEUE.add(checker);
                }
            }
        }
    }
    
    /**
     * Runs a file checker on a new thread.
     * @param checker The checker that should be started.
     */
    private void runChecker(FileChecker checker) {
        new Thread(){
            @Override
            public void run() {
                try {
                    CHECKERS.replace(checker, 0);
                    checker.checkFiles();  
                    THREADS.release();
                } catch(Exception e) {
                    e.printStackTrace();
                    THREADS.release();
                }
            }
        }.start();
    }
    
}
