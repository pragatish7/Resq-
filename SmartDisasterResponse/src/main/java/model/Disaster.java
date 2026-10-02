package model;

import java.util.List;
import java.util.ArrayList;

/**
 * Represents a disaster event with type, severity, and affected areas.
 */
public class Disaster {
    public enum Type {
        // Natural Disasters
        FLOOD, EARTHQUAKE, CYCLONE, TSUNAMI, LANDSLIDE, AVALANCHE, DROUGHT, THUNDERSTORM, TORNADO,
        // Human-caused / Technological
        INDUSTRIAL_FIRE, CHEMICAL_LEAK, GAS_LEAK, NUCLEAR_EMERGENCY, BUILDING_COLLAPSE, DAM_FAILURE;

        public String getDisplayName() {
            return name().replace('_', ' ');
        }
    }

    public enum Severity {
        LOW(1.0, 0.3),       // Small risk increase
        MEDIUM(2.0, 0.6),    // Moderate risk
        HIGH(3.0, 0.85),     // Major risk
        CRITICAL(4.0, 1.0);  // Maximum — many roads blocked

        private final double multiplier;
        private final double blockChance;
        Severity(double multiplier, double blockChance) {
            this.multiplier = multiplier;
            this.blockChance = blockChance;
        }
        public double getMultiplier() { return multiplier; }
        public double getBlockChance() { return blockChance; }
    }

    private Type type;
    private Severity severity;
    private String description;
    private String recommendedAction;
    private List<Location> affectedLocations;
    private List<Road> affectedRoads;

    public Disaster(Type type, Severity severity, String description, String recommendedAction) {
        this.type = type;
        this.severity = severity;
        this.description = description;
        this.recommendedAction = recommendedAction;
        this.affectedLocations = new ArrayList<>();
        this.affectedRoads = new ArrayList<>();
    }

    public Disaster(Type type, String description) {
        this(type, Severity.MEDIUM, description, "Move to safety and avoid affected roads.");
    }

    public Type getType() { return type; }
    public Severity getSeverity() { return severity; }
    public String getDescription() { return description; }
    public String getRecommendedAction() { return recommendedAction; }
    public void setDescription(String description) { this.description = description; }
    public void setRecommendedAction(String action) { this.recommendedAction = action; }
    public List<Location> getAffectedLocations() { return affectedLocations; }
    public List<Road> getAffectedRoads() { return affectedRoads; }

    public void addAffectedLocation(Location loc) { affectedLocations.add(loc); }
    public void addAffectedRoad(Road road) { affectedRoads.add(road); }

    public int getBlockedRoadCount() {
        int count = 0;
        for (Road r : affectedRoads) { if (r.isBlocked()) count++; }
        return count;
    }

    public int getHighRiskRoadCount() {
        int count = 0;
        for (Road r : affectedRoads) {
            if (r.getStatus() == Road.Status.HIGH_RISK) count++;
        }
        return count;
    }

    public int getDamagedRoadCount() {
        int count = 0;
        for (Road r : affectedRoads) {
            if (r.getStatus() == Road.Status.DAMAGED) count++;
        }
        return count;
    }

    @Override
    public String toString() {
        return type.getDisplayName() + " (" + severity + "): " + description;
    }
}
