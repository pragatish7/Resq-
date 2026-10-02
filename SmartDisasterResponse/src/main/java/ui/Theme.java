package ui;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;

/**
 * Centralized theme for the disaster command-center UI.
 * shadcn/ui-inspired "zinc dark" design system:
 *   Background #09090B / Card #0E0E10 / Border #27272A / Foreground #FAFAFA
 *   Muted #A1A1AA / Primary white buttons / Destructive red #EF4444
 * Flat surfaces, hairline borders, no gradients; status colors kept semantic.
 * Factory API unchanged.
 */
public final class Theme {
    private Theme() {}

    // ==== Core surfaces (shadcn zinc dark) ====
    public static final Color BG_DARK         = new Color(0x09, 0x09, 0x0B);   // zinc-950 backdrop
    public static final Color BG_CARD         = new Color(0x0E, 0x0E, 0x10);   // card surface
    public static final Color BG_CARD_HOVER   = new Color(0x18, 0x18, 0x1B);   // zinc-900 hover
    public static final Color BG_INPUT        = new Color(0x0C, 0x0C, 0x0E);   // input wells
    public static final Color BG_SIDEBAR      = new Color(0x0C, 0x0C, 0x0E);   // sidebar
    public static final Color BORDER          = new Color(0x27, 0x27, 0x2A);   // zinc-800 hairline
    public static final Color BORDER_LIGHT    = new Color(0x3F, 0x3F, 0x46);   // zinc-700

    // ==== Text ====
    public static final Color TEXT_PRIMARY   = new Color(0xFA, 0xFA, 0xFA);   // #FAFAFA foreground
    public static final Color TEXT_SECONDARY = new Color(0xA1, 0xA1, 0xAA);   // zinc-400 muted
    public static final Color TEXT_MUTED     = new Color(0x71, 0x71, 0x7A);   // zinc-500 muted

    // ==== Accent (shadcn semantic system) ====
    public static final Color ACCENT         = new Color(0xFA, 0xFA, 0xFA);   // primary (white CTA)
    public static final Color ACCENT_DEEP    = new Color(0x18, 0x18, 0x1B);   // elevated surface
    public static final Color ACCENT_BRIGHT  = new Color(0xEF, 0x44, 0x44);   // destructive red
    public static final Color ACCENT_BLUE    = new Color(0xA1, 0xA1, 0xAA);   // neutral (secondary)
    public static final Color ACCENT_GREEN   = new Color(0x10, 0xB9, 0x81);   // emerald-500 SAFE
    public static final Color ACCENT_ORANGE  = new Color(0xF5, 0x9E, 0x0B);   // amber-500 warnings
    public static final Color ACCENT_RED     = new Color(0xEF, 0x44, 0x44);   // red-500 CRITICAL
    public static final Color ACCENT_YELLOW  = new Color(0xEA, 0xB3, 0x08);   // yellow-500
    public static final Color ACCENT_PURPLE  = new Color(0x8B, 0x5C, 0xF6);   // violet-500 (DFS)
    public static final Color ACCENT_CYAN    = new Color(0x06, 0xB6, 0xD4);   // cyan-500 (BFS)

    // ==== Status colors ====
    public static final Color STATUS_SAFE      = ACCENT_GREEN;
    public static final Color STATUS_CAUTION   = ACCENT_YELLOW;
    public static final Color STATUS_HIGH_RISK = ACCENT_ORANGE;
    public static final Color STATUS_CRITICAL  = ACCENT_RED;

    // ==== Road / map colors (visible on wine-black backdrop) ====
    public static final Color ROAD_OPEN      = new Color(0x52, 0x52, 0x5B);   // zinc-600
    public static final Color ROAD_BLOCKED   = ACCENT_RED;
    public static final Color ROAD_HIGH_RISK = ACCENT_ORANGE;
    public static final Color ROAD_DAMAGED   = new Color(0xEA, 0x58, 0x0C);   // orange-600
    public static final Color ROAD_HIGHLIGHT = new Color(0xFA, 0xFA, 0xFA);   // white = evacuation route
    public static final Color MAP_GRID       = new Color(0x1A, 0x1A, 0x1E);   // faint zinc grid
    public static final Color MAP_GLOW       = new Color(0x18, 0x18, 0x1B);   // soft neutral glow

