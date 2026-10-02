package ui;

import model.*;
import service.*;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Panel showing all road statuses in a table with ability to manually update.
 * Command-center styling; table logic and dialogs unchanged.
 */
public class RoadStatusPanel extends JPanel {

    private Graph graph;
    private DisasterService disasterService;
    private JTable roadTable;
    private DefaultTableModel tableModel;
    private JLabel countLabel;

    public RoadStatusPanel(Graph graph, DisasterService disasterService) {
        this.graph = graph;
        this.disasterService = disasterService;

        Theme.styleDarkPanel(this);
        setLayout(new BorderLayout(0, 0));
        setBorder(BorderFactory.createEmptyBorder(22, 26, 22, 26));

        add(createHeader(), BorderLayout.NORTH);
        add(createTablePanel(), BorderLayout.CENTER);
        add(createButtonBar(), BorderLayout.SOUTH);
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, 18, 0));

        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        JLabel subtitle = Theme.styledLabel("NETWORK EDGE CONDITIONS & MANUAL OVERRIDES", Theme.FONT_LABEL, Theme.TEXT_MUTED);
        JLabel title = Theme.displayLabel("Road Status", 26);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleBox.add(subtitle);
        titleBox.add(Box.createVerticalStrut(4));
        titleBox.add(title);
        header.add(titleBox, BorderLayout.WEST);

        countLabel = Theme.monoLabel("", Theme.TEXT_SECONDARY);
        header.add(countLabel, BorderLayout.EAST);
        return header;
    }

    private JPanel createTablePanel() {
        String[] columns = {"Road", "Distance", "Terrain", "Status", "Risk", "Traffic", "Disaster Penalty"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        roadTable = new JTable(tableModel);
        roadTable.setFont(Theme.FONT_MONO_SMALL);
        roadTable.setBackground(Theme.BG_CARD);
        roadTable.setForeground(Theme.TEXT_PRIMARY);
        roadTable.setSelectionBackground(new Color(0x3F, 0x3F, 0x46, 110));
        roadTable.setSelectionForeground(Theme.TEXT_PRIMARY);
        roadTable.setGridColor(Theme.BORDER);
        roadTable.setRowHeight(30);
        roadTable.setShowGrid(false);
        roadTable.setIntercellSpacing(new Dimension(0, 0));
        roadTable.setFillsViewportHeight(true);

        // Header styling
        roadTable.getTableHeader().setFont(Theme.FONT_LABEL);
        roadTable.getTableHeader().setBackground(Theme.BG_SIDEBAR);
        roadTable.getTableHeader().setForeground(Theme.TEXT_MUTED);
        roadTable.getTableHeader().setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER));
        roadTable.getTableHeader().setDefaultRenderer(new DefaultTableCellRenderer() {
            {
                setHorizontalAlignment(SwingConstants.LEFT);
            }

            @Override
            public Component getTableCellRendererComponent(JTable t, Object val, boolean sel, boolean foc, int r, int c) {
                Component comp = super.getTableCellRendererComponent(t, val, sel, foc, r, c);
                comp.setBackground(Theme.BG_SIDEBAR);
                comp.setForeground(Theme.TEXT_MUTED);
                comp.setFont(Theme.FONT_LABEL);
                return comp;
            }
        });

        // Zebra rows + colored status cells
        DefaultTableCellRenderer zebra = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val, boolean sel, boolean foc, int r, int c) {
                Component comp = super.getTableCellRendererComponent(t, val, sel, foc, r, c);
                comp.setFont(Theme.FONT_MONO_SMALL);
                if (!sel) {
                    comp.setBackground(r % 2 == 0 ? Theme.BG_CARD : Theme.BG_INPUT);
                    comp.setForeground(Theme.TEXT_SECONDARY);
                }
                return comp;
            }
        };
        for (int c = 0; c < roadTable.getColumnCount(); c++) {
            if (c != 3) roadTable.getColumnModel().getColumn(c).setCellRenderer(zebra);
        }

        // Custom cell renderer for status column
        roadTable.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val, boolean sel, boolean foc, int r, int c) {
                Component comp = super.getTableCellRendererComponent(t, val, sel, foc, r, c);
                String status = val != null ? val.toString() : "";
                setOpaque(true);
                comp.setFont(Theme.FONT_LABEL);
                switch (status) {
                    case "OPEN": comp.setBackground(new Color(0x0D, 0x1F, 0x16)); comp.setForeground(Theme.ACCENT_GREEN); break;
                    case "BLOCKED": comp.setBackground(new Color(0x25, 0x10, 0x10)); comp.setForeground(Theme.ACCENT_RED); break;
                    case "HIGH_RISK": comp.setBackground(new Color(0x25, 0x1B, 0x0B)); comp.setForeground(Theme.ACCENT_ORANGE); break;
                    case "DAMAGED": comp.setBackground(new Color(0x33, 0x1D, 0x12)); comp.setForeground(Theme.ROAD_DAMAGED); break;
                    default: comp.setBackground(Theme.BG_CARD); comp.setForeground(Theme.TEXT_PRIMARY);
                }
                return comp;
            }
        });

        JScrollPane scroll = new JScrollPane(roadTable);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        scroll.getViewport().setBackground(Theme.BG_CARD);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(scroll, BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel createButtonBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 12));
        bar.setOpaque(false);

        JButton refreshBtn = Theme.styledButton("REFRESH", Theme.ACCENT_BLUE);
        refreshBtn.addActionListener(e -> refreshTable());

        JButton updateBtn = Theme.styledButton("UPDATE ROAD STATUS", Theme.ACCENT);
        updateBtn.addActionListener(e -> updateRoadStatus());

        JButton resetBtn = Theme.ghostButton("RESET ALL");
        resetBtn.addActionListener(e -> {
            graph.resetRoadStatuses();
            refreshTable();
        });

        bar.add(refreshBtn);
        bar.add(updateBtn);
        bar.add(resetBtn);

        // Initial load
        refreshTable();

        return bar;
    }

    public void refreshTable() {
        tableModel.setRowCount(0);
        Set<String> drawn = new HashSet<>();
        int blocked = 0;
        for (Road road : graph.getAllRoads()) {
            String key = road.getSource().getId() + "-" + road.getDestination().getId();
            if (!drawn.add(key)) continue;
            if (road.isBlocked()) blocked++;
            tableModel.addRow(new Object[]{
                    road.getSource().getName() + " \u2192 " + road.getDestination().getName(),
                    String.format("%.1f km", road.getDistance()),
                    road.getRoadTerrain().name(),
                    road.getStatus().name(),
                    road.getRiskLevel().name(),
                    road.getTrafficLevel(),
                    String.format("%.1f", road.getDisasterPenalty())
            });
        }
        int total = tableModel.getRowCount();
        countLabel.setText(total + " segments \u00B7 " + blocked + " blocked");
    }

    private void updateRoadStatus() {
        Set<String> roadNames = new LinkedHashSet<>();
        for (Road road : graph.getAllRoads()) {
            roadNames.add(road.getSource().getName() + " \u2192 " + road.getDestination().getName());
        }
        String[] roads = roadNames.toArray(new String[0]);
        String[] statuses = {"OPEN", "BLOCKED", "DAMAGED", "HIGH_RISK"};

        JPanel panel = new JPanel(new GridLayout(2, 2, 8, 8));
        panel.setBackground(Theme.BG_CARD);
        JComboBox<String> roadSelector = new JComboBox<>(roads);
        JComboBox<String> statusSelector = new JComboBox<>(statuses);
        panel.add(Theme.styledLabel("Select Road:", Theme.FONT_BOLD, Theme.TEXT_PRIMARY));
        panel.add(roadSelector);
        panel.add(Theme.styledLabel("New Status:", Theme.FONT_BOLD, Theme.TEXT_PRIMARY));
        panel.add(statusSelector);

        int result = JOptionPane.showConfirmDialog(this, panel, "Update Road Status",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String selected = (String) roadSelector.getSelectedItem();
            String newStatus = (String) statusSelector.getSelectedItem();
            if (selected != null && newStatus != null) {
                String[] parts = selected.split(" \u2192 ");
                Location src = graph.getLocationByName(parts[0].trim());
                Location dst = graph.getLocationByName(parts[1].trim());
                if (src != null && dst != null) {
                    disasterService.updateRoadStatus(src, dst, Road.Status.valueOf(newStatus));
                    refreshTable();
                }
            }
        }
    }
}
