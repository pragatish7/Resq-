package com.disaster.web.dto;

import model.Road;

/** Serializable view of a road for the /api/roads map feed. */
public record RoadDto(String from, String fromId, String to, String toId,
                      double distanceKm, String status, String terrain) {

    public static RoadDto from(Road road) {
        return new RoadDto(
                road.getSource().getName(), road.getSource().getId(),
                road.getDestination().getName(), road.getDestination().getId(),
                road.getDistance(),
                road.getStatus().name(),
                road.getRoadTerrain().name());
    }
}
