package com.example.taskmanager.service;

import com.example.taskmanager.model.Project;
import com.example.taskmanager.model.Task;
import com.example.taskmanager.model.TaskPriority;
import com.example.taskmanager.model.TaskStatus;
import com.example.taskmanager.repository.ProjectRepository;
import com.example.taskmanager.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ReportService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;

    public ReportService(TaskRepository taskRepository, ProjectRepository projectRepository) {
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
    }

    public Map<String, Long> getTaskCountByStatus() {
        List<Task> allTasks = taskRepository.findAll();
        Map<String, Long> countByStatus = new LinkedHashMap<>();
        for (TaskStatus status : TaskStatus.values()) {
            long count = allTasks.stream()
                    .filter(t -> t.getStatus() == status)
                    .count();
            countByStatus.put(status.name(), count);
        }
        return countByStatus;
    }

    public Map<String, List<String>> getTasksByPriority() {
        List<Task> allTasks = taskRepository.findAll();
        Map<String, List<String>> tasksByPriority = new HashMap<>();
        for (TaskPriority priority : TaskPriority.values()) {
            List<String> titles = allTasks.stream()
                    .filter(t -> t.getPriority() == priority)
                    .map(Task::getTitle)
                    .collect(Collectors.toList());
            tasksByPriority.put(priority.name(), titles);
        }
        return tasksByPriority;
    }

    public List<Map<String, Object>> getProjectSummaries() {
        List<Project> projects = projectRepository.findAll();
        List<Map<String, Object>> summaries = new ArrayList<>();
        for (Project project : projects) {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("name", project.getName());
            summary.put("taskCount", project.getTasks() != null ? project.getTasks().size() : 0);
            if (project.getTasks() != null && !project.getTasks().isEmpty()) {
                long done = project.getTasks().stream()
                        .filter(t -> t.getStatus() == TaskStatus.DONE)
                        .count();
                summary.put("completedCount", done);
                summary.put("completionRate", (double) done / project.getTasks().size());
            } else {
                summary.put("completedCount", 0L);
                summary.put("completionRate", 0.0);
            }
            summaries.add(summary);
        }
        return summaries;
    }
}
