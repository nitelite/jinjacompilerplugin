package com.xeneta.plugins;

import com.intellij.ui.components.JBPanel;
import com.intellij.ui.table.JBTable;

import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

public class DataModelPanel extends JBPanel {
    private JBTable table;
    private HashMap<String, String> data = new HashMap<>();

    public DataModelPanel(List<String> keywords) {
        super(new BorderLayout());

        DefaultTableModel model = new DefaultTableModel(0,0);
        model.addColumn("Placeholder");
        model.addColumn("Value");
        keywords.forEach(k -> {
            Vector<String> row = new Vector<>();
            row.add(k);
            row.add("");
            model.addRow(row);
            data.put(k, "");
        });
        table = new JBTable(model);
        table.setTableHeader(new JTableHeader());

        DataModelPanel parent = this;

        table.getModel().addTableModelListener(e -> {
            String key = (String)model.getValueAt(e.getFirstRow(), e.getColumn());
            String value = (String)model.getValueAt(e.getLastRow(), e.getColumn());
            parent.data.put(key, value);
        });

        this.setMinimumSize(new Dimension(200, 300));
        this.add(table, BorderLayout.CENTER);
    }

    public Map<String, String> getData() {
        return this.data;
    }
}
