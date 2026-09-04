package com.taskpulse.api.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.taskpulse.api.common.PageResponse;
import com.taskpulse.api.exception.TaskNotFoundException;
import com.taskpulse.api.security.AppUserDetailsService;
import com.taskpulse.api.security.JwtService;
import com.taskpulse.api.tag.dto.TagResponse;
import com.taskpulse.api.task.dto.BoardResponse;
import com.taskpulse.api.task.dto.BoardResponse.BoardColumn;
import com.taskpulse.api.task.dto.TaskMoveRequest;
import com.taskpulse.api.task.dto.TaskRequest;
import com.taskpulse.api.task.dto.TaskResponse;

/**
 * Web-layer tests for {@link TaskController}.
 *
 * <p>{@code addFilters = false} takes the security filter chain out of the picture: what is
 * under test here is the HTTP contract — status codes, JSON shape, how query parameters bind
 * and how exceptions become {@code ApiError} bodies. Authentication itself is covered by
 * {@code JwtServiceTest} and {@code AuthServiceTest}.</p>
 *
 * <p>{@link JwtService} and {@link AppUserDetailsService} are still mocked because
 * {@code @WebMvcTest} includes {@code Filter} beans in the slice, and
 * {@code JwtAuthenticationFilter} is one; without them the context could not be built.</p>
 */
