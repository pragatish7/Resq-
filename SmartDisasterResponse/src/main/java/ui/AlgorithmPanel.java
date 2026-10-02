package ui;

import model.*;
import algorithms.*;
import service.*;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Panel for running and displaying BFS, DFS, and MST algorithms.
 * Command-center styling; algorithm invocation logic unchanged.
 */
public class AlgorithmPanel extends JPanel {

    private Graph graph;
    private RouteService routeService;
    private JComboBox<String> locationCombo;
    private JTextArea outputArea;
    private Consumer<List<Location>> bfsCallback;
    private Consumer<List<Location>> dfsCallback;
    private Consumer<List<KruskalMST.MSTEdge>> mstCallback;

    public AlgorithmPanel(Graph graph, RouteService routeService) {
        this.graph = graph;
        this.routeService = routeService;

        Theme.styleDarkPanel(this);
        setLayout(new BorderLayout(0, 0));
        setBorder(BorderFactory.createEmptyBorder(22, 26, 22, 26));

        add(createHeader(), BorderLayout.NORTH);
        add(createContent(), BorderLayout.CENTER);
    }

    public void setBfsCallback(Consumer<List<Location>> cb) { this.bfsCallback = cb; }
    public void setDfsCallback(Consumer<List<Location>> cb) { this.dfsCallback = cb; }
    public void setMstCallback(Consumer<List<KruskalMST.MSTEdge>> cb) { this.mstCallback = cb; }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, 18, 0));

        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        JLabel subtitle = Theme.styledLabel("TRAVERSALS & NETWORK ANALYSIS", Theme.FONT_LABEL, Theme.TEXT_MUTED);
        JLabel title = Theme.displayLabel("Algorithms", 26);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleBox.add(subtitle);
        titleBox.add(Box.createVerticalStrut(4));
        titleBox.add(title);
        header.add(titleBox, BorderLayout.WEST);
        return header;
    }

    private JPanel createContent() {
        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setOpaque(false);

        // Controls row
        JPanel controls = Theme.cardPanel();
        controls.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 8));

        controls.add(Theme.sectionLabel("Start Location", Theme.TEXT_MUTED));
        controls.add(Box.createHorizontalStrut(4));

        List<String> names = new ArrayList<>();
        for (Location loc : graph.getAllLocations()) names.add(loc.getName());
        Collections.sort(names);
        locationCombo = new JComboBox<>(names.toArray(new String[0]));
        locationCombo.setFont(Theme.FONT_BODY);
        locationCombo.setBackground(Theme.BG_INPUT);
        locationCombo.setForeground(Theme.TEXT_PRIMARY);
        controls.add(locationCombo);

        controls.add(Box.createHorizontalStrut(8));

        JButton bfsBtn = Theme.styledButton("RUN BFS", Theme.ACCENT_CYAN);
        bfsBtn.addActionListener(e -> runBFS());
        controls.add(bfsBtn);

        JButton dfsBtn = Theme.styledButton("RUN DFS", Theme.ACCENT_PURPLE);
        dfsBtn.addActionListener(e -> runDFS());
        controls.add(dfsBtn);

        JButton mstBtn = Theme.styledButton("GENERATE MST", Theme.ACCENT);
        mstBtn.addActionListener(e -> runMST());
        controls.add(mstBtn);

        content.add(controls, BorderLayout.NORTH);

        // Output area
        outputArea = new JTextArea();
        outputArea.setEditable(false);
        outputArea.setBackground(Theme.BG_INPUT);
        outputArea.setForeground(Theme.TEXT_PRIMARY);
        outputArea.setFont(Theme.FONT_MONO);
        outputArea.setLineWrap(true);
        outputArea.setWrapStyleWord(true);
        outputArea.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        outputArea.setText("Select a location and click an algorithm button to see results.\n\n"
                + "BFS \u2014 Breadth-First Search: Level-wise exploration, minimum edges.\n"
                + "DFS \u2014 Depth-First Search: Deep exploration, connectivity check.\n"
                + "MST \u2014 Minimum Spanning Tree: Kruskal's algorithm for minimum-cost network.");

        JScrollPane scroll = new JScrollPane(outputArea);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        scroll.getViewport().setBackground(Theme.BG_INPUT);
        content.add(scroll, BorderLayout.CENTER);

        return content;
    }

    private void runBFS() {
        String name = (String) locationCombo.getSelectedItem();
        Location source = graph.getLocationByName(name);
        if (source == null) { showError("Invalid location"); return; }

        List<Location> traversal = routeService.runBFSTraversal(source);
        Map<Integer, List<Location>> levels = routeService.runBFSByLevel(source);

        StringBuilder sb = new StringBuilder();
        sb.append("\u2550\u2550\u2550 BFS \u2014 BREADTH-FIRST SEARCH \u2550\u2550\u2550\n\n");
        sb.append("Algorithm: Breadth-First Search\n");
        sb.append("Source: ").append(name).append("\n");
        sb.append("Purpose: Find minimum number of roads between locations\n\n");
        sb.append("Traversal Order:\n");
        for (int i = 0; i < traversal.size(); i++) {
            sb.append(String.format("  %2d. %s%n", i + 1, traversal.get(i).getName()));
        }
        sb.append("\nLevel-wise Exploration:\n");
        for (Map.Entry<Integer, List<Location>> entry : levels.entrySet()) {
            List<String> levelNames = new ArrayList<>();
            for (Location l : entry.getValue()) levelNames.add(l.getName());
            sb.append("  Level ").append(entry.getKey()).append(": ").append(String.join(", ", levelNames)).append("\n");
        }
        sb.append("\nBFS String:\n  ");
        for (int i = 0; i < traversal.size(); i++) {
            sb.append(traversal.get(i).getName());
            if (i < traversal.size() - 1) sb.append(" \u2192 ");
        }
        sb.append("\n");

        outputArea.setText(sb.toString());
        outputArea.setCaretPosition(0);
        if (bfsCallback != null) bfsCallback.accept(traversal);
    }

    private void runDFS() {
        String name = (String) locationCombo.getSelectedItem();
        Location source = graph.getLocationByName(name);
        if (source == null) { showError("Invalid location"); return; }

        List<Location> traversal = routeService.runDFSTraversal(source);

        StringBuilder sb = new StringBuilder();
        sb.append("\u2550\u2550\u2550 DFS \u2014 DEPTH-FIRST SEARCH \u2550\u2550\u2550\n\n");
        sb.append("Algorithm: Depth-First Search\n");
        sb.append("Source: ").append(name).append("\n");
        sb.append("Purpose: Explore all connected disaster-affected regions\n\n");
        sb.append("Traversal Order:\n");
        for (int i = 0; i < traversal.size(); i++) {
            sb.append(String.format("  %2d. %s%n", i + 1, traversal.get(i).getName()));
        }
        sb.append("\nDFS String:\n  ");
        for (int i = 0; i < traversal.size(); i++) {
            sb.append(traversal.get(i).getName());
            if (i < traversal.size() - 1) sb.append(" \u2192 ");
        }

        sb.append("\n\nConnectivity Check:\n");
        for (Location loc : graph.getAllLocations()) {
            if (!loc.equals(source)) {
                boolean connected = routeService.checkConnectivity(source, loc);
                sb.append(String.format("  %s \u2192 %s: %s%n",
                        source.getName(), loc.getName(),
                        connected ? "CONNECTED \u2713" : "NOT CONNECTED \u2717"));
            }
        }

        outputArea.setText(sb.toString());
        outputArea.setCaretPosition(0);
        if (dfsCallback != null) dfsCallback.accept(traversal);
    }

    private void runMST() {
        KruskalMST.MSTResult result = routeService.generateMST();

        StringBuilder sb = new StringBuilder();
        sb.append("\u2550\u2550\u2550 MST \u2014 KRUSKAL'S ALGORITHM \u2550\u2550\u2550\n\n");
        sb.append("Algorithm: Kruskal's Minimum Spanning Tree\n");
        sb.append("Purpose: Connect all locations with minimum total road distance\n\n");
        sb.append("Selected Edges:\n");
        for (int i = 0; i < result.getEdges().size(); i++) {
            KruskalMST.MSTEdge e = result.getEdges().get(i);
            sb.append(String.format("  %2d. %s \u2014 %.1f km \u2014 %s%n",
                    i + 1, e.getSource().getName(), e.getDistance(), e.getDestination().getName()));
        }
        sb.append(String.format("%nTotal MST Cost: %.1f km%n", result.getTotalCost()));
        sb.append(String.format("Edges: %d / %d (V-1)%n", result.getEdges().size(), graph.getLocationCount() - 1));
        sb.append("Complete: ").append(result.isComplete() ? "YES \u2713" : "NO \u2717 (disconnected graph)").append("\n");

        outputArea.setText(sb.toString());
        outputArea.setCaretPosition(0);
        if (mstCallback != null) mstCallback.accept(result.getEdges());
    }

    private void showError(String msg) {
        outputArea.setText("ERROR: " + msg);
        outputArea.setCaretPosition(0);
    }
}
