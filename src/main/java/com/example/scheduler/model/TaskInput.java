package com.example.scheduler.model;

import java.util.List;

/**
 * TaskInput represents a single unit of work to be scheduled.
 * Fields mirror the JSON contract shared with the Haskell component.
 */
public class TaskInput {
    /** Unique identifier for the task. */
    private int taskId;
    /** Duration in discrete time units; must be > 0. */
    private int duration;
    /** Priority (e.g., 1–10), higher means more important. */
    private int priority;
    /** Resource ID required to execute this task. */
    private String requiredResource;
    /** List of task IDs that must complete before this task can start. */
    private List<Integer> dependsOn;

    public TaskInput() {}

    public TaskInput(int taskId, int duration, int priority, String requiredResource, List<Integer> dependsOn) {
        this.taskId = taskId;
        this.duration = duration;
        this.priority = priority;
        this.requiredResource = requiredResource;
        this.dependsOn = dependsOn;
    }

    public int getTaskId() { return taskId; }
    public void setTaskId(int taskId) { this.taskId = taskId; }

    public int getDuration() { return duration; }
    public void setDuration(int duration) { this.duration = duration; }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }

    public String getRequiredResource() { return requiredResource; }
    public void setRequiredResource(String requiredResource) { this.requiredResource = requiredResource; }

    public List<Integer> getDependsOn() { return dependsOn; }
    public void setDependsOn(List<Integer> dependsOn) { this.dependsOn = dependsOn; }
}
