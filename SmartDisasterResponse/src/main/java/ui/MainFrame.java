package ui;

import model.*;
import algorithms.*;
import service.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;

/**
 * Main application frame — premium Red Noir command-center shell.
 * Left: collapsible sidebar navigation with sections and pill active states.
 * Top: refined header with view title, system status, live clock and alert badge.
 * Center: card-based views (Live Map, Dashboard, Route Finder, Disasters,
 * Safe Zones, Roads, Algorithms). All view wiring and callbacks unchanged.
 */
public class MainFrame extends JFrame {

    private Graph graph;
    private RouteService routeService;
    private DisasterService disasterService;
    private SafeZoneService safeZoneService;

    private GraphPanel graphPanel;
    private RoutePanel routePanel;
    private DashboardPanel dashboardPanel;
    private DisasterPanel disasterPanel;
    private SafeZonePanel safeZonePanel;
    private RoadStatusPanel roadPanel;
    private AlgorithmPanel algoPanel;

    private CardLayout cardLayout;
    private JPanel cardPanel;
    private JPanel navItemsPanel;
    private final java.util.List<NavButton> navButtons = new java.util.ArrayList<>();
    private JLabel alertBadgeHolderLabel;
    private JPanel alertBadgeHolder;
    private JLabel clockLabel;
    private JLabel sidebarStatsLabel;
    private JLabel headerViewTitle;
    private JPanel sidebarPanel;
    private boolean sidebarExpanded = true;
    private javax.swing.Timer collapseTimer;

    private static final String VIEW_MAP = "map";
    private static final String VIEW_DASHBOARD = "dashboard";
    private static final String VIEW_ROUTE = "route";
    private static final String VIEW_DISASTER = "disaster";
    private static final String VIEW_SHELTERS = "shelters";
    private static final String VIEW_ROADS = "roads";
    private static final String VIEW_ALGORITHMS = "algorithms";

    private static final int SIDEBAR_W_EXPANDED = 224;
    private static final int SIDEBAR_W_COLLAPSED = 68;

    public MainFrame(Graph graph, RouteService routeService,
                     DisasterService disasterService, SafeZoneService safeZoneService) {
        this.graph = graph;
        this.routeService = routeService;
        this.disasterService = disasterService;
        this.safeZoneService = safeZoneService;
        initUI();
    }

    private void initUI() {
        setTitle("Smart Disaster Response & Evacuation Management System");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1380, 870);
        setMinimumSize(new Dimension(1024, 680));
        setLocationRelativeTo(null);
        getContentPane().setBackground(Theme.BG_DARK);

        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(Theme.BG_DARK);
        root.add(createSidebar(), BorderLayout.WEST);

        JPanel mainArea = new JPanel(new BorderLayout(0, 0));
        mainArea.setOpaque(false);
        mainArea.add(createHeader(), BorderLayout.NORTH);
        mainArea.add(createCardPanel(), BorderLayout.CENTER);
        root.add(mainArea, BorderLayout.CENTER);

        add(root);

