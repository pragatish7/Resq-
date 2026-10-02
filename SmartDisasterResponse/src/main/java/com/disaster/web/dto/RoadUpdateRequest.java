package com.disaster.web.dto;

/** Request body for setting one directed road's status. */
public record RoadUpdateRequest(String from, String to, String status) {
}
