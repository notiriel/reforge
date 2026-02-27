package com.example.taskmanager.service.scheduling;

import com.example.taskmanager.model.Task;
import com.example.taskmanager.model.TaskStatus;
import com.example.taskmanager.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskDeadlineSchedulerTest {

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private TaskDeadlineScheduler scheduler;

    private Task overdueTask;
    private Task futureTask;
    private Task doneTask;

    @BeforeEach
    void setUp() {
        overdueTask = new Task();
        overdueTask.setId(1L);
        overdueTask.setTitle("Overdue Task");
        overdueTask.setStatus(TaskStatus.TODO);
        overdueTask.setDueDate(LocalDate.now().minusDays(3));

        futureTask = new Task();
        futureTask.setId(2L);
        futureTask.setTitle("Future Task");
        futureTask.setStatus(TaskStatus.IN_PROGRESS);
        futureTask.setDueDate(LocalDate.now().plusDays(2));

        doneTask = new Task();
        doneTask.setId(3L);
        doneTask.setTitle("Done Task");
        doneTask.setStatus(TaskStatus.DONE);
        doneTask.setDueDate(LocalDate.now().minusDays(1));
    }

    @Test
    void findOverdueTasks_returnsOnlyOverdueAndNotDone() {
        when(taskRepository.findByProjectId(1L))
                .thenReturn(Arrays.asList(overdueTask, futureTask, doneTask));

        List<Task> result = scheduler.findOverdueTasks(1L);

        assertEquals(1, result.size());
        assertEquals("Overdue Task", result.get(0).getTitle());
    }

    @Test
    void findTasksDueSoon_returnsTasksWithinRange() {
        when(taskRepository.findByProjectId(1L))
                .thenReturn(Arrays.asList(overdueTask, futureTask, doneTask));

        List<Task> result = scheduler.findTasksDueSoon(1L, 7);

        assertEquals(1, result.size());
        assertEquals("Future Task", result.get(0).getTitle());
    }

    @Test
    void hasOverdueTasks_withOverdueTask_returnsTrue() {
        when(taskRepository.findByProjectId(1L))
                .thenReturn(Arrays.asList(overdueTask));

        assertTrue(scheduler.hasOverdueTasks(1L));
    }

    @Test
    void hasOverdueTasks_withNoOverdueTasks_returnsFalse() {
        when(taskRepository.findByProjectId(1L))
                .thenReturn(Arrays.asList(futureTask));

        assertFalse(scheduler.hasOverdueTasks(1L));
    }

    /**
     * Regression test: exact FQN resolution must place TaskDeadlineScheduler
     * in the task.scheduling package (not common.scheduling from the wildcard catch-all).
     * If this fails, Bug 1 (exact FQN resolution returning 0 classes) has regressed.
     */
    @Test
    void taskDeadlineScheduler_shouldBeInTaskSchedulingPackage() {
        assertEquals("com.example.taskmanager.task.scheduling",
                TaskDeadlineScheduler.class.getPackageName(),
                "TaskDeadlineScheduler should be moved to task.scheduling by exact FQN rule, " +
                "not to common.scheduling by wildcard catch-all");
    }
}
