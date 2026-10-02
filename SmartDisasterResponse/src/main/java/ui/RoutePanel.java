package ui;

import model.*;
import algorithms.*;
import service.*;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Modern route finder panel with dark theme, severity-aware routing, and emergency alerts.
 * Command-center styling; routing logic, callbacks and data flow preserved exactly.
 */
public class RoutePanel extends JPanel {

    private Graph graph;
    private RouteService routeService;
    private DisasterService disasterService;
    private SafeZoneService safeZoneService;
    private GraphPanel graphPanel;
    private DashboardPanel dashboardPanel;

    private JComboBox<String> locationCombo;
    private JComboBox<String> destinationCombo;
    private JTextArea outputArea;
    private JPanel alertBanner;
    private JLabel routeSummaryLabel;

    private java.util.function.Consumer<List<Location>> routeHighlightCallback;
    private java.util.function.Consumer<Set<Location>> bfsHighlightCallback;
    private java.util.function.Consumer<Set<Location>> dfsHighlightCallback;

    public RoutePanel(Graph graph, RouteService routeService,
                      DisasterService disasterService, SafeZoneService safeZoneService,
                      GraphPanel graphPanel) {
        this.graph = graph;
        this.routeService = routeService;
        this.disasterService = disasterService;
        this.safeZoneService = safeZoneService;
        this.graphPanel = graphPanel;

        Theme.styleDarkPanel(this);
        setLayout(new BorderLayout(0, 0));
        setBorder(BorderFactory.createEmptyBorder(22, 26, 22, 26));

        add(createHeader(), BorderLayout.NORTH);
        add(createCenterPanel(), BorderLayout.CENTER);
    }

