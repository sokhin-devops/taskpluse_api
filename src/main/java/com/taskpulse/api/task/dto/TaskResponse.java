package com.taskpulse.api.task.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(name = "TaskResponse", description = "A task as returned by the API.")
public class TaskResponse {

    @Schema(description = "Generated identifier of the task.", example = "1")
    private Long id;

    @Schema(description = "Short summary of the task.", example = "Write the sprint report")
    private String title;

    @Schema(
            description = "Longer details about the task, or null when none was provided.",
            example = "Summarise velocity, blockers and the demo agenda for Friday.",
            nullable = true
    )
    private String description;

    @Schema(
            description = "Due date in yyyy-MM-dd format, or null when the task has no deadline.",
            example = "2026-09-10",
            type = "string",
            format = "date",
            nullable = true
    )
    private LocalDate dueDate;

    @Schema(description = "Whether the task has been completed.", example = "false")
    private boolean completed;

    @Schema(
            description = "Timestamp of when the task was created.",
            example = "2026-09-02T10:00:00",
            type = "string",
            format = "date-time"
    )
    private LocalDateTime createdAt;

    @Schema(
            description = "Timestamp of the last update to the task.",
            example = "2026-09-02T10:00:00",
            type = "string",
            format = "date-time"
    )
    private LocalDateTime updatedAt;

    public TaskResponse() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
