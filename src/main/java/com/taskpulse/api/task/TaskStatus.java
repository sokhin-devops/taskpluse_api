package com.taskpulse.api.task;

/**
 * Where a task sits in the kanban workflow.
 *
 * <p>This supersedes the original {@code completed} boolean, which is kept alongside it as
 * a mirror of {@code status == DONE} so the {@code PATCH /api/tasks/{id}/complete}
 * endpoint and any existing row keep their meaning. {@link Task} maintains that mirror;
 * nothing else should write it.</p>
 *
 * <p>Each constant also carries a {@code weight} giving its place in the workflow. The enum
 * is persisted as a string for readability, so the database would otherwise order it
 * alphabetically — DONE, IN_PROGRESS, TODO — which is the workflow very nearly backwards.
 * {@link Task} stores the weight in its own column and every "by status" ordering uses
 * that.</p>
 */
public enum TaskStatus {

	TODO("To do", 1),
	IN_PROGRESS("In progress", 2),
	DONE("Done", 3);

	private final String label;
	private final int weight;

	TaskStatus(String label, int weight) {
		this.label = label;
		this.weight = weight;
	}

	public String getLabel() {
		return label;
	}

	/** @return this status's place in the workflow, ascending from {@link #TODO} */
	public int getWeight() {
		return weight;
	}

	/** @return whether a task in this status counts as finished */
	public boolean isDone() {
		return this == DONE;
	}
}
