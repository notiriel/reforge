package com.example.taskmanager.repository;

import com.example.taskmanager.model.Task;
import com.example.taskmanager.model.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByProjectId(Long projectId);

    List<Task> findByProjectIdAndStatus(Long projectId, TaskStatus status);

    @Query("SELECT t FROM Task t WHERE t.project.id = :projectId ORDER BY " +
           "CASE t.priority WHEN com.example.taskmanager.model.TaskPriority.HIGH THEN 0 " +
           "WHEN com.example.taskmanager.model.TaskPriority.MEDIUM THEN 1 " +
           "WHEN com.example.taskmanager.model.TaskPriority.LOW THEN 2 END ASC, " +
           "t.dueDate ASC")
    List<Task> findByProjectIdOrderByPriorityAndDueDate(@Param("projectId") Long projectId);

    long countByProjectIdAndStatus(Long projectId, TaskStatus status);
}
