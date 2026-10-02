package ui;

import model.*;
import service.*;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/**
 * Panel for disaster simulation, selection, severity, and information display.
 * Command-center styling; simulation logic unchanged.
 */
public class DisasterPanel extends JPanel {

    private Graph graph;
    private DisasterService disasterService;
    private JComboBox<String> disasterTypeCombo;
    private JComboBox<String> severityCombo;
    private JTextArea infoArea;
    private JTextArea actionArea;
    private Consumer<Disaster> onDisasterApplied;

    public DisasterPanel(Graph graph, DisasterService disasterService) {
        this.graph = graph;
        this.disasterService = disasterService;

        Theme.styleDarkPanel(this);
        setLayout(new BorderLayout(0, 0));
        setBorder(BorderFactory.createEmptyBorder(22, 26, 22, 26));

        add(createHeader(), BorderLayout.NORTH);
        add(createContent(), BorderLayout.CENTER);
    }

    public void setOnDisasterApplied(Consumer<Disaster> callback) {
        this.onDisasterApplied = callback;
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, 18, 0));

        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        JLabel subtitle = Theme.styledLabel("INCIDENT SIMULATION & ROAD NETWORK IMPACT", Theme.FONT_LABEL, Theme.TEXT_MUTED);
        JLabel title = Theme.displayLabel("Disaster Management", 26);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleBox.add(subtitle);
        titleBox.add(Box.createVerticalStrut(4));
        titleBox.add(title);
        header.add(titleBox, BorderLayout.WEST);
        return header;
    }

    private JPanel createContent() {
        JPanel content = new JPanel(new BorderLayout(16, 0));
        content.setOpaque(false);

        // Left: Controls
        JPanel controls = Theme.cardPanel();
        controls.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        // Disaster type
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        controls.add(Theme.sectionLabel("Disaster Type", Theme.TEXT_MUTED), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        disasterTypeCombo = createCombo(getDisasterTypeNames());
        controls.add(disasterTypeCombo, gbc);

        // Severity
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        controls.add(Theme.sectionLabel("Severity", Theme.TEXT_MUTED), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        severityCombo = createCombo(new String[]{"LOW", "MEDIUM", "HIGH", "CRITICAL"});
        controls.add(severityCombo, gbc);

        // Simulate button
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        JButton simulateBtn = Theme.styledButton("SIMULATE DISASTER", Theme.ACCENT);
        simulateBtn.addActionListener(e -> simulateDisaster());
        controls.add(simulateBtn, gbc);

        // Reset button
        gbc.gridy = 3;
        JButton resetBtn = Theme.ghostButton("RESET ALL ROADS");
        resetBtn.addActionListener(e -> resetDisaster());
        controls.add(resetBtn, gbc);

        // Severity legend
        gbc.gridy = 4; gbc.weighty = 1.0;
        JPanel legendPanel = new JPanel(new GridLayout(4, 1, 0, 4));
        legendPanel.setOpaque(false);
        legendPanel.add(createLegendItem("LOW \u2014 Small risk increase", Theme.STATUS_SAFE));
        legendPanel.add(createLegendItem("MEDIUM \u2014 Moderate risk", Theme.STATUS_CAUTION));
        legendPanel.add(createLegendItem("HIGH \u2014 Major risk, some blocked", Theme.STATUS_HIGH_RISK));
        legendPanel.add(createLegendItem("CRITICAL \u2014 Maximum destruction", Theme.STATUS_CRITICAL));
        controls.add(legendPanel, gbc);

        // Right: Info and recommendations
        JPanel infoPanel = new JPanel(new BorderLayout(0, 12));
        infoPanel.setOpaque(false);

        // Disaster info card
        JPanel infoCard = Theme.titledCard("Disaster Information", Theme.ACCENT_CYAN);
        infoArea = new JTextArea();
        styleInfoArea(infoArea);
        infoArea.setText("Select a disaster type and severity, then click SIMULATE DISASTER to see how it affects the road network.");
        infoCard.add(wrapScroll(infoArea), BorderLayout.CENTER);
        infoCard.setPreferredSize(new Dimension(400, 300));
        infoPanel.add(infoCard, BorderLayout.CENTER);

        // Recommendation card
        JPanel recCard = Theme.titledCard("Recommended Action", Theme.ACCENT_GREEN);
        actionArea = new JTextArea();
        styleInfoArea(actionArea);
        actionArea.setText("Awaiting disaster simulation...");
        recCard.add(wrapScroll(actionArea), BorderLayout.CENTER);
        recCard.setPreferredSize(new Dimension(400, 200));
        infoPanel.add(recCard, BorderLayout.SOUTH);

        content.add(controls, BorderLayout.WEST);
        content.add(infoPanel, BorderLayout.CENTER);

        return content;
    }

    private JComboBox<String> createCombo(String[] items) {
        JComboBox<String> combo = new JComboBox<>(items);
        combo.setFont(Theme.interFont(Font.PLAIN, 13f));
        combo.setBackground(Theme.BG_INPUT);
        combo.setForeground(Theme.TEXT_PRIMARY);
        return combo;
    }

    private void styleInfoArea(JTextArea area) {
        area.setEditable(false);
        area.setOpaque(false);
        area.setForeground(Theme.TEXT_SECONDARY);
        area.setFont(Theme.FONT_BODY);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
    }

    private JScrollPane wrapScroll(JTextArea area) {
        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        return scroll;
    }

    private JPanel createLegendItem(String text, Color color) {
        JPanel item = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        item.setOpaque(false);
        JLabel dot = new JLabel("\u25CF");
        dot.setForeground(color);
        dot.setFont(Theme.FONT_SMALL);
        item.add(dot);
        item.add(Theme.styledLabel(text, Theme.FONT_SMALL, Theme.TEXT_SECONDARY));
        return item;
    }

    private String[] getDisasterTypeNames() {
        Disaster.Type[] types = Disaster.Type.values();
        String[] names = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            names[i] = types[i].getDisplayName();
        }
        return names;
    }

    private void simulateDisaster() {
        String typeName = (String) disasterTypeCombo.getSelectedItem();
        String severityName = (String) severityCombo.getSelectedItem();

        Disaster.Type type = Disaster.Type.valueOf(typeName.replace(' ', '_'));
        Disaster.Severity severity = Disaster.Severity.valueOf(severityName);

        Disaster disaster = disasterService.applyDisaster(type, severity);

        // Update info display
        StringBuilder info = new StringBuilder();
        info.append("Disaster: ").append(disaster.getType().getDisplayName()).append("\n");
        info.append("Severity: ").append(disaster.getSeverity()).append("\n\n");
        info.append("Roads Affected: ").append(disaster.getAffectedRoads().size()).append("\n");
        info.append("Blocked Roads: ").append(disaster.getBlockedRoadCount()).append("\n");
        info.append("High Risk Roads: ").append(disaster.getHighRiskRoadCount()).append("\n");
        info.append("Damaged Roads: ").append(disaster.getDamagedRoadCount()).append("\n\n");
        info.append(disaster.getDescription());
        infoArea.setText(info.toString());

        actionArea.setText(disaster.getRecommendedAction());

        if (onDisasterApplied != null) {
            onDisasterApplied.accept(disaster);
        }
    }

    private void resetDisaster() {
        graph.resetRoadStatuses();
        infoArea.setText("All roads reset to OPEN. No active disaster.");
        actionArea.setText("Awaiting disaster simulation...");
        if (onDisasterApplied != null) {
            onDisasterApplied.accept(null);
        }
    }
}
