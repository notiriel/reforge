package com.example.taskmanager.mapper;

import com.example.taskmanager.dto.TaskResponse;
import com.example.taskmanager.model.Task;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface TaskMapper {

    TaskMapper INSTANCE = Mappers.getMapper(TaskMapper.class);

    @Mapping(target = "status", expression = "java(task.getStatus().name())")
    @Mapping(target = "priority", expression = "java(task.getPriority().name())")
    @Mapping(target = "projectId", source = "project.id")
    @Mapping(target = "projectName", source = "project.name")
    @Mapping(target = "dependencyIds", ignore = true)
    TaskResponse toResponse(Task task);
}
