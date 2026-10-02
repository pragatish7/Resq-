package ui;

import model.*;
import service.*;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Panel displaying safe zones as cards with capacity, distance, and safety info.
 * Command-center styling; data flow unchanged.
 */
public class SafeZonePanel extends JPanel {

    private Graph graph;
    private SafeZoneService safeZoneService;
    private RouteService routeService;
    private JPanel cardsContainer;

    public SafeZonePanel(Graph graph, SafeZoneService safeZoneService, RouteService routeService) {
        this.graph = graph;
        this.safeZoneService = safeZoneService;
        this.routeService = routeService;

        Theme.styleDarkPanel(this);
        setLayout(new BorderLayout(0, 0));
        setBorder(BorderFactory.createEmptyBorder(22, 26, 22, 26));

        add(createHeader(), BorderLayout.NORTH);
        add(createCards(), BorderLayout.CENTER);
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, 18, 0));

        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        JLabel subtitle = Theme.styledLabel("SHELTER CAPACITY & SAFETY STATUS", Theme.FONT_LABEL, Theme.TEXT_MUTED);
        JLabel title = Theme.displayLabel("Emergency Shelters", 26);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleBox.add(subtitle);
        titleBox.add(Box.createVerticalStrut(4));
        titleBox.add(title);
        header.add(titleBox, BorderLayout.WEST);

        return header;
    }

    private JPanel createCards() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        cardsContainer = new JPanel(new GridLayout(0, 3, 14, 14));
        cardsContainer.setOpaque(false);
        cardsContainer.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        updateCards();
        JScrollPane scroll = new JScrollPane(cardsContainer);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(Theme.BG_DARK);
        wrapper.add(scroll, BorderLayout.CENTER);
        return wrapper;
    }

    public void updateCards() {
        cardsContainer.removeAll();

        List<SafeZone> zones = safeZoneService.getAllSafeZones();

        for (SafeZone zone : zones) {
            cardsContainer.add(createSafeZoneCard(zone));
        }

        // Add spacer card if needed
        if (zones.size() % 3 != 0) {
            JPanel spacer = new JPanel();
            spacer.setOpaque(false);
            cardsContainer.add(spacer);
        }

        cardsContainer.revalidate();
        cardsContainer.repaint();
    }

    private JPanel createSafeZoneCard(SafeZone zone) {
        JPanel card = Theme.cardPanel();
        card.setLayout(new BorderLayout(0, 10));
        card.setPreferredSize(new Dimension(280, 210));

        // Header: Name + status pill
        JPanel headerRow = new JPanel(new BorderLayout());
        headerRow.setOpaque(false);
        headerRow.add(Theme.styledLabel(zone.getName(), Theme.FONT_TITLE, Theme.TEXT_PRIMARY), BorderLayout.WEST);

        Color statusColor = zone.isAvailable() ? Theme.ACCENT_GREEN : Theme.ACCENT_RED;
        String statusText = zone.isAvailable() ? "Available" : "Full";
        headerRow.add(Theme.pillBadge(statusText, statusColor), BorderLayout.EAST);
        card.add(headerRow, BorderLayout.NORTH);

        // Capacity bar
        JPanel barPanel = new JPanel(new BorderLayout(0, 4));
        barPanel.setOpaque(false);

        double pct = zone.getOccupancyPercent();
        String pctText = String.format("%d / %d \u00B7 %.0f%% occupied", zone.getCurrentOccupants(), zone.getCapacity(), pct);

        JProgressBar bar = new JProgressBar(0, 100);
        bar.setValue((int) pct);
        bar.setStringPainted(false);
        bar.setPreferredSize(new Dimension(0, 8));
        if (pct >= 90) bar.setForeground(Theme.ACCENT_RED);
        else if (pct >= 70) bar.setForeground(Theme.ACCENT);
        else bar.setForeground(Theme.ACCENT_GREEN);
        bar.setBackground(Theme.BG_INPUT);

        barPanel.add(Theme.monoLabel(pctText, Theme.TEXT_SECONDARY), BorderLayout.NORTH);
        barPanel.add(bar, BorderLayout.CENTER);
        card.add(barPanel, BorderLayout.CENTER);

        // Info rows
        JPanel infoGrid = new JPanel(new GridLayout(3, 2, 6, 6));
        infoGrid.setOpaque(false);

        infoGrid.add(createInfoRow("Safety", zone.getSafetyLevel().name(),
                zone.getSafetyLevel() == SafeZone.SafetyLevel.HIGH ? Theme.ACCENT_GREEN :
                zone.getSafetyLevel() == SafeZone.SafetyLevel.MEDIUM ? Theme.ACCENT : Theme.ACCENT_RED));
        infoGrid.add(createInfoRow("Available", String.valueOf(zone.getAvailableCapacity()), Theme.ACCENT_CYAN));
        infoGrid.add(createInfoRow("Capacity", String.valueOf(zone.getCapacity()), Theme.TEXT_SECONDARY));
        infoGrid.add(createInfoRow("Occupied", String.valueOf(zone.getCurrentOccupants()), Theme.TEXT_SECONDARY));
        infoGrid.add(createInfoRow("Location", zone.getLocation().getName(), Theme.ACCENT_BLUE));
        infoGrid.add(createInfoRow("Coords",
                "(" + zone.getLocation().getX() + ", " + zone.getLocation().getY() + ")",
                Theme.TEXT_MUTED));

        card.add(infoGrid, BorderLayout.SOUTH);

        return card;
    }

    private JPanel createInfoRow(String label, String value, Color valueColor) {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setOpaque(false);
        row.add(Theme.styledLabel(label, Theme.FONT_LABEL, Theme.TEXT_MUTED), BorderLayout.WEST);
        row.add(Theme.monoLabel(value, valueColor), BorderLayout.EAST);
        return row;
    }
}
