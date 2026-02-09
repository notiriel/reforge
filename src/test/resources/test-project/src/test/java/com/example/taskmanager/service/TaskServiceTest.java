package com.example.taskmanager.service;

import com.example.taskmanager.dto.CreateTaskRequest;
import com.example.taskmanager.dto.TaskResponse;
import com.example.taskmanager.dto.UpdateTaskRequest;
import com.example.taskmanager.exception.DependencyNotMetException;
import com.example.taskmanager.exception.TaskNotFoundException;
import com.example.taskmanager.model.Project;
import com.example.taskmanager.model.Task;
import com.example.taskmanager.model.TaskPriority;
import com.example.taskmanager.model.TaskStatus;
import com.example.taskmanager.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private ProjectService projectService;

    @InjectMocks
    private TaskService taskService;

    private Project project;
    private Task task;
    private Task dependency;

    @BeforeEach
    void setUp() {
        project = new Project("Test Project", "Description");
        project.setId(1L);
        project.setCreatedDate(LocalDateTime.now());

        task = new Task("Test Task", "Task Description", project);
        task.setId(1L);
        task.setStatus(TaskStatus.TODO);
        task.setPriority(TaskPriority.MEDIUM);
        task.setCreatedDate(LocalDateTime.now());
        task.setUpdatedDate(LocalDateTime.now());

        dependency = new Task("Dependency Task", "Dependency Description", project);
        dependency.setId(2L);
        dependency.setStatus(TaskStatus.TODO);
        dependency.setPriority(TaskPriority.HIGH);
        dependency.setCreatedDate(LocalDateTime.now());
        dependency.setUpdatedDate(LocalDateTime.now());
        dependency.setDependencies(new HashSet<>());
    }

    @Test
    void getTasksByProjectId_returnsTaskList() {
        when(projectService.findProjectOrThrow(1L)).thenReturn(project);
        when(taskRepository.findByProjectId(1L)).thenReturn(Arrays.asList(task));

        List<TaskResponse> result = taskService.getTasksByProjectId(1L);

        assertEquals(1, result.size());
        assertEquals("Test Task", result.get(0).getTitle());
    }

    @Test
    void getTaskById_existingTask_returnsTask() {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        TaskResponse result = taskService.getTaskById(1L);

        assertNotNull(result);
        assertEquals("Test Task", result.getTitle());
    }

    @Test
    void getTaskById_nonExistingTask_throwsException() {
        when(taskRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(TaskNotFoundException.class, () -> taskService.getTaskById(99L));
    }

    @Test
    void createTask_validRequest_returnsCreatedTask() {
        CreateTaskRequest request = new CreateTaskRequest("New Task", "New Desc", "HIGH", LocalDate.now().plusDays(5));
        when(projectService.findProjectOrThrow(1L)).thenReturn(project);
        when(taskRepository.save(any(Task.class))).thenReturn(task);

        TaskResponse result = taskService.createTask(1L, request);

        assertNotNull(result);
        verify(taskRepository).save(any(Task.class));
    }

    @Test
    void createTask_blankTitle_throwsException() {
        CreateTaskRequest request = new CreateTaskRequest("", "Desc", "HIGH", null);

        assertThrows(IllegalArgumentException.class, () -> taskService.createTask(1L, request));
    }

    @Test
    void updateTask_validRequest_updatesAndReturns() {
        UpdateTaskRequest request = new UpdateTaskRequest("Updated Title", "Updated Desc", "IN_PROGRESS", "HIGH", LocalDate.now());
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(Task.class))).thenReturn(task);

        TaskResponse result = taskService.updateTask(1L, request);

        assertNotNull(result);
        verify(taskRepository).save(any(Task.class));
    }

    @Test
    void updateTask_markDoneWithUnfinishedDependencies_throwsException() {
        task.getDependencies().add(dependency);
        UpdateTaskRequest request = new UpdateTaskRequest(null, null, "DONE", null, null);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        assertThrows(DependencyNotMetException.class, () -> taskService.updateTask(1L, request));
    }

    @Test
    void updateTask_markDoneWithFinishedDependencies_succeeds() {
        dependency.setStatus(TaskStatus.DONE);
        task.getDependencies().add(dependency);
        UpdateTaskRequest request = new UpdateTaskRequest(null, null, "DONE", null, null);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(Task.class))).thenReturn(task);

        TaskResponse result = taskService.updateTask(1L, request);

        assertNotNull(result);
    }

    @Test
    void deleteTask_existingTask_deletesSuccessfully() {
        task.setDependents(new HashSet<>());
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        taskService.deleteTask(1L);

        verify(taskRepository).delete(task);
    }

    @Test
    void addDependency_validDependency_addsDependency() {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(taskRepository.findById(2L)).thenReturn(Optional.of(dependency));
        when(taskRepository.save(any(Task.class))).thenReturn(task);

        TaskResponse result = taskService.addDependency(1L, 2L);

        assertNotNull(result);
        assertTrue(task.getDependencies().contains(dependency));
    }

    @Test
    void addDependency_selfDependency_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> taskService.addDependency(1L, 1L));
    }

    @Test
    void removeDependency_existingDependency_removesDependency() {
        task.getDependencies().add(dependency);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(taskRepository.findById(2L)).thenReturn(Optional.of(dependency));
        when(taskRepository.save(any(Task.class))).thenReturn(task);

        taskService.removeDependency(1L, 2L);

        verify(taskRepository).save(any(Task.class));
    }

    @Test
    void getDependencies_returnsListOfDependencies() {
        task.getDependencies().add(dependency);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        List<TaskResponse> result = taskService.getDependencies(1L);

        assertEquals(1, result.size());
    }
}
