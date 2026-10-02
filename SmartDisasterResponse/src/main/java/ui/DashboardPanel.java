package ui;

import model.*;
import service.*;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Dashboard panel showing statistics, emergency alerts, and quick status.
 * Premium Red Noir styling with a hero banner; logic identical to previous version.
 */
public class DashboardPanel extends JPanel {

    private Graph graph;
    private RouteService routeService;
    private DisasterService disasterService;
    private SafeZoneService safeZoneService;

    private JPanel statsGrid;
    private JPanel alertPanel;
    private JLabel alertLevelLabel;
    private JTextArea disasterInfoArea;
    private JTextArea recommendationArea;
    private JPanel disasterStatusPillHolder;
    private JLabel shelterSummaryLabel;

    public DashboardPanel(Graph graph, RouteService routeService,
                          DisasterService disasterService, SafeZoneService safeZoneService) {
        this.graph = graph;
        this.routeService = routeService;
        this.disasterService = disasterService;
        this.safeZoneService = safeZoneService;

        Theme.styleDarkPanel(this);
        setLayout(new BorderLayout(0, 0));
        setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));

        add(createHeader(), BorderLayout.NORTH);
        add(createContent(), BorderLayout.CENTER);
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, 20, 0));

        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        JLabel subtitle = Theme.styledLabel("REAL-TIME DISASTER MONITORING AND EVACUATION MANAGEMENT",
                Theme.FONT_LABEL, Theme.TEXT_MUTED);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel hero = Theme.heroTitle("Command Center", 34);
        hero.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleBox.add(subtitle);
        titleBox.add(Box.createVerticalStrut(4));
        titleBox.add(hero);
        header.add(titleBox, BorderLayout.WEST);

        // Emergency alert indicator
        alertPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        alertPanel.setOpaque(false);
        alertLevelLabel = Theme.styledLabel("", Theme.FONT_BOLD, Theme.STATUS_SAFE);
        disasterStatusPillHolder = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        disasterStatusPillHolder.setOpaque(false);
        alertPanel.add(alertLevelLabel);
        alertPanel.add(disasterStatusPillHolder);
        header.add(alertPanel, BorderLayout.EAST);

        setAlertLevel("GREEN");
        return header;
    }

    private JPanel createContent() {
        JPanel content = new JPanel(new BorderLayout(0, 16));
        content.setOpaque(false);

        content.add(createStatsRow(), BorderLayout.NORTH);
        content.add(createBottomSection(), BorderLayout.CENTER);

        return content;
    }

    private JPanel createStatsRow() {
        statsGrid = new JPanel(new GridLayout(1, 6, 12, 0));
        statsGrid.setOpaque(false);
        statsGrid.setPreferredSize(new Dimension(0, 116));
        updateStats();
        return statsGrid;
    }

    public void updateStats() {
        statsGrid.removeAll();

        int totalLocs = graph.getLocationCount();
        int totalRoads = graph.getRoadCount();
        int blocked = graph.getBlockedRoadCount();
        int highRisk = graph.getHighRiskRoadCount();
        int safeZones = safeZoneService.getAllSafeZones().size();
        int availableCapacity = 0;
        for (SafeZone sz : safeZoneService.getAllSafeZones()) {
            availableCapacity += sz.getAvailableCapacity();
        }

        statsGrid.add(Theme.statCard("Locations", String.valueOf(totalLocs), Theme.TEXT_PRIMARY, "mapped"));
        statsGrid.add(Theme.statCard("Roads", String.valueOf(totalRoads), Theme.TEXT_PRIMARY, "network edges"));
        statsGrid.add(Theme.statCard("Blocked", String.valueOf(blocked),
                blocked > 0 ? Theme.ACCENT_RED : Theme.TEXT_MUTED, "impassable"));
        statsGrid.add(Theme.statCard("High Risk", String.valueOf(highRisk),
                highRisk > 0 ? Theme.ACCENT_ORANGE : Theme.TEXT_MUTED, "caution advised"));
        statsGrid.add(Theme.statCard("Safe Zones", String.valueOf(safeZones), Theme.ACCENT_GREEN, "shelters"));
        statsGrid.add(Theme.statCard("Free Capacity", String.valueOf(availableCapacity), Theme.ACCENT_GREEN, "persons"));

        statsGrid.revalidate();
        statsGrid.repaint();
    }

    private JPanel createBottomSection() {
        JPanel mainRow = new JPanel(new BorderLayout(16, 0));
        mainRow.setOpaque(false);

        // Left column: disaster status + recommendation stacked
        JPanel leftCol = new JPanel();
        leftCol.setOpaque(false);
        leftCol.setLayout(new BoxLayout(leftCol, BoxLayout.Y_AXIS));

        JPanel leftCard = Theme.titledCard("Disaster Status", Theme.ACCENT_BRIGHT);
        disasterInfoArea = createInfoArea();
        disasterInfoArea.setText("No active disaster. Use the Disasters view to simulate one.");
        leftCard.add(wrapScroll(disasterInfoArea), BorderLayout.CENTER);
        leftCard.setPreferredSize(new Dimension(400, 220));
        leftCard.setMinimumSize(new Dimension(100, 180));

        JPanel rightCard = Theme.titledCard("Recommended Action", Theme.ACCENT_GREEN);
        recommendationArea = createInfoArea();
        recommendationArea.setText("Select a location and disaster type, then use Evacuation Routes to calculate the safest evacuation route.");
        rightCard.add(wrapScroll(recommendationArea), BorderLayout.CENTER);
        rightCard.setPreferredSize(new Dimension(400, 200));
        rightCard.setMinimumSize(new Dimension(100, 160));

        leftCol.add(leftCard);
        leftCol.add(Box.createVerticalStrut(14));
        leftCol.add(rightCard);

        // Right: accent shelter overview card (real data, refreshed by updateStats callers)
        JPanel shelterCard = buildShelterOverview();

        leftCol.setPreferredSize(new Dimension(660, 0));
        mainRow.add(leftCol, BorderLayout.CENTER);
        mainRow.add(shelterCard, BorderLayout.EAST);

        JPanel outer = new JPanel(new BorderLayout(0, 0));
        outer.setOpaque(false);
        outer.add(mainRow, BorderLayout.CENTER);
        return outer;
    }

    /** Burgundy accent card summarizing shelter capacity — real service data only. */
    private JPanel buildShelterOverview() {
        JPanel card = Theme.cardPanel(Theme.ACCENT_DEEP);
        card.setLayout(new BorderLayout(0, 12));
        card.setPreferredSize(new Dimension(280, 0));

        JPanel head = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        head.setOpaque(false);
        head.add(Theme.iconTile("\u2714", 34));
        JPanel headText = new JPanel();
        headText.setOpaque(false);
        headText.setLayout(new BoxLayout(headText, BoxLayout.Y_AXIS));
        JLabel t1 = Theme.styledLabel("Shelter Network", Theme.FONT_TITLE, Theme.TEXT_PRIMARY);
        JLabel t2 = Theme.styledLabel("LIVE CAPACITY STATUS", Theme.FONT_LABEL, Theme.TEXT_MUTED);
        t1.setAlignmentX(Component.LEFT_ALIGNMENT);
        t2.setAlignmentX(Component.LEFT_ALIGNMENT);
        headText.add(t1);
        headText.add(t2);
        head.add(headText);
        card.add(head, BorderLayout.NORTH);

        JPanel rows = new JPanel();
        rows.setOpaque(false);
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));

        shelterSummaryLabel = Theme.monoLabel("", Theme.TEXT_SECONDARY);
        List<SafeZone> zones = safeZoneService.getAllSafeZones();
        for (SafeZone zone : zones) {
            JPanel row = new JPanel(new BorderLayout(8, 0));
            row.setOpaque(false);
            row.add(Theme.statusDot(zone.isAvailable() ? Theme.ACCENT_GREEN : Theme.ACCENT_RED, 8), BorderLayout.WEST);
            String label = zone.getName() + "  \u00B7  " + zone.getAvailableCapacity() + " free of " + zone.getCapacity();
            row.add(Theme.styledLabel(label, Theme.FONT_SMALL, Theme.TEXT_SECONDARY), BorderLayout.CENTER);
            row.add(Theme.pillBadge(zone.isAvailable() ? "Open" : "Full",
                    zone.isAvailable() ? Theme.ACCENT_GREEN : Theme.ACCENT_RED), BorderLayout.EAST);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
            rows.add(row);
            rows.add(Box.createVerticalStrut(8));
        }
        card.add(rows, BorderLayout.CENTER);

        int totalCap = 0, totalOcc = 0;
        for (SafeZone z : zones) { totalCap += z.getCapacity(); totalOcc += z.getCurrentOccupants(); }
        shelterSummaryLabel.setText(totalOcc + " / " + totalCap + " persons sheltered");
        shelterSummaryLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(shelterSummaryLabel, BorderLayout.SOUTH);

        return card;
    }

    private JTextArea createInfoArea() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setOpaque(false);
        area.setForeground(Theme.TEXT_SECONDARY);
        area.setFont(Theme.FONT_BODY);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        return area;
    }

    private JScrollPane wrapScroll(JTextArea area) {
        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        return scroll;
    }

    /**
     * Update the dashboard with current disaster status.
     */
    public void updateDisasterStatus(Disaster disaster) {
        if (disaster == null) {
            disasterInfoArea.setText("No active disaster. Use the Disasters view to simulate one.");
            setAlertLevel("GREEN");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Active Disaster: ").append(disaster.getType().getDisplayName()).append("\n");
        sb.append("Severity: ").append(disaster.getSeverity()).append("\n");
        sb.append("Affected Roads: ").append(disaster.getAffectedRoads().size()).append("\n");
        sb.append("Blocked Roads: ").append(disaster.getBlockedRoadCount()).append("\n");
        sb.append("High Risk Roads: ").append(disaster.getHighRiskRoadCount()).append("\n");
        sb.append("Damaged Roads: ").append(disaster.getDamagedRoadCount()).append("\n");
        sb.append("\n").append(disaster.getDescription());
        disasterInfoArea.setText(sb.toString());

        recommendationArea.setText(disaster.getRecommendedAction());

        // Update alert level based on severity
        switch (disaster.getSeverity()) {
            case CRITICAL: setAlertLevel("RED"); break;
            case HIGH: setAlertLevel("ORANGE"); break;
            case MEDIUM: setAlertLevel("YELLOW"); break;
            default: setAlertLevel("GREEN");
        }
    }

    /**
     * Update the emergency alert level indicator.
     */
    public void setAlertLevel(String level) {
        Color color;
        String text;
        switch (level) {
            case "RED":
                color = Theme.STATUS_CRITICAL; text = "\u25B2  CRITICAL"; break;
            case "ORANGE":
                color = Theme.STATUS_HIGH_RISK; text = "\u25B2  HIGH RISK"; break;
            case "YELLOW":
                color = Theme.STATUS_CAUTION; text = "\u25CF  CAUTION"; break;
            default:
                color = Theme.STATUS_SAFE; text = "\u25CF  SAFE";
        }
        alertLevelLabel.setText(text);
        alertLevelLabel.setForeground(color);

        disasterStatusPillHolder.removeAll();
        disasterStatusPillHolder.add(Theme.pillBadge(levelWord(level), color));
        disasterStatusPillHolder.revalidate();
        disasterStatusPillHolder.repaint();
    }

    private String levelWord(String level) {
        switch (level) {
            case "RED": return "Alert \u00B7 Critical";
            case "ORANGE": return "Alert \u00B7 High";
            case "YELLOW": return "Watch";
            default: return "Operational";
        }
    }
}
