package com.example.taskmanager.service;

import com.example.taskmanager.dto.CreateProjectRequest;
import com.example.taskmanager.dto.ProjectResponse;
import com.example.taskmanager.exception.ProjectNotFoundException;
import com.example.taskmanager.model.Project;
import com.example.taskmanager.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @InjectMocks
    private ProjectService projectService;

    private Project project;

    @BeforeEach
    void setUp() {
        project = new Project("Test Project", "Test Description");
        project.setId(1L);
        project.setCreatedDate(LocalDateTime.now());
    }

    @Test
    void getAllProjects_returnsAllProjects() {
        Project project2 = new Project("Project 2", "Description 2");
        project2.setId(2L);
        project2.setCreatedDate(LocalDateTime.now());
        when(projectRepository.findAll()).thenReturn(Arrays.asList(project, project2));

        List<ProjectResponse> result = projectService.getAllProjects();

        assertEquals(2, result.size());
        assertEquals("Test Project", result.get(0).getName());
        assertEquals("Project 2", result.get(1).getName());
    }

    @Test
    void getProjectById_existingProject_returnsProject() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));

        ProjectResponse result = projectService.getProjectById(1L);

        assertNotNull(result);
        assertEquals("Test Project", result.getName());
        assertEquals("Test Description", result.getDescription());
    }

    @Test
    void getProjectById_nonExistingProject_throwsException() {
        when(projectRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ProjectNotFoundException.class, () -> projectService.getProjectById(99L));
    }

    @Test
    void createProject_validRequest_returnsCreatedProject() {
        CreateProjectRequest request = new CreateProjectRequest("New Project", "New Description");
        when(projectRepository.save(any(Project.class))).thenReturn(project);

        ProjectResponse result = projectService.createProject(request);

        assertNotNull(result);
        verify(projectRepository).save(any(Project.class));
    }

    @Test
    void createProject_blankName_throwsException() {
        CreateProjectRequest request = new CreateProjectRequest("", "Description");

        assertThrows(IllegalArgumentException.class, () -> projectService.createProject(request));
    }

    @Test
    void createProject_nullName_throwsException() {
        CreateProjectRequest request = new CreateProjectRequest(null, "Description");

        assertThrows(IllegalArgumentException.class, () -> projectService.createProject(request));
    }

    @Test
    void updateProject_existingProject_updatesAndReturns() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(projectRepository.save(any(Project.class))).thenReturn(project);
        CreateProjectRequest request = new CreateProjectRequest("Updated Name", "Updated Desc");

        ProjectResponse result = projectService.updateProject(1L, request);

        assertNotNull(result);
        verify(projectRepository).save(any(Project.class));
    }

    @Test
    void deleteProject_existingProject_deletesSuccessfully() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));

        projectService.deleteProject(1L);

        verify(projectRepository).delete(project);
    }

    @Test
    void deleteProject_nonExistingProject_throwsException() {
        when(projectRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ProjectNotFoundException.class, () -> projectService.deleteProject(99L));
    }
}
