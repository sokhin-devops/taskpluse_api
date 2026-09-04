package com.taskpulse.api.task;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.taskpulse.api.common.PageResponse;
import com.taskpulse.api.config.OpenApiConfig;
import com.taskpulse.api.exception.ApiError;
import com.taskpulse.api.task.dto.BoardResponse;
import com.taskpulse.api.task.dto.TaskMoveRequest;
import com.taskpulse.api.task.dto.TaskRequest;
import com.taskpulse.api.task.dto.TaskResponse;
import com.taskpulse.api.task.dto.TaskStatsResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * REST endpoints for task management.
 *
 * <p>Every endpoint operates on the tasks of the account behind the bearer token. The list
 * endpoint filters, sorts and pages in the database rather than returning everything for
 * the client to sift through, which is what keeps the payload flat as a board fills up.</p>
 */
@RestController
@RequestMapping("/api/tasks")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
@Tag(name = "Tasks", description = "Create, read, update, complete, move and delete your tasks")
public class TaskController {

    /** Reused in the 401 block of every operation below. */
    private static final String UNAUTHORIZED = "Missing, expired or invalid token";

    private final TaskService service;

    public TaskController(TaskService service) {
        this.service = service;
    }

    // ------------------------------------------------------------------- list

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List your tasks",
            description = """
                    Returns one page of your tasks. Every filter is optional and they combine with AND; \
                    the multi-value filters (status, priority, tagIds) match any of the values given. \
                    Enum values are upper-case, exactly as they appear in the schema.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of tasks returned",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = PageResponse.class))),
            @ApiResponse(responseCode = "400", description = "A parameter was rejected, e.g. an unknown sort field",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = UNAUTHORIZED,
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<PageResponse<TaskResponse>> findAll(

            @Parameter(description = "Free-text search over title and description, case-insensitive.",
                    example = "report")
            @RequestParam(required = false) String q,

            @Parameter(description = "Keep only these workflow columns. Repeat the parameter for several.",
                    example = "TODO")
            @RequestParam(required = false) Set<TaskStatus> status,

            @Parameter(description = "Keep only these priorities. Repeat the parameter for several.",
                    example = "HIGH")
            @RequestParam(required = false) Set<TaskPriority> priority,

            @Parameter(description = "Keep tasks carrying any of these tag ids.", example = "3")
            @RequestParam(required = false) List<Long> tagIds,

            @Parameter(description = "Keep only tasks with no tags at all. Overrides tagIds.")
            @RequestParam(defaultValue = "false") boolean untagged,

            @Parameter(description = "Earliest due date to include, yyyy-MM-dd.", example = "2026-09-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueFrom,

            @Parameter(description = "Latest due date to include, yyyy-MM-dd.", example = "2026-09-30")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueTo,

            @Parameter(description = "Keep only open tasks whose due date has already passed.")
            @RequestParam(defaultValue = "false") boolean overdue,

            @Parameter(description = "true keeps only tasks that have a due date, false only those that do not.")
            @RequestParam(required = false) Boolean hasDueDate,

            @Parameter(description = "Zero-based page index.", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Page size, capped at 200.", example = "10")
            @RequestParam(defaultValue = "10") int size,

            @Parameter(description = "Field to sort on: title, dueDate, priority, status, createdAt, "
                    + "updatedAt or position.", example = "dueDate")
            @RequestParam(defaultValue = TaskQuery.DEFAULT_SORT) String sort,

            @Parameter(description = "Sort direction: asc or desc.", example = "asc")
            @RequestParam(defaultValue = "asc") String direction) {

        TaskQuery query = new TaskQuery(q, status, priority, tagIds, untagged,
                dueFrom, dueTo, overdue, hasDueDate, page, size, sort, direction);
        return ResponseEntity.ok(service.findAll(query));
    }

    @GetMapping(path = "/board", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the kanban board",
            description = "Returns every workflow column with its tasks already in display order. "
                    + "One call rather than one per column, so the board never renders half-loaded.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Board returned",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = BoardResponse.class))),
            @ApiResponse(responseCode = "401", description = UNAUTHORIZED,
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<BoardResponse> board() {
        return ResponseEntity.ok(service.board());
    }

    @GetMapping(path = "/stats", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get dashboard figures",
            description = "Returns the counts, breakdowns and daily trend behind the dashboard, all measured "
                    + "at the same instant so the numbers cannot disagree with each other.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Statistics returned",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = TaskStatsResponse.class))),
            @ApiResponse(responseCode = "401", description = UNAUTHORIZED,
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<TaskStatsResponse> stats(
            @Parameter(description = "Width of the trend window in days. Clamped to 1..90.", example = "14")
            @RequestParam(defaultValue = "14") int days) {
        return ResponseEntity.ok(service.stats(days));
    }

    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a task by id", description = "Returns the single task identified by the given id.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = TaskResponse.class))),
            @ApiResponse(responseCode = "401", description = UNAUTHORIZED,
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "You have no task with that id",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<TaskResponse> findById(
            @Parameter(description = "Identifier of the task", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    // ------------------------------------------------------------------ write

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create a task",
            description = "Creates a task on your account and returns it with a Location header pointing at "
                    + "the new resource. It lands at the top of its board column.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Task created",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = TaskResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = UNAUTHORIZED,
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "A supplied tag id is not one of yours",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<TaskResponse> create(@Valid @RequestBody TaskRequest request) {
        TaskResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/tasks/" + created.id())).body(created);
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update a task",
            description = "Replaces the editable fields of one of your tasks. A field you omit is left as "
                    + "it was; send tagIds as an empty array to clear every tag.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task updated",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = TaskResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = UNAUTHORIZED,
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "No such task, or a supplied tag id is not yours",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<TaskResponse> update(
            @Parameter(description = "Identifier of the task", example = "1") @PathVariable Long id,
            @Valid @RequestBody TaskRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @PatchMapping(path = "/{id}/complete", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Toggle completion",
            description = "Flips the task between done and to-do. Prefer PATCH /{id}/move when you also need "
                    + "to express 'in progress' or a position.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Completion toggled",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = TaskResponse.class))),
            @ApiResponse(responseCode = "401", description = UNAUTHORIZED,
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "You have no task with that id",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<TaskResponse> toggleComplete(
            @Parameter(description = "Identifier of the task", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(service.toggleComplete(id));
    }

    @PatchMapping(path = "/{id}/move", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Move a task on the board",
            description = "Places the task in a workflow column at a given index, renumbering both the "
                    + "column it left and the one it joined so positions stay contiguous.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task moved",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = TaskResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = UNAUTHORIZED,
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "You have no task with that id",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<TaskResponse> move(
            @Parameter(description = "Identifier of the task", example = "1") @PathVariable Long id,
            @Valid @RequestBody TaskMoveRequest request) {
        return ResponseEntity.ok(service.move(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a task", description = "Permanently removes one of your tasks.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Task deleted", content = @Content),
            @ApiResponse(responseCode = "401", description = UNAUTHORIZED,
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "You have no task with that id",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<Void> delete(
            @Parameter(description = "Identifier of the task", example = "1") @PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
