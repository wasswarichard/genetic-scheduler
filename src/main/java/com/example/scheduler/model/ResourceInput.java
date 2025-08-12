package com.example.scheduler.model;

/**
 * ResourceInput describes a schedulable resource and its per-slot capacity.
 */
public class ResourceInput {
    /** Unique identifier of the resource (string). */
    private String resourceId;
    /** Maximum number of tasks that can run concurrently on this resource per time slot. */
    private int capacityPerSlot;

    public ResourceInput() {}

    public ResourceInput(String resourceId, int capacityPerSlot) {
        this.resourceId = resourceId;
        this.capacityPerSlot = capacityPerSlot;
    }

    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }

    public int getCapacityPerSlot() { return capacityPerSlot; }
    public void setCapacityPerSlot(int capacityPerSlot) { this.capacityPerSlot = capacityPerSlot; }
}
