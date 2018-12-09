package fr.sorbonne_u.datacenter.hardware.processors;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import fr.sorbonne_u.components.AbstractComponent;
import fr.sorbonne_u.datacenter.TimeManagement;
import fr.sorbonne_u.datacenter.software.applicationvm.interfaces.TaskI;

/**
 * The class <code>Core</code> simulates a core in a processor that can execute
 * tasks with a number of instructions known a priori and which can operate at
 * different admissible frequencies.
 *
 * <p>
 * <strong>Description</strong>
 * </p>
 * 
 * The core has a set of admissible frequencies and for each of these a number
 * of instructions that it can execute per second at this frequency. The
 * execution of tasks uses a discrete event simulation scheme with three events:
 * 
 * <ol>
 * <li>start task: the core must be idle, and then it switches to busy and plan
 * the end of the task after the time required to execute the number of
 * instructions in the task;</li>
 * <li>end task: the core must be busy, and then it switches back to idle;</li>
 * <li>set new frequency: changes the current frequency of the core to some new
 * admissible value, also replans the end of the task if the core is busy and
 * the delay until the end of the current task is greater than a predefined
 * threshold.</li>
 * </ol>
 * 
 * A core is owned by a processor component, which threads will be used to
 * execute the core's methods. Callbacks to the processor of accesses to its
 * instance variables assume that no synchronization is required.
 * 
 * <p>
 * <strong>Invariant</strong>
 * </p>
 * 
 * <pre>
 * invariant		owner != null and admissibleFrequencies != null
 * invariant		admissibleFrequencies.contains(defaultFrequency)
 * invariant		for all i in admissibleFrequencies, i &gt; 0
 * invariant		processingPower != null
 * invariant		for all i in processingPower.values(), i &gt; 0
 * </pre>
 * 
 * <p>
 * Created on : January 15, 2015
 * </p>
 * 
 * @author <a href="mailto:Jacques.Malenfant@lip6.fr">Jacques Malenfant</a>
 */
public class Core {
	// ------------------------------------------------------------------------
	// Constants
	// ------------------------------------------------------------------------

	/**
	 * shortest delay (in ms) before task termination to authorize a replanning of
	 * its termination event after changing the frequency.
	 */
	private static int SHORTEST_DELAY_FOR_REPLANNING = 20;

	// ------------------------------------------------------------------------
	// Instance variables
	// ------------------------------------------------------------------------

	/** owner processor. */
	protected final Processor owner;
	/** number of the core in the processor. */
	protected final int coreNo;
	/** possible frequencies in MHz. */
	private final Set<Integer> admissibleFrequencies;
	/** Mips for the different possible frequencies. */
	protected final HashMap<Integer, Integer> processingPower;
	/** current frequency of the core. */
	private int currentFrequency;
	/** true if the core is currently idle, false otherwise. */
	private boolean idle;
	/** task currently running on the core, if any, null otherwise. */
	private TaskI currentTask;
	/** starting time or last restarting time of the current task. */
	private long lastTaskReStartTime;
	/** projected time of ending of the current task. */
	private long currentTaskProjectedTermination;
	/**
	 * a future giving a handle on the end of task processing currently scheduled,
	 * allowing to cancel it when a replanning of the end if necessary.
	 */
	private Future<?> currentTaskEndFuture;

	// ------------------------------------------------------------------------
	// Constructors
	// ------------------------------------------------------------------------

	/**
	 * create a core object.
	 * 
	 * <p>
	 * <strong>Contract</strong>
	 * </p>
	 * 
	 * <pre>
	 * pre	owner != null
	 * pre	coreNo &gt;= 0
	 * pre	admissibleFrequencies != null and for all i in admissibleFrequencies, i &gt;= 0
	 * pre	processingPower != null and for all i in processingPower.values(), i &gt;= 0
	 * pre	for all i in admissibleFrequencies, processingPower.containsKey(i)
	 * pre	admissibleFrequencies.contains(defaultFrequency)
	 * post	true			// no postcondition.
	 * </pre>
	 *
	 * @param owner                 owner processor.
	 * @param coreNo                number of the core.
	 * @param admissibleFrequencies possible frequencies in MHz.
	 * @param processingPower       Mips for the different possible frequencies.
	 * @param defaultFrequency      default frequency at which the cores run.
	 */
	public Core(Processor owner, int coreNo, Set<Integer> admissibleFrequencies, Map<Integer, Integer> processingPower,
			int defaultFrequency) {
		super();

		// Invariant checking
		assert owner != null;
		assert coreNo >= 0 && coreNo < owner.cores.length;
		assert admissibleFrequencies != null;
		assert processingPower != null;
		boolean positiveAdmissibleFrequencies = true;
		boolean positiveProcessingPower = true;
		boolean frequenciesProcessingPowerDefined = true;
		for (Integer af : admissibleFrequencies) {
			positiveAdmissibleFrequencies = positiveAdmissibleFrequencies && af >= 0;
			frequenciesProcessingPowerDefined = frequenciesProcessingPowerDefined && processingPower.containsKey(af);
			if (frequenciesProcessingPowerDefined) {
				positiveProcessingPower = positiveProcessingPower && processingPower.get(af) >= 0;
			}
		}
		assert positiveAdmissibleFrequencies;
		assert frequenciesProcessingPowerDefined;
		assert positiveProcessingPower;
		assert admissibleFrequencies.contains(defaultFrequency);

		// Setting the internal representation of the core object.
		this.owner = owner;
		this.coreNo = coreNo;
		this.admissibleFrequencies = new HashSet<>(admissibleFrequencies.size());
		this.admissibleFrequencies.addAll(admissibleFrequencies);
		this.processingPower = new HashMap<>(this.admissibleFrequencies.size());
		for (int f : processingPower.keySet()) {
			this.processingPower.put(f, processingPower.get(f));
		}
		/* default frequency at which the cores run. */

		this.currentFrequency = defaultFrequency;
		this.idle = true;
		this.currentTask = null;
		this.lastTaskReStartTime = -1;
		this.currentTaskProjectedTermination = -1;
		this.currentTaskEndFuture = null;

		// Debugging mode information
		if (Processor.DEBUG) {
			System.out.println("core " + coreNo + "/" + (owner.cores.length - 1) + " created.");
		}
	}

