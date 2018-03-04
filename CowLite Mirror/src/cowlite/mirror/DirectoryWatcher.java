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
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
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
    
    private final CopyOnWriteArrayList<DirectoryEvent> events;
    private final CopyOnWriteArrayList<DirectoryEvent> creationEvents;
    private final CopyOnWriteArrayList<DirectoryEvent> modificationEvents;
    private final CopyOnWriteArrayList<DirectoryEvent> deletionEvents;
    private final ConcurrentHashMap<String, DirectoryEvent> aggregatedEvents;
    
    private Trigger trigger;
    
    //private SemaPhore takeLock
    
    public DirectoryWatcher(String pathOrigin) throws Exception {
        watcher = FileSystems.getDefault().newWatchService();
        events = new CopyOnWriteArrayList<>();
        creationEvents = new CopyOnWriteArrayList<>();
        modificationEvents = new CopyOnWriteArrayList<>();
        deletionEvents = new CopyOnWriteArrayList<>();
        aggregatedEvents = new ConcurrentHashMap<>();
        trigger = new Trigger(0);
        
        System.out.println(pathOrigin);
        registerAll(Paths.get(pathOrigin));
    }
    
    @Override
    public void run() {
        boolean reset = true;
        while(true) {
            try {
                WatchKey key = watcher.take();
                String dir = key.watchable().toString();
                for(WatchEvent<?> ev : key.pollEvents()) {
                    WatchEvent e = (WatchEvent<Path>) ev;
                    String path = dir + "\\" + e.context();
                    File f = new File(path);
                    
                    if(e.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                        processEvent(new DirectoryEvent(DirectoryEvent.FILE_CREATE, f));
                        if(f.isDirectory()) {
                            registerAll(Paths.get(path));
                            addFilesAsEvents(path, DirectoryEvent.FILE_CREATE);
                        }
                        
                        reset = true;
                    } else if(e.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                        if(!f.isDirectory()) {
                            processEvent(new DirectoryEvent(DirectoryEvent.FILE_MODIFY, f));
                        }
                        
                        reset = true;
                    } else if(e.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                        processEvent(new DirectoryEvent(DirectoryEvent.FILE_DELETE, f));
                        
                        if(f.isDirectory()) {
                            reset = false;
                        } else {
                            reset = true;
                        }
                    }  
                }
                
                if(reset) {
                    key.reset();
                } else {
                    key.cancel();
                    registerAll(Paths.get(dir));
                }
            } catch (InterruptedException ex) {
                Logger.getLogger(DirectoryWatcher.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(DirectoryWatcher.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    
    private void processEvent(DirectoryEvent event) throws InterruptedException {
        System.out.println(event.getType() + "  " + event.getFile());
        boolean exists = aggregatedEvents.containsKey(event.getFile().getAbsolutePath());
        
        DirectoryEvent oldEvent = null;
        if(exists) {
            oldEvent = aggregatedEvents.get(event.getFile().getAbsolutePath());
        }
        
        switch (event.getType()) {
            case DirectoryEvent.FILE_MODIFY:
                if(oldEvent == null || (!creationEvents.contains(oldEvent) && !modificationEvents.contains(oldEvent))) {
                    modificationEvents.add(event);
                    aggregatedEvents.put(event.getFile().getAbsolutePath(), event);
                    
                    updateEvents(exists, oldEvent, event);
                }
                break;
            case DirectoryEvent.FILE_CREATE:
                if(!exists || deletionEvents.contains(oldEvent)) {
                    deletionEvents.remove(oldEvent);
                    creationEvents.add(event);
                    
                    updateEvents(exists, oldEvent, event);
                }
                break;
            case DirectoryEvent.FILE_DELETE:
                if(oldEvent != null && !deletionEvents.contains(oldEvent)) {
                    modificationEvents.remove(oldEvent);
                    creationEvents.remove(oldEvent);
                }
                
               // Paths.get(event.getFile().getAbsolutePath()).
                
                deletionEvents.add(event);
                updateEvents(exists, oldEvent, event);
                
                break;
        }
    }
    
    private void updateEvents(boolean exists, DirectoryEvent oldEvent, DirectoryEvent newEvent) {
        events.remove(oldEvent);
        events.add(newEvent);
        
        if(exists) {
            aggregatedEvents.replace(newEvent.getFile().getAbsolutePath(), newEvent);
        } else {
            aggregatedEvents.put(newEvent.getFile().getAbsolutePath(), newEvent);
        }

        trigger.release();
    }
    
    private void addFilesAsEvents(String path, int type) throws InterruptedException {
        addFilesAsEvents(new File(path), type);
    }
    
    private void addFilesAsEvents(File f, int type) throws InterruptedException {
        if(f.isDirectory()) {
            File[] contents = f.listFiles();
            
            if(contents != null && contents.length > 0) {
                for(File file : contents) {
                    addFilesAsEvents(file, type);
                }
            }
        } else if(f.exists()) {
            processEvent(new DirectoryEvent(type, f));
        }
    }

    public List<DirectoryEvent> getEvents() {
        ArrayList<DirectoryEvent> converted = new ArrayList<>(events);
        clearAll();
        return converted;
    }
    
    public List<DirectoryEvent> takeEvents(int minEvents) throws InterruptedException {
        trigger.reset();
        trigger = new Trigger(minEvents);
        trigger.await();
        
        return getEvents();
    }
    
    public List<DirectoryEvent> takeEvents(int time, boolean aged) throws InterruptedException {
        List<DirectoryEvent> events = null;
        
        do {
            CountDownLatch latch = new CountDownLatch(1);

            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    latch.countDown();
                }
            }, time);
            System.out.println(time);

            latch.await();
            //System.out.println(aged);
           // System.out.println(getAgedEvents(time).isEmpty());
            //System.out.println(getEvents().isEmpty());
        } while((aged && (events = getAgedEvents(time)).isEmpty()) ||
                (!aged && (events = getEvents()).isEmpty()));
        
        return events;
    }
    
    public List<DirectoryEvent> takeEvents(int minEvents, int time) throws InterruptedException {
        trigger.reset();
        trigger = new Trigger(minEvents);
        
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                trigger.reset();
            }
        }, (int) Math.round(time));
        
        trigger.await();
        
        return getEvents();
    }
    
    private List<DirectoryEvent> getAgedEvents(int time) {
        ArrayList<DirectoryEvent> aged = new ArrayList<>();
        
        for(DirectoryEvent e : events) {
            if(System.currentTimeMillis() - e.getTime() > time) {
                aged.add(e);
                aggregatedEvents.remove(e);
                deletionEvents.remove(e);
                creationEvents.remove(e);
                modificationEvents.remove(e);
            }
        }
        
        events.removeAll(aged);
        
        return aged;
    }
    
    private void clearAll() {
        events.clear();
        creationEvents.clear();
        modificationEvents.clear();
        deletionEvents.clear();
        aggregatedEvents.clear();
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
    
    private void registerAll(final Path start) throws IOException {
        // register directory and sub-directories
        
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                throws IOException {
                    System.out.println(dir.toString());
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
        private final long time;
        
        public DirectoryEvent(int type, File file) {
            this.type = type;
            this.file = file;
            this.time = System.currentTimeMillis();
        }
        
        public DirectoryEvent(int type, String path) {
            this.type = type;
            this.file = new File(path);
            this.time = System.currentTimeMillis();
        }

        public int getType() {
            return type;
        }

        public File getFile() {
            return file;
        }
        
        public long getTime() {
            return time;
        }
    }
    
    private class Trigger {
        private CountDownLatch latch;
        private int count;
        private int progress;
        
        public Trigger(int count) {
            this.count = count;
            progress = 0;
            
            latch = new CountDownLatch(1);
        }
        
        public void acquire() {
            progress--;
            System.out.println(progress);
        }
        
        public void release() {
            progress++;
            System.out.println(progress);
            checkTrigger();
        }
        
        public void reset() {
            progress = count;
            checkTrigger();
        }
        
        public void addProgress(int progress) {
            this.progress += progress;
            checkTrigger();
        }
        
        public void await() throws InterruptedException {
            latch.await();
        }
        
        private void checkTrigger() {
            if(progress >= count) {
                latch.countDown();
                latch = new CountDownLatch(1);
                progress = 0;
            }
        }
    }
}
