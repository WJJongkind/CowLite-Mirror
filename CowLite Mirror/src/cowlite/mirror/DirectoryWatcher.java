package cowlite.mirror;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author MemeMeister
 */
public class DirectoryWatcher extends Thread {
    private final WatchService watcher;
    
    private final CopyOnWriteArrayList<String> modifyQueue;
    private final HashMap<String, Integer> newFileQueue;
    
    private final CopyOnWriteArrayList<DirectoryEvent> events;
    private final CopyOnWriteArrayList<DirectoryEvent> creationEvents;
    private final CopyOnWriteArrayList<DirectoryEvent> modificationEvents;
    private final CopyOnWriteArrayList<DirectoryEvent> deletionEvents;
    
    private final Semaphore lock;
    
    //private SemaPhore takeLock111
    
    public DirectoryWatcher(String pathOrigin, String pathMirror, String tempPath, int bufferMultiplier) throws Exception {
        watcher = FileSystems.getDefault().newWatchService();
        modifyQueue = new CopyOnWriteArrayList<>();
        newFileQueue = new HashMap<>();
        events = new CopyOnWriteArrayList<>();
        creationEvents = new CopyOnWriteArrayList<>();
        modificationEvents = new CopyOnWriteArrayList<>();
        deletionEvents = new CopyOnWriteArrayList<>();
        lock = new Semaphore(1);
        lock.acquire();
        
        registerAll(Paths.get(pathOrigin));
    }
    
    @Override
    public void run() {
        while(true) {
            try {
                WatchKey key = watcher.take();
                String dir = key.watchable().toString();
                for(WatchEvent<?> ev : key.pollEvents()) {
                    WatchEvent e = (WatchEvent<Path>) ev;
                    String path = dir + "\\" + e.context();
                    File f = new File(path);
                    
                    if(e.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                        if(f.isDirectory()) {
                            addEvent(new DirectoryEvent(DirectoryEvent.FILE_CREATE, f));
                            registerAll(Paths.get(path));
                            addFilesAsEvents(path, DirectoryEvent.FILE_CREATE);
                        } else {
                            newFileQueue.put(path, 1);
                        }
                    } else if(e.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                        if(!f.isDirectory()) {
                            if(newFileQueue.containsKey(path)) {
                                newFileQueue.replace(path, newFileQueue.get(path) + 1);
                                
                                if(newFileQueue.get(path) == 3 || f.length() == 0) {
                                    addEvent(new DirectoryEvent(DirectoryEvent.FILE_CREATE, f));
                                    newFileQueue.remove(path);
                                }
                            } else if(modifyQueue.contains(path)) {
                                addEvent(new DirectoryEvent(DirectoryEvent.FILE_MODIFY, f));
                                modifyQueue.remove(path);
                            } else {
                                modifyQueue.add(path);
                            }
                        }
                    } else if(e.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                        addEvent(new DirectoryEvent(DirectoryEvent.FILE_DELETE, f));
                    }
                    
                }
                
                key.reset();
            } catch (InterruptedException ex) {
                Logger.getLogger(DirectoryWatcher.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(DirectoryWatcher.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    
    private void addFilesAsEvents(String path, int type) {
        addFilesAsEvents(new File(path), type);
    }
    
    private void addFilesAsEvents(File f, int type) {
        if(f.isDirectory()) {
            File[] contents = f.listFiles();
            
            if(contents != null && contents.length > 0) {
                for(File file : contents) {
                    addFilesAsEvents(file, type);
                }
            }
        } else if(f.exists()) {
            addEvent(new DirectoryEvent(type, f));
        }
    }
    
    private void addEvent(DirectoryEvent event) {
        events.add(event);
        
        switch (event.getType()) {
            case DirectoryEvent.FILE_MODIFY:
                modificationEvents.add(event);
                break;
            case DirectoryEvent.FILE_CREATE:
                creationEvents.add(event);
                break;
            case DirectoryEvent.FILE_DELETE:
                deletionEvents.add(event);
                break;
        }
        
        lock.release();
    }

    public List<DirectoryEvent> getEvents() {
        return convertAndClearEvents(events);
    }
    
    public List<DirectoryEvent> takeEvents() throws InterruptedException {
        lock.acquire();
        return getEvents();
    }

    public List<DirectoryEvent> getCreationEvents() {
        return convertAndClearEvents(creationEvents);
    }

    public List<DirectoryEvent> getModificationEvents() {
        return convertAndClearEvents(modificationEvents);
    }

    public List<DirectoryEvent> getDeletionEvents() {
        return convertAndClearEvents(deletionEvents);
    }
    
    private ArrayList<DirectoryEvent> convertAndClearEvents(CopyOnWriteArrayList<DirectoryEvent> events) {
        ArrayList<DirectoryEvent> converted = new ArrayList<>(events);
        events.clear();
        return converted;
    }
    
    public static void main(String[] args) throws Exception {
        /*d.start();
        
        while(true) {
            List<DirectoryEvent> events = d.takeEvents();
            for(DirectoryEvent e : events) {
                System.out.println(e.getType() + " " + e.getFile().getAbsolutePath());
            }
        }*/
    }
    
    private void registerAll(final Path start) throws IOException {
        // register directory and sub-directories
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                throws IOException {
                    dir.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
                    return FileVisitResult.CONTINUE;
            }
        });
    }
    
    public class DirectoryEvent {
        public static final int FILE_CREATE = 1;
        public static final int FILE_MODIFY = 2;
        public static final int FILE_DELETE = 3;
        
        private final int type;
        private final File file;
        
        public DirectoryEvent(int type, File file) {
            this.type = type;
            this.file = file;
        }
        
        public DirectoryEvent(int type, String path) {
            this.type = type;
            this.file = new File(path);
        }

        public int getType() {
            return type;
        }

        public File getFile() {
            return file;
        }
    }
}
