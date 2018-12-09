package fr.sorbonne_u.datacenter.hardware.processors.ports;

import fr.sorbonne_u.components.ComponentI;
import fr.sorbonne_u.components.cvm.AbstractCVM;
import fr.sorbonne_u.components.helpers.CVMDebugModes;
import fr.sorbonne_u.components.ports.AbstractOutboundPort;
import fr.sorbonne_u.datacenter.hardware.processors.interfaces.ProcessorServicesNotificationI;
import fr.sorbonne_u.datacenter.software.applicationvm.interfaces.TaskI;

/**
 * The class <code>ProcessorServicesNotificationOutboundPort</code> defines an
 * outbound port associated with the interface
 * <code>ProcessorServicesNotificationI</code>.
 *
 * <p>
 * <strong>Description</strong>
 * </p>
 * 
 * <p>
 * <strong>Invariant</strong>
 * </p>
 * 
 * <pre>
 * invariant	true
 * </pre>
 * 
 * <p>
 * Created on : April 24, 2015
 * </p>
 * 
 * @author <a href="mailto:Jacques.Malenfant@lip6.fr">Jacques Malenfant</a>
 */
public class ProcessorServicesNotificationOutboundPort extends AbstractOutboundPort
		implements ProcessorServicesNotificationI {
	private static final long serialVersionUID = 1L;

	// ------------------------------------------------------------------------
	// Constructors
	// ------------------------------------------------------------------------

	public ProcessorServicesNotificationOutboundPort(ComponentI owner) throws Exception {
		super(ProcessorServicesNotificationI.class, owner);
	}

	public ProcessorServicesNotificationOutboundPort(String uri, ComponentI owner) throws Exception {
		super(uri, ProcessorServicesNotificationI.class, owner);
	}

	// ------------------------------------------------------------------------
	// Methods
	// ------------------------------------------------------------------------

	/**
	 * @see fr.sorbonne_u.datacenter.hardware.processors.interfaces.ProcessorServicesNotificationI#notifyEndOfTask(fr.sorbonne_u.datacenter.software.applicationvm.interfaces.TaskI)
	 */
	@Override
	public void notifyEndOfTask(TaskI t) throws Exception {
		if (AbstractCVM.DEBUG_MODE.contains(CVMDebugModes.CALLING)) {
			System.out.println("ProcessorServicesNotificationOutboundPort>>notifyEndOfTask(" + t.getTaskURI() + ")");
		}

		((ProcessorServicesNotificationI) this.connector).notifyEndOfTask(t);
	}
}
