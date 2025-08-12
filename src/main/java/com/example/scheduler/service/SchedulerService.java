package com.example.scheduler.service;

import com.example.scheduler.model.ResourceInput;
import com.example.scheduler.model.TaskInput;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * SchedulerService orchestrates the polyglot scheduling pipeline.
 * Responsibilities:
 * - Accept validated task/resource input from the controller layer.
 * - Invoke the Haskell scheduler (via stdin/stdout JSON contract).
 * - Optionally validate the resulting schedule using SWI-Prolog via JPL.
 * - Return a response map suitable for JSON serialization by Spring.
 *
 * This service intentionally avoids extra dependencies (e.g., Jackson) to keep the
 * repository lightweight. For production, consider replacing the homegrown JSON builder
 * with a proper JSON library and adding a build tool (Gradle/Maven).
 */
@Service
public class SchedulerService {

    /**
     * ScheduleRequest is a simple DTO containing tasks and resources.
     * The controller maps the HTTP payload into this type.
     */
    public static class ScheduleRequest {
        public List<TaskInput> tasks;
        public List<ResourceInput> resources;
    }

    /**
     * Generate a schedule by delegating to the Haskell scheduler and then
     * attempting a Prolog validation. If Prolog (JPL) is unavailable, the
     * method degrades gracefully and still returns the Haskell output with
     * diagnostics for transparency.
     */
    public Map<String, Object> schedule(ScheduleRequest req) throws IOException, InterruptedException {
        // Validate input early to fail fast with actionable messages
        validateRequest(req);

        // 1) Call Haskell to get a candidate schedule
        String haskellOutput = callHaskell(req);

        // 2) Optionally validate via Prolog (JPL). Here we attempt and, if not available, return Haskell result as-is
        Map<String, Object> result = new HashMap<>();
        result.put("haskellOutput", jsonParse(haskellOutput));

        boolean valid = false;
        try {
            valid = validateWithProlog(req, haskellOutput);
        } catch (Throwable t) {
            // JPL may not be present in this skeleton environment. We log and continue.
            result.put("prologValidationError", t.toString());
        }
        result.put("valid", valid);
        result.put("diagnostics", diagnostics());
        return result;
    }

    /**
     * Invoke the Haskell scheduler executable/script using a configurable command
     * (defaults to 'runhaskell haskell/GeneticSchedule.hs').
     *
     * The request is serialized as JSON and piped to stdin. A timeout protects against
     * hung processes. Non-zero exit codes are captured and returned as IOException.
     */
    private String callHaskell(ScheduleRequest req) throws IOException, InterruptedException {
        // Serialize request as JSON
        String json = toJson(req);
        // Allow environment overrides for command/script and timeout
        String cmd = Optional.ofNullable(System.getenv("SCHEDULER_HS_CMD")).orElse("runhaskell");
        String script = Optional.ofNullable(System.getenv("SCHEDULER_HS_SCRIPT")).orElse("haskell/GeneticSchedule.hs");
        long timeoutSec = 30L;
        try {
            timeoutSec = Long.parseLong(Optional.ofNullable(System.getenv("SCHEDULER_HS_TIMEOUT_SEC")).orElse("30"));
        } catch (NumberFormatException ignore) { }

        ProcessBuilder pb = new ProcessBuilder(cmd, script);
        pb.directory(new File("."));
        Process proc;
        try {
            proc = pb.start();
        } catch (IOException e) {
            throw new IOException("Failed to start Haskell scheduler. Ensure '" + cmd + "' and script '" + script + "' are available. Original error: " + e, e);
        }
        try (OutputStream os = proc.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
            os.flush();
        }
        boolean finished = proc.waitFor(timeoutSec, TimeUnit.SECONDS);
        if (!finished) {
            proc.destroyForcibly();
            throw new IOException("Haskell scheduler timed out after " + timeoutSec + "s");
        }
        String stdout = readStream(proc.getInputStream());
        String stderr = readStream(proc.getErrorStream());
        int code = proc.exitValue();
        if (code != 0) {
            throw new IOException("Haskell scheduler failed: code=" + code + ", stderr=" + stderr);
        }
        return stdout;
    }

