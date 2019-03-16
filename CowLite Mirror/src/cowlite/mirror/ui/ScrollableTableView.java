/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cowlite.mirror.ui;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

/**
 *
 * @author Wessel
 */
public class ScrollableTableView<DataType extends TableRepresentable> extends JPanel implements MouseListener {
    
    // MARK: - Private properties
    
    private final JScrollPane scrollView;
    private final JTable tableView;
    private final DefaultTableModel tableData;
    private final List<DataType> rows;
    private final List<TableViewObserver<DataType>> observers;
    
    // MARK: - Object lifecycle
    
    public ScrollableTableView(String[] headers) {
        super();
        
        rows = new ArrayList<>();
        observers = new ArrayList<>();

        tableView = new JTable(new Object[0][0], headers);
        tableData = (DefaultTableModel) tableView.getModel();
        
        scrollView = new JScrollPane(tableView);
        scrollView.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollView.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        
        layoutSubViews();
    }
    
    private void layoutSubViews() {        
        super.setLayout(new GridBagLayout());
        super.add(scrollView, Layout.scrollViewLayout());
    }
    
    // MARK: - Public methods
    
    public void addRow(DataType row) {
        tableData.addRow(row.getTableRow());
        rows.add(row);
        repaint();
    }
    
    public void addRows(DataType[] rows) {
        for(DataType row : rows) {
            this.addRow(row);
        }
        repaint();
    }
    
    public void setRows(DataType[] rows) {
        clearRows();
        this.addRows(rows);
        repaint();
    }
    
    public void clearRows() {
        tableView.setModel(new DefaultTableModel());
        repaint();
    }

    // MARK: - MouseListener
    
    @Override
    public void mouseClicked(MouseEvent e) {}

    @Override
    public void mousePressed(MouseEvent e) {}

    @Override
    public void mouseReleased(MouseEvent e) {
        
        if(e.getClickCount() == 1 && tableView.getSelectedRow() != -1) {
            for(TableViewObserver<DataType> observer: observers) {
                observer.didSelectData(rows.get(tableView.getSelectedRow()));
            }
        }
        
        if(e.getClickCount() == 2 && tableView.getSelectedRow() != -1) {
            for(TableViewObserver<DataType> observer: observers) {
                observer.didDoubleClickData(rows.get(tableView.getSelectedRow()));
            }
        }
        
    }

    @Override
    public void mouseEntered(MouseEvent e) {}

    @Override
    public void mouseExited(MouseEvent e) {}
    
    // MARK: - Layout properties
    
    private static final class Layout {
        
        public static GridBagConstraints scrollViewLayout() {
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.fill = GridBagConstraints.BOTH;
            constraints.weightx = 1;
            constraints.weighty = 1;
            constraints.gridx = 0;
            constraints.gridy = 0;
            constraints.gridwidth = 1;
            constraints.gridheight = 1;
            
            return constraints;
        }
        
    }
    
}
