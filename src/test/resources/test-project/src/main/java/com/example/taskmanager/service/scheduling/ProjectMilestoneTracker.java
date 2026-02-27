package com.example.taskmanager.service.scheduling;

import com.example.taskmanager.model.Project;
import com.example.taskmanager.model.Task;
import com.example.taskmanager.model.TaskStatus;
import com.example.taskmanager.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ProjectMilestoneTracker {

    private final ProjectRepository projectRepository;

    public ProjectMilestoneTracker(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    public double getCompletionPercentage(Long projectId) {
        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null || project.getTasks() == null || project.getTasks().isEmpty()) {
            return 0.0;
        }
        long total = project.getTasks().size();
        long done = project.getTasks().stream()
                .filter(t -> t.getStatus() == TaskStatus.DONE)
                .count();
        return (double) done / total * 100.0;
    }

    public List<String> getInProgressTaskTitles(Long projectId) {
        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null || project.getTasks() == null) {
            return List.of();
        }
        return project.getTasks().stream()
                .filter(t -> t.getStatus() == TaskStatus.IN_PROGRESS)
                .map(Task::getTitle)
                .collect(Collectors.toList());
    }
}
