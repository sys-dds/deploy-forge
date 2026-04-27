package com.deployforge.api.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.deployforge.api.project.LifecycleStatus;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ServiceRepository {

    private final JdbcTemplate jdbcTemplate;

    public ServiceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ServiceResponse create(UUID projectId, CreateServiceRequest request) {
        UUID id = UUID.randomUUID();
        return jdbcTemplate.queryForObject("""
                insert into deployable_services (
                    id, project_id, name, slug, repository_url, service_tier, runtime_kind, lifecycle_status
                )
                values (?, ?, ?, ?, ?, ?, ?, ?)
                returning id, project_id, name, slug, repository_url, service_tier, runtime_kind, lifecycle_status, created_at
                """, this::mapService, id, projectId, request.name(), request.slug(), request.repositoryUrl(),
                serviceTier(request.serviceTier()).name(), runtimeKind(request.runtimeKind()).name(),
                lifecycleStatus(request.lifecycleStatus()).name());
    }

    public List<ServiceResponse> findByProjectId(UUID projectId) {
        return jdbcTemplate.query("""
                select id, project_id, name, slug, repository_url, service_tier, runtime_kind, lifecycle_status, created_at
                from deployable_services
                where project_id = ?
                order by created_at, name
                """, this::mapService, projectId);
    }

    public Optional<ServiceResponse> findById(UUID serviceId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                    select id, project_id, name, slug, repository_url, service_tier, runtime_kind, lifecycle_status, created_at
                    from deployable_services
                    where id = ?
                    """, this::mapService, serviceId));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private ServiceResponse mapService(ResultSet rs, int rowNum) throws SQLException {
        return new ServiceResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getString("name"),
                rs.getString("slug"),
                rs.getString("repository_url"),
                ServiceTier.valueOf(rs.getString("service_tier")),
                RuntimeKind.valueOf(rs.getString("runtime_kind")),
                LifecycleStatus.valueOf(rs.getString("lifecycle_status")),
                rs.getObject("created_at", OffsetDateTime.class)
        );
    }

    private ServiceTier serviceTier(ServiceTier serviceTier) {
        return serviceTier == null ? ServiceTier.STANDARD : serviceTier;
    }

    private RuntimeKind runtimeKind(RuntimeKind runtimeKind) {
        return runtimeKind == null ? RuntimeKind.SERVICE : runtimeKind;
    }

    private LifecycleStatus lifecycleStatus(LifecycleStatus lifecycleStatus) {
        return lifecycleStatus == null ? LifecycleStatus.ACTIVE : lifecycleStatus;
    }
}