        showView(VIEW_MAP);
    }

    // ====================================================================
    //  Collapsible sidebar navigation
    // ====================================================================

    private JPanel createSidebar() {
        JPanel sidebar = sidebarPanel = new JPanel(new BorderLayout(0, 0)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                // flat shadcn sidebar surface
                g2.setColor(Theme.BG_SIDEBAR);
                g2.fillRect(0, 0, w, h);
                g2.dispose();
            }
        };
        sidebar.setPreferredSize(new Dimension(SIDEBAR_W_EXPANDED, 0));
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.BORDER));

        // ---- Brand block ----
        JPanel brand = new JPanel(new BorderLayout(10, 0));
        brand.setOpaque(false);
        brand.setBorder(BorderFactory.createEmptyBorder(22, 16, 18, 12));
        JPanel brandLeft = new JPanel(new BorderLayout(10, 0));
        brandLeft.setOpaque(false);
        brandLeft.add(Theme.iconTile("\u2691", 38), BorderLayout.WEST);
        JPanel brandText = new JPanel();
        brandText.setOpaque(false);
        brandText.setLayout(new BoxLayout(brandText, BoxLayout.Y_AXIS));
        JLabel brandName = Theme.styledLabel("Resq.", Theme.FONT_HEADER, Theme.TEXT_PRIMARY);
        JLabel brandTag = Theme.styledLabel("RESPONSE COMMAND", Theme.FONT_LABEL, Theme.TEXT_MUTED);
        brandName.setAlignmentX(Component.LEFT_ALIGNMENT);
        brandTag.setAlignmentX(Component.LEFT_ALIGNMENT);
        brandText.add(brandName);
        brandText.add(Box.createVerticalStrut(2));
        brandText.add(brandTag);
        brandLeft.add(brandText, BorderLayout.CENTER);
        brand.add(brandLeft, BorderLayout.CENTER);
        sidebar.add(brand, BorderLayout.NORTH);

        // ---- Nav sections ----
        navItemsPanel = new JPanel();
        navItemsPanel.setOpaque(false);
        navItemsPanel.setLayout(new BoxLayout(navItemsPanel, BoxLayout.Y_AXIS));
        navItemsPanel.setBorder(BorderFactory.createEmptyBorder(4, 12, 6, 12));

        JLabel opsHeader = sectionHeader("Operations");
        navItemsPanel.add(opsHeader);
        navItemsPanel.add(Box.createVerticalStrut(6));
        addNavItem(VIEW_MAP, "Live Map", "\u25C8");
        addNavItem(VIEW_DASHBOARD, "Dashboard", "\u2302");
        navItemsPanel.add(Box.createVerticalStrut(14));

        JLabel planHeader = sectionHeader("Planning");
        navItemsPanel.add(planHeader);
        navItemsPanel.add(Box.createVerticalStrut(6));
        addNavItem(VIEW_ROUTE, "Evacuation Routes", "\u2192");
        addNavItem(VIEW_DISASTER, "Disasters", "\u26A1");
        addNavItem(VIEW_SHELTERS, "Emergency Shelters", "\u2714");
        navItemsPanel.add(Box.createVerticalStrut(14));

        JLabel netHeader = sectionHeader("Network");
        navItemsPanel.add(netHeader);
        navItemsPanel.add(Box.createVerticalStrut(6));
        addNavItem(VIEW_ROADS, "Road Status", "\u25A0");
        addNavItem(VIEW_ALGORITHMS, "Algorithms", "\u03A3");

        JPanel navScrollHolder = new JPanel(new BorderLayout());
        navScrollHolder.setOpaque(false);
        navScrollHolder.add(navItemsPanel, BorderLayout.NORTH);
        sidebar.add(navScrollHolder, BorderLayout.CENTER);

        // ---- Footer: collapse toggle + network stats ----
        JPanel footer = new JPanel(new BorderLayout(0, 8));
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(12, 12, 14, 12)));

        JButton collapseBtn = Theme.ghostButton("\u276E  COLLAPSE");
        collapseBtn.setFont(Theme.FONT_LABEL);
        collapseBtn.addActionListener(e -> toggleSidebar());
        collapseBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        footer.add(collapseBtn, BorderLayout.NORTH);

        sidebarStatsLabel = Theme.monoLabel(networkStats(), Theme.TEXT_MUTED);
        footer.add(sidebarStatsLabel, BorderLayout.CENTER);
        sidebar.add(footer, BorderLayout.SOUTH);

        return sidebar;
    }

    private JLabel sectionHeader(String text) {
        JLabel l = Theme.sectionLabel(text, Theme.TEXT_MUTED);
        l.setBorder(BorderFactory.createEmptyBorder(4, 8, 2, 8));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private void addNavItem(String key, String label, String symbol) {
        NavButton btn = new NavButton(label, symbol);
        btn.addActionListener(e -> showView(key));
        navButtons.add(btn);
        navItemsPanel.add(btn);
        navItemsPanel.add(Box.createVerticalStrut(4));
    }

    /** Sidebar item with crimson pill active state, hover wash and slide hint. */
    private class NavButton extends JButton {
        private final String label;
        private final String symbol;
        private boolean active;
        private int hoverShift = 0;

        NavButton(String label, String symbol) {
            this.label = label;
            this.symbol = symbol;
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setHorizontalAlignment(SwingConstants.LEFT);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setRolloverEnabled(true);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
            setPreferredSize(new Dimension(SIDEBAR_W_EXPANDED - 24, 40));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setToolTipText(label);
        }

        void setActive(boolean active) {
            this.active = active;
            repaint();
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();

            boolean collapsed = !MainFrame.this.sidebarExpanded;

            if (active) {
                // shadcn active item: elevated zinc pill + white indicator dot
                g2.setColor(new Color(0x27, 0x27, 0x2A));
                g2.fillRoundRect(0, 0, w, h, 8, 8);
                g2.setColor(new Color(0xFA, 0xFA, 0xFA));
                g2.fillRoundRect(9, h / 2 - 2, 4, 4, 2, 2);
            } else if (getModel().isRollover()) {
                g2.setColor(new Color(0x27, 0x27, 0x2A, 110));
                g2.fillRoundRect(0, 0, w, h, 8, 8);
            }

            hoverShift = getModel().isRollover() && !active ? 2 : 0;

            int iconX = collapsed ? w / 2 - 8 : 14;
            g2.setFont(new Font("SansSerif", Font.PLAIN, 15));
            g2.setColor(active || getModel().isRollover() ? Theme.TEXT_PRIMARY : Theme.TEXT_MUTED);
            g2.drawString(symbol, iconX + hoverShift, h / 2 + g2.getFontMetrics().getAscent() / 2 - 3);

            if (!collapsed) {
                g2.setFont(Theme.FONT_BODY);
                g2.setColor(active ? Theme.TEXT_PRIMARY : Theme.TEXT_SECONDARY);
                g2.drawString(label, 40 + hoverShift, h / 2 + g2.getFontMetrics().getAscent() / 2 - 1);
            }
            g2.dispose();
        }
    }

    private void toggleSidebar() {
        if (collapseTimer != null && collapseTimer.isRunning()) return;
        final boolean target = !sidebarExpanded;
        sidebarExpanded = target;
        final int from = sidebarPanel.getPreferredSize().width;
        final int to = target ? SIDEBAR_W_EXPANDED : SIDEBAR_W_COLLAPSED;
        collapseTimer = new javax.swing.Timer(14, null);
        final int[] step = {0};
        final int steps = 10;
        collapseTimer.addActionListener(e -> {
            step[0]++;
            float t = Math.min(1f, step[0] / (float) steps);
            float ease = 1f - (1f - t) * (1f - t); // ease-out
            int width = (int) (from + (to - from) * ease);
            sidebarPanel.setPreferredSize(new Dimension(width, 0));
            sidebarPanel.revalidate();
            navItemsPanel.repaint();
            if (step[0] >= steps) {
                collapseTimer.stop();
                for (NavButton b : navButtons) b.repaint();
            }
        });
        collapseTimer.start();
    }

    private void showView(String key) {
        cardLayout.show(cardPanel, key);
        for (NavButton b : navButtons) b.setActive(b.label.equals(labelForKey(key)));
        headerViewTitle.setText(labelForKey(key));
        if (VIEW_MAP.equals(key)) {
            graphPanel.resetView(); // refit when returning to the map — never gets stuck
        }
    }

    private String labelForKey(String key) {
        switch (key) {
            case VIEW_MAP: return "Live Map";
            case VIEW_DASHBOARD: return "Dashboard";
            case VIEW_ROUTE: return "Evacuation Routes";
            case VIEW_DISASTER: return "Disasters";
            case VIEW_SHELTERS: return "Emergency Shelters";
            case VIEW_ROADS: return "Road Status";
            default: return "Algorithms";
        }
    }

    // ====================================================================
    //  Command header
    // ====================================================================

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout(16, 0)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(Theme.BG_SIDEBAR);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(12, 20, 12, 20)));

        // Left: current view title (replaces generic status text)
        headerViewTitle = Theme.styledLabel("Live Map", Theme.FONT_SUBHEADER, Theme.TEXT_PRIMARY);
        header.add(headerViewTitle, BorderLayout.WEST);

        // Right: clock | alert badge | algorithms
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        right.setOpaque(false);

        clockLabel = Theme.monoLabel(currentTime(), Theme.TEXT_SECONDARY);
        right.add(clockLabel);
        new javax.swing.Timer(30_000, e -> clockLabel.setText(currentTime())).start();

        right.add(verticalDivider());

        alertBadgeHolder = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        alertBadgeHolder.setOpaque(false);
        setAlertLevel("GREEN");
        right.add(alertBadgeHolder);

        right.add(verticalDivider());
        right.add(Theme.monoLabel("DIJKSTRA \u00B7 BFS \u00B7 DFS \u00B7 MST", Theme.TEXT_MUTED));

        header.add(right, BorderLayout.EAST);
        return header;
    }

    private JComponent verticalDivider() {
        JPanel p = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(Theme.BORDER);
                g.fillRect(0, 0, 1, getHeight());
            }
        };
        p.setOpaque(false);
        p.setPreferredSize(new Dimension(1, 16));
        return p;
    }

    private String currentTime() {
        return new SimpleDateFormat("HH:mm:ss").format(new Date());
    }

    /** Header emergency alert badge (green/orange/red per severity). */
    public void setAlertLevel(String level) {
        Color color;
        String text;
        switch (level) {
            case "RED": color = Theme.STATUS_CRITICAL; text = "CRITICAL ALERT"; break;
            case "ORANGE": color = Theme.STATUS_HIGH_RISK; text = "HIGH RISK"; break;
            case "YELLOW": color = Theme.STATUS_CAUTION; text = "CAUTION"; break;
            default: color = Theme.STATUS_SAFE; text = "ALL CLEAR";
        }
        alertBadgeHolder.removeAll();
        alertBadgeHolder.add(Theme.pillBadge(text, color));
        alertBadgeHolder.revalidate();
        alertBadgeHolder.repaint();
    }

    private void updateHeaderAlert(Disaster disaster) {
        if (disaster == null) { setAlertLevel("GREEN"); return; }
        switch (disaster.getSeverity()) {
            case CRITICAL: setAlertLevel("RED"); break;
            case HIGH: setAlertLevel("ORANGE"); break;
            case MEDIUM: setAlertLevel("YELLOW"); break;
            default: setAlertLevel("GREEN");
        }
    }

    private String networkStats() {
        return graph.getLocationCount() + " loc \u00B7 " + graph.getRoadCount()
                + " roads \u00B7 " + safeZoneService.getAllSafeZones().size() + " shelters";
    }

    private void refreshSidebarStats() {
        sidebarStatsLabel.setText(networkStats());
    }

    // ====================================================================
    //  Views (CardLayout)
    // ====================================================================

    private JPanel createCardPanel() {
        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(Theme.BG_DARK);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        cardPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        // --- Live Map ---
        cardPanel.add(createMapView(), VIEW_MAP);

        // --- Dashboard ---
        dashboardPanel = new DashboardPanel(graph, routeService, disasterService, safeZoneService);
        cardPanel.add(dashboardPanel, VIEW_DASHBOARD);

        // --- Route Finder ---
        routePanel = new RoutePanel(graph, routeService, disasterService, safeZoneService, graphPanel);
        routePanel.setDashboardPanel(dashboardPanel);
        routePanel.setRouteHighlightCallback(route -> {
            graphPanel.setHighlightedRoute(route);
            graphPanel.setBFSVisited(null);
            graphPanel.setDFSVisited(null);
        });
        routePanel.setBfsHighlightCallback(visited -> {
            graphPanel.setBFSVisited(visited);
            graphPanel.setHighlightedRoute(null);
            graphPanel.setDFSVisited(null);
        });
        routePanel.setDfsHighlightCallback(visited -> {
            graphPanel.setDFSVisited(visited);
            graphPanel.setHighlightedRoute(null);
            graphPanel.setBFSVisited(null);
        });
        cardPanel.add(routePanel, VIEW_ROUTE);

        // --- Disaster Management ---
        disasterPanel = new DisasterPanel(graph, disasterService);
        disasterPanel.setOnDisasterApplied(disaster -> {
            dashboardPanel.updateDisasterStatus(disaster);
            dashboardPanel.updateStats();
            graphPanel.clearHighlights();
            updateHeaderAlert(disaster);
            refreshSidebarStats();
        });
        cardPanel.add(disasterPanel, VIEW_DISASTER);

        // --- Safe Zones ---
        safeZonePanel = new SafeZonePanel(graph, safeZoneService, routeService);
        cardPanel.add(safeZonePanel, VIEW_SHELTERS);

        // --- Road Status ---
        roadPanel = new RoadStatusPanel(graph, disasterService);
        cardPanel.add(roadPanel, VIEW_ROADS);

        // --- Algorithms ---
        algoPanel = new AlgorithmPanel(graph, routeService);
        algoPanel.setBfsCallback(visited -> {
            Set<Location> set = new java.util.LinkedHashSet<>(visited);
            graphPanel.setBFSVisited(set);
            graphPanel.setHighlightedRoute(null);
            graphPanel.setDFSVisited(null);
        });
        algoPanel.setDfsCallback(visited -> {
            Set<Location> set = new java.util.LinkedHashSet<>(visited);
            graphPanel.setDFSVisited(set);
            graphPanel.setHighlightedRoute(null);
            graphPanel.setBFSVisited(null);
        });
        algoPanel.setMstCallback(edges -> {
            java.util.List<Location> mstRoute = new java.util.ArrayList<>();
            Set<String> added = new java.util.HashSet<>();
            for (KruskalMST.MSTEdge e : edges) {
                if (added.add(e.getSource().getId() + "-" + e.getDestination().getId())) {
                    mstRoute.add(e.getSource());
                }
            }
            if (!edges.isEmpty()) mstRoute.add(edges.get(edges.size() - 1).getDestination());
            graphPanel.setHighlightedRoute(mstRoute);
            graphPanel.setBFSVisited(null);
            graphPanel.setDFSVisited(null);
        });
        cardPanel.add(algoPanel, VIEW_ALGORITHMS);

        return cardPanel;
    }

    /**
     * Live Map view: header strip (title + zoom/fit/locate controls) and the
     * interactive GraphPanel directly beneath it. No decorative component is
     * layered above the map — the canvas is the single interactive surface.
     */
    private JPanel createMapView() {
        JPanel view = new JPanel(new BorderLayout(0, 0));
        view.setOpaque(false);
        view.setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));

        // Header strip for the map card
        JPanel strip = new JPanel(new BorderLayout(12, 0));
        strip.setOpaque(false);
        strip.setBorder(BorderFactory.createEmptyBorder(0, 2, 12, 2));

        JPanel titleBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        titleBox.setOpaque(false);
        titleBox.add(Theme.iconTile("\u2691", 34));
        JPanel titleText = new JPanel();
        titleText.setOpaque(false);
        titleText.setLayout(new BoxLayout(titleText, BoxLayout.Y_AXIS));
        JLabel mapTitle = Theme.styledLabel("Live Operations Map", Theme.FONT_TITLE, Theme.TEXT_PRIMARY);
        JLabel mapSub = Theme.styledLabel("DRAG TO PAN \u00B7 SCROLL TO ZOOM \u00B7 CLICK MARKERS", Theme.FONT_LABEL, Theme.TEXT_MUTED);
        mapTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        mapSub.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleText.add(mapTitle);
        titleText.add(mapSub);
        titleBox.add(titleText);
        titleBox.add(Theme.pillBadge("LIVE", Theme.STATUS_SAFE));
        strip.add(titleBox, BorderLayout.WEST);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        controls.setOpaque(false);
        JButton zoomOutBtn = Theme.mapControlButton("\u2212");
        zoomOutBtn.addActionListener(e -> graphPanel.zoomOut());
        JButton zoomInBtn = Theme.mapControlButton("+");
        zoomInBtn.addActionListener(e -> graphPanel.zoomIn());
        JButton fitBtn = Theme.ghostButton("FIT");
        fitBtn.addActionListener(e -> graphPanel.resetView());
        JButton locateBtn = Theme.ghostButton("LOCATE");
        locateBtn.addActionListener(e -> graphPanel.locate());
        controls.add(zoomOutBtn);
        controls.add(zoomInBtn);
        controls.add(fitBtn);
        controls.add(locateBtn);
        strip.add(controls, BorderLayout.EAST);

        view.add(strip, BorderLayout.NORTH);

        // Map card wrapper: the interactive canvas fills the entire interior.
        JPanel mapCard = new JPanel(new BorderLayout(0, 0));
        mapCard.setOpaque(false);
        mapCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1, true),
                BorderFactory.createEmptyBorder(1, 1, 1, 1)));
        graphPanel = new GraphPanel(graph);
        Set<Location> szLocs = new java.util.HashSet<>();
        for (SafeZone sz : safeZoneService.getAllSafeZones()) szLocs.add(sz.getLocation());
        graphPanel.setSafeZoneLocations(szLocs);
        mapCard.add(graphPanel, BorderLayout.CENTER);
        view.add(mapCard, BorderLayout.CENTER);

        return view;
    }

    /**
     * Create sample disaster area data with terrain types.
     */
    public static Graph createSampleData(SafeZoneService safeZoneService) {
        Graph graph = new Graph();

        Location residentialArea = new Location("loc1", "Residential Area", 100, 300,
                Location.TerrainType.RESIDENTIAL);
        Location school = new Location("loc2", "School", 200, 150,
                Location.TerrainType.URBAN);
        Location hospital = new Location("loc3", "Hospital", 350, 100,
                Location.TerrainType.URBAN);
        Location market = new Location("loc4", "Market", 350, 280,
                Location.TerrainType.COMMERCIAL);
        Location junctionA = new Location("loc5", "Junction A", 250, 250,
                Location.TerrainType.URBAN);
        Location junctionB = new Location("loc6", "Junction B", 450, 200,
                Location.TerrainType.URBAN);
        Location railwayStation = new Location("loc7", "Railway Station", 500, 350,
                Location.TerrainType.TRANSPORT);
        Location stadium = new Location("loc8", "Stadium", 550, 100,
                Location.TerrainType.OPEN);
        Location reliefCampA = new Location("loc9", "Relief Camp A", 480, 400,
                Location.TerrainType.OPEN);
        Location reliefCampB = new Location("loc10", "Relief Camp B", 650, 250,
                Location.TerrainType.OPEN);

        graph.addLocation(residentialArea); graph.addLocation(school);
        graph.addLocation(hospital); graph.addLocation(market);
        graph.addLocation(junctionA); graph.addLocation(junctionB);
        graph.addLocation(railwayStation); graph.addLocation(stadium);
        graph.addLocation(reliefCampA); graph.addLocation(reliefCampB);

        // Roads with terrain types for disaster-specific risk
        graph.addRoad(new Road(residentialArea, junctionA, 3.0, Road.RiskLevel.LOW, Road.Status.OPEN, 2, Road.RoadTerrain.NORMAL));
        graph.addRoad(new Road(residentialArea, school, 5.0, Road.RiskLevel.LOW, Road.Status.OPEN, 1, Road.RoadTerrain.NORMAL));
        graph.addRoad(new Road(school, hospital, 4.0, Road.RiskLevel.LOW, Road.Status.OPEN, 3, Road.RoadTerrain.NORMAL));
        graph.addRoad(new Road(school, junctionA, 2.5, Road.RiskLevel.LOW, Road.Status.OPEN, 1, Road.RoadTerrain.NORMAL));
        graph.addRoad(new Road(junctionA, market, 3.0, Road.RiskLevel.LOW, Road.Status.OPEN, 4, Road.RoadTerrain.NORMAL));
        graph.addRoad(new Road(junctionA, junctionB, 4.5, Road.RiskLevel.LOW, Road.Status.OPEN, 2, Road.RoadTerrain.BRIDGE));
        graph.addRoad(new Road(hospital, junctionB, 3.5, Road.RiskLevel.LOW, Road.Status.OPEN, 1, Road.RoadTerrain.NORMAL));
        graph.addRoad(new Road(hospital, stadium, 2.0, Road.RiskLevel.LOW, Road.Status.OPEN, 0, Road.RoadTerrain.OPEN));
        graph.addRoad(new Road(market, railwayStation, 4.0, Road.RiskLevel.LOW, Road.Status.OPEN, 5, Road.RoadTerrain.INDUSTRIAL));
        graph.addRoad(new Road(market, junctionB, 3.0, Road.RiskLevel.LOW, Road.Status.OPEN, 2, Road.RoadTerrain.NORMAL));
        graph.addRoad(new Road(junctionB, stadium, 3.0, Road.RiskLevel.LOW, Road.Status.OPEN, 1, Road.RoadTerrain.OPEN));
        graph.addRoad(new Road(junctionB, reliefCampB, 2.5, Road.RiskLevel.LOW, Road.Status.OPEN, 0, Road.RoadTerrain.OPEN));
        graph.addRoad(new Road(railwayStation, reliefCampA, 2.0, Road.RiskLevel.LOW, Road.Status.OPEN, 1, Road.RoadTerrain.COASTAL));
        graph.addRoad(new Road(railwayStation, reliefCampB, 3.5, Road.RiskLevel.LOW, Road.Status.OPEN, 2, Road.RoadTerrain.COASTAL));
        graph.addRoad(new Road(stadium, reliefCampB, 2.0, Road.RiskLevel.LOW, Road.Status.OPEN, 0, Road.RoadTerrain.OPEN));
        graph.addRoad(new Road(residentialArea, market, 6.0, Road.RiskLevel.LOW, Road.Status.OPEN, 3, Road.RoadTerrain.FORESTED));

        // Safe zones
        safeZoneService.addSafeZone(new SafeZone("Relief Camp A", reliefCampA,
                500, 120, SafeZone.SafetyLevel.HIGH));
        safeZoneService.addSafeZone(new SafeZone("Relief Camp B", reliefCampB,
                300, 280, SafeZone.SafetyLevel.HIGH));
        safeZoneService.addSafeZone(new SafeZone("Stadium", stadium,
                1000, 50, SafeZone.SafetyLevel.MEDIUM));

        return graph;
    }
}
