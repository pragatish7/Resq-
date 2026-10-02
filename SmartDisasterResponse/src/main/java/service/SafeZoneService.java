package service;

import model.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing safe zones.
 * Handles safe zone queries, availability checks, and occupancy management.
 */
public class SafeZoneService {

    private List<SafeZone> safeZones;

    public SafeZoneService() {
        this.safeZones = new ArrayList<>();
    }

    /**
     * Add a safe zone to the system.
     */
    public void addSafeZone(SafeZone safeZone) {
        safeZones.add(safeZone);
    }

    /**
     * Get all safe zones.
     */
    public List<SafeZone> getAllSafeZones() {
        return new ArrayList<>(safeZones);
    }

    /**
     * Get only available (not full) safe zones.
     */
    public List<SafeZone> getAvailableSafeZones() {
        return safeZones.stream()
                .filter(SafeZone::isAvailable)
                .collect(Collectors.toList());
    }

    /**
     * Find a safe zone by name.
     */
    public SafeZone findByName(String name) {
        for (SafeZone zone : safeZones) {
            if (zone.getName().equalsIgnoreCase(name)) {
                return zone;
            }
        }
        return null;
    }

    /**
     * Find a safe zone by its location.
     */
    public SafeZone findByLocation(Location location) {
        for (SafeZone zone : safeZones) {
            if (zone.getLocation().equals(location)) {
                return zone;
            }
        }
        return null;
    }

    /**
     * Add occupants to a safe zone.
     */
    public boolean addOccupants(String safeZoneName, int count) {
        SafeZone zone = findByName(safeZoneName);
        if (zone != null && zone.isAvailable()) {
            zone.addOccupants(count);
            return true;
        }
        return false;
    }

    /**
     * Get safe zones sorted by available capacity (descending).
     */
    public List<SafeZone> getSafeZonesByCapacity() {
        return safeZones.stream()
                .filter(SafeZone::isAvailable)
                .sorted((a, b) -> b.getAvailableCapacity() - a.getAvailableCapacity())
                .collect(Collectors.toList());
    }

    /**
     * Get a summary of all safe zones.
     */
    public String getSafeZonesSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Safe Zones Summary ===\n");
        for (SafeZone zone : safeZones) {
            sb.append(String.format("%-20s | Capacity: %4d | Occupied: %4d | Available: %4d | Safety: %s | %s\n",
                    zone.getName(), zone.getCapacity(), zone.getCurrentOccupants(),
                    zone.getAvailableCapacity(), zone.getSafetyLevel(),
                    zone.isAvailable() ? "AVAILABLE" : "FULL"));
        }
        return sb.toString();
    }
}
