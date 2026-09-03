package com.taskpulse.api.task.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(name = "TaskRequest", description = "Payload used to create or update a task.")
public class TaskRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must be at most 200 characters")
    @Schema(
            description = "Short summary of the task. Required, 1 to 200 characters.",
            example = "Write the sprint report",
            requiredMode = Schema.RequiredMode.REQUIRED,
            maxLength = 200
    )
    private String title;

    @Size(max = 5000, message = "Description must be at most 5000 characters")
    @Schema(
            description = "Optional longer details about the task.",
            example = "Summarise velocity, blockers and the demo agenda for Friday.",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED,
            maxLength = 5000,
            nullable = true
    )
    private String description;

    @Schema(
            description = "Optional due date in yyyy-MM-dd format.",
            example = "2026-09-10",
            type = "string",
            format = "date",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED,
            nullable = true
    )
    private LocalDate dueDate;

    @Schema(
            description = "Completion flag. When omitted the current value is left untouched; on create it defaults to false.",
            example = "false",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED,
            nullable = true
    )
    private Boolean completed;

    public TaskRequest() {
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

    public Boolean getCompleted() {
        return completed;
    }

    public void setCompleted(Boolean completed) {
        this.completed = completed;
    }
}