    // ==== Node colors ====
    public static final Color NODE_DEFAULT   = new Color(0xE4, 0xE4, 0xE7);   // zinc-200 location
    public static final Color NODE_SAFE_ZONE = ACCENT_GREEN;
    public static final Color NODE_ROUTE     = new Color(0xFA, 0xFA, 0xFA);   // white route nodes
    public static final Color NODE_BFS       = ACCENT_CYAN;
    public static final Color NODE_DFS       = ACCENT_PURPLE;
    public static final Color NODE_CURRENT   = ACCENT_YELLOW;

    // ==== Fonts ====
    public static final Font FONT_DISPLAY = new Font("Inter", Font.BOLD, 30);      // sans display
    public static final Font FONT_HEADER  = new Font("Inter", Font.BOLD, 26);
    public static final Font FONT_SUBHEADER = new Font("Inter", Font.PLAIN, 18);
    public static final Font FONT_TITLE   = new Font("Inter", Font.BOLD, 14);
    public static final Font FONT_BODY    = new Font("Inter", Font.PLAIN, 13);
    public static final Font FONT_SMALL   = new Font("Inter", Font.PLAIN, 12);
    public static final Font FONT_BOLD    = new Font("Inter", Font.BOLD, 13);
    public static final Font FONT_LABEL   = new Font("Inter", Font.BOLD, 10);        // small uppercase labels
    public static final Font FONT_MONO    = new Font("Consolas", Font.PLAIN, 12);
    public static final Font FONT_MONO_SMALL = new Font("Consolas", Font.PLAIN, 11);
    public static final Font FONT_STAT    = new Font("Inter", Font.BOLD, 30);     // large stat values
    public static final Font FONT_HERO    = new Font("Inter", Font.BOLD, 40);     // hero banner

    public static Font displayFont(float size) { return font("Inter", Font.BOLD, size); }
    public static Font monoFont(float size)    { return font("Consolas", Font.PLAIN, size); }
    public static Font interFont(int style, float size) { return font("Inter", style, size); }

    private static Font font(String family, int style, float size) {
        Font base = new Font(family, style, (int) size);
        if (!base.getFamily().equalsIgnoreCase(family) && !base.getFontName().toLowerCase().contains(family.toLowerCase())) {
            String logical = family.equals("Consolas") ? Font.MONOSPACED : Font.SANS_SERIF;
            base = new Font(logical, style, (int) size);
        }
        return base;
    }

    // ==== Component factories ====

    public static void stylePanel(JPanel panel) {
        panel.setBackground(BG_CARD);
        panel.setForeground(TEXT_PRIMARY);
    }

    public static void styleDarkPanel(JPanel panel) {
        panel.setBackground(BG_DARK);
        panel.setForeground(TEXT_PRIMARY);
    }

    /** Uppercase micro-label used above values and sections. */
    public static JLabel sectionLabel(String text, Color color) {
        JLabel l = new JLabel(text.toUpperCase());
        l.setFont(FONT_LABEL);
        l.setForeground(color != null ? color : TEXT_MUTED);
        return l;
    }

    /** Serif display heading (cream with trailing accent dot available via heroTitle). */
    public static JLabel displayLabel(String text, int size) {
        JLabel l = new JLabel(text);
        l.setFont(displayFont(size));
        l.setForeground(TEXT_PRIMARY);
        return l;
    }