@WebMvcTest(TaskController.class)
@AutoConfigureMockMvc(addFilters = false)
class TaskControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private TaskService service;

	@MockitoBean
	private JwtService jwtService;

	@MockitoBean
	private AppUserDetailsService userDetailsService;

	// ----------------------------------------------------------------- fixtures

	private static TaskResponse sample() {
		return new TaskResponse(1L, "Write the report", "Q3 numbers",
				LocalDate.of(2026, 9, 10), TaskStatus.IN_PROGRESS, "In progress",
				TaskPriority.HIGH, "High", 2, false, null, false,
				List.of(new TagResponse(3L, "Work", "#6366f1", null)),
				LocalDateTime.of(2026, 9, 2, 10, 0), LocalDateTime.of(2026, 9, 2, 10, 0));
	}

	private static PageResponse<TaskResponse> onePage() {
		return new PageResponse<>(List.of(sample()), 0, 10, 1, 1, true, true);
	}

	// --------------------------------------------------------------------- list

	@Nested
	@DisplayName("GET /api/tasks")
	class ListTasks {

		@Test
		@DisplayName("returns a page envelope carrying the task fields")
		void returnsPage() throws Exception {
			when(service.findAll(any(TaskQuery.class))).thenReturn(onePage());

			mockMvc.perform(get("/api/tasks"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.totalElements").value(1))
					.andExpect(jsonPath("$.page").value(0))
					.andExpect(jsonPath("$.first").value(true))
					.andExpect(jsonPath("$.content[0].id").value(1))
					.andExpect(jsonPath("$.content[0].title").value("Write the report"))
					.andExpect(jsonPath("$.content[0].status").value("IN_PROGRESS"))
					.andExpect(jsonPath("$.content[0].statusLabel").value("In progress"))
					.andExpect(jsonPath("$.content[0].priority").value("HIGH"))
					.andExpect(jsonPath("$.content[0].dueDate").value("2026-09-10"))
					.andExpect(jsonPath("$.content[0].tags[0].name").value("Work"));
		}

		@Test
		@DisplayName("binds every filter from the query string")
		void bindsFilters() throws Exception {
			when(service.findAll(any(TaskQuery.class))).thenReturn(onePage());

			mockMvc.perform(get("/api/tasks")
							.param("q", "report")
							.param("status", "TODO", "IN_PROGRESS")
							.param("priority", "HIGH")
							.param("tagIds", "3", "4")
							.param("dueFrom", "2026-09-01")
							.param("dueTo", "2026-09-30")
							.param("overdue", "true")
							.param("hasDueDate", "true")
							.param("page", "2")
							.param("size", "25")
							.param("sort", "priority")
							.param("direction", "desc"))
					.andExpect(status().isOk());

			ArgumentCaptor<TaskQuery> captured = ArgumentCaptor.forClass(TaskQuery.class);
			verify(service).findAll(captured.capture());
			TaskQuery query = captured.getValue();

			assertThat(query.q()).isEqualTo("report");
			assertThat(query.status())
					.containsExactlyInAnyOrder(TaskStatus.TODO, TaskStatus.IN_PROGRESS);
			assertThat(query.priority())
					.containsExactly(TaskPriority.HIGH);
			assertThat(query.tagIds()).containsExactly(3L, 4L);
			assertThat(query.dueFrom())
					.isEqualTo(LocalDate.of(2026, 9, 1));
			assertThat(query.overdue()).isTrue();
			assertThat(query.hasDueDate()).isTrue();
			assertThat(query.page()).isEqualTo(2);
			assertThat(query.size()).isEqualTo(25);
			assertThat(query.sort()).isEqualTo("priority");
			assertThat(query.direction()).isEqualTo("desc");
		}

		@Test
		@DisplayName("answers 400 with the valid options when the sort field is unknown")
		void rejectsUnknownSort() throws Exception {
			mockMvc.perform(get("/api/tasks").param("sort", "owner.email"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.status").value(400))
					.andExpect(jsonPath("$.message").value(containsString("dueDate")))
					.andExpect(jsonPath("$.path").value("/api/tasks"));

			verify(service, never()).findAll(any());
		}

		@Test
		@DisplayName("answers 400 when an enum parameter is not a known constant")
		void rejectsUnknownEnumValue() throws Exception {
			mockMvc.perform(get("/api/tasks").param("status", "SOMEDAY"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.status").value(400));
		}
	}

	// -------------------------------------------------------------------- board

	@Test
	@DisplayName("GET /api/tasks/board returns every column with its label")
	void board() throws Exception {
		when(service.board()).thenReturn(new BoardResponse(List.of(
				BoardColumn.of(TaskStatus.TODO, List.of()),
				BoardColumn.of(TaskStatus.IN_PROGRESS, List.of(sample())),
				BoardColumn.of(TaskStatus.DONE, List.of()))));

		mockMvc.perform(get("/api/tasks/board"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.columns.length()").value(3))
				.andExpect(jsonPath("$.columns[0].status").value("TODO"))
				.andExpect(jsonPath("$.columns[0].label").value("To do"))
				.andExpect(jsonPath("$.columns[0].total").value(0))
				.andExpect(jsonPath("$.columns[1].tasks[0].title").value("Write the report"));
	}

	// -------------------------------------------------------------------- stats

	@Test
	@DisplayName("GET /api/tasks/stats defaults the trend window")
	void statsUsesDefaultWindow() throws Exception {
		when(service.stats(anyInt())).thenReturn(null);

		mockMvc.perform(get("/api/tasks/stats")).andExpect(status().isOk());

		verify(service).stats(14);
	}

	@Test
	@DisplayName("GET /api/tasks/stats passes an explicit window through")
	void statsAcceptsWindow() throws Exception {
		when(service.stats(anyInt())).thenReturn(null);

		mockMvc.perform(get("/api/tasks/stats").param("days", "30")).andExpect(status().isOk());

		verify(service).stats(30);
	}

	// --------------------------------------------------------------- single get

	@Test
	@DisplayName("GET /api/tasks/{id} returns the task")
	void findById() throws Exception {
		when(service.findById(1L)).thenReturn(sample());

		mockMvc.perform(get("/api/tasks/1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(1));
	}

	@Test
	@DisplayName("GET /api/tasks/{id} answers 404 in the shared error shape")
	void findByIdNotFound() throws Exception {
		when(service.findById(99L)).thenThrow(new TaskNotFoundException(99L));

		mockMvc.perform(get("/api/tasks/99"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.error").value("Not Found"))
				.andExpect(jsonPath("$.message").value("Task not found with id 99"))
				.andExpect(jsonPath("$.path").value("/api/tasks/99"))
				.andExpect(jsonPath("$.timestamp").exists());
	}

	@Test
	@DisplayName("GET /api/tasks/{id} answers 400 for a non-numeric id")
	void findByIdWithBadId() throws Exception {
		mockMvc.perform(get("/api/tasks/abc"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));
	}

	// ------------------------------------------------------------------- create

	@Nested
	@DisplayName("POST /api/tasks")
	class CreateTask {

		@Test
		@DisplayName("returns 201 with a Location header")
		void created() throws Exception {
			when(service.create(any(TaskRequest.class))).thenReturn(sample());

			mockMvc.perform(post("/api/tasks")
							.contentType(MediaType.APPLICATION_JSON)
							.content("""
									{"title":"Write the report","priority":"HIGH","tagIds":[3]}"""))
					.andExpect(status().isCreated())
					.andExpect(header().string("Location", "/api/tasks/1"))
					.andExpect(jsonPath("$.id").value(1));
		}

		@Test
		@DisplayName("deserialises the full request body onto the record")
		void bindsBody() throws Exception {
			when(service.create(any(TaskRequest.class))).thenReturn(sample());

			mockMvc.perform(post("/api/tasks")
							.contentType(MediaType.APPLICATION_JSON)
							.content("""
									{
									  "title": "Write the report",
									  "description": "Q3 numbers",
									  "dueDate": "2026-09-10",
									  "status": "IN_PROGRESS",
									  "priority": "URGENT",
									  "tagIds": [3, 4],
									  "completed": false
									}"""))
					.andExpect(status().isCreated());

			ArgumentCaptor<TaskRequest> captured = ArgumentCaptor.forClass(TaskRequest.class);
			verify(service).create(captured.capture());
			TaskRequest request = captured.getValue();

			assertThat(request.title()).isEqualTo("Write the report");
			assertThat(request.description()).isEqualTo("Q3 numbers");
			assertThat(request.dueDate())
					.isEqualTo(LocalDate.of(2026, 9, 10));
			assertThat(request.status()).isEqualTo(TaskStatus.IN_PROGRESS);
			assertThat(request.priority()).isEqualTo(TaskPriority.URGENT);
			assertThat(request.tagIds()).containsExactly(3L, 4L);
			assertThat(request.completed()).isFalse();
		}

		@Test
		@DisplayName("answers 400 with a per-field message when the title is missing")
		void rejectsMissingTitle() throws Exception {
			mockMvc.perform(post("/api/tasks")
							.contentType(MediaType.APPLICATION_JSON)
							.content("{}"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.status").value(400))
					.andExpect(jsonPath("$.fieldErrors.title").value("Title is required"));

			verify(service, never()).create(any());
		}

		@Test
		@DisplayName("answers 400 when the title is blank")
		void rejectsBlankTitle() throws Exception {
			mockMvc.perform(post("/api/tasks")
							.contentType(MediaType.APPLICATION_JSON)
							.content("""
									{"title":"   "}"""))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.fieldErrors.title").exists());
		}

		@Test
		@DisplayName("answers 400 for a malformed body")
		void rejectsMalformedJson() throws Exception {
			mockMvc.perform(post("/api/tasks")
							.contentType(MediaType.APPLICATION_JSON)
							.content("{not json"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.message").value("Malformed JSON request body"));
		}
	}

	// ------------------------------------------------------------------- update

	@Test
	@DisplayName("PUT /api/tasks/{id} returns the updated task")
	void update() throws Exception {
		when(service.update(eq(1L), any(TaskRequest.class))).thenReturn(sample());

		mockMvc.perform(put("/api/tasks/1")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"title":"Renamed"}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(1));
	}

	@Test
	@DisplayName("PATCH /api/tasks/{id}/complete returns the toggled task")
	void toggleComplete() throws Exception {
		when(service.toggleComplete(1L)).thenReturn(sample());

		mockMvc.perform(patch("/api/tasks/1/complete"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(1));
	}

	// --------------------------------------------------------------------- move

	@Nested
	@DisplayName("PATCH /api/tasks/{id}/move")
	class MoveTask {

		@Test
		@DisplayName("binds the target column and index")
		void movesTask() throws Exception {
			when(service.move(eq(1L), any(TaskMoveRequest.class))).thenReturn(sample());

			mockMvc.perform(patch("/api/tasks/1/move")
							.contentType(MediaType.APPLICATION_JSON)
							.content("""
									{"status":"DONE","position":0}"""))
					.andExpect(status().isOk());

			ArgumentCaptor<TaskMoveRequest> captured = ArgumentCaptor.forClass(TaskMoveRequest.class);
			verify(service).move(eq(1L), captured.capture());
			assertThat(captured.getValue().status())
					.isEqualTo(TaskStatus.DONE);
			assertThat(captured.getValue().position()).isZero();
		}

		@Test
		@DisplayName("accepts an omitted position, which means append")
		void positionIsOptional() throws Exception {
			when(service.move(eq(1L), any(TaskMoveRequest.class))).thenReturn(sample());

			mockMvc.perform(patch("/api/tasks/1/move")
							.contentType(MediaType.APPLICATION_JSON)
							.content("""
									{"status":"DONE"}"""))
					.andExpect(status().isOk());
		}

		@Test
		@DisplayName("answers 400 when the target column is missing")
		void rejectsMissingStatus() throws Exception {
			mockMvc.perform(patch("/api/tasks/1/move")
							.contentType(MediaType.APPLICATION_JSON)
							.content("{}"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.fieldErrors.status").value("Status is required"));
		}

		@Test
		@DisplayName("answers 400 for a negative position")
		void rejectsNegativePosition() throws Exception {
			mockMvc.perform(patch("/api/tasks/1/move")
							.contentType(MediaType.APPLICATION_JSON)
							.content("""
									{"status":"DONE","position":-1}"""))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.fieldErrors.position").exists());
		}
	}

	// ------------------------------------------------------------------- delete

	@Test
	@DisplayName("DELETE /api/tasks/{id} returns 204 with no body")
	void deleteTask() throws Exception {
		mockMvc.perform(delete("/api/tasks/1"))
				.andExpect(status().isNoContent());

		verify(service).delete(1L);
	}

	@Test
	@DisplayName("DELETE /api/tasks/{id} answers 404 for an unknown id")
	void deleteUnknown() throws Exception {
		doThrow(new TaskNotFoundException(99L)).when(service).delete(99L);

		mockMvc.perform(delete("/api/tasks/99"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("Task not found with id 99"));
	}

	@Test
	@DisplayName("answers 405 when a known path is called with the wrong verb")
	void wrongVerb() throws Exception {
		mockMvc.perform(post("/api/tasks/1")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.status").value(405));
	}
}
