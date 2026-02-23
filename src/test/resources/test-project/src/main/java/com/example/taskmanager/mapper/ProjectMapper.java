package com.example.taskmanager.mapper;

import com.example.taskmanager.dto.ProjectResponse;
import com.example.taskmanager.model.Project;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface ProjectMapper {

    ProjectMapper INSTANCE = Mappers.getMapper(ProjectMapper.class);

    @Mapping(target = "taskCount", expression = "java(project.getTasks() != null ? project.getTasks().size() : 0)")
    ProjectResponse toResponse(Project project);
}