    /** Hero heading with a crimson trailing period, like the reference. */
    public static JPanel heroTitle(String text, int size) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        p.setOpaque(false);
        JLabel main = new JLabel(text);
        main.setFont(displayFont(size));
        main.setForeground(TEXT_PRIMARY);
        JLabel dot = new JLabel(".");
        dot.setFont(displayFont(size));
        dot.setForeground(ACCENT_BRIGHT);
        p.add(main);
        p.add(dot);
        return p;
    }

    /** Rounded square icon tile (crimson wash, used next to headings). */
    public static JPanel iconTile(String symbol, int size) {
        JPanel tile = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0x27, 0x27, 0x2A, 150));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(new Color(0x3F, 0x3F, 0x46));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        tile.setOpaque(false);
        tile.setPreferredSize(new Dimension(size, size));
        JLabel icon = new JLabel(symbol, SwingConstants.CENTER);
        icon.setFont(new Font("SansSerif", Font.PLAIN, size * 2 / 3));
        icon.setForeground(TEXT_PRIMARY);
        tile.setLayout(new BorderLayout());
        tile.add(icon, BorderLayout.CENTER);
        return tile;
    }

    /** Mono metadata label (coordinates, timestamps, IDs). */
    public static JLabel monoLabel(String text, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(FONT_MONO_SMALL);
        l.setForeground(color != null ? color : TEXT_SECONDARY);
        return l;
    }

    public static JLabel styledLabel(String text, Font font, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        label.setForeground(color);
        return label;
    }

    /** Primary button: crimson gradient fill, cream text; light fill variant handled. */
    public static JButton styledButton(String text, Color bgColor) {
        return styledButton(text, bgColor, 0);
    }

    public static JButton styledButton(String text, Color bgColor, int minWidth) {
        final Color base = bgColor;
        final boolean primary = base.equals(ACCENT);            // white primary CTA
        final boolean destructive = base.equals(ACCENT_BRIGHT); // red destructive
        final Color fill      = primary ? new Color(0xFA, 0xFA, 0xFA)
                : destructive ? new Color(0xEF, 0x44, 0x44) : new Color(0x27, 0x27, 0x2A);
        final Color fillHover = primary ? new Color(0xE4, 0xE4, 0xE7)
                : destructive ? new Color(0xDC, 0x26, 0x26) : new Color(0x33, 0x33, 0x38);
        final Color fillPress = primary ? new Color(0xD4, 0xD4, 0xD8)
                : destructive ? new Color(0xB9, 0x1C, 0x1C) : new Color(0x3B, 0x3B, 0x41);
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color c = getModel().isPressed() ? fillPress
                        : getModel().isRollover() ? fillHover : fill;
                g2.setColor(c);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                if (!primary) { // hairline ring on secondary/destructive
                    g2.setColor(getModel().isRollover() ? BORDER_LIGHT : BORDER);
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                } else { // hairline top highlight
                    g2.setColor(new Color(255, 255, 255, 70));
                    g2.drawLine(8, 1, getWidth() - 8, 1);
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setForeground(primary ? new Color(0x18, 0x18, 0x1B) : TEXT_PRIMARY);
        btn.setFont(FONT_BOLD);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setRolloverEnabled(true);
        btn.setBorder(BorderFactory.createEmptyBorder(9, 18, 9, 18));
        if (minWidth > 0) btn.setPreferredSize(new Dimension(minWidth, 38));
        return btn;
    }

    /** Ghost/outline button: transparent surface, burgundy border, cream text. */
    public static JButton ghostButton(String text) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? new Color(0x27, 0x27, 0x2A, 120) : new Color(0x27, 0x27, 0x2A, 40));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(getModel().isRollover() ? BORDER_LIGHT : BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setForeground(TEXT_PRIMARY);
        btn.setFont(FONT_BOLD);
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setRolloverEnabled(true);
        btn.setBorder(BorderFactory.createEmptyBorder(9, 16, 9, 16));
        return btn;
    }

    /** Small square map control button: maroon glass with cream glyph. */
    public static JButton mapControlButton(String text) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0x10, 0x10, 0x12, 244));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(getModel().isRollover() ? BORDER_LIGHT : BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setForeground(TEXT_PRIMARY);
        btn.setFont(FONT_BOLD);
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setRolloverEnabled(true);
        btn.setPreferredSize(new Dimension(34, 34));
        return btn;
    }

    /** Rounded pill badge (status indicator) with translucent wash. */
    public static JPanel pillBadge(String text, Color color) {
        JPanel pill = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 46));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 140));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
                g2.dispose();
            }
        };
        pill.setOpaque(false);
        JLabel l = new JLabel(text.toUpperCase());
        l.setFont(FONT_LABEL);
        l.setForeground(color);
        l.setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 10));
        pill.add(l, BorderLayout.CENTER);
        return pill;
    }

    /** Small colored status dot. */
    public static JLabel statusDot(Color color, int size) {
        JLabel dot = new JLabel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.fillOval(0, 0, getWidth() - 2, getHeight() - 2);
                g2.dispose();
            }
        };
        dot.setPreferredSize(new Dimension(size + 2, size + 2));
        return dot;
    }

    /**
     * Premium card with layered gradient, thin translucent border, rounded 18px,
     * hover elevation. Variant "accent" adds a crimson radial bloom.
     */
    public static JPanel cardPanel() {
        return cardPanel(BG_CARD);
    }

    public static JPanel cardPanel(Color bg) {
        final boolean light = isLight(bg);
        JPanel panel = new JPanel() {
            private boolean hover;
            {
                MouseAdapter h = new MouseAdapter() {
                    @Override public void mouseEntered(java.awt.event.MouseEvent e) { hover = true; repaint(); }
                    @Override public void mouseExited(java.awt.event.MouseEvent e) { hover = false; repaint(); }
                };
                addMouseListener(h);
            }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                // flat shadcn surface; elevation shift on hover only
                Color fill = hover ? (light ? darken(bg, 0.96f) : BG_CARD_HOVER) : bg;
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, w, h, 12, 12);
                g2.setColor(hover ? BORDER_LIGHT : BORDER);
                g2.drawRoundRect(0, 0, w - 1, h - 1, 12, 12);
                g2.dispose();
            }
        };
        panel.setOpaque(false);
        panel.setBackground(bg);
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        return panel;
    }

    /** Light inverse card for editorial contrast. */
    public static JPanel creamCard() {
        JPanel card = cardPanel(new Color(0xFA, 0xFA, 0xFA));
        return card;
    }

    /** Card with a small uppercase heading and optional accent dot. */
    public static JPanel titledCard(String title, Color accent) {
        JPanel card = cardPanel();
        card.setLayout(new BorderLayout(0, 10));
        JPanel head = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        head.setOpaque(false);
        if (accent != null) {
            JLabel dot = new JLabel("\u25CF");
            dot.setFont(FONT_SMALL);
            dot.setForeground(accent);
            head.add(dot);
            head.add(Box.createHorizontalStrut(8));
        }
        head.add(sectionLabel(title, TEXT_SECONDARY));
        card.add(head, BorderLayout.NORTH);
        return card;
    }

    /** Stat card: uppercase label, big serif value, supporting note. */
    public static JPanel statCard(String label, String value, Color accentColor, String note) {
        JPanel card = cardPanel();
        card.setLayout(new BorderLayout(0, 6));
        card.setPreferredSize(new Dimension(170, 108));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        top.setOpaque(false);
        top.add(sectionLabel(label, TEXT_MUTED));
        card.add(top, BorderLayout.NORTH);

        JLabel valueLabel = new JLabel(value);
        valueLabel.setFont(FONT_STAT);
        valueLabel.setForeground(accentColor);
        valueLabel.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
        card.add(valueLabel, BorderLayout.CENTER);

        if (note != null && !note.isEmpty()) {
            JLabel noteLabel = monoLabel(note, TEXT_MUTED);
            card.add(noteLabel, BorderLayout.SOUTH);
        }
        return card;
    }

    /** Legacy overload kept for compatibility. */
    public static JPanel statCard(String label, String value, Color accentColor) {
        return statCard(label, value, accentColor, null);
    }

    /** Dark translucent burgundy overlay panel with hairline border. */
    public static JPanel glassPanel() {
        JPanel p = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0x0E, 0x0E, 0x10, 236));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.setColor(BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.dispose();
            }
        };
        p.setOpaque(false);
        return p;
    }

    public static JSeparator separator() {
        JSeparator sep = new JSeparator();
        sep.setForeground(new Color(0x27, 0x27, 0x2A, 130));
        return sep;
    }

    /** Subtle hover border transition helper (used by sidebar items). */
    public static Border softBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1, true),
                BorderFactory.createEmptyBorder(1, 1, 1, 1));
    }

    private static boolean isDark(Color c) {
        return (c.getRed() * 0.299 + c.getGreen() * 0.587 + c.getBlue() * 0.114) > 140;
    }

    static boolean isLight(Color c) {
        return (c.getRed() * 0.299 + c.getGreen() * 0.587 + c.getBlue() * 0.114) >= 140;
    }

    private static Color brighten(Color c, float factor) {
        int r = Math.min(255, (int) (c.getRed() * factor));
        int g = Math.min(255, (int) (c.getGreen() * factor));
        int b = Math.min(255, (int) (c.getBlue() * factor));
        return new Color(r, g, b, c.getAlpha());
    }

    private static Color darken(Color c, float factor) {
        return brighten(c, factor);
    }
}
