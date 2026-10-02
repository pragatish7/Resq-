package com.disaster.web.dto;

/**
 * Request body for route/connectivity lookups between two locations.
 * {@code watch} (optional, default true) says whether this lookup should become
 * the watched route that rerouting status is reported against — background
 * queries (e.g. the evacuation-cost curve) pass false so they never hijack the
 * route the user actually planned.
 */
public record RouteRequest(String from, String to, Boolean watch) {
}
