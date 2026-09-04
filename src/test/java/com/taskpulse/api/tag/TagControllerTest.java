package com.taskpulse.api.tag;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.taskpulse.api.exception.TagNotFoundException;
import com.taskpulse.api.security.AppUserDetailsService;
import com.taskpulse.api.security.JwtService;
import com.taskpulse.api.tag.dto.TagRequest;
import com.taskpulse.api.tag.dto.TagResponse;

/**
 * Web-layer tests for {@link TagController}.
 */
@WebMvcTest(TagController.class)
@AutoConfigureMockMvc(addFilters = false)
class TagControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private TagService service;

	@MockitoBean
	private JwtService jwtService;

	@MockitoBean
	private AppUserDetailsService userDetailsService;

	private static TagResponse sample() {
		return new TagResponse(1L, "Work", "#6366f1", 7L);
	}

	@Test
	@DisplayName("GET /api/tags returns the tags with their usage counts")
	void list() throws Exception {
		when(service.findAll()).thenReturn(List.of(sample()));

		mockMvc.perform(get("/api/tags"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(1))
				.andExpect(jsonPath("$[0].name").value("Work"))
				.andExpect(jsonPath("$[0].color").value("#6366f1"))
				.andExpect(jsonPath("$[0].taskCount").value(7));
	}

	@Test
	@DisplayName("POST /api/tags returns 201 with a Location header")
	void create() throws Exception {
		when(service.create(any(TagRequest.class))).thenReturn(sample());

		mockMvc.perform(post("/api/tags")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Work","color":"#6366f1"}"""))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/api/tags/1"))
				.andExpect(jsonPath("$.name").value("Work"));
	}

	@Test
	@DisplayName("POST /api/tags accepts a request with no colour")
	void createWithoutColour() throws Exception {
		when(service.create(any(TagRequest.class))).thenReturn(sample());

		mockMvc.perform(post("/api/tags")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Work"}"""))
				.andExpect(status().isCreated());
	}

	@Test
	@DisplayName("POST /api/tags answers 400 for a missing name")
	void rejectsMissingName() throws Exception {
		mockMvc.perform(post("/api/tags")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.name").value("Name is required"));

		verify(service, never()).create(any());
	}

	@Test
	@DisplayName("POST /api/tags answers 400 for a colour that is not a hex value")
	void rejectsBadColour() throws Exception {
		mockMvc.perform(post("/api/tags")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Work","color":"indigo"}"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.color").exists());
	}

	@Test
	@DisplayName("POST /api/tags answers 400 when the name is already taken")
	void rejectsDuplicateName() throws Exception {
		when(service.create(any(TagRequest.class)))
				.thenThrow(new IllegalArgumentException("You already have a tag called 'Work'"));

		mockMvc.perform(post("/api/tags")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Work"}"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("You already have a tag called 'Work'"));
	}

	@Test
	@DisplayName("PUT /api/tags/{id} returns the updated tag")
	void update() throws Exception {
		when(service.update(eq(1L), any(TagRequest.class))).thenReturn(sample());

		mockMvc.perform(put("/api/tags/1")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Work","color":"#ef4444"}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(1));
	}

	@Test
	@DisplayName("PUT /api/tags/{id} answers 404 for a tag that is not yours")
	void updateUnknown() throws Exception {
		when(service.update(eq(99L), any(TagRequest.class))).thenThrow(new TagNotFoundException(99L));

		mockMvc.perform(put("/api/tags/99")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Work"}"""))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("Tag not found with id 99"));
	}

	@Test
	@DisplayName("DELETE /api/tags/{id} returns 204")
	void deleteTag() throws Exception {
		mockMvc.perform(delete("/api/tags/1")).andExpect(status().isNoContent());

		verify(service).delete(1L);
	}

	@Test
	@DisplayName("DELETE /api/tags/{id} answers 404 for a tag that is not yours")
	void deleteUnknown() throws Exception {
		doThrow(new TagNotFoundException(99L)).when(service).delete(99L);

		mockMvc.perform(delete("/api/tags/99"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404));
	}
}
