package com.example.taskmanager.repository;

import com.example.taskmanager.model.Project;
import com.example.taskmanager.model.Task;
import com.example.taskmanager.model.TaskPriority;
import com.example.taskmanager.model.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ActiveProfiles("test")
class TaskRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TaskRepository taskRepository;

    private Project project;
    private Task task1;
    private Task task2;
    private Task task3;

    @BeforeEach
    void setUp() {
        project = new Project("Test Project", "Test Description");
        entityManager.persist(project);

        task1 = new Task("Task 1", "Description 1", project);
        task1.setStatus(TaskStatus.TODO);
        task1.setPriority(TaskPriority.HIGH);
        task1.setDueDate(LocalDate.now().plusDays(3));
        entityManager.persist(task1);

        task2 = new Task("Task 2", "Description 2", project);
        task2.setStatus(TaskStatus.IN_PROGRESS);
        task2.setPriority(TaskPriority.MEDIUM);
        task2.setDueDate(LocalDate.now().plusDays(1));
        entityManager.persist(task2);

        task3 = new Task("Task 3", "Description 3", project);
        task3.setStatus(TaskStatus.DONE);
        task3.setPriority(TaskPriority.LOW);
        entityManager.persist(task3);

        entityManager.flush();
    }

    @Test
    void findByProjectId_returnsAllProjectTasks() {
        List<Task> tasks = taskRepository.findByProjectId(project.getId());

        assertEquals(3, tasks.size());
    }

    @Test
    void findByProjectIdAndStatus_returnsFilteredTasks() {
        List<Task> todoTasks = taskRepository.findByProjectIdAndStatus(project.getId(), TaskStatus.TODO);

        assertEquals(1, todoTasks.size());
        assertEquals("Task 1", todoTasks.get(0).getTitle());
    }

    @Test
    void findByProjectIdAndStatus_done_returnsCompletedTasks() {
        List<Task> doneTasks = taskRepository.findByProjectIdAndStatus(project.getId(), TaskStatus.DONE);

        assertEquals(1, doneTasks.size());
        assertEquals("Task 3", doneTasks.get(0).getTitle());
    }

    @Test
    void findByProjectIdOrderByPriorityAndDueDate_returnsOrderedTasks() {
        List<Task> tasks = taskRepository.findByProjectIdOrderByPriorityAndDueDate(project.getId());

        assertFalse(tasks.isEmpty());
        assertEquals("Task 1", tasks.get(0).getTitle()); // HIGH priority comes first
    }

    @Test
    void countByProjectIdAndStatus_returnsCorrectCount() {
        long todoCount = taskRepository.countByProjectIdAndStatus(project.getId(), TaskStatus.TODO);
        long doneCount = taskRepository.countByProjectIdAndStatus(project.getId(), TaskStatus.DONE);

        assertEquals(1, todoCount);
        assertEquals(1, doneCount);
    }

    @Test
    void findByProjectId_nonExistingProject_returnsEmptyList() {
        List<Task> tasks = taskRepository.findByProjectId(999L);

        assertTrue(tasks.isEmpty());
    }

    @Test
    void save_taskWithDependencies_persistsDependencies() {
        task1.getDependencies().add(task3);
        taskRepository.save(task1);
        entityManager.flush();
        entityManager.clear();

        Task found = taskRepository.findById(task1.getId()).orElseThrow();
        assertEquals(1, found.getDependencies().size());
        assertTrue(found.getDependencies().stream().anyMatch(d -> d.getId().equals(task3.getId())));
    }
}
