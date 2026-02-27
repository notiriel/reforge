package com.example.taskmanager.service.scheduling;

import com.example.taskmanager.model.Task;
import com.example.taskmanager.model.TaskStatus;
import com.example.taskmanager.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class TaskDeadlineScheduler {

    private final TaskRepository taskRepository;

    public TaskDeadlineScheduler(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public List<Task> findOverdueTasks(Long projectId) {
        LocalDate today = LocalDate.now();
        return taskRepository.findByProjectId(projectId).stream()
                .filter(t -> t.getStatus() != TaskStatus.DONE)
                .filter(t -> t.getDueDate() != null && t.getDueDate().isBefore(today))
                .collect(Collectors.toList());
    }

    public List<Task> findTasksDueSoon(Long projectId, int daysAhead) {
        LocalDate today = LocalDate.now();
        LocalDate cutoff = today.plusDays(daysAhead);
        return taskRepository.findByProjectId(projectId).stream()
                .filter(t -> t.getStatus() != TaskStatus.DONE)
                .filter(t -> t.getDueDate() != null)
                .filter(t -> !t.getDueDate().isBefore(today) && !t.getDueDate().isAfter(cutoff))
                .collect(Collectors.toList());
    }

    public boolean hasOverdueTasks(Long projectId) {
        return !findOverdueTasks(projectId).isEmpty();
    }
}
