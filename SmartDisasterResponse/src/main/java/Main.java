import model.*;
import service.*;
import ui.MainFrame;

import javax.swing.*;
import java.awt.*;

/**
 * Main entry point for the Smart Disaster Response and Evacuation Management System.
 * 
 * Run: javac -d bin src/model/*.java src/algorithms/*.java src/service/*.java src/ui/*.java src/Main.java && java -cp bin Main
 */
public class Main {

    public static void main(String[] args) {
        System.out.println("Starting Smart Disaster Response and Evacuation Management System...");

        SafeZoneService safeZoneService = new SafeZoneService();
        Graph graph = MainFrame.createSampleData(safeZoneService);

        System.out.println("Graph loaded: " + graph.getLocationCount() + " locations, "
                + graph.getRoadCount() + " roads");
        System.out.println("Safe zones: " + safeZoneService.getAllSafeZones().size());
        graph.printGraph();

        RouteService routeService = new RouteService(graph);
        DisasterService disasterService = new DisasterService(graph);

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) { }

            // Force dark command-center theme on ALL Swing UI components BEFORE creating the frame
            UIManager.put("Panel.background", ui.Theme.BG_DARK);
            UIManager.put("OptionPane.background", ui.Theme.BG_CARD);
            UIManager.put("OptionPane.messageForeground", ui.Theme.TEXT_PRIMARY);
            UIManager.put("List.background", ui.Theme.BG_CARD);
            UIManager.put("List.foreground", ui.Theme.TEXT_PRIMARY);
            UIManager.put("List.selectionBackground", new Color(0x3F, 0x3F, 0x46));
            UIManager.put("List.selectionForeground", ui.Theme.TEXT_PRIMARY);
            UIManager.put("ComboBox.background", ui.Theme.BG_INPUT);
            UIManager.put("ComboBox.foreground", ui.Theme.TEXT_PRIMARY);
            UIManager.put("ComboBox.selectionBackground", new Color(0x3F, 0x3F, 0x46));
            UIManager.put("ComboBox.selectionForeground", ui.Theme.TEXT_PRIMARY);
            UIManager.put("ComboBox.buttonBackground", ui.Theme.BG_INPUT);
            UIManager.put("ComboBox.buttonDarkShadow", ui.Theme.BORDER);
            UIManager.put("ScrollPane.background", ui.Theme.BG_DARK);
            UIManager.put("Viewport.background", ui.Theme.BG_DARK);
            UIManager.put("Table.background", ui.Theme.BG_CARD);
            UIManager.put("Table.foreground", ui.Theme.TEXT_PRIMARY);
            UIManager.put("Table.gridColor", ui.Theme.BORDER);
            UIManager.put("Table.selectionBackground", new Color(0x3F, 0x3F, 0x46));
            UIManager.put("TableHeader.background", ui.Theme.BG_SIDEBAR);
            UIManager.put("TableHeader.foreground", ui.Theme.TEXT_MUTED);
            UIManager.put("ToolTip.background", ui.Theme.BG_SIDEBAR);
            UIManager.put("ToolTip.foreground", ui.Theme.TEXT_PRIMARY);
            UIManager.put("ToolTip.border", javax.swing.BorderFactory.createLineBorder(ui.Theme.BORDER));
            UIManager.put("ProgressBar.foreground", ui.Theme.ACCENT_GREEN);
            UIManager.put("ProgressBar.background", ui.Theme.BG_INPUT);
            UIManager.put("ProgressBar.selectionBackground", ui.Theme.TEXT_PRIMARY);
            UIManager.put("Separator.foreground", ui.Theme.BORDER);

            MainFrame frame = new MainFrame(graph, routeService, disasterService, safeZoneService);
            frame.setVisible(true);

            System.out.println("\nApplication launched successfully!");
        });
    }
}
