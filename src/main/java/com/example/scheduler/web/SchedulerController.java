package com.example.scheduler.web;

import com.example.scheduler.model.ResourceInput;
import com.example.scheduler.model.TaskInput;
import com.example.scheduler.service.SchedulerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller exposing scheduling endpoints.
 * - POST /api/scheduler/generate: Accepts tasks/resources and returns a schedule.
 * - GET  /api/scheduler/health: Basic diagnostics, including presence of external tools.
 */
@RestController
@RequestMapping("/api/scheduler")
public class SchedulerController {

    private final SchedulerService service;

    public SchedulerController(SchedulerService service) { this.service = service; }

    /** Request payload mapped from JSON. */
    public static class SchedulePayload {
        public List<TaskInput> tasks;
        public List<ResourceInput> resources;
    }

    /** Health and environment diagnostics for troubleshooting. */
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        Map<String, Object> res = new HashMap<>();
        res.put("status", "OK");
        res.put("diagnostics", service.diagnostics());
        return ResponseEntity.ok(res);
    }

    /** Generate a schedule using the underlying polyglot pipeline. */
    @PostMapping("/generate")
    public ResponseEntity<?> generate(@RequestBody SchedulePayload payload) {
        try {
            SchedulerService.ScheduleRequest req = new SchedulerService.ScheduleRequest();
            req.tasks = payload.tasks;
            req.resources = payload.resources;
            Map<String, Object> res = service.schedule(req);
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", e.toString());
            return ResponseEntity.status(500).body(err);
        }
    }
}
