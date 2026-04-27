package com.deployforge.api.approval;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ApprovalRepository {
    private final JdbcTemplate jdbcTemplate;

    public ApprovalRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ApprovalRequestResponse create(UUID projectId, UUID planId, UUID environmentId, CreateApprovalRequestRequest request, int requiredCount) {
        UUID id = UUID.randomUUID();
        return jdbcTemplate.queryForObject("""
                insert into deployment_approval_requests (
                    id, project_id, deployment_plan_id, environment_id, status, required_approval_count,
                    requested_by, reason, expires_at
                )
                values (?, ?, ?, ?, 'PENDING', ?, ?, ?, ?)
                returning id, project_id, deployment_plan_id, environment_id, status, required_approval_count, approved_count,
                    requested_by, reason, expires_at, created_at, updated_at
                """, this::mapRequest, id, projectId, planId, environmentId, requiredCount,
                request.requestedBy(), request.reason(), request.expiresAt());
    }

    public Optional<ApprovalRequestResponse> findPendingByPlan(UUID planId) {
        return findRequest("""
                select id, project_id, deployment_plan_id, environment_id, status, required_approval_count, approved_count,
                    requested_by, reason, expires_at, created_at, updated_at
                from deployment_approval_requests where deployment_plan_id = ? and status = 'PENDING'
                """, planId);
    }

    public Optional<ApprovalRequestResponse> findRequest(UUID projectId, UUID id) {
        return findRequest("""
                select id, project_id, deployment_plan_id, environment_id, status, required_approval_count, approved_count,
                    requested_by, reason, expires_at, created_at, updated_at
                from deployment_approval_requests where project_id = ? and id = ?
                """, projectId, id);
    }

    public List<ApprovalRequestResponse> listByPlan(UUID projectId, UUID planId) {
        return jdbcTemplate.query("""
                select id, project_id, deployment_plan_id, environment_id, status, required_approval_count, approved_count,
                    requested_by, reason, expires_at, created_at, updated_at
                from deployment_approval_requests where project_id = ? and deployment_plan_id = ? order by created_at, id
                """, this::mapRequest, projectId, planId);
    }

    public ApprovalDecisionResponse addDecision(UUID requestId, CreateApprovalDecisionRequest request) {
        UUID id = UUID.randomUUID();
        return jdbcTemplate.queryForObject("""
                insert into deployment_approval_decisions (id, approval_request_id, decision, decided_by, reason)
                values (?, ?, ?, ?, ?)
                returning id, approval_request_id, decision, decided_by, reason, created_at
                """, this::mapDecision, id, requestId, request.decision().name(), request.decidedBy(), request.reason());
    }

    public ApprovalRequestResponse approve(ApprovalRequestResponse request) {
        return jdbcTemplate.queryForObject("""
                update deployment_approval_requests
                set approved_count = approved_count + 1,
                    status = case when approved_count + 1 >= required_approval_count then 'APPROVED' else status end,
                    updated_at = now()
                where id = ?
                returning id, project_id, deployment_plan_id, environment_id, status, required_approval_count, approved_count,
                    requested_by, reason, expires_at, created_at, updated_at
                """, this::mapRequest, request.id());
    }

    public ApprovalRequestResponse mark(UUID id, ApprovalStatus status) {
        return jdbcTemplate.queryForObject("""
                update deployment_approval_requests set status = ?, updated_at = now()
                where id = ?
                returning id, project_id, deployment_plan_id, environment_id, status, required_approval_count, approved_count,
                    requested_by, reason, expires_at, created_at, updated_at
                """, this::mapRequest, status.name(), id);
    }

    public boolean hasApprovedForPlan(UUID projectId, UUID planId) {
        Boolean exists = jdbcTemplate.queryForObject("""
                select exists(select 1 from deployment_approval_requests where project_id = ? and deployment_plan_id = ? and status = 'APPROVED')
                """, Boolean.class, projectId, planId);
        return Boolean.TRUE.equals(exists);
    }

    public boolean hasRejectedOrExpiredForPlan(UUID projectId, UUID planId) {
        Boolean exists = jdbcTemplate.queryForObject("""
                select exists(select 1 from deployment_approval_requests where project_id = ? and deployment_plan_id = ? and status in ('REJECTED','EXPIRED'))
                """, Boolean.class, projectId, planId);
        return Boolean.TRUE.equals(exists);
    }

    private Optional<ApprovalRequestResponse> findRequest(String sql, Object... args) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, this::mapRequest, args));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private ApprovalRequestResponse mapRequest(ResultSet rs, int rowNum) throws SQLException {
        return new ApprovalRequestResponse(rs.getObject("id", UUID.class), rs.getObject("project_id", UUID.class),
                rs.getObject("deployment_plan_id", UUID.class), rs.getObject("environment_id", UUID.class),
                ApprovalStatus.valueOf(rs.getString("status")), rs.getInt("required_approval_count"),
                rs.getInt("approved_count"), rs.getString("requested_by"), rs.getString("reason"),
                rs.getObject("expires_at", OffsetDateTime.class), rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class));
    }

    private ApprovalDecisionResponse mapDecision(ResultSet rs, int rowNum) throws SQLException {
        return new ApprovalDecisionResponse(rs.getObject("id", UUID.class), rs.getObject("approval_request_id", UUID.class),
                ApprovalDecision.valueOf(rs.getString("decision")), rs.getString("decided_by"),
                rs.getString("reason"), rs.getObject("created_at", OffsetDateTime.class));
    }
}
