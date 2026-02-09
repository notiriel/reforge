package com.example.taskmanager.controller;

import com.example.taskmanager.dto.CreateProjectRequest;
import com.example.taskmanager.dto.ProjectResponse;
import com.example.taskmanager.exception.ProjectNotFoundException;
import com.example.taskmanager.service.ProjectService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProjectController.class)
class ProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectService projectService;

    @Autowired
    private ObjectMapper objectMapper;

    private ProjectResponse createProjectResponse(Long id, String name, String description) {
        ProjectResponse response = new ProjectResponse();
        response.setId(id);
        response.setName(name);
        response.setDescription(description);
        response.setCreatedDate(LocalDateTime.of(2025, 1, 1, 12, 0));
        response.setTaskCount(0);
        return response;
    }

    @Test
    void getAllProjects_returnsProjectList() throws Exception {
        List<ProjectResponse> projects = Arrays.asList(
            createProjectResponse(1L, "Project 1", "Desc 1"),
            createProjectResponse(2L, "Project 2", "Desc 2")
        );
        when(projectService.getAllProjects()).thenReturn(projects);

        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name", is("Project 1")))
                .andExpect(jsonPath("$[1].name", is("Project 2")));
    }

    @Test
    void getProject_existingProject_returnsProject() throws Exception {
        ProjectResponse response = createProjectResponse(1L, "Test Project", "Test Description");
        when(projectService.getProjectById(1L)).thenReturn(response);

        mockMvc.perform(get("/api/projects/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Test Project")))
                .andExpect(jsonPath("$.description", is("Test Description")));
    }

    @Test
    void getProject_nonExistingProject_returns404() throws Exception {
        when(projectService.getProjectById(99L)).thenThrow(new ProjectNotFoundException(99L));

        mockMvc.perform(get("/api/projects/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createProject_validRequest_returns201() throws Exception {
        CreateProjectRequest request = new CreateProjectRequest("New Project", "New Description");
        ProjectResponse response = createProjectResponse(1L, "New Project", "New Description");
        when(projectService.createProject(any(CreateProjectRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("New Project")));
    }

    @Test
    void updateProject_validRequest_returns200() throws Exception {
        CreateProjectRequest request = new CreateProjectRequest("Updated", "Updated Desc");
        ProjectResponse response = createProjectResponse(1L, "Updated", "Updated Desc");
        when(projectService.updateProject(eq(1L), any(CreateProjectRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/projects/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Updated")));
    }

    @Test
    void deleteProject_existingProject_returns204() throws Exception {
        doNothing().when(projectService).deleteProject(1L);

        mockMvc.perform(delete("/api/projects/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteProject_nonExistingProject_returns404() throws Exception {
        doThrow(new ProjectNotFoundException(99L)).when(projectService).deleteProject(99L);

        mockMvc.perform(delete("/api/projects/99"))
                .andExpect(status().isNotFound());
    }
}
