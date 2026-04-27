package com.deployforge.api.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

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
                insert into deployable_services (id, project_id, name, slug, repository_url)
                values (?, ?, ?, ?, ?)
                returning id, project_id, name, slug, repository_url, created_at
                """, this::mapService, id, projectId, request.name(), request.slug(), request.repositoryUrl());
    }

    public List<ServiceResponse> findByProjectId(UUID projectId) {
        return jdbcTemplate.query("""
                select id, project_id, name, slug, repository_url, created_at
                from deployable_services
                where project_id = ?
                order by created_at, name
                """, this::mapService, projectId);
    }

    private ServiceResponse mapService(ResultSet rs, int rowNum) throws SQLException {
        return new ServiceResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getString("name"),
                rs.getString("slug"),
                rs.getString("repository_url"),
                rs.getObject("created_at", OffsetDateTime.class)
        );
    }
}
