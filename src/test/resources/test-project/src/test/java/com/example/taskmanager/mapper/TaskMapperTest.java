package com.example.taskmanager.mapper;

import com.example.taskmanager.dto.TaskResponse;
import com.example.taskmanager.model.Project;
import com.example.taskmanager.model.Task;
import com.example.taskmanager.model.TaskPriority;
import com.example.taskmanager.model.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TaskMapperTest {

    private Project project;
    private Task task;

    @BeforeEach
    void setUp() {
        project = new Project("Test Project", "Description");
        project.setId(10L);
        project.setCreatedDate(LocalDateTime.now());

        task = new Task("Build feature", "Implement the feature", project);
        task.setId(1L);
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setPriority(TaskPriority.HIGH);
        task.setCreatedDate(LocalDateTime.now());
        task.setUpdatedDate(LocalDateTime.now());
    }

    @Test
    void toResponse_mapsBasicFields() {
        TaskResponse response = TaskMapper.INSTANCE.toResponse(task);

        assertNotNull(response);
        assertEquals("Build feature", response.getTitle());
        assertEquals("Implement the feature", response.getDescription());
        assertEquals(10L, response.getProjectId());
        assertEquals("Test Project", response.getProjectName());
        assertNull(response.getDependencyIds());
    }

    @Test
    void toResponse_mapsEnumsToStrings() {
        TaskResponse response = TaskMapper.INSTANCE.toResponse(task);

        assertEquals("IN_PROGRESS", response.getStatus());
        assertEquals("HIGH", response.getPriority());
    }
}
