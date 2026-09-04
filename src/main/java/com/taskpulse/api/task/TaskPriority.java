package com.taskpulse.api.task;

/**
 * How urgent a task is.
 *
 * <p>Each constant carries a numeric {@code weight}. The enum is persisted as a string for
 * readability, which means the database would sort it alphabetically (HIGH, LOW, MEDIUM,
 * URGENT) — useless as a ranking. {@link Task} therefore also stores the weight in its own
 * column and "sort by priority" orders on that instead.</p>
 */
public enum TaskPriority {

	LOW("Low", 1),
	MEDIUM("Medium", 2),
	HIGH("High", 3),
	URGENT("Urgent", 4);

	private final String label;
	private final int weight;

	TaskPriority(String label, int weight) {
		this.label = label;
		this.weight = weight;
	}

	public String getLabel() {
		return label;
	}

	public int getWeight() {
		return weight;
	}
}
