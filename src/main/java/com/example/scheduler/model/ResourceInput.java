package com.example.scheduler.model;

/**
 * ResourceInput describes a schedulable resource and its per-slot capacity.
 * For class scheduling, this can represent a room with seat capacity.
 */
public class ResourceInput {
    /** Unique identifier of the resource (string). */
    private String resourceId;
    /** Maximum number of tasks that can run concurrently on this resource per time slot. */
    private int capacityPerSlot;
    /** Seat capacity of the room (number of students it can hold). Optional; <=0 means unknown). */
    private int seatCapacity;

    public ResourceInput() {}

    public ResourceInput(String resourceId, int capacityPerSlot) {
        this.resourceId = resourceId;
        this.capacityPerSlot = capacityPerSlot;
    }

    public ResourceInput(String resourceId, int capacityPerSlot, int seatCapacity) {
        this.resourceId = resourceId;
        this.capacityPerSlot = capacityPerSlot;
        this.seatCapacity = seatCapacity;
    }

    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }

    public int getCapacityPerSlot() { return capacityPerSlot; }
    public void setCapacityPerSlot(int capacityPerSlot) { this.capacityPerSlot = capacityPerSlot; }

    public int getSeatCapacity() { return seatCapacity; }
    public void setSeatCapacity(int seatCapacity) { this.seatCapacity = seatCapacity; }
}
