package com.taskpulse.api.task;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.taskpulse.api.exception.TaskNotFoundException;
import com.taskpulse.api.task.dto.TaskRequest;
import com.taskpulse.api.task.dto.TaskResponse;

/**
 * Application service for the task CRUD use cases.
 * All persistence access goes through {@link TaskRepository}; the controller
 * only ever sees DTOs produced by {@link TaskMapper}.
 */
@Service
@Transactional
public class TaskService {

    private final TaskRepository repository;
    private final TaskMapper mapper;

    public TaskService(TaskRepository repository, TaskMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> findAll() {
        return repository.findAllByOrderByCompletedAscDueDateAscIdDesc()
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TaskResponse findById(Long id) {
        return mapper.toResponse(getOrThrow(id));
    }

    public TaskResponse create(TaskRequest request) {
        Task task = new Task();
        mapper.apply(request, task);
        return mapper.toResponse(repository.save(task));
    }

    public TaskResponse update(Long id, TaskRequest request) {
        Task task = getOrThrow(id);
        mapper.apply(request, task);
        return mapper.toResponse(repository.save(task));
    }

    public TaskResponse toggleComplete(Long id) {
        Task task = getOrThrow(id);
        task.setCompleted(!task.isCompleted());
        return mapper.toResponse(repository.save(task));
    }

    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new TaskNotFoundException(id);
        }
        repository.deleteById(id);
    }

    private Task getOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
    }
}
