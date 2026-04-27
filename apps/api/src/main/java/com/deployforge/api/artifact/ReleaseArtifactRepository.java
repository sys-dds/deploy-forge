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
public class ReleaseArtifactRepository {

    private final JdbcTemplate jdbcTemplate;

    public ReleaseArtifactRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ReleaseArtifactResponse create(UUID projectId, UUID serviceId, RegisterReleaseArtifactRequest request) {
        UUID id = UUID.randomUUID();
        return jdbcTemplate.queryForObject("""
                insert into release_artifacts (
                    id, project_id, service_id, version, git_sha, image_digest, build_number,
                    source_branch, commit_message, created_by, metadata_json, readiness_status
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                returning id, project_id, service_id, version, git_sha, image_digest, build_number,
                    source_branch, commit_message, created_by, metadata_json::text, readiness_status, created_at
                """, this::mapArtifact, id, projectId, serviceId, request.version(), request.gitSha(),
                request.imageDigest(), request.buildNumber(), request.sourceBranch(), request.commitMessage(),
                request.createdBy(), Jsonb.toPgObject(request.metadata()), readiness(request).name());
    }

    public Optional<ReleaseArtifactResponse> findByServiceAndVersion(UUID serviceId, String version) {
        return findOne("""
                select id, project_id, service_id, version, git_sha, image_digest, build_number,
                    source_branch, commit_message, created_by, metadata_json::text, readiness_status, created_at
                from release_artifacts
                where service_id = ? and version = ?
                """, serviceId, version);
    }

    public Optional<ReleaseArtifactResponse> findById(UUID artifactId) {
        return findOne("""
                select id, project_id, service_id, version, git_sha, image_digest, build_number,
                    source_branch, commit_message, created_by, metadata_json::text, readiness_status, created_at
                from release_artifacts
                where id = ?
                """, artifactId);
    }

    public List<ReleaseArtifactResponse> findByService(UUID serviceId) {
        return jdbcTemplate.query("""
                select id, project_id, service_id, version, git_sha, image_digest, build_number,
                    source_branch, commit_message, created_by, metadata_json::text, readiness_status, created_at
                from release_artifacts
                where service_id = ?
                order by created_at, version
                """, this::mapArtifact, serviceId);
    }

    private Optional<ReleaseArtifactResponse> findOne(String sql, Object... args) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, this::mapArtifact, args));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private ReleaseArtifactResponse mapArtifact(ResultSet rs, int rowNum) throws SQLException {
        return new ReleaseArtifactResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getObject("service_id", UUID.class),
                rs.getString("version"),
                rs.getString("git_sha"),
                rs.getString("image_digest"),
                rs.getString("build_number"),
                rs.getString("source_branch"),
                rs.getString("commit_message"),
                rs.getString("created_by"),
                Jsonb.fromString(rs.getString("metadata_json")),
                ArtifactReadinessStatus.valueOf(rs.getString("readiness_status")),
                rs.getObject("created_at", OffsetDateTime.class)
        );
    }

    private ArtifactReadinessStatus readiness(RegisterReleaseArtifactRequest request) {
        return request.readinessStatus() == null ? ArtifactReadinessStatus.READY : request.readinessStatus();
    }
}
