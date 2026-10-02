package ui;

import model.*;

import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

/**
 * Interactive map canvas for the disaster-area graph.
 *
 * Full map interaction:
 *  - Left-click + drag pans the map (grab/grabbing cursors)
 *  - Mouse wheel zooms (anchored at the cursor)
 *  - Zoom in / out / fit / locate controls (public API, wired by MainFrame)
 *  - Clicking a marker opens an on-canvas detail card (no modal dialogs)
 *  - Clicking empty space clears the selection
 *  - Auto-fits content on first layout and on resize until the user navigates
 *
 * Decorative layers (grid, glow, legend, zoom badge, detail card) are painted
 * directly in paintComponent, so no overlay component ever blocks pointer
 * events meant for the map.
 */
public class GraphPanel extends JPanel {

    private Graph graph;
    private List<Location> highlightedRoute;
    private Set<Location> safeZoneLocations;
    private Set<Location> bfsVisited;
    private Set<Location> dfsVisited;
    private Location currentLocation;
    private Location selectedLocation;
    private Location hoveredLocation;

    // ==== Viewport transform (world coords -> screen) ====
    private double zoom = 1.0;
    private int offsetX = 0;
    private int offsetY = 0;
    private boolean userNavigated = false;
    private boolean fittedOnce = false;

    private static final int NODE_RADIUS = 16;          // world-space radius
    private static final double MIN_ZOOM = 0.3;
    private static final double MAX_ZOOM = 4.0;
    private static final int HIT_SLOP = 8;              // extra px for marker hit test

    // ==== Decorative scenery (deterministic, derived from real graph data) ====
    private final Map<Location, BuildingFootprint[]> sceneryCache = new HashMap<>();
    private final Map<Location, DistrictPatch> districtCache = new HashMap<>();

    // ==== Ambient animation (repaint-only; never intercepts input) ====
    private final javax.swing.Timer animTimer;
    private int animPhase = 0;                          // ticks at ~30fps

