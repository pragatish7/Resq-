package model;

/**
 * Represents a safe zone where people can evacuate to during disasters.
 */
public class SafeZone {
    public enum SafetyLevel { HIGH, MEDIUM, LOW }

    private String name;
    private Location location;
    private int capacity;
    private int currentOccupants;
    private SafetyLevel safetyLevel;
    private double estimatedDistance; // From origin (set dynamically)

    public SafeZone(String name, Location location, int capacity,
                    int currentOccupants, SafetyLevel safetyLevel) {
        this.name = name;
        this.location = location;
        this.capacity = capacity;
        this.currentOccupants = currentOccupants;
        this.safetyLevel = safetyLevel;
        this.estimatedDistance = 0;
    }

    public boolean isAvailable() { return currentOccupants < capacity; }
    public int getAvailableCapacity() { return capacity - currentOccupants; }
    public double getOccupancyPercent() { return capacity > 0 ? (currentOccupants * 100.0 / capacity) : 100; }

    public void addOccupants(int count) {
        this.currentOccupants = Math.min(currentOccupants + count, capacity);
    }

    // Getters and Setters
    public String getName() { return name; }
    public Location getLocation() { return location; }
    public int getCapacity() { return capacity; }
    public int getCurrentOccupants() { return currentOccupants; }
    public void setCurrentOccupants(int currentOccupants) { this.currentOccupants = currentOccupants; }
    public SafetyLevel getSafetyLevel() { return safetyLevel; }
    public double getEstimatedDistance() { return estimatedDistance; }
    public void setEstimatedDistance(double d) { this.estimatedDistance = d; }

    @Override
    public String toString() {
        return name + " (Cap:" + capacity + ", Occ:" + currentOccupants + ", Safety:" + safetyLevel + ")";
    }
}
