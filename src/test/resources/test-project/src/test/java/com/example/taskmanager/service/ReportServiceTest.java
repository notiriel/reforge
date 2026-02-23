package com.example.taskmanager.service;

import com.example.taskmanager.model.Project;
import com.example.taskmanager.model.Task;
import com.example.taskmanager.model.TaskPriority;
import com.example.taskmanager.model.TaskStatus;
import com.example.taskmanager.repository.ProjectRepository;
import com.example.taskmanager.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private ProjectRepository projectRepository;

    @InjectMocks
    private ReportService reportService;

    private Project project;
    private Task task1;
    private Task task2;
    private Task task3;

    @BeforeEach
    void setUp() {
        project = new Project("Alpha", "Alpha project");
        project.setId(1L);
        project.setCreatedDate(LocalDateTime.now());

        task1 = new Task("Task A", "First task", project);
        task1.setId(1L);
        task1.setStatus(TaskStatus.DONE);
        task1.setPriority(TaskPriority.HIGH);
        task1.setCreatedDate(LocalDateTime.now());
        task1.setUpdatedDate(LocalDateTime.now());

        task2 = new Task("Task B", "Second task", project);
        task2.setId(2L);
        task2.setStatus(TaskStatus.IN_PROGRESS);
        task2.setPriority(TaskPriority.MEDIUM);
        task2.setCreatedDate(LocalDateTime.now());
        task2.setUpdatedDate(LocalDateTime.now());

        task3 = new Task("Task C", "Third task", project);
        task3.setId(3L);
        task3.setStatus(TaskStatus.TODO);
        task3.setPriority(TaskPriority.HIGH);
        task3.setCreatedDate(LocalDateTime.now());
        task3.setUpdatedDate(LocalDateTime.now());

        project.setTasks(Arrays.asList(task1, task2, task3));
    }

    @Test
    void getTaskCountByStatus_returnsCountsForAllStatuses() {
        when(taskRepository.findAll()).thenReturn(Arrays.asList(task1, task2, task3));

        Map<String, Long> result = reportService.getTaskCountByStatus();

        assertEquals(3, result.size());
        assertEquals(1L, result.get("TODO"));
        assertEquals(1L, result.get("IN_PROGRESS"));
        assertEquals(1L, result.get("DONE"));
    }

    @Test
    void getTasksByPriority_groupsTaskTitlesByPriority() {
        when(taskRepository.findAll()).thenReturn(Arrays.asList(task1, task2, task3));

        Map<String, List<String>> result = reportService.getTasksByPriority();

        assertEquals(3, result.size());
        assertEquals(Arrays.asList("Task A", "Task C"), result.get("HIGH"));
        assertEquals(Arrays.asList("Task B"), result.get("MEDIUM"));
        assertEquals(List.of(), result.get("LOW"));
    }

    @Test
    void getProjectSummaries_returnsCompletionStats() {
        when(projectRepository.findAll()).thenReturn(List.of(project));

        List<Map<String, Object>> result = reportService.getProjectSummaries();

        assertEquals(1, result.size());
        Map<String, Object> summary = result.get(0);
        assertEquals("Alpha", summary.get("name"));
        assertEquals(3, summary.get("taskCount"));
        assertEquals(1L, summary.get("completedCount"));
    }
}