    public void setDashboardPanel(DashboardPanel dp) { this.dashboardPanel = dp; }
    public void setRouteHighlightCallback(java.util.function.Consumer<List<Location>> cb) { this.routeHighlightCallback = cb; }
    public void setBfsHighlightCallback(java.util.function.Consumer<Set<Location>> cb) { this.bfsHighlightCallback = cb; }
    public void setDfsHighlightCallback(java.util.function.Consumer<Set<Location>> cb) { this.dfsHighlightCallback = cb; }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, 18, 0));

        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        JLabel subtitle = Theme.styledLabel("DIJKSTRA EVACUATION PLANNER", Theme.FONT_LABEL, Theme.TEXT_MUTED);
        JLabel title = Theme.displayLabel("Evacuation Routes", 26);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleBox.add(subtitle);
        titleBox.add(Box.createVerticalStrut(4));
        titleBox.add(title);
        header.add(titleBox, BorderLayout.WEST);
        return header;
    }

    private JPanel createCenterPanel() {
        JPanel center = new JPanel(new BorderLayout(0, 12));
        center.setOpaque(false);

        // Alert banner (initially hidden)
        alertBanner = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        alertBanner.setVisible(false);

        // Input card
        JPanel inputCard = Theme.cardPanel();
        inputCard.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 8, 6, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        List<String> names = new ArrayList<>();
        for (Location loc : graph.getAllLocations()) names.add(loc.getName());
        Collections.sort(names);

        // Row 0: From / To / button in a single row
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        inputCard.add(Theme.sectionLabel("From", Theme.TEXT_MUTED), gbc);
        gbc.gridx = 1; gbc.weightx = 0.5;
        locationCombo = createCombo(names.toArray(new String[0]));
        inputCard.add(locationCombo, gbc);

        gbc.gridx = 2; gbc.weightx = 0;
        inputCard.add(Theme.sectionLabel("To", Theme.TEXT_MUTED), gbc);
        gbc.gridx = 3; gbc.weightx = 0.5;
        List<String> destNames = new ArrayList<>();
        destNames.add("-- Nearest Safe Zone --");
        destNames.addAll(names);
        destinationCombo = createCombo(destNames.toArray(new String[0]));
        inputCard.add(destinationCombo, gbc);

        gbc.gridx = 4; gbc.weightx = 0;
        JButton findBtn = Theme.styledButton("FIND SAFEST ROUTE", Theme.ACCENT);
        findBtn.addActionListener(e -> findSafestRoute());
        inputCard.add(findBtn, gbc);

        // Route summary strip (filled after calculation)
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 5; gbc.weightx = 1;
        routeSummaryLabel = Theme.monoLabel("No route calculated yet \u00B7 select origin and destination above",
                Theme.TEXT_MUTED);
        inputCard.add(routeSummaryLabel, gbc);

        // Alert banner + input card stacked at the top
        JPanel northStack = new JPanel();
        northStack.setOpaque(false);
        northStack.setLayout(new BoxLayout(northStack, BoxLayout.Y_AXIS));
        northStack.add(alertBanner);
        northStack.add(Box.createVerticalStrut(4));
        northStack.add(inputCard);
        center.add(northStack, BorderLayout.NORTH);

        // Output area
        outputArea = new JTextArea();
        outputArea.setEditable(false);
        outputArea.setBackground(Theme.BG_INPUT);
        outputArea.setForeground(Theme.TEXT_PRIMARY);
        outputArea.setFont(Theme.FONT_MONO);
        outputArea.setLineWrap(true);
        outputArea.setWrapStyleWord(true);
        outputArea.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        outputArea.setText("Select your current location and destination, then click FIND SAFEST ROUTE.\n\n"
                + "The system uses Dijkstra's Algorithm to calculate the safest route considering:\n"
                + "  \u2022 Road distance\n"
                + "  \u2022 Risk level\n"
                + "  \u2022 Disaster severity\n"
                + "  \u2022 Road condition (blocked roads excluded)");

        JScrollPane scroll = new JScrollPane(outputArea);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        scroll.getViewport().setBackground(Theme.BG_INPUT);
        center.add(scroll, BorderLayout.CENTER);

        return center;
    }

    private JComboBox<String> createCombo(String[] items) {
        JComboBox<String> combo = new JComboBox<>(items);
        combo.setFont(Theme.FONT_BODY);
        combo.setBackground(Theme.BG_INPUT);
        combo.setForeground(Theme.TEXT_PRIMARY);
        return combo;
    }

    private void findSafestRoute() {
        String sourceName = (String) locationCombo.getSelectedItem();
        if (sourceName == null) { showError("Please select a location."); return; }

        Location source = graph.getLocationByName(sourceName);
        if (source == null) { showError("Invalid location."); return; }

        // Set current location on graph
        graphPanel.setCurrentLocation(source);

        // Determine destination
        Location destination = null;
        String destName = (String) destinationCombo.getSelectedItem();
        boolean findNearest = destName == null || destName.startsWith("--");

        if (!findNearest) {
            destination = graph.getLocationByName(destName);
            if (destination == null) { showError("Invalid destination."); return; }
        }

        if (!findNearest && source.equals(destination)) {
            showError("Source and destination are the same!");
            return;
        }

        // Calculate route
        DijkstraAlgorithm.DijkstraResult result;
        if (destination != null) {
            result = routeService.findSafestRoute(source, destination);
        } else {
            result = routeService.findNearestSafeZone(source, safeZoneService.getAllSafeZones());
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\u2550\u2550\u2550 EVACUATION ROUTE \u2014 DIJKSTRA'S ALGORITHM \u2550\u2550\u2550\n\n");
        sb.append("Source: ").append(sourceName).append("\n");
        if (destination != null) {
            sb.append("Destination: ").append(destination.getName()).append("\n");
        } else {
            sb.append("Destination: Nearest Available Safe Zone\n");
        }

        // Show active disaster info
        Disaster disaster = disasterService.getCurrentDisaster();
        if (disaster != null) {
            sb.append("Active Disaster: ").append(disaster.getType().getDisplayName())
              .append(" (").append(disaster.getSeverity()).append(")\n");
        }

        sb.append("\n");

        if (result.isRouteFound()) {
            List<Location> route = result.getRoute();
            String riskLevel = routeService.getRouteRiskLevel(route);
            String alertLevel = routeService.getAlertLevel(riskLevel);

            // Show alert banner
            showAlertBanner(alertLevel, riskLevel);

            // Status
            sb.append("Status: ");
            switch (alertLevel) {
                case "RED": sb.append("\u26A0 CRITICAL \u2014 Route passes through dangerous areas\n"); break;
                case "ORANGE": sb.append("\u26A0 HIGH RISK \u2014 Route contains high-risk segments\n"); break;
                case "YELLOW": sb.append("\u26A0 CAUTION \u2014 Route contains damaged roads\n"); break;
                default: sb.append("\u2713 SAFE ROUTE AVAILABLE\n");
            }

            sb.append("Algorithm: Dijkstra's Algorithm\n\n");
            sb.append("Recommended Route:\n");
            sb.append("\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\n");
            for (int i = 0; i < route.size(); i++) {
                sb.append("  ").append(route.get(i).getName());
                if (i < route.size() - 1) sb.append("\n    \u2193\n");
            }
            sb.append("\n\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\n");
            sb.append(String.format("Total Distance: %.1f km%n", result.getTotalDistance()));
            sb.append(String.format("Total Cost:     %.1f%n", result.getTotalCost()));
            sb.append("Risk Level:     ").append(riskLevel).append("\n");

            // Route warnings
            List<String> warnings = routeService.getRouteWarnings(route);
            if (!warnings.isEmpty()) {
                sb.append("\n\u26A0 WARNINGS:\n");
                for (String w : warnings) {
                    sb.append("  \u2022 ").append(w).append("\n");
                }
            }

            routeSummaryLabel.setText(String.format(
                    "%s \u2192 %s   \u00B7   %.1f km   \u00B7   cost %.1f   \u00B7   risk %s",
                    sourceName,
                    destination != null ? destination.getName() : "Nearest Safe Zone",
                    result.getTotalDistance(), result.getTotalCost(), riskLevel));

            if (routeHighlightCallback != null) routeHighlightCallback.accept(route);
        } else {
            showAlertBanner("RED", "NO ROUTE");
            routeSummaryLabel.setText("NO SAFE ROUTE FOUND \u00B7 " + result.getMessage());
            sb.append("Status: \u2717 ").append(result.getMessage()).append("\n\n");
            sb.append("Recommendation: Try a different destination or reset road conditions.\n");
        }

        outputArea.setText(sb.toString());
        outputArea.setCaretPosition(0);

        // Update dashboard
        if (dashboardPanel != null) {
            dashboardPanel.updateStats();
        }
    }

    private void showAlertBanner(String level, String riskLevel) {
        alertBanner.removeAll();
        alertBanner.setVisible(true);

        Color fgColor;
        String icon, text;

        switch (level) {
            case "RED":
                fgColor = Theme.ACCENT_RED;
                icon = "\u26A0";
                text = "CRITICAL \u2014 Route passes through " + riskLevel + " risk areas. Alternative route recommended.";
                break;
            case "ORANGE":
                fgColor = Theme.ACCENT;
                icon = "\u26A0";
                text = "HIGH RISK \u2014 Route contains dangerous segments. Proceed with extreme caution.";
                break;
            case "YELLOW":
                fgColor = Theme.ACCENT_YELLOW;
                icon = "\u26A1";
                text = "CAUTION \u2014 Route contains damaged roads. Stay alert.";
                break;
            default:
                fgColor = Theme.ACCENT_GREEN;
                icon = "\u2713";
                text = "SAFE \u2014 No high-risk conditions on this route.";
        }

        final Color bannerFg = fgColor;
        JPanel banner = Theme.glassPanel();
        banner.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 8));
        banner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(
                        bannerFg.getRed(), bannerFg.getGreen(), bannerFg.getBlue(), 110), 1, true),
                BorderFactory.createEmptyBorder(2, 8, 2, 8)));
        banner.add(Theme.styledLabel(icon, new Font("SansSerif", Font.BOLD, 15), bannerFg));
        banner.add(Theme.styledLabel(text, Theme.FONT_BOLD, bannerFg));
        alertBanner.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
        alertBanner.setOpaque(false);
        alertBanner.add(banner);
        alertBanner.revalidate();
    }

    private void showError(String msg) {
        outputArea.setText("ERROR: " + msg);
        alertBanner.setVisible(false);
        routeSummaryLabel.setText("ERROR \u00B7 " + msg);
    }
}
