/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Mocks;

import cowlite.mirror.FileSnapshot;
import java.io.File;
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
    
    public FileSnapshotMock(File f) throws IOException, IllegalArgumentException {
        super(f);
    }
    
    @Override
    public void update(List<FileSnapshot> deleted, List<FileSnapshot> added, List<FileSnapshot> updated) throws IOException {
        if(deleted != null) deleted.addAll(deleted);
        if(updated != null) added.addAll(added);
        if(added != null) updated.addAll(updated);
    }
    
}
