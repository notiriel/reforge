package com.example.taskmanager.controller;

import com.example.taskmanager.dto.CreateTaskRequest;
import com.example.taskmanager.dto.TaskResponse;
import com.example.taskmanager.dto.UpdateTaskRequest;
import com.example.taskmanager.exception.DependencyNotMetException;
import com.example.taskmanager.exception.TaskNotFoundException;
import com.example.taskmanager.service.TaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaskController.class)
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TaskService taskService;

    @Autowired
    private ObjectMapper objectMapper;

    private TaskResponse taskResponse;

    @BeforeEach
    void setUp() {
        objectMapper.registerModule(new JavaTimeModule());

        taskResponse = new TaskResponse();
        taskResponse.setId(1L);
        taskResponse.setTitle("Test Task");
        taskResponse.setDescription("Test Description");
        taskResponse.setStatus("TODO");
        taskResponse.setPriority("MEDIUM");
        taskResponse.setDueDate(LocalDate.of(2025, 6, 15));
        taskResponse.setCreatedDate(LocalDateTime.of(2025, 1, 1, 12, 0));
        taskResponse.setUpdatedDate(LocalDateTime.of(2025, 1, 1, 12, 0));
        taskResponse.setProjectId(1L);
        taskResponse.setProjectName("Test Project");
        taskResponse.setDependencyIds(Collections.emptyList());
    }

    @Test
    void getTasksByProject_returnsTaskList() throws Exception {
        when(taskService.getTasksByProjectId(1L)).thenReturn(Arrays.asList(taskResponse));

        mockMvc.perform(get("/api/projects/1/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title", is("Test Task")));
    }

    @Test
    void createTask_validRequest_returns201() throws Exception {
        CreateTaskRequest request = new CreateTaskRequest("New Task", "New Desc", "HIGH", LocalDate.now().plusDays(5));
        when(taskService.createTask(eq(1L), any(CreateTaskRequest.class))).thenReturn(taskResponse);

        mockMvc.perform(post("/api/projects/1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title", is("Test Task")));
    }

    @Test
    void getTask_existingTask_returnsTask() throws Exception {
        when(taskService.getTaskById(1L)).thenReturn(taskResponse);

        mockMvc.perform(get("/api/tasks/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Test Task")))
                .andExpect(jsonPath("$.status", is("TODO")));
    }

    @Test
    void getTask_nonExistingTask_returns404() throws Exception {
        when(taskService.getTaskById(99L)).thenThrow(new TaskNotFoundException(99L));

        mockMvc.perform(get("/api/tasks/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateTask_validRequest_returns200() throws Exception {
        UpdateTaskRequest request = new UpdateTaskRequest("Updated", "Updated Desc", "IN_PROGRESS", "HIGH", LocalDate.now());
        when(taskService.updateTask(eq(1L), any(UpdateTaskRequest.class))).thenReturn(taskResponse);

        mockMvc.perform(put("/api/tasks/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void updateTask_dependencyNotMet_returns409() throws Exception {
        UpdateTaskRequest request = new UpdateTaskRequest(null, null, "DONE", null, null);
        when(taskService.updateTask(eq(1L), any(UpdateTaskRequest.class)))
                .thenThrow(new DependencyNotMetException("Unfinished dependencies: [2]"));

        mockMvc.perform(put("/api/tasks/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteTask_existingTask_returns204() throws Exception {
        doNothing().when(taskService).deleteTask(1L);

        mockMvc.perform(delete("/api/tasks/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void addDependency_validRequest_returns200() throws Exception {
        taskResponse.setDependencyIds(List.of(2L));
        when(taskService.addDependency(1L, 2L)).thenReturn(taskResponse);

        mockMvc.perform(post("/api/tasks/1/dependencies/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dependencyIds", hasSize(1)));
    }

    @Test
    void removeDependency_validRequest_returns200() throws Exception {
        when(taskService.removeDependency(1L, 2L)).thenReturn(taskResponse);

        mockMvc.perform(delete("/api/tasks/1/dependencies/2"))
                .andExpect(status().isOk());
    }

    @Test
    void getDependencies_returnsListOfDependencies() throws Exception {
        when(taskService.getDependencies(1L)).thenReturn(Arrays.asList(taskResponse));

        mockMvc.perform(get("/api/tasks/1/dependencies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }
}
