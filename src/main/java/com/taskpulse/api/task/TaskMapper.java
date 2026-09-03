package com.taskpulse.api.task;

import com.taskpulse.api.task.dto.TaskRequest;
import com.taskpulse.api.task.dto.TaskResponse;
import org.springframework.stereotype.Component;

/**
 * Converts between the {@link Task} entity and its transport representations.
 */
@Component
public class TaskMapper {

    /**
     * Maps a persisted task onto the JSON response shape.
     *
     * @param task the entity to convert
     * @return the response DTO, or {@code null} when {@code task} is {@code null}
     */
    public TaskResponse toResponse(Task task) {
        if (task == null) {
            return null;
        }
        TaskResponse response = new TaskResponse();
        response.setId(task.getId());
        response.setTitle(task.getTitle());
        response.setDescription(task.getDescription());
        response.setDueDate(task.getDueDate());
        response.setCompleted(task.isCompleted());
        response.setCreatedAt(task.getCreatedAt());
        response.setUpdatedAt(task.getUpdatedAt());
        return response;
    }

    /**
     * Copies the mutable fields of a request onto the given entity. The completion flag is only
     * applied when the caller actually supplied it, so an update that omits {@code completed}
     * leaves the current state of the task untouched.
     *
     * @param request the incoming payload
     * @param task    the entity to mutate
     */
    public void apply(TaskRequest request, Task task) {
        if (request == null || task == null) {
            return;
        }
        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setDueDate(request.getDueDate());
        if (request.getCompleted() != null) {
            task.setCompleted(request.getCompleted());
        }
    }
}
