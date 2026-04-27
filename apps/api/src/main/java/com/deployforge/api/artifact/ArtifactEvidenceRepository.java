package com.deployforge.api.artifact;

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
public class ArtifactEvidenceRepository {

    private final JdbcTemplate jdbcTemplate;

    public ArtifactEvidenceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ArtifactEvidenceResponse create(UUID artifactId, AddArtifactEvidenceRequest request) {
        UUID id = UUID.randomUUID();
        return jdbcTemplate.queryForObject("""
                insert into release_artifact_evidence (
                    id, artifact_id, evidence_type, evidence_ref, evidence_sha, metadata_json
                )
                values (?, ?, ?, ?, ?, ?)
                returning id, artifact_id, evidence_type, evidence_ref, evidence_sha, metadata_json::text, created_at
                """, this::mapEvidence, id, artifactId, request.evidenceType().name(), request.evidenceRef(),
                request.evidenceSha(), Jsonb.toPgObject(request.metadata()));
    }

    public Optional<ArtifactEvidenceResponse> findByNaturalKey(UUID artifactId, EvidenceType type, String ref) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                    select id, artifact_id, evidence_type, evidence_ref, evidence_sha, metadata_json::text, created_at
                    from release_artifact_evidence
                    where artifact_id = ? and evidence_type = ? and evidence_ref = ?
                    """, this::mapEvidence, artifactId, type.name(), ref));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public List<ArtifactEvidenceResponse> findByArtifact(UUID artifactId) {
        return jdbcTemplate.query("""
                select id, artifact_id, evidence_type, evidence_ref, evidence_sha, metadata_json::text, created_at
                from release_artifact_evidence
                where artifact_id = ?
                order by created_at, id
                """, this::mapEvidence, artifactId);
    }

    public boolean existsByArtifactAndType(UUID artifactId, EvidenceType evidenceType) {
        Boolean exists = jdbcTemplate.queryForObject("""
                select exists(
                    select 1 from release_artifact_evidence where artifact_id = ? and evidence_type = ?
                )
                """, Boolean.class, artifactId, evidenceType.name());
        return Boolean.TRUE.equals(exists);
    }

    private ArtifactEvidenceResponse mapEvidence(ResultSet rs, int rowNum) throws SQLException {
        return new ArtifactEvidenceResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("artifact_id", UUID.class),
                EvidenceType.valueOf(rs.getString("evidence_type")),
                rs.getString("evidence_ref"),
                rs.getString("evidence_sha"),
                Jsonb.fromString(rs.getString("metadata_json")),
                rs.getObject("created_at", OffsetDateTime.class)
        );
    }
}
