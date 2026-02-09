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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@Transactional
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectService projectService;

    public TaskService(TaskRepository taskRepository, ProjectService projectService) {
        this.taskRepository = taskRepository;
        this.projectService = projectService;
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> getTasksByProjectId(Long projectId) {
        projectService.findProjectOrThrow(projectId);
        return taskRepository.findByProjectId(projectId).stream()
                .map(TaskResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public TaskResponse getTaskById(Long id) {
        Task task = findTaskOrThrow(id);
        return TaskResponse.fromEntity(task);
    }

    public TaskResponse createTask(Long projectId, CreateTaskRequest request) {
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            throw new IllegalArgumentException("Task title is required");
        }
        Project project = projectService.findProjectOrThrow(projectId);
        Task task = new Task(request.getTitle(), request.getDescription(), project);
        if (request.getPriority() != null) {
            task.setPriority(TaskPriority.valueOf(request.getPriority().toUpperCase()));
        }
        if (request.getDueDate() != null) {
            task.setDueDate(request.getDueDate());
        }
        Task saved = taskRepository.save(task);
        return TaskResponse.fromEntity(saved);
    }

    public TaskResponse updateTask(Long id, UpdateTaskRequest request) {
        Task task = findTaskOrThrow(id);
        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            task.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            task.setDescription(request.getDescription());
        }
        if (request.getPriority() != null) {
            task.setPriority(TaskPriority.valueOf(request.getPriority().toUpperCase()));
        }
        if (request.getDueDate() != null) {
            task.setDueDate(request.getDueDate());
        }
        if (request.getStatus() != null) {
            TaskStatus newStatus = TaskStatus.valueOf(request.getStatus().toUpperCase());
            if (newStatus == TaskStatus.DONE) {
                validateDependenciesMet(task);
            }
            task.setStatus(newStatus);
        }
        Task saved = taskRepository.save(task);
        return TaskResponse.fromEntity(saved);
    }

    public void deleteTask(Long id) {
        Task task = findTaskOrThrow(id);
        // Remove this task from all dependents' dependency lists
        for (Task dependent : task.getDependents()) {
            dependent.getDependencies().remove(task);
        }
        taskRepository.delete(task);
    }

    public TaskResponse addDependency(Long taskId, Long dependencyId) {
        if (taskId.equals(dependencyId)) {
            throw new IllegalArgumentException("A task cannot depend on itself");
        }
        Task task = findTaskOrThrow(taskId);
        Task dependency = findTaskOrThrow(dependencyId);

        // Check for circular dependency
        if (hasCircularDependency(dependency, taskId)) {
            throw new IllegalArgumentException("Adding this dependency would create a circular dependency");
        }

        task.getDependencies().add(dependency);
        Task saved = taskRepository.save(task);
        return TaskResponse.fromEntity(saved);
    }

    public TaskResponse removeDependency(Long taskId, Long dependencyId) {
        Task task = findTaskOrThrow(taskId);
        Task dependency = findTaskOrThrow(dependencyId);
        task.getDependencies().remove(dependency);
        Task saved = taskRepository.save(task);
        return TaskResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> getDependencies(Long taskId) {
        Task task = findTaskOrThrow(taskId);
        return task.getDependencies().stream()
                .map(TaskResponse::fromEntity)
                .toList();
    }

    public Task findTaskOrThrow(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
    }

    void validateDependenciesMet(Task task) {
        Set<Task> dependencies = task.getDependencies();
        List<Task> unfinished = dependencies.stream()
                .filter(dep -> dep.getStatus() != TaskStatus.DONE)
                .toList();
        if (!unfinished.isEmpty()) {
            String unfinishedIds = unfinished.stream()
                    .map(t -> String.valueOf(t.getId()))
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
            throw new DependencyNotMetException(
                "Cannot mark task as DONE. Unfinished dependencies: [" + unfinishedIds + "]"
            );
        }
    }

    private boolean hasCircularDependency(Task dependency, Long originalTaskId) {
        for (Task dep : dependency.getDependencies()) {
            if (dep.getId().equals(originalTaskId)) {
                return true;
            }
            if (hasCircularDependency(dep, originalTaskId)) {
                return true;
            }
        }
        return false;
    }
}