	// ------------------------------------------------------------------------
	// Methods
	// ------------------------------------------------------------------------

	/**
	 * return true if the core is currently idle, and false otherwise.
	 * 
	 * <p>
	 * <strong>Contract</strong>
	 * </p>
	 * 
	 * <pre>
	 * pre	true			// no precondition.
	 * post	true			// no postcondition.
	 * </pre>
	 *
	 * @return true if the core is currently idle, and false otherwise.
	 */
	boolean isIdle() {
		return this.idle;
	}

	/**
	 * return the current frequency of the core.
	 * 
	 * <p>
	 * <strong>Contract</strong>
	 * </p>
	 * 
	 * <pre>
	 * pre	true			// no precondition.
	 * post	return &gt; 0
	 * </pre>
	 *
	 * @return the current frequency of the core.
	 */
	int getCurrentFrequency() {
		return this.currentFrequency;
	}

	/**
	 * modify the current frequency of the core, and therefore modify the duration
	 * of the task currently executing if any, and if the delay until the end of the
	 * task is greater than the shortest delay for replanning defined above.
	 * 
	 * <p>
	 * <strong>Contract</strong>
	 * </p>
	 * 
	 * <pre>
	 * pre	newFrequency != this.currentFrequency
	 * pre	this.admissibleFrequencies.contains(newFrequency)
	 * post	true			// no postcondition.
	 * </pre>
	 *
	 * @param newFrequency new frequency of the core.
	 */
	public void setFrequency(int newFrequency) throws Exception {
		assert newFrequency != this.currentFrequency;
		assert this.admissibleFrequencies.contains(newFrequency);

		if (Processor.DEBUG) {
			this.owner.logMessage("Core>>setCoreFrequency(" + newFrequency + ")");
		}

		boolean raising = (newFrequency > this.currentFrequency);
		int oldFrequency = this.currentFrequency;
		this.currentFrequency = newFrequency;

		if (Processor.DEBUG) {
			this.owner.logMessage("Core>>setCoreFrequency\n" + "    oldFrequency = " + oldFrequency + "\n"
					+ "    raising      = " + raising + "\n" + "    isIdle       = " + this.isIdle());
		}

		if (!this.isIdle()) {
			assert this.currentTask != null;
			// recompute an ending time when changing the frequency and
			// a task is running.
			long currentTime = TimeManagement.currentTime();
			long elapsedTime = currentTime - this.lastTaskReStartTime;
			long executedInstructions = this.computeNumberOfInstructionsForTime(elapsedTime, oldFrequency);

			if (Processor.DEBUG) {
				this.owner.logMessage("Core>>setCoreFrequency 4\n" + "    numberOfInstructions = "
						+ this.currentTask.getRequest().getPredictedNumberOfInstructions() + "\n"
						+ "    executedInstructions = " + executedInstructions);
			}

			long remainingInstructions = this.currentTask.getRequest().getPredictedNumberOfInstructions()
					- executedInstructions;
			long remainingTime = this.computeCurrentProcessingTime(remainingInstructions);

			if (Processor.DEBUG) {
				this.owner.logMessage("Core>>setCoreFrequency\n" + "    shortest delay = "
						+ Core.SHORTEST_DELAY_FOR_REPLANNING + "\n" + "    remainingTime  = " + remainingTime);
			}

			if ((raising && remainingTime > Core.SHORTEST_DELAY_FOR_REPLANNING) || (!raising
					&& this.currentTaskProjectedTermination - currentTime > Core.SHORTEST_DELAY_FOR_REPLANNING)) {
				// cancel the currently scheduled end of task processing
				this.currentTaskEndFuture.cancel(false);
				this.lastTaskReStartTime = currentTime;
				this.currentTaskProjectedTermination = this.lastTaskReStartTime + remainingTime;

				if (Processor.DEBUG) {
					this.owner.logMessage("core " + this.coreNo + " replans task end in " + remainingTime
							+ " milliseconds (termination at " + this.currentTaskProjectedTermination + ").");
				}

				// reschedule an end of current task storing the future if
				// another replanning occurs.
				this.currentTaskEndFuture = this.owner.scheduleTask(new AbstractComponent.AbstractTask() {
					@Override
					public void run() {
						try {
							((Processor) this.getOwner()).cores[coreNo].endCurrentTask();
						} catch (Exception e) {
							throw new RuntimeException(e);
						}
					}
				}, TimeManagement.acceleratedDelay(remainingTime), TimeUnit.MILLISECONDS);
			}
		}
	}

