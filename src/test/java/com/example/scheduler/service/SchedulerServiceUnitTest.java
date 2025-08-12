package com.example.scheduler.service;

import com.example.scheduler.model.ResourceInput;
import com.example.scheduler.model.TaskInput;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Minimal unit-like test using a main method to avoid adding build tools.
 * Verifies private helper methods of SchedulerService via reflection.
 *
 * How to run (from project root):
 *   javac -cp src/main/java src/test/java/com/example/scheduler/service/SchedulerServiceUnitTest.java
 *   java -cp src/main/java:src/test/java com.example.scheduler.service.SchedulerServiceUnitTest
 */
public class SchedulerServiceUnitTest {

    public static void main(String[] args) throws Exception {
        testExtractAssignments();
        testToJson();
        System.out.println("All Java unit tests passed.");
    }

    private static void testExtractAssignments() throws Exception {
        SchedulerService svc = new SchedulerService();
        String mockJson = "{\"bestSchedule\":[{\"taskId\":1,\"timeSlot\":0,\"resourceId\":\"R1\"}],\"fitness\":5.0}";

        Method m = SchedulerService.class.getDeclaredMethod("extractAssignments", String.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) m.invoke(svc, mockJson);

        assertTrue(list.size() == 1, "Expected 1 assignment");
        Map<String, Object> a = list.get(0);
        assertTrue(Objects.equals(1, ((Number)a.get("taskId")).intValue()), "taskId mismatch");
        assertTrue(Objects.equals(0, ((Number)a.get("timeSlot")).intValue()), "timeSlot mismatch");
        assertTrue(Objects.equals("R1", (String)a.get("resourceId")), "resourceId mismatch");
        System.out.println("[OK] extractAssignments parses one assignment");
    }

    private static void testToJson() throws Exception {
        SchedulerService svc = new SchedulerService();
        SchedulerService.ScheduleRequest req = new SchedulerService.ScheduleRequest();
        TaskInput t1 = new TaskInput(1, 2, 8, "R1", Arrays.asList());
        TaskInput t2 = new TaskInput(2, 1, 5, "R1", Arrays.asList(1));
        ResourceInput r1 = new ResourceInput("R1", 1);
        ResourceInput r2 = new ResourceInput("R2", 1);
        req.tasks = Arrays.asList(t1, t2);
        req.resources = Arrays.asList(r1, r2);

        Method m = SchedulerService.class.getDeclaredMethod("toJson", SchedulerService.ScheduleRequest.class);
        m.setAccessible(true);
        String json = (String) m.invoke(svc, req);

        assertTrue(json.contains("\"tasks\""), "JSON should contain tasks");
        assertTrue(json.contains("\"resources\""), "JSON should contain resources");
        assertTrue(json.contains("\"taskId\":1"), "JSON should contain taskId 1");
        assertTrue(json.contains("\"capacityPerSlot\":1"), "JSON should contain capacity");
        System.out.println("[OK] toJson serializes fields");
    }

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) {
            throw new AssertionError(msg);
        }
    }
}
