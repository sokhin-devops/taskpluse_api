package com.taskpulse.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.taskpulse.api.tag.TagService;
import com.taskpulse.api.task.TaskPriority;
import com.taskpulse.api.task.TaskRepository;
import com.taskpulse.api.task.TaskStatus;
import com.taskpulse.api.user.User;
import com.taskpulse.api.user.UserRepository;

/**
 * Brings an existing database up to date with the current entity model on start-up.
 *
 * <p>The schema itself is handled by {@code ddl-auto=update}, but two things a DDL tool
 * cannot do are needed as well:</p>
 * <ol>
 *   <li><strong>Reconcile the derived completion columns.</strong> Tasks written before the
 *       kanban workflow existed get the {@code TODO} default when {@code status} is added,
 *       even where {@code completed} was already true. Both repairs are conditional on the
 *       columns disagreeing, so running them on every start is a no-op once done.</li>
 *   <li><strong>Adopt owner-less tasks.</strong> {@code owner_id} has no sensible default, so
 *       rows that predate accounts would otherwise be invisible to every signed-in user.</li>
 * </ol>
 *
 * <p>Seeding the demo account is development convenience and can be switched off with
 * {@code app.seed.demo-user=false}. It only ever runs when the accounts table is empty, so
 * it cannot resurrect an account someone deliberately deleted.</p>
 */
@Component
public class TaskDataInitializer implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(TaskDataInitializer.class);

	private final TaskRepository tasks;
	private final UserRepository users;
	private final TagService tagService;
	private final PasswordEncoder passwordEncoder;

	private final boolean seedDemoUser;
	private final String demoEmail;
	private final String demoPassword;
	private final String demoName;

	public TaskDataInitializer(TaskRepository tasks, UserRepository users, TagService tagService,
			PasswordEncoder passwordEncoder,
			@Value("${app.seed.demo-user:true}") boolean seedDemoUser,
			@Value("${app.seed.demo-email:demo@taskpulse.dev}") String demoEmail,
			@Value("${app.seed.demo-password:taskpulse123}") String demoPassword,
			@Value("${app.seed.demo-name:Demo User}") String demoName) {
		this.tasks = tasks;
		this.users = users;
		this.tagService = tagService;
		this.passwordEncoder = passwordEncoder;
		this.seedDemoUser = seedDemoUser;
		this.demoEmail = demoEmail;
		this.demoPassword = demoPassword;
		this.demoName = demoName;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		reconcileCompletionColumns();
		reconcileSortWeights();
		adoptOwnerlessTasks();
	}

	private void reconcileCompletionColumns() {
		int toDone = tasks.markCompletedRowsAsDone();
		int toCompleted = tasks.markDoneRowsAsCompleted();
		if (toDone + toCompleted > 0) {
			log.info("Reconciled task completion columns: {} row(s) moved to DONE, {} row(s) marked completed",
					toDone, toCompleted);
		}
	}

	/**
	 * Realigns the numeric sort keys with the enum columns they mirror.
	 *
	 * <p>Rows added before {@code status_weight} and {@code priority_weight} existed took the
	 * SQL default for those columns, which would sort a DONE task as though it were still to
	 * do. Each statement only touches rows that actually disagree.</p>
	 */
	private void reconcileSortWeights() {
		int repaired = 0;
		for (TaskStatus status : TaskStatus.values()) {
			repaired += tasks.syncStatusWeight(status, status.getWeight());
		}
		for (TaskPriority priority : TaskPriority.values()) {
			repaired += tasks.syncPriorityWeight(priority, priority.getWeight());
		}
		if (repaired > 0) {
			log.info("Realigned sort weights on {} task row(s)", repaired);
		}
	}

	/**
	 * Hands tasks that have no owner to an account, creating the demo account if the
	 * database has none at all.
	 */
	private void adoptOwnerlessTasks() {
		long orphans = tasks.countByOwnerIsNull();
		boolean noAccountsYet = users.count() == 0;

		if (orphans == 0 && !noAccountsYet) {
			return;
		}
		if (noAccountsYet && !seedDemoUser) {
			if (orphans > 0) {
				log.warn("{} task(s) have no owner and demo seeding is disabled. They will stay hidden "
						+ "until an account exists; register one, then restart, or set app.seed.demo-user=true.",
						orphans);
			}
			return;
		}

		User owner = noAccountsYet ? createDemoUser() : users.findAll().get(0);
		if (orphans > 0) {
			int adopted = tasks.assignMissingOwner(owner);
			log.info("Assigned {} pre-existing task(s) to {}", adopted, owner.getEmail());
		}
	}

	private User createDemoUser() {
		User demo = users.save(new User(demoEmail.toLowerCase(), demoName, passwordEncoder.encode(demoPassword)));
		tagService.createStarterTags(demo);
		log.warn("""
				Seeded the demo account {} with password '{}'.
				This exists so a fresh checkout is usable immediately. Set app.seed.demo-user=false \
				before running TaskPulse anywhere other than your own machine.""",
				demoEmail, demoPassword);
		return demo;
	}
}
