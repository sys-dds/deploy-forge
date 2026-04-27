package com.deployforge.api.project;

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
public class ProjectRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProjectRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ProjectResponse create(CreateProjectRequest request) {
        UUID id = UUID.randomUUID();
        return jdbcTemplate.queryForObject("""
                insert into deployment_projects (id, name, slug, description, owner_team, lifecycle_status)
                values (?, ?, ?, ?, ?, ?)
                returning id, name, slug, description, owner_team, lifecycle_status, created_at
                """, this::mapProject, id, request.name(), request.slug(), request.description(),
                request.ownerTeam(), lifecycleStatus(request.lifecycleStatus()).name());
    }

    public Optional<ProjectResponse> findById(UUID id) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                    select id, name, slug, description, owner_team, lifecycle_status, created_at
                    from deployment_projects
                    where id = ?
                    """, this::mapProject, id));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public List<ProjectResponse> findAll() {
        return jdbcTemplate.query("""
                select id, name, slug, description, owner_team, lifecycle_status, created_at
                from deployment_projects
                order by created_at, name
                """, this::mapProject);
    }

    public boolean existsById(UUID id) {
        Boolean exists = jdbcTemplate.queryForObject(
                "select exists(select 1 from deployment_projects where id = ?)",
                Boolean.class,
                id
        );
        return Boolean.TRUE.equals(exists);
    }

    private ProjectResponse mapProject(ResultSet rs, int rowNum) throws SQLException {
        return new ProjectResponse(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("slug"),
                rs.getString("description"),
                rs.getString("owner_team"),
                LifecycleStatus.valueOf(rs.getString("lifecycle_status")),
                rs.getObject("created_at", OffsetDateTime.class)
        );
    }

    private LifecycleStatus lifecycleStatus(LifecycleStatus status) {
        return status == null ? LifecycleStatus.ACTIVE : status;
    }
}
