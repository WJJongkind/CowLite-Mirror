/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cowlite.mirror.ui;

/**
 *
 * @author Wessel
 */
public interface TableViewObserver<Data extends TableRepresentable> {
    public void didSelectData(Data row);
    public void didDoubleClickData(Data row);
}