    /**
     * Minimal JSON serialization using StringBuilder to avoid external dependencies.
     * Keep in sync with the Haskell Input model. Replace with Jackson in production.
     */
    private String toJson(ScheduleRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"tasks\":[");
        for (int i = 0; i < req.tasks.size(); i++) {
            TaskInput t = req.tasks.get(i);
            if (i > 0) sb.append(',');
            sb.append('{')
              .append("\"taskId\":").append(t.getTaskId()).append(',')
              .append("\"duration\":").append(t.getDuration()).append(',')
              .append("\"priority\":").append(t.getPriority()).append(',')
              .append("\"requiredResource\":\"").append(escape(t.getRequiredResource())).append("\",")
              .append("\"dependsOn\":[");
            List<Integer> deps = t.getDependsOn();
            for (int j = 0; j < (deps == null ? 0 : deps.size()); j++) {
                if (j > 0) sb.append(',');
                sb.append(deps.get(j));
            }
            sb.append(']');
            sb.append('}');
        }
        sb.append(']');
        sb.append(',');
        sb.append("\"resources\":[");
        for (int i = 0; i < req.resources.size(); i++) {
            ResourceInput r = req.resources.get(i);
            if (i > 0) sb.append(',');
            sb.append('{')
              .append("\"resourceId\":\"").append(escape(r.getResourceId())).append("\",")
              .append("\"capacityPerSlot\":").append(r.getCapacityPerSlot())
              .append('}');
        }
        sb.append(']');
        sb.append('}');
        return sb.toString();
    }

    /** Escape JSON string content (very small subset). */
    private String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** Read full content from InputStream as UTF-8 string. */
    private String readStream(InputStream is) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    /** Placeholder for JSON parsing; currently returns the raw JSON string. */
    private Object jsonParse(String json) { // very naive passthrough; a real project would use Jackson
        return json;
    }

    /**
     * Basic request validation to fail fast. Ensures non-null collections, positive durations,
     * and valid resource IDs and capacities.
     */
    private void validateRequest(ScheduleRequest req) {
        if (req == null) throw new IllegalArgumentException("Request cannot be null");
        if (req.tasks == null) req.tasks = Collections.emptyList();
        if (req.resources == null) req.resources = Collections.emptyList();
        for (TaskInput t : req.tasks) {
            if (t.getDuration() <= 0) {
                throw new IllegalArgumentException("Task " + t.getTaskId() + " has non-positive duration");
            }
            if (t.getRequiredResource() == null || t.getRequiredResource().isEmpty()) {
                throw new IllegalArgumentException("Task " + t.getTaskId() + " missing requiredResource");
            }
        }
        for (ResourceInput r : req.resources) {
            if (r.getCapacityPerSlot() <= 0) {
                throw new IllegalArgumentException("Resource " + r.getResourceId() + " must have capacityPerSlot > 0");
            }
            if (r.getResourceId() == null || r.getResourceId().isEmpty()) {
                throw new IllegalArgumentException("Resource has empty resourceId");
            }
        }
    }

    /**
     * Diagnostics intended for the /health endpoint and included in the response
     * of schedule() to aid observability and environment troubleshooting.
     */
    public Map<String, Object> diagnostics() {
        Map<String, Object> d = new HashMap<>();
        d.put("jplPresent", isClassPresent("jpl.Query"));
        d.put("runhaskellPresent", isCommandAvailable(Optional.ofNullable(System.getenv("SCHEDULER_HS_CMD")).orElse("runhaskell")));
        d.put("swiplPresent", isCommandAvailable("swipl"));
        d.put("haskellScript", Optional.ofNullable(System.getenv("SCHEDULER_HS_SCRIPT")).orElse("haskell/GeneticSchedule.hs"));
        return d;
    }

    /** Utility: return true if a class is available on the classpath. */
    private boolean isClassPresent(String cn) {
        try {
            Class.forName(cn);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Utility: best-effort check whether a command exists by calling --version. */
    private boolean isCommandAvailable(String cmd) {
        try {
            Process p = new ProcessBuilder(cmd, "--version").start();
            p.destroy();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Validate with Prolog via JPL by asserting facts and checking valid_schedule/0.
     * If JPL is not present, returns false without failing the request.
     */
    @SuppressWarnings("unchecked")
    private boolean validateWithProlog(ScheduleRequest req, String haskellJson) throws Exception {
        // We'll parse the Haskell JSON lightly to extract assignments.
        // Expected shape: {"bestSchedule":[{"taskId":..,"timeSlot":..,"resourceId":".."},...],"fitness":..}
        List<Map<String,Object>> assignments = extractAssignments(haskellJson);

        // Setup JPL and assert facts
        // Note: This requires JPL on classpath and a SWI-Prolog installation.
        // Code left as illustrative; it will compile/run when dependencies are added.
        try {
            Class<?> jplQuery = Class.forName("jpl.Query");
            // consult constraints.pl
            Object q1 = jplQuery.getConstructor(String.class).newInstance("consult('prolog/constraints.pl')");
            boolean ok1 = (boolean) jplQuery.getMethod("hasSolution").invoke(q1);
            if (!ok1) throw new IllegalStateException("Cannot consult constraints.pl");

            // clear existing facts
            Object qClear = jplQuery.getConstructor(String.class).newInstance("clear_facts");
            jplQuery.getMethod("hasSolution").invoke(qClear);

            // assert resources
            for (ResourceInput r : req.resources) {
                String q = String.format("assert_resource('%s', %d)", escape(r.getResourceId()), r.getCapacityPerSlot());
                Object qr = jplQuery.getConstructor(String.class).newInstance(q);
                jplQuery.getMethod("hasSolution").invoke(qr);
            }

            // assert tasks and dependencies
            Map<Integer, TaskInput> taskById = new HashMap<>();
            for (TaskInput t : req.tasks) taskById.put(t.getTaskId(), t);

            for (Map<String, Object> a : assignments) {
                int tid = ((Number)a.get("taskId")).intValue();
                int slot = ((Number)a.get("timeSlot")).intValue();
                String rid = (String)a.get("resourceId");
                int dur = taskById.get(tid).getDuration();
                String q = String.format("assert_task(%d, %d, %d, '%s')", tid, slot, dur, escape(rid));
                Object qt = jplQuery.getConstructor(String.class).newInstance(q);
                jplQuery.getMethod("hasSolution").invoke(qt);
            }

            for (TaskInput t : req.tasks) {
                if (t.getDependsOn() != null) {
                    for (Integer dep : t.getDependsOn()) {
                        String q = String.format("assert_dependency(%d, %d)", t.getTaskId(), dep);
                        Object qd = jplQuery.getConstructor(String.class).newInstance(q);
                        jplQuery.getMethod("hasSolution").invoke(qd);
                    }
                }
            }

            Object qValid = jplQuery.getConstructor(String.class).newInstance("valid_schedule");
            boolean okValid = (boolean) jplQuery.getMethod("hasSolution").invoke(qValid);
            return okValid;
        } catch (ClassNotFoundException e) {
            // JPL not present; report as not validated
            return false;
        }
    }

    /**
     * Extract the list of assignments from the Haskell JSON output using a
     * naive string-based parsing logic. This is sufficient for the skeleton
     * but should be replaced with a real JSON parser in production.
     */
    private List<Map<String,Object>> extractAssignments(String json) {
        List<Map<String,Object>> res = new ArrayList<>();
        // Very naive extraction to keep deps minimal; replace with JSON parser later.
        int idx = json.indexOf("\"bestSchedule\"");
        if (idx < 0) return res;
        int start = json.indexOf('[', idx);
        int end = json.indexOf(']', start);
        if (start < 0 || end < 0) return res;
        String arr = json.substring(start+1, end);
        String[] items = arr.split("\\},\\{");
        for (String item : items) {
            String it = item.replace("{", "").replace("}", "");
            String[] parts = it.split(",");
            Map<String,Object> m = new HashMap<>();
            for (String p : parts) {
                String[] kv = p.split(":");
                if (kv.length < 2) continue;
                String key = kv[0].replace("\"", "");
                String val = kv[1];
                if (key.equals("taskId") || key.equals("timeSlot")) {
                    m.put(key, Integer.parseInt(val));
                } else if (key.equals("resourceId")) {
                    String v = val.replace("\"", "");
                    m.put(key, v);
                }
            }
            if (!m.isEmpty()) res.add(m);
        }
        return res;
    }
}
