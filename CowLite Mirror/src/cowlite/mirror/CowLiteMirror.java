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

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * Default class for CowLite Mirror. Loads in all settings that were previously
 * stored, such as previously configured mirrors. Currently, it automatically
 * starts all mirrors but this should change soon when a GUI will be added to
 * the project.
 *
 * @author Wessel Jelle Jongkind
 * @version 2018-02-17
 */
public class CowLiteMirror {

    /**
     * The timer that is used to start mirror checks.
     */
    public static FileCheckerTimer timer;
    
    /**
     * This enumeration defines all the possible arguments.
     */
    private static enum PossibleArgument {
        origin("origin"),
        mirror("mirror"),
        maxSize("maxsize"),
        interval("interval"),
        bufferMultiplier("buffermultiplier");
        
        final String rawValue;
        
        private PossibleArgument(String rawValue) {
            this.rawValue = rawValue;
        }
        
        static PossibleArgument stringToArgument(String rawValue) {
            switch(rawValue) {
                case "origin": return origin;
                case "mirror": return mirror;
                case "maxsize": return maxSize;
                case "interval": return interval;
                case "buffermultiplier": return bufferMultiplier;
                default: return null;
            }
        }
    }
    
    /**
     * This enumeration defines the valid arguments that are allowed.
     */
    private static final List<String> requiredArguments = Arrays.asList(PossibleArgument.origin.rawValue, PossibleArgument.mirror.rawValue, PossibleArgument.interval.rawValue, PossibleArgument.maxSize.rawValue);
    
    /**
     * The optional arguments that may be provided.
     */
    private static final List<String> optionalArguments = Arrays.asList(PossibleArgument.bufferMultiplier.rawValue);

    /**
     * @param args the command line arguments
     * @throws java.lang.Exception When start settings could not be found or
     * loaded.
     */
    public static void main(String[] args) throws Exception {
        System.out.println(new File("").getAbsolutePath());
        
        
        
        Map<PossibleArgument, String> arguments = new HashMap<>();
        List<String> missingArguments = new ArrayList<>(requiredArguments);
        
        for(String s : args) {
            String[] splitArgument = s.split("=");
            addArgument(arguments, missingArguments, splitArgument[0], String.join("", Arrays.copyOfRange(splitArgument, 1, splitArgument.length)));
        }
        
        checkMissingArguments(missingArguments);

        File origin = new File(arguments.get(PossibleArgument.origin));
        if (!origin.exists()) {
            System.out.println("Specified origin folder does not exist.");
        }
        
        File mirror = new File(arguments.get(PossibleArgument.mirror));
        if (!mirror.exists()) {
            System.out.println("Specified mirror folder does not exist.");
        }
        
        int timerInterval = -1;
        try {
            timerInterval = Integer.parseInt(arguments.get(PossibleArgument.interval));
        } catch(NumberFormatException e) {
            System.out.println("Argument for key " + PossibleArgument.interval.rawValue + " is invalid. Please specify the time in milliseconds.");
            System.exit(0);
        }
        
        long maxFileSize = -1;
        try {
            maxFileSize = Long.parseLong(arguments.get(PossibleArgument.maxSize));
        } catch(NumberFormatException e) {
            System.out.println("Argument for key " + PossibleArgument.maxSize.rawValue + " is invalid. Please specify the time in milliseconds.");
            System.exit(0);
        }
        
        int bufferMultiplier = 4;
        try {
            String stringRepresentation;
            if ((stringRepresentation = arguments.get(PossibleArgument.bufferMultiplier)) != null) {
                bufferMultiplier = Integer.parseInt(arguments.get(PossibleArgument.bufferMultiplier));
            }
        } catch(NumberFormatException e) {
            System.out.println("Argument for key " + PossibleArgument.bufferMultiplier.rawValue + " is invalid. Please specify the time in milliseconds.");
            System.exit(0);
        }
        
        FileChecker checker = new FileChecker(origin, mirror, bufferMultiplier, timerInterval, maxFileSize);

        // Start the timer, which will run the filecheckers.
        timer = new FileCheckerTimer(checker, true);

        // To prevent the application from stopping prematurely...
        CountDownLatch latch = new CountDownLatch(1);
        latch.await();
    }

    private static void addArgument(Map<PossibleArgument, String> arguments, List<String> missing, String key, String value) {
        PossibleArgument mapKey;
        if((mapKey = PossibleArgument.stringToArgument(key)) == null) {
            System.out.println("Unknown argument " + key + " was provided.");
            System.exit(0);
        }
        
        if(arguments.containsKey(mapKey)) {
            System.out.println("Argument " + key + " has been specified twice. Please specify it only once.");
            System.exit(0);
        }
        
        missing.remove(key);
        arguments.put(mapKey, value);
    }
    
    private static void checkMissingArguments(List<String> missingArguments) {
        if(missingArguments.size() > 0) {
            String message = "Too few arguments provided. Please provide values for: ";
            
            for(String missing: missingArguments) {
                message += missing + ", ";
            }
            
            message = message.substring(0, message.length() - 2);
            message += ".";
            System.out.println(message);
            System.exit(0);
        }
    }
}
