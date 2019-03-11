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
import javax.swing.Timer;

/**
 * This class is used to run FileCheckers at the interval that they are required to be
 * ran at.
 *
 * @author Wessel Jelle Jongkind
 * @version 2019-03-11 (yyyy-mm-dd)
 */
public class FileCheckerTimer implements ActionListener {
    
    /**
     * The FileChecker which has to be triggered at a given interval.
     */
    private final FileChecker checker;
    
    /**
     * Timer used for timing the file checkers.
     */
    private static Timer timer;
    
    /**
     * Instantiates and starts a new IntervalTimer.
     *
     * @param checker FileChecker that should be triggered at it's given interval.
     */
    public FileCheckerTimer(FileChecker checker, boolean shouldDoInitialCheck) {
        this.checker = checker;

        // Start the timer if it has not been created and started yet.
        if (timer == null) {
            timer = new Timer(checker.getInterval(), this);
            timer.start();
        }
        
        if (shouldDoInitialCheck) {
            checker.checkFiles();
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        checker.checkFiles();
    }
}
