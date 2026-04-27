package com.deployforge.api.promotion;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.deployforge.api.shared.Jsonb;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PromotionEvidenceRepository {
    private final JdbcTemplate jdbcTemplate;

    public PromotionEvidenceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PromotionEvidenceResponse create(UUID projectId, UUID serviceId, UUID artifactId, CreatePromotionEvidenceRequest request) {
        UUID id = UUID.randomUUID();
        return jdbcTemplate.queryForObject("""
                insert into promotion_evidence (
                    id, project_id, service_id, artifact_id, source_environment_id, target_environment_id,
                    evidence_type, evidence_ref, recorded_by, reason, metadata_json
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                returning id, project_id, service_id, artifact_id, source_environment_id, target_environment_id,
                    evidence_type, evidence_ref, recorded_by, reason, metadata_json::text, created_at
                """, this::map, id, projectId, serviceId, artifactId, request.sourceEnvironmentId(),
                request.targetEnvironmentId(), request.evidenceType().name(), request.evidenceRef(),
                request.recordedBy(), request.reason(), Jsonb.toPgObject(request.metadata()));
    }

    public Optional<PromotionEvidenceResponse> findNatural(UUID artifactId, UUID sourceEnvironmentId, UUID targetEnvironmentId,
            PromotionEvidenceType type, String ref) {
        return findOne("""
                select id, project_id, service_id, artifact_id, source_environment_id, target_environment_id,
                    evidence_type, evidence_ref, recorded_by, reason, metadata_json::text, created_at
                from promotion_evidence
                where artifact_id = ? and source_environment_id = ? and target_environment_id is not distinct from ?
                    and evidence_type = ? and evidence_ref = ?
                """, artifactId, sourceEnvironmentId, targetEnvironmentId, type.name(), ref);
    }

    public List<PromotionEvidenceResponse> list(UUID projectId, UUID serviceId, UUID artifactId) {
        return jdbcTemplate.query("""
                select id, project_id, service_id, artifact_id, source_environment_id, target_environment_id,
                    evidence_type, evidence_ref, recorded_by, reason, metadata_json::text, created_at
                from promotion_evidence
                where project_id = ? and service_id = ? and artifact_id = ?
                order by created_at, id
                """, this::map, projectId, serviceId, artifactId);
    }

    public boolean existsForPromotion(UUID projectId, UUID serviceId, UUID artifactId, UUID sourceEnvironmentId, UUID targetEnvironmentId) {
        Boolean exists = jdbcTemplate.queryForObject("""
                select exists(
                    select 1 from promotion_evidence
                    where project_id = ? and service_id = ? and artifact_id = ?
                      and source_environment_id = ? and target_environment_id is not distinct from ?
                )
                """, Boolean.class, projectId, serviceId, artifactId, sourceEnvironmentId, targetEnvironmentId);
        return Boolean.TRUE.equals(exists);
    }

    private Optional<PromotionEvidenceResponse> findOne(String sql, Object... args) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, this::map, args));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private PromotionEvidenceResponse map(ResultSet rs, int rowNum) throws SQLException {
        return new PromotionEvidenceResponse(rs.getObject("id", UUID.class), rs.getObject("project_id", UUID.class),
                rs.getObject("service_id", UUID.class), rs.getObject("artifact_id", UUID.class),
                rs.getObject("source_environment_id", UUID.class), rs.getObject("target_environment_id", UUID.class),
                PromotionEvidenceType.valueOf(rs.getString("evidence_type")), rs.getString("evidence_ref"),
                rs.getString("recorded_by"), rs.getString("reason"), Jsonb.fromString(rs.getString("metadata_json")),
                rs.getObject("created_at", OffsetDateTime.class));
    }
}