	/**
	 * end the current task, leaving the core idle.
	 * 
	 * <p>
	 * <strong>Contract</strong>
	 * </p>
	 * 
	 * <pre>
	 * pre	!this.isIdle() and this.currentTask != null and this.currentTaskEndFuture != null
	 * post	this.isIdle() and this.currentTask == null and this.currentTaskEndFuture == null
	 * </pre>
	 *
	 *
	 */
	private void endCurrentTask() throws Exception {
		assert !this.isIdle();
		assert this.currentTask != null;
		assert this.currentTaskEndFuture != null;

		if (Processor.DEBUG) {
			this.owner.logMessage("core " + this.coreNo + " of " + this.owner.processorURI + " ends the task "
					+ this.currentTask.getTaskURI() + ".");
		}
		this.idle = true;
		TaskI oldTask = this.currentTask;
		this.currentTask = null;
		this.currentTaskProjectedTermination = -1;
		this.currentTaskEndFuture = null;
		this.owner.endOfTask(oldTask);
	}

	/**
	 * simulate the beginning of the execution of a task on the core by computing
	 * the expected time required to execute the number of instructions of the task
	 * and plan the end of the task after that time by scheduling the execution of
	 * the.
	 * 
	 * <p>
	 * <strong>Contract</strong>
	 * </p>
	 * 
	 * <pre>
	 * pre	task != null and task.getNumberOfInstructions() &gt; 0
	 * post	true			// no postcondition.
	 * </pre>
	 *
	 * @param task task to be started.
	 */
	void startTask(TaskI task) {
		assert this.isIdle();

		this.idle = false;
		this.currentTask = task;
		long delay = this.computeCurrentProcessingTime(task.getRequest().getPredictedNumberOfInstructions());

		if (Processor.DEBUG) {
			this.owner.logMessage(
					"core " + this.coreNo + " of " + this.owner.processorURI + " starts a task for " + delay + " ms.");
		}

		this.lastTaskReStartTime = TimeManagement.currentTime();
		this.currentTaskProjectedTermination = this.lastTaskReStartTime + delay;

		if (Processor.DEBUG) {
			this.owner.logMessage("core " + this.coreNo + " starts task, scheduling end in " + delay
					+ " milliseconds (termination at " + this.currentTaskProjectedTermination + ").");
		}

		// schedule the end of current task processing, storing the future to
		// be able to cancel it when a change in the frequency of the core
		// occurs.
		this.currentTaskEndFuture = this.owner.scheduleTask(new AbstractComponent.AbstractTask() {
			@Override
			public void run() {
				try {
					((Processor) this.getOwner()).cores[coreNo].endCurrentTask();
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		}, TimeManagement.acceleratedDelay(delay), TimeUnit.MILLISECONDS);
	}

	/**
	 * compute the number of milliseconds required to execute a given number of
	 * instructions at the current frequency of the core.
	 * 
	 * <p>
	 * <strong>Contract</strong>
	 * </p>
	 * 
	 * <pre>
	 * pre	numberOfInstructions &gt; 0
	 * post	return &gt; 0
	 * </pre>
	 *
	 * @param numberOfInstructions to be executed
	 * @return number of milliseconds currently required to execute the instructions
	 */
	private long computeCurrentProcessingTime(long numberOfInstructions) {
		assert numberOfInstructions > 0;

		int ret = (int) (numberOfInstructions / this.owner.processingPower.get(this.currentFrequency));
		return ret > 0 ? ret : 1;
	}

	/**
	 * compute the number of instructions that the core can execute in some given
	 * time given in milliseconds for some frequency of the core.
	 * 
	 * <p>
	 * <strong>Contract</strong>
	 * </p>
	 * 
	 * <pre>
	 * pre	time &gt; 0 and this.admissibleFrequency(frequency)
	 * post	return &gt;= 0
	 * </pre>
	 *
	 * @param time      time given to execute (in milliseconds).
	 * @param frequency frequency at which the core would execute (in MHz).
	 * @return number of instructions that can be executed.
	 */
	private long computeNumberOfInstructionsForTime(long time, int frequency) {
		assert time > 0 && this.admissibleFrequencies.contains(frequency);

		return time * this.owner.processingPower.get(frequency);
	}
}