    public GraphPanel(Graph graph) {
        this.graph = graph;
        this.highlightedRoute = new ArrayList<>();
        this.safeZoneLocations = new HashSet<>();
        this.bfsVisited = new HashSet<>();
        this.dfsVisited = new HashSet<>();
        this.currentLocation = null;

        setBackground(Theme.BG_DARK);
        setPreferredSize(new Dimension(680, 520));
        // The canvas itself is the interactive layer; nothing sits above it.
        setFocusable(true);
        setOpaque(true);

        installInteractions();

        // Ambient animation clock: repaints only — it never mutates pan/zoom
        // state or intercepts mouse/keyboard events.
        animTimer = new javax.swing.Timer(33, e -> {
            animPhase = (animPhase + 1) % 1800;
            repaint();
        });
        animTimer.setCoalesce(true);

        // Refit when the container resizes (unless the user has navigated).
        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                if (!userNavigated) fitToContent(false);
            }
        });
    }

    @Override public void addNotify() { super.addNotify(); animTimer.start(); }
    @Override public void removeNotify() { animTimer.stop(); super.removeNotify(); }

    // ====================================================================
    //  Public state setters (existing API — preserved)
    // ====================================================================

    public void setHighlightedRoute(List<Location> route) {
        this.highlightedRoute = route != null ? route : new ArrayList<>();
        if (!this.highlightedRoute.isEmpty() && !userNavigated) fitToContent(false);
        repaint();
    }
    public void setSafeZoneLocations(Set<Location> locations) {
        this.safeZoneLocations = locations != null ? locations : new HashSet<>();
        repaint();
    }
    public void setBFSVisited(Set<Location> visited) {
        this.bfsVisited = visited != null ? visited : new HashSet<>();
        this.dfsVisited = new HashSet<>();
        repaint();
    }
    public void setDFSVisited(Set<Location> visited) {
        this.dfsVisited = visited != null ? visited : new HashSet<>();
        this.bfsVisited = new HashSet<>();
        repaint();
    }
    public void setCurrentLocation(Location loc) {
        this.currentLocation = loc;
        repaint();
    }
    public void clearHighlights() {
        this.highlightedRoute = new ArrayList<>();
        this.bfsVisited = new HashSet<>();
        this.dfsVisited = new HashSet<>();
        this.currentLocation = null;
        repaint();
    }

    // ====================================================================
    //  Map controls (wired to header buttons in MainFrame)
    // ====================================================================

    public void zoomIn()  { zoomAt(getWidth() / 2.0, getHeight() / 2.0, Math.min(MAX_ZOOM, zoom * 1.25)); }
    public void zoomOut() { zoomAt(getWidth() / 2.0, getHeight() / 2.0, Math.max(MIN_ZOOM, zoom / 1.25)); }
    public void resetView() {
        userNavigated = false;
        selectedLocation = null;
        fitToContent(true);
    }
    /** Center on the current/route location if present, otherwise fit all. */
    public void locate() {
        Location target = currentLocation != null ? currentLocation
                : (!highlightedRoute.isEmpty() ? highlightedRoute.get(0) : null);
        if (target == null) { resetView(); return; }
        double newZoom = Math.max(zoom, 1.4);
        offsetX = (int) (getWidth() / 2.0 - target.getX() * newZoom);
        offsetY = (int) (getHeight() / 2.0 - target.getY() * newZoom);
        zoom = newZoom;
        userNavigated = true;
        repaint();
    }

    private void fitToContent(boolean force) {
        if (graph == null || graph.getAllLocations().isEmpty()) return;
        int w = getWidth(), h = getHeight();
        if (w < 40 || h < 40) { fittedOnce = false; return; }   // wait for real layout

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (Location loc : graph.getAllLocations()) {
            minX = Math.min(minX, loc.getX()); minY = Math.min(minY, loc.getY());
            maxX = Math.max(maxX, loc.getX()); maxY = Math.max(maxY, loc.getY());
        }
        int pad = 70;
        double zx = (w - pad * 2.0) / Math.max(1, (maxX - minX));
        double zy = (h - pad * 2.0) / Math.max(1, (maxY - minY));
        double newZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, Math.min(zx, zy)));

        double cx = (minX + maxX) / 2.0, cy = (minY + maxY) / 2.0;
        zoom = newZoom;
        offsetX = (int) (w / 2.0 - cx * zoom);
        offsetY = (int) (h / 2.0 - cy * zoom);
        fittedOnce = true;
        repaint();
    }

    // ====================================================================
    //  Interaction: drag-pan, wheel-zoom, marker clicks, hover
    // ====================================================================

    private void installInteractions() {
        final Point pressPoint = new Point();
        final int[] pressOffset = new int[2];
        final boolean[] dragging = { false };
        final boolean[] moved = { false };

        MouseAdapter ma = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                pressPoint.setLocation(e.getPoint());
                pressOffset[0] = offsetX;
                pressOffset[1] = offsetY;
                moved[0] = false;
                dragging[0] = true;
                setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            }

            @Override public void mouseDragged(MouseEvent e) {
                if (!dragging[0]) return;
                int dx = e.getX() - pressPoint.x;
                int dy = e.getY() - pressPoint.y;
                if (Math.abs(dx) + Math.abs(dy) > 3) moved[0] = true;
                offsetX = pressOffset[0] + dx;
                offsetY = pressOffset[1] + dy;
                userNavigated = true;
                repaint();
            }

            @Override public void mouseReleased(MouseEvent e) {
                dragging[0] = false;
                setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                if (!moved[0]) {
                    // Treat as a click: toggle marker selection or clear it.
                    Location hit = nodeAt(e.getX(), e.getY());
                    selectedLocation = (hit != selectedLocation) ? hit : null;
                    repaint();
                }
            }

            @Override public void mouseMoved(MouseEvent e) {
                Location hit = nodeAt(e.getX(), e.getY());
                if (hit != hoveredLocation) {
                    hoveredLocation = hit;
                    setCursor(hit != null
                            ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                            : Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                    repaint();
                }
            }

            @Override public void mouseWheelMoved(MouseWheelEvent e) {
                double factor = Math.pow(1.15, -e.getWheelRotation());
                zoomAt(e.getX(), e.getY(), Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * factor)));
            }

            @Override public void mouseExited(MouseEvent e) {
                if (hoveredLocation != null) { hoveredLocation = null; repaint(); }
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
        addMouseWheelListener(ma);
    }

    /** Zoom keeping the world point under (ax, ay) anchored in place. */
    private void zoomAt(double ax, double ay, double newZoom) {
        if (!fittedOnce && !userNavigated) fitToContent(false);
        newZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, newZoom));
        if (Math.abs(newZoom - zoom) < 1e-6) return;
        offsetX = (int) (ax - (ax - offsetX) * newZoom / zoom);
        offsetY = (int) (ay - (ay - offsetY) * newZoom / zoom);
        zoom = newZoom;
        userNavigated = true;
        repaint();
    }

    /** Find the top-most node under screen coordinates (null if none). */
    private Location nodeAt(int sx, int sy) {
        if (graph == null) return null;
        Location best = null;
        double bestDist = Double.MAX_VALUE;
        for (Location loc : graph.getAllLocations()) {
            int r = nodeScreenRadius();
            double dx = sx - (loc.getX() * zoom + offsetX);
            double dy = sy - (loc.getY() * zoom + offsetY);
            double d = Math.hypot(dx, dy);
            if (d <= r + HIT_SLOP && d < bestDist) { best = loc; bestDist = d; }
        }
        return best;
    }

    private int nodeScreenRadius() {
        return (int) Math.max(9, Math.min(24, NODE_RADIUS * zoom));
    }

    // ====================================================================
    //  Painting
    // ====================================================================

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        if (graph == null || graph.getAllLocations().isEmpty()) {
            g2d.setColor(Theme.TEXT_MUTED);
            g2d.setFont(Theme.FONT_SUBHEADER);
            g2d.drawString("No graph data to display", 200, 250);
            return;
        }

        paintBackdrop(g2d);
        paintDistricts(g2d);
        paintRoads(g2d);
        paintNodes(g2d);
        paintOnCanvasChrome(g2d);
    }

    /** Near-black backdrop with subtle maroon corner vignettes + faint world grid. */
    private void paintBackdrop(Graphics2D g2d) {
        int w = getWidth(), h = getHeight();

        // Subtle neutral corner light — purely decorative, painted behind content.
        g2d.setPaint(new RadialGradientPaint(
                new Point(0, 0), Math.max(w, h) * 1.1f,
                new float[]{0f, 1f},
                new Color[]{new Color(0x1C, 0x1C, 0x20, 120), new Color(0, 0, 0, 0)}));
        g2d.fillRect(0, 0, w, h);
        g2d.setPaint(null);

        // World-space grid so panning feels physical.
        double step = 50 * zoom;
        if (step > 14) {
            g2d.setColor(Theme.MAP_GRID);
            g2d.setStroke(new BasicStroke(1));
            double startX = offsetX % step; if (startX < 0) startX += step;
            for (double x = startX; x < w; x += step) g2d.drawLine((int) x, 0, (int) x, h);
            double startY = offsetY % step; if (startY < 0) startY += step;
            for (double y = startY; y < h; y += step) g2d.drawLine(0, (int) y, w, (int) y);
        }
    }

    private void paintRoads(Graphics2D g2d) {
        Set<String> drawn = new HashSet<>();
        for (Location loc : graph.getAllLocations()) {
            for (Road road : graph.getRoads(loc)) {
                Location dest = road.getDestination();
                String key = loc.getId().compareTo(dest.getId()) < 0
                        ? loc.getId() + "-" + dest.getId() : dest.getId() + "-" + loc.getId();
                if (!drawn.add(key)) continue;

                boolean isHighlighted = isRouteEdge(loc, dest);

                int x1 = (int) (loc.getX() * zoom + offsetX);
                int y1 = (int) (loc.getY() * zoom + offsetY);
                int x2 = (int) (dest.getX() * zoom + offsetX);
                int y2 = (int) (dest.getY() * zoom + offsetY);

                Color roadColor;
                BasicStroke stroke;
                if (road.isBlocked()) {
                    roadColor = Theme.ROAD_BLOCKED;
                    stroke = new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{6, 4}, -animPhase * 0.25f);
                } else if (road.getStatus() == Road.Status.HIGH_RISK) {
                    roadColor = Theme.ROAD_HIGH_RISK;
                    stroke = new BasicStroke(2.2f);
                } else if (road.getStatus() == Road.Status.DAMAGED) {
                    roadColor = Theme.ROAD_DAMAGED;
                    stroke = new BasicStroke(1.8f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{8, 4}, -animPhase * 0.15f);
                } else if (isHighlighted) {
                    // skip — painted separately below with glow
                    continue;
                } else {
                    // dark casing underlay for a real-map look
                    g2d.setColor(new Color(0x07, 0x03, 0x06));
                    g2d.setStroke(new BasicStroke(3.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2d.drawLine(x1, y1, x2, y2);
                    roadColor = Theme.ROAD_OPEN;
                    stroke = new BasicStroke(1.4f);
                }
                g2d.setColor(roadColor);
                g2d.setStroke(stroke);
                g2d.drawLine(x1, y1, x2, y2);

                if (zoom > 0.6) {
                    g2d.setFont(Theme.monoFont(10f));
                    g2d.setColor(Theme.TEXT_MUTED);
                    g2d.drawString(String.format("%.1f", road.getDistance()),
                            (x1 + x2) / 2 + 3, (y1 + y2) / 2 - 5);
                }
            }
        }

        // Highlighted evacuation route: breathing glow underlay + solid cream
        // stroke + flowing direction dashes (animated, continuous across turns).
        if (highlightedRoute != null && highlightedRoute.size() > 1) {
            java.awt.geom.Path2D.Float path = new java.awt.geom.Path2D.Float();
            Location first = highlightedRoute.get(0);
            path.moveTo(first.getX() * zoom + offsetX, first.getY() * zoom + offsetY);
            for (int i = 1; i < highlightedRoute.size(); i++) {
                Location p = highlightedRoute.get(i);
                path.lineTo(p.getX() * zoom + offsetX, p.getY() * zoom + offsetY);
            }
            int breathe = Math.max(0, (int) (Math.sin(animPhase / 16.0) * 12));
            g2d.setColor(new Color(0xFA, 0xFA, 0xFA, 18 + breathe));
            g2d.setStroke(new BasicStroke(9.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2d.draw(path);
            g2d.setColor(new Color(0xD4, 0xD4, 0xD8, 70));
            g2d.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2d.draw(path);
            g2d.setColor(Theme.ROAD_HIGHLIGHT);
            g2d.setStroke(new BasicStroke(3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2d.draw(path);
            g2d.setColor(new Color(0x09, 0x09, 0x0B, 185));
            g2d.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10,
                    new float[]{9, 13}, -animPhase * 0.6f));
            g2d.draw(path);
        }
    }

    private boolean isRouteEdge(Location a, Location b) {
        if (highlightedRoute == null || highlightedRoute.size() < 2) return false;
        for (int i = 0; i < highlightedRoute.size() - 1; i++) {
            Location u = highlightedRoute.get(i), v = highlightedRoute.get(i + 1);
            if ((a.equals(u) && b.equals(v)) || (a.equals(v) && b.equals(u))) return true;
        }
        return false;
    }

    private void paintNodes(Graphics2D g2d) {
        for (Location loc : graph.getAllLocations()) {
            int x = (int) (loc.getX() * zoom + offsetX);
            int y = (int) (loc.getY() * zoom + offsetY);
            if (x < -60 || y < -60 || x > getWidth() + 60 || y > getHeight() + 60) continue;
            int r = nodeScreenRadius();

            Color nodeColor;
            if (loc.equals(currentLocation)) nodeColor = Theme.NODE_CURRENT;
            else if (safeZoneLocations.contains(loc)) nodeColor = Theme.NODE_SAFE_ZONE;
            else if (bfsVisited.contains(loc)) nodeColor = Theme.NODE_BFS;
            else if (dfsVisited.contains(loc)) nodeColor = Theme.NODE_DFS;
            else if (highlightedRoute != null && highlightedRoute.contains(loc)) nodeColor = Theme.NODE_ROUTE;
            else nodeColor = Theme.NODE_DEFAULT;

            // Route / visited / current nodes get a soft halo.
            boolean emphasized = highlightedRoute.contains(loc) || loc.equals(currentLocation)
                    || bfsVisited.contains(loc) || dfsVisited.contains(loc);
            if (emphasized) {
                g2d.setColor(new Color(nodeColor.getRed(), nodeColor.getGreen(), nodeColor.getBlue(), 46));
                g2d.fillOval(x - r - 7, y - r - 7, (r + 7) * 2, (r + 7) * 2);
            }
            // Safe-zone expanding pulse rings (two offset phases)
            if (safeZoneLocations.contains(loc) && zoom > 0.4) {
                double t = (animPhase % 90) / 90.0;
                for (int k = 0; k < 2; k++) {
                    double tt = (t + k * 0.5) % 1.0;
                    float rad = (float) ((r + 4) + tt * 17);
                    int alpha = (int) (95 * (1 - tt));
                    g2d.setColor(new Color(Theme.NODE_SAFE_ZONE.getRed(), Theme.NODE_SAFE_ZONE.getGreen(), Theme.NODE_SAFE_ZONE.getBlue(), alpha));
                    g2d.setStroke(new BasicStroke(1.5f));
                    g2d.drawOval((int) (x - rad), (int) (y - rad), (int) (rad * 2), (int) (rad * 2));
                }
            }

            g2d.setColor(new Color(0, 0, 0, 130));           // contact shadow
            g2d.fillOval(x - r + 2, y - r + 3, r * 2, r * 2);
            g2d.setColor(nodeColor);
            g2d.fillOval(x - r, y - r, r * 2, r * 2);
            g2d.setColor(loc.equals(hoveredLocation) || loc.equals(selectedLocation)
                    ? Color.WHITE : new Color(0xE4, 0xE4, 0xE7));
            g2d.setStroke(new BasicStroke(loc.equals(selectedLocation) ? 2.4f : 1.6f));
            g2d.drawOval(x - r, y - r, r * 2, r * 2);

            // Hover ripple — slow expanding ring on the hovered marker
            if (loc.equals(hoveredLocation) && zoom > 0.4) {
                double t = (animPhase % 60) / 60.0;
                float rad = (float) ((r + 2) + t * 9);
                int alpha = (int) (120 * (1 - t));
                g2d.setColor(new Color(0xE4, 0xE4, 0xE7, alpha));
                g2d.setStroke(new BasicStroke(1.3f));
                g2d.drawOval((int) (x - rad), (int) (y - rad), (int) (rad * 2), (int) (rad * 2));
            }

            if (loc.equals(currentLocation)) { // start-of-route marker + pulsing halo
                g2d.setColor(Theme.NODE_CURRENT);
                g2d.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{3, 3}, 0));
                g2d.drawOval(x - r - 5, y - r - 5, (r + 5) * 2, (r + 5) * 2);
                if (zoom > 0.4) {
                    double t = (animPhase % 75) / 75.0;
                    float rad = (float) ((r + 6) + t * 13);
                    int alpha = (int) (110 * (1 - t));
                    g2d.setColor(new Color(Theme.NODE_CURRENT.getRed(), Theme.NODE_CURRENT.getGreen(), Theme.NODE_CURRENT.getBlue(), alpha));
                    g2d.setStroke(new BasicStroke(1.6f));
                    g2d.drawOval((int) (x - rad), (int) (y - rad), (int) (rad * 2), (int) (rad * 2));
                }
            }

            // Name label
            float fontSize = (float) Math.max(9, Math.min(13, 11 * zoom));
            g2d.setFont(Theme.interFont(Font.BOLD, fontSize));
            FontMetrics fm = g2d.getFontMetrics();
            String name = loc.getName();
            int tw = fm.stringWidth(name);
            g2d.setColor(Theme.TEXT_PRIMARY);
            g2d.drawString(name, x - tw / 2, y + r + fm.getAscent() + 2);
        }
    }

    // ====================================================================
    //  Procedural scenery — deterministic footprints derived from the graph
    // ====================================================================

    /** A small rotated rectangle building footprint; offsets in world units. */
    private static final class BuildingFootprint {
        final float dx, dy, w, h, rotDeg, shade;
        BuildingFootprint(float dx, float dy, float w, float h, float rotDeg, float shade) {
            this.dx = dx; this.dy = dy; this.w = w; this.h = h; this.rotDeg = rotDeg; this.shade = shade;
        }
    }

    /** Soft district ground patch (park / water / plaza) in world units. */
    private static final class DistrictPatch {
        final float x, y, w, h, rotDeg;
        final Location.TerrainType terrain;
        DistrictPatch(float x, float y, float w, float h, float rotDeg, Location.TerrainType terrain) {
            this.x = x; this.y = y; this.w = w; this.h = h; this.rotDeg = rotDeg; this.terrain = terrain;
        }
    }

    /** Buildings around a location, seeded from its id — stable across runs. */
    private BuildingFootprint[] buildingsFor(Location loc) {
        BuildingFootprint[] cached = sceneryCache.get(loc);
        if (cached != null) return cached;
        Random rng = new Random(0x515CA5EDL ^ (long) loc.getId().hashCode() * 7919);
        List<Road> roads = graph.getRoads(loc);
        List<BuildingFootprint> list = new ArrayList<>();
        int attempts = 0;
        while (list.size() < 7 && attempts++ < 40) {
            float ang = rng.nextFloat() * (float) (Math.PI * 2);
            float dist = 30 + rng.nextFloat() * 54;
            float dx = (float) Math.cos(ang) * dist;
            float dy = (float) Math.sin(ang) * dist;
            if (tooCloseToRoad(loc, dx, dy, roads)) continue;   // keep plots off the roadways
            float w = 11 + rng.nextFloat() * 16;
            float h = 9 + rng.nextFloat() * 13;
            float rot = (rng.nextFloat() - 0.5f) * 26f;
            list.add(new BuildingFootprint(dx, dy, w, h, rot, 0.75f + rng.nextFloat() * 0.55f));
        }
        BuildingFootprint[] arr = list.toArray(new BuildingFootprint[0]);
        sceneryCache.put(loc, arr);
        return arr;
    }

    /** True if world point (dx,dy relative to loc) sits within 16u of a road. */
    private boolean tooCloseToRoad(Location loc, float dx, float dy, List<Road> roads) {
        float px = loc.getX() + dx, py = loc.getY() + dy;
        for (Road r : roads) {
            Location o = r.getSource().equals(loc) ? r.getDestination() : r.getSource();
            float x1 = loc.getX(), y1 = loc.getY(), x2 = o.getX(), y2 = o.getY();
            double segLen2 = (double) (x2 - x1) * (x2 - x1) + (double) (y2 - y1) * (y2 - y1);
            double t = segLen2 == 0 ? 0 : ((px - x1) * (x2 - x1) + (py - y1) * (y2 - y1)) / segLen2;
            t = Math.max(0, Math.min(1, t));
            double cx = x1 + t * (x2 - x1), cy = y1 + t * (y2 - y1);
            if (Math.hypot(px - cx, py - cy) < 16) return true;
        }
        return false;
    }

    /** One ground patch per location, typed by its real terrain. */
    private DistrictPatch districtFor(Location loc) {
        DistrictPatch p = districtCache.get(loc);
        if (p != null) return p;
        Random rng = new Random(0xC0FFEEL ^ (long) loc.getId().hashCode() * 2749);
        float ang = rng.nextFloat() * (float) (Math.PI * 2);
        float dist = 55 + rng.nextFloat() * 40;
        float w, h;
        switch (loc.getTerrainType()) {
            case FORESTED: case OPEN: w = 95 + rng.nextFloat() * 45; h = 65 + rng.nextFloat() * 35; break;
            case COASTAL:  w = 120 + rng.nextFloat() * 50; h = 55 + rng.nextFloat() * 30; break;
            default:       w = 80 + rng.nextFloat() * 40; h = 60 + rng.nextFloat() * 30;
        }
        float rot = (rng.nextFloat() - 0.5f) * 30f;
        p = new DistrictPatch(loc.getX() + (float) Math.cos(ang) * dist,
                loc.getY() + (float) Math.sin(ang) * dist, w, h, rot, loc.getTerrainType());
        districtCache.put(loc, p);
        return p;
    }

    /** District patches + building footprints — painted beneath roads/nodes. */
    private void paintDistricts(Graphics2D g2d) {
        if (zoom < 0.45) return;   // scenery reveals itself as you zoom in

        // 1) Ground patches: parks, water, plazas (barely lighter than the base)
        for (Location loc : graph.getAllLocations()) {
            DistrictPatch p = districtFor(loc);
            double sx = p.x * zoom + offsetX, sy = p.y * zoom + offsetY;
            double sw = p.w * zoom, sh = p.h * zoom;
            if (sx + sw < -80 || sy + sh < -80 || sx - sw > getWidth() + 80 || sy - sh > getHeight() + 80) continue;
            Graphics2D g = (Graphics2D) g2d.create();
            g.translate(sx, sy);
            g.rotate(Math.toRadians(p.rotDeg));
            g.setColor(patchColor(p.terrain));
            g.fillRoundRect((int) (-sw / 2), (int) (-sh / 2), (int) sw, (int) sh, (int) (sw * 0.22), (int) (sh * 0.22));
            g.dispose();
        }

        // 2) Building footprints with contact shadows and a lit roof edge
        for (Location loc : graph.getAllLocations()) {
            double bx = loc.getX() * zoom + offsetX, by = loc.getY() * zoom + offsetY;
            if (bx < -150 || by < -150 || bx > getWidth() + 150 || by > getHeight() + 150) continue;
            for (BuildingFootprint b : buildingsFor(loc)) {
                double cx = bx + b.dx * zoom, cy = by + b.dy * zoom;
                double bw = b.w * zoom, bh = b.h * zoom;
                Graphics2D g = (Graphics2D) g2d.create();
                g.translate(cx, cy);
                g.rotate(Math.toRadians(b.rotDeg));
                g.setColor(new Color(0, 0, 0, 100));
                g.fillRoundRect((int) (-bw / 2) + 2, (int) (-bh / 2) + 3, (int) bw, (int) bh, 4, 4);
                g.setColor(buildingColor(loc.getTerrainType(), b.shade));
                g.fillRoundRect((int) (-bw / 2), (int) (-bh / 2), (int) bw, (int) bh, 4, 4);
                g.setColor(buildingColor(loc.getTerrainType(), b.shade + 0.45f));
                g.drawLine((int) (-bw / 2) + 2, (int) (-bh / 2) + 1, (int) (bw / 2) - 2, (int) (-bh / 2) + 1);
                g.dispose();
            }
        }
    }

    private Color patchColor(Location.TerrainType t) {
        switch (t) {
            case FORESTED:
            case OPEN:      return new Color(0x10, 0x18, 0x13);   // desaturated park
            case COASTAL:   return new Color(0x0D, 0x14, 0x1B);   // dark water
            case INDUSTRIAL: return new Color(0x1A, 0x17, 0x12);  // warm gray
            case TRANSPORT: return new Color(0x15, 0x15, 0x1A);   // platform
            default:        return new Color(0x14, 0x14, 0x16);   // plaza charcoal
        }
    }

    private Color buildingColor(Location.TerrainType t, float shade) {
        int r, g, b;
        switch (t) {
            case URBAN: case COMMERCIAL: r = 0x22; g = 0x22; b = 0x26; break; // zinc
            case RESIDENTIAL:            r = 0x21; g = 0x20; b = 0x22; break; // warm zinc
            case INDUSTRIAL:             r = 0x22; g = 0x1F; b = 0x1B; break; // bronze-gray
            case TRANSPORT:              r = 0x24; g = 0x24; b = 0x26; break;
            default:                     r = 0x20; g = 0x20; b = 0x23;
        }
        return new Color(clamp255(r * shade), clamp255(g * shade), clamp255(b * shade));
    }

    private static int clamp255(double v) { return (int) Math.max(0, Math.min(255, v)); }

    /** Legend, zoom badge, and marker detail card — painted, never overlay components. */
    private void paintOnCanvasChrome(Graphics2D g2d) {
        // ---- Zoom badge (top-right) ----
        String zoomText = String.format("ZOOM %d%%", Math.round(zoom * 100));
        g2d.setFont(Theme.monoFont(11f));
        int zw = g2d.getFontMetrics().stringWidth(zoomText) + 20;
        glassRect(g2d, getWidth() - zw - 12, 12, zw, 24, 8);
        g2d.setColor(Theme.TEXT_SECONDARY);
        g2d.drawString(zoomText, getWidth() - zw - 2, 28);

        // ---- Legend (bottom-left) ----
        String[][] items = {
                {"Location"}, {"Safe Zone"}, {"Evacuation Route"},
                {"Open Road"}, {"Blocked Road"}, {"High-Risk Road"}, {"Damaged Road"}
        };
        Color[] colors = { Theme.NODE_DEFAULT, Theme.NODE_SAFE_ZONE, Theme.NODE_ROUTE,
                Theme.ROAD_OPEN, Theme.ROAD_BLOCKED, Theme.ROAD_HIGH_RISK, Theme.ROAD_DAMAGED };
        int lx = 14, ly = getHeight() - items.length * 18 - 16;
        glassRect(g2d, lx - 8, ly - 16, 150, items.length * 18 + 10, 10);
        g2d.setFont(Theme.interFont(Font.PLAIN, 11f));
        for (int i = 0; i < items.length; i++) {
            g2d.setColor(colors[i]);
            g2d.fillRoundRect(lx, ly + i * 18, 12, 8, 3, 3);
            g2d.setColor(Theme.TEXT_SECONDARY);
            g2d.drawString(items[i][0], lx + 18, ly + i * 18 + 8);
        }

        // ---- Marker detail card (top-left, replaces modal popups) ----
        if (selectedLocation != null) paintDetailCard(g2d, selectedLocation);
    }

    private void paintDetailCard(Graphics2D g2d, Location loc) {
        int w = 240, x = 14, y = 14;
        Font labelFont = Theme.interFont(Font.BOLD, 10f);
        Font bodyFont = Theme.interFont(Font.PLAIN, 12f);
        Font monoFont = Theme.monoFont(11f);

        boolean isSafe = safeZoneLocations.contains(loc);
        List<Road> roads = graph.getRoads(loc);
        int open = 0, blocked = 0, risky = 0;
        for (Road r : roads) {
            if (r.isBlocked()) blocked++;
            else if (r.getStatus() == Road.Status.HIGH_RISK || r.getStatus() == Road.Status.DAMAGED) risky++;
            else open++;
        }
        int h = 96 + (isSafe ? 16 : 0) + (blocked + risky > 0 ? 16 : 0);
        glassRect(g2d, x, y, w, h, 12);

        int ty = y + 20;
        g2d.setFont(labelFont);
        g2d.setColor(isSafe ? Theme.NODE_SAFE_ZONE : Theme.TEXT_PRIMARY);
        g2d.drawString(isSafe ? "SAFE ZONE" : "LOCATION", x + 14, ty);
        ty += 20;

        g2d.setFont(Theme.interFont(Font.BOLD, 14f));
        g2d.setColor(Theme.TEXT_PRIMARY);
        g2d.drawString(loc.getName(), x + 14, ty);
        ty += 18;

        g2d.setFont(monoFont);
        g2d.setColor(Theme.TEXT_SECONDARY);
        g2d.drawString("ID " + loc.getId() + "   (" + loc.getX() + ", " + loc.getY() + ")", x + 14, ty);
        ty += 16;
        g2d.drawString("TERRAIN " + loc.getTerrainType(), x + 14, ty);
        ty += 16;
        g2d.drawString("ROADS " + roads.size() + "  \u00B7  OPEN " + open, x + 14, ty);

        if (blocked + risky > 0) {
            ty += 16;
            g2d.setColor(blocked > 0 ? Theme.ROAD_BLOCKED : Theme.ROAD_HIGH_RISK);
            g2d.drawString("AFFECTED \u00B7 BLOCKED " + blocked + " \u00B7 RISKY " + risky, x + 14, ty);
        }
        if (isSafe) {
            ty += 16;
            g2d.setColor(Theme.NODE_SAFE_ZONE);
            g2d.drawString("AVAILABLE FOR EVACUATION", x + 14, ty);
        }

        // hint
        g2d.setFont(Theme.interFont(Font.PLAIN, 10f));
        g2d.setColor(Theme.TEXT_MUTED);
        g2d.drawString("Click map to close", x + 14, y + h - 10);
    }

    /** Semi-transparent dark rounded rect used for on-canvas chrome. */
    private void glassRect(Graphics2D g2d, int x, int y, int w, int h, int arc) {
        g2d.setColor(new Color(0x0E, 0x0E, 0x10, 240));
        g2d.fillRoundRect(x, y, w, h, arc, arc);
        g2d.setColor(new Color(0x3F, 0x3F, 0x46, 130));
        g2d.setStroke(new BasicStroke(1f));
        g2d.drawRoundRect(x, y, w - 1, h - 1, arc, arc);
    }
}
