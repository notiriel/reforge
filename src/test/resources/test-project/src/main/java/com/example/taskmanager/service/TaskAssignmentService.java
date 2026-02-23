package com.example.taskmanager.service;

import com.example.taskmanager.exception.TaskNotFoundException;
import com.example.taskmanager.model.Task;
import com.example.taskmanager.model.TaskStatus;
import com.example.taskmanager.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class TaskAssignmentService {

    private final TaskRepository taskRepository;

    public TaskAssignmentService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public boolean canAssign(Long taskId) {
        Optional<Task> task = taskRepository.findById(taskId);
        if (task.isEmpty()) {
            throw new TaskNotFoundException(taskId);
        }
        return task.get().getStatus() != TaskStatus.DONE;
    }

    @Transactional(readOnly = true)
    public List<Task> findAssignable(Long projectId) {
        return taskRepository.findByProjectId(projectId).stream()
                .filter(t -> t.getStatus() != TaskStatus.DONE)
                .toList();
    }
}
