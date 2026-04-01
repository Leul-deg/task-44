package com.shiftworks.jobops.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiftworks.jobops.config.AppProperties;
import com.shiftworks.jobops.dto.DashboardConfigResponse;
import com.shiftworks.jobops.dto.DashboardPreviewRequest;
import com.shiftworks.jobops.dto.DashboardRequest;
import com.shiftworks.jobops.dto.ExportRequest;
import com.shiftworks.jobops.entity.DashboardConfig;
import com.shiftworks.jobops.entity.User;
import com.shiftworks.jobops.enums.JobStatus;
import com.shiftworks.jobops.enums.ReviewActionType;
import com.shiftworks.jobops.enums.UserRole;
import com.shiftworks.jobops.exception.BusinessException;
import com.shiftworks.jobops.repository.DashboardConfigRepository;
import com.shiftworks.jobops.repository.ScheduledReportRepository;
import com.shiftworks.jobops.repository.UserRepository;
import com.shiftworks.jobops.security.AuthenticatedUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private final DashboardConfigRepository repository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final StepUpVerificationService stepUpVerificationService;
    private final FileStorageService fileStorageService;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;
    private final AppProperties properties;
    private final ScheduledReportRepository scheduledReportRepository;

    @Transactional(readOnly = true)
    public List<DashboardConfigResponse> list(AuthenticatedUser user) {
        return repository.findByUser_Id(user.id()).stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public DashboardConfigResponse create(DashboardRequest request, AuthenticatedUser user) {
        log.info("Dashboard created name={} by userId={}", request.name(), user.id());
        User owner = userRepository.findById(user.id())
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "user: Not found"));
        DashboardConfig config = new DashboardConfig();
        config.setUser(owner);
        config.setName(request.name());
        config.setMetricsJson(toJsonString(request.metricsJson()));
        config.setDimensionsJson(toJsonString(request.dimensionsJson()));
        config.setFiltersJson(toJsonString(request.filtersJson()));
        config.setCreatedAt(Instant.now());
        config.setUpdatedAt(Instant.now());
        repository.save(config);
        auditService.log(user.id(), "CREATE_DASHBOARD", "DashboardConfig", config.getId(), null, config);
        return toResponse(config);
    }

    @Transactional
    public DashboardConfigResponse update(Long id, DashboardRequest request, AuthenticatedUser user) {
        DashboardConfig config = repository.findById(id)
            .filter(d -> d.getUser().getId().equals(user.id()))
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "dashboard: Not found or not owner"));
        config.setName(request.name());
        config.setMetricsJson(toJsonString(request.metricsJson()));
        config.setDimensionsJson(toJsonString(request.dimensionsJson()));
        config.setFiltersJson(toJsonString(request.filtersJson()));
        config.setUpdatedAt(Instant.now());
        repository.save(config);
        auditService.log(user.id(), "UPDATE_DASHBOARD", "DashboardConfig", config.getId(), null, config);
        return toResponse(config);
    }

    @Transactional
    public void delete(Long id, AuthenticatedUser user) {
        DashboardConfig config = repository.findById(id)
            .filter(d -> d.getUser().getId().equals(user.id()))
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "dashboard: Not found or not owner"));
        scheduledReportRepository.deleteByDashboardConfig_Id(id);
        repository.delete(config);
        auditService.log(user.id(), "DELETE_DASHBOARD", "DashboardConfig", config.getId(), config, null);
    }

    public List<Map<String, Object>> data(Long id, AuthenticatedUser user) {
        DashboardConfig config = repository.findById(id)
            .filter(d -> d.getUser().getId().equals(user.id()) || user.role() == UserRole.ADMIN)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "dashboard: Not found"));
        return executeQuery(config);
    }

    public DashboardConfigResponse get(Long id, AuthenticatedUser user) {
        DashboardConfig config = repository.findById(id)
            .filter(d -> d.getUser().getId().equals(user.id()) || user.role() == UserRole.ADMIN)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "dashboard: Not found"));
        return toResponse(config);
    }

    public List<Map<String, Object>> preview(DashboardPreviewRequest request) {
        DashboardConfig temp = new DashboardConfig();
        temp.setMetricsJson(toJsonString(request.metricsJson()));
        temp.setDimensionsJson(toJsonString(request.dimensionsJson()));
        temp.setFiltersJson(toJsonString(request.filtersJson()));
        return executeQuery(temp);
    }

    public byte[] export(Long id, boolean masked, ExportRequest request, AuthenticatedUser user) {
        log.info("Dashboard export id={} masked={} by userId={}", id, masked, user.id());
        DashboardConfig config = repository.findById(id)
            .filter(d -> d.getUser().getId().equals(user.id()) || user.role() == UserRole.ADMIN)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "dashboard: Not found"));
        if (!masked && user.role() != UserRole.ADMIN) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "export: Admins only");
        }
        if (!masked) {
            if (!stepUpVerificationService.verify(user.id(), request.stepUpPassword())) {
                throw new BusinessException(HttpStatus.FORBIDDEN, "stepUpPassword: Verification failed");
            }
        }
        List<Map<String, Object>> rows = executeQuery(config);
        if (rows.isEmpty()) {
            return new byte[0];
        }
        List<Map<String, Object>> exportRows = rows;
        if (masked) {
            // Metrics are aggregate; only reviewer/username labels may carry PII, so mask those values.
            exportRows = rows.stream()
                .map(this::maskSensitiveFields)
                .toList();
        }
        byte[] csv = toCsv(exportRows);
        String path = String.format("reports/%s-%s.csv", id, UUID.randomUUID());
        fileStorageService.save(path, csv);
        auditService.log(user.id(), "EXPORT_DASHBOARD", "DashboardConfig", id, null, rows);
        return csv;
    }

    List<Map<String, Object>> execute(DashboardConfig config) {
        return executeQuery(config);
    }

    List<Map<String, Object>> maskRows(List<Map<String, Object>> rows) {
        return rows.stream().map(this::maskSensitiveFields).toList();
    }

    private List<Map<String, Object>> executeQuery(DashboardConfig config) {
        List<String> metrics;
        String dimension;
        Map<String, Object> filters;
        try {
            metrics = objectMapper.readValue(config.getMetricsJson(), new TypeReference<>() {});
            dimension = objectMapper.readValue(config.getDimensionsJson(), String.class);
            filters = config.getFiltersJson() != null && !config.getFiltersJson().isBlank()
                ? objectMapper.readValue(config.getFiltersJson(), new TypeReference<>() {})
                : Map.of();
        } catch (Exception e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "dashboard: Invalid config JSON — " + e.getMessage());
        }

        Map<String, Map<String, Object>> rows = new LinkedHashMap<>();
        for (String metric : metrics) {
            List<Map<String, Object>> results = queryMetric(metric, dimension, filters);
            for (Map<String, Object> row : results) {
                String key = row.get("dimension").toString();
                Map<String, Object> entry = rows.computeIfAbsent(key, k -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("dimension", k);
                    return map;
                });
                entry.put(metric, row.get("value"));
            }
        }
        return new ArrayList<>(rows.values());
    }

    /**
     * SECURITY CONTRACT: This method builds native SQL via StringBuilder. All user-controlled
     * inputs (metric, dimension, statuses) are validated through exhaustive switch/enum checks
     * before reaching the SQL string. Date parameters use named bind variables (:from, :to).
     * If adding new metrics or dimensions, they MUST go through the same whitelist pattern —
     * never interpolate user input directly into the SQL string.
     */
    private List<Map<String, Object>> queryMetric(String metric, String dimension, Map<String, Object> filters) {
        String table;
        String condition = "";
        String join = "";
        switch (metric) {
            case "post_volume":
                table = "job_postings";
                break;
            case "claim_count":
                table = "claims";
                break;
            case "review_count":
            case "approval_rate":
            case "handling_time":
            case "takedown_count":
                table = "review_actions";
                break;
            default:
                throw new BusinessException(HttpStatus.BAD_REQUEST, "metric: Unsupported");
        }
        Instant from = filters.containsKey("dateFrom") ? Instant.parse(filters.get("dateFrom").toString()) : Instant.now().minusSeconds(30 * 86400);
        Instant to = filters.containsKey("dateTo") ? Instant.parse(filters.get("dateTo").toString()) : Instant.now();
        List<String> statuses = filters.containsKey("statuses") ? (List<String>) filters.get("statuses") : List.of();

        String baseAlias = table.equals("review_actions") ? "ra" : table.equals("claims") ? "c" : "jp";
        String dimensionExpr = resolveDimension(dimension, baseAlias);

        String metricExpr = switch (metric) {
            case "post_volume", "claim_count", "review_count" -> "COUNT(*)";
            case "approval_rate" -> "ROUND(SUM(CASE WHEN ra.action = 'APPROVE' THEN 1 ELSE 0 END) * 1.0 / NULLIF(COUNT(*), 0), 4)";
            case "handling_time" -> "AVG(TIMESTAMPDIFF(HOUR, jp.created_at, ra.created_at))";
            case "takedown_count" -> "SUM(CASE WHEN ra.action = 'TAKEDOWN' THEN 1 ELSE 0 END)";
            default -> "COUNT(*)";
        };

        StringBuilder sql = new StringBuilder("SELECT " + dimensionExpr + " AS dimension, " + metricExpr + " AS value FROM " + table + " " + baseAlias + " ");
        if (!table.equals("job_postings")) {
            String joinCondition = table.equals("review_actions") ? "ra.job_posting_id = jp.id" : "c.job_posting_id = jp.id";
            sql.append("JOIN job_postings jp ON ").append(joinCondition).append(" ");
        }
        if (dimension.equals("category")) {
            sql.append("JOIN categories cat ON cat.id = jp.category_id ");
        }
        if (dimension.equals("location_state")) {
            sql.append("JOIN locations loc ON loc.id = jp.location_id ");
        }
        if (dimension.equals("reviewer") && table.equals("review_actions")) {
            sql.append("JOIN users usr ON usr.id = ra.reviewer_id ");
        }
        sql.append("WHERE ").append(baseAlias).append(".created_at >= :from AND ").append(baseAlias).append(".created_at <= :to ");
        if (!statuses.isEmpty()) {
            List<String> safeStatuses = statuses.stream()
                .filter(s -> {
                    try {
                        com.shiftworks.jobops.enums.JobStatus.valueOf(s.toUpperCase(java.util.Locale.ROOT));
                        return true;
                    } catch (IllegalArgumentException e) {
                        return false;
                    }
                })
                .map(s -> s.toUpperCase(java.util.Locale.ROOT))
                .toList();
            if (!safeStatuses.isEmpty()) {
                String statusList = safeStatuses.stream().map(s -> "'" + s + "'").reduce((a, b) -> a + "," + b).orElse("");
                sql.append("AND jp.status IN (").append(statusList).append(") ");
            }
        }
        sql.append("GROUP BY dimension ORDER BY dimension");

        Query query = entityManager.createNativeQuery(sql.toString());
        query.setParameter("from", from);
        query.setParameter("to", to);
        List<Object[]> raw = query.getResultList();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object[] row : raw) {
            Map<String, Object> map = new HashMap<>();
            map.put("dimension", row[0]);
            map.put("value", row[1]);
            rows.add(map);
        }
        return rows;
    }

    private String resolveDimension(String dimension, String alias) {
        return switch (dimension) {
            case "date_daily" -> "DATE(" + alias + ".created_at)";
            case "date_weekly" -> "DATE_FORMAT(" + alias + ".created_at, '%x-%v')";
            case "date_monthly" -> "DATE_FORMAT(" + alias + ".created_at, '%Y-%m')";
            case "status" -> "jp.status";
            case "category" -> "cat.name";
            case "location_state" -> "loc.state";
            case "reviewer" -> "usr.username";
            default -> "DATE(" + alias + ".created_at)";
        };
    }

    byte[] toCsv(List<Map<String, Object>> rows) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             OutputStreamWriter writer = new OutputStreamWriter(bos, StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader(rows.get(0).keySet().toArray(new String[0])))) {
            for (Map<String, Object> row : rows) {
                printer.printRecord(row.values());
            }
            printer.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private DashboardConfigResponse toResponse(DashboardConfig config) {
        return new DashboardConfigResponse(
            config.getId(),
            config.getName(),
            parseJson(config.getMetricsJson()),
            parseJson(config.getDimensionsJson()),
            parseJson(config.getFiltersJson())
        );
    }

    private Object parseJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return json;
        }
    }

    private Map<String, Object> maskSensitiveFields(Map<String, Object> row) {
        Map<String, Object> copy = new HashMap<>(row);
        if (copy.containsKey("username")) {
            copy.put("username", maskLabel(copy.get("username")));
        }
        if (copy.containsKey("reviewer")) {
            copy.put("reviewer", maskLabel(copy.get("reviewer")));
        }
        Object dim = copy.get("dimension");
        if (dim != null && dim.toString().matches(".*[a-zA-Z].*") && !dim.toString().matches("\\d{4}-.+")) {
            copy.put("dimension", maskLabel(dim));
        }
        return copy;
    }

    private String toJsonString(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private String maskLabel(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        if (text.length() <= 3) {
            return "***";
        }
        return text.substring(0, Math.min(3, text.length())) + "***";
    }
}
