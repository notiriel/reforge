package com.example.taskmanager.config;

import com.example.taskmanager.model.Project;
import com.example.taskmanager.model.Task;
import com.example.taskmanager.model.TaskPriority;
import com.example.taskmanager.model.TaskStatus;
import com.example.taskmanager.repository.ProjectRepository;
import com.example.taskmanager.repository.TaskRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@Profile("!test")
public class DataInitializer implements CommandLineRunner {

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;

    public DataInitializer(ProjectRepository projectRepository, TaskRepository taskRepository) {
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
    }

    @Override
    public void run(String... args) {
        // Create sample projects
        Project webApp = new Project("Web Application", "Main web application project");
        webApp = projectRepository.save(webApp);

        Project mobileApp = new Project("Mobile App", "iOS and Android mobile application");
        mobileApp = projectRepository.save(mobileApp);

        Project infrastructure = new Project("Infrastructure", "DevOps and infrastructure setup");
        infrastructure = projectRepository.save(infrastructure);

        // Create tasks for Web Application
        Task setupDb = new Task("Setup Database", "Configure PostgreSQL database schema", webApp);
        setupDb.setPriority(TaskPriority.HIGH);
        setupDb.setDueDate(LocalDate.now().plusDays(3));
        setupDb = taskRepository.save(setupDb);

        Task buildApi = new Task("Build REST API", "Implement CRUD endpoints", webApp);
        buildApi.setPriority(TaskPriority.HIGH);
        buildApi.setDueDate(LocalDate.now().plusDays(7));
        buildApi.getDependencies().add(setupDb);
        buildApi = taskRepository.save(buildApi);

        Task buildUi = new Task("Build Frontend", "Create React frontend", webApp);
        buildUi.setPriority(TaskPriority.MEDIUM);
        buildUi.setDueDate(LocalDate.now().plusDays(14));
        buildUi.getDependencies().add(buildApi);
        taskRepository.save(buildUi);

        Task writeTests = new Task("Write Tests", "Unit and integration tests", webApp);
        writeTests.setPriority(TaskPriority.MEDIUM);
        writeTests.setStatus(TaskStatus.TODO);
        taskRepository.save(writeTests);

        // Create tasks for Mobile App
        Task mobileDesign = new Task("UI/UX Design", "Design mobile app screens", mobileApp);
        mobileDesign.setPriority(TaskPriority.HIGH);
        mobileDesign.setStatus(TaskStatus.IN_PROGRESS);
        taskRepository.save(mobileDesign);

        Task mobileAuth = new Task("Authentication", "Implement OAuth2 login", mobileApp);
        mobileAuth.setPriority(TaskPriority.HIGH);
        taskRepository.save(mobileAuth);

        // Create tasks for Infrastructure
        Task setupCi = new Task("Setup CI/CD", "Configure GitHub Actions pipeline", infrastructure);
        setupCi.setPriority(TaskPriority.HIGH);
        setupCi.setStatus(TaskStatus.DONE);
        taskRepository.save(setupCi);

        Task setupMonitoring = new Task("Setup Monitoring", "Configure Prometheus and Grafana", infrastructure);
        setupMonitoring.setPriority(TaskPriority.LOW);
        setupMonitoring.getDependencies().add(setupCi);
        taskRepository.save(setupMonitoring);
    }
}
