package com.taskflow.repository;

import com.taskflow.domain.Project;
import com.taskflow.domain.Team;
import com.taskflow.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    Page<Project> findByTeam(Team team, Pageable pageable);

    List<Project> findByTeam(Team team);

    Page<Project> findByCreator(User creator, Pageable pageable);

    @Query("""
    SELECT p FROM Project p
    LEFT JOIN p.team t
    LEFT JOIN t.members m
    WHERE p.creator = :user
       OR m.user = :user
    """)
    Page<Project> findAllAccessibleByUser(User user, Pageable pageable);

    long countByTeam(Team team);
}
