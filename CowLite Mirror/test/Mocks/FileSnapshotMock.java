/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Mocks;

import cowlite.mirror.FileSnapshot;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Wessel Jelle Jongkind
 */
public class FileSnapshotMock extends FileSnapshot {
    
    public final List<FileSnapshot> deleted = new ArrayList<>();
    public final List<FileSnapshot> added = new ArrayList<>();
    public final List<FileSnapshot> updated = new ArrayList<>();
    public final List<FileSnapshot> missing = new ArrayList<>();
    public final List<FileSnapshot> additional = new ArrayList<>();
    public boolean storeCalled = false;
    
    public FileSnapshotMock(File f) throws IOException, IllegalArgumentException {
        super(f);
    }
    
    @Override
    public void update(List<FileSnapshot> deleted, List<FileSnapshot> added, List<FileSnapshot> updated) throws IOException {
        if(deleted != null) deleted.addAll(this.deleted);
        if(added != null) added.addAll(this.added);
        if(updated != null) updated.addAll(this.updated);
    }
    
    @Override
    public void compareTo(FileSnapshot other, List<FileSnapshot> missing, List<FileSnapshot> additionalFiles) {
        if(missing != null) missing.addAll(this.missing);
        if(additionalFiles != null) additionalFiles.addAll(this.additional);
    }
    
    @Override
    public void store(File f) throws FileNotFoundException, IOException {
        storeCalled = true;
    }
}
