package fr.sorbonne_u.sylalexcenter.application.ports;

import fr.sorbonne_u.components.ComponentI;
import fr.sorbonne_u.components.ports.AbstractOutboundPort;
import fr.sorbonne_u.sylalexcenter.application.interfaces.ApplicationSubmissionI;

public class ApplicationSubmissionOutboundPort extends AbstractOutboundPort implements ApplicationSubmissionI {

	private static final long serialVersionUID = 1L;

	public ApplicationSubmissionOutboundPort (ComponentI owner) throws Exception {
		super(ApplicationSubmissionI.class, owner);
	}
	
	public ApplicationSubmissionOutboundPort (String uri, ComponentI owner)
			throws Exception {
		super(uri, ApplicationSubmissionI.class, owner);
	}

	@Override
	public void submitApplicationAndNotify(String appUri, int numCores) throws Exception {
		
		((ApplicationSubmissionI)this.connector).submitApplicationAndNotify(appUri, numCores);
		
	}

}
