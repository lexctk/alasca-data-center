package fr.sorbonne_u.sylalexcenter.requestdispatcher;

import java.util.ArrayList;

import fr.sorbonne_u.components.AbstractComponent;
import fr.sorbonne_u.components.exceptions.ComponentShutdownException;
import fr.sorbonne_u.components.exceptions.ComponentStartException;
import fr.sorbonne_u.datacenter.software.interfaces.RequestI;
import fr.sorbonne_u.datacenter.software.interfaces.RequestNotificationHandlerI;
import fr.sorbonne_u.datacenter.software.interfaces.RequestNotificationI;
import fr.sorbonne_u.datacenter.software.interfaces.RequestSubmissionHandlerI;
import fr.sorbonne_u.datacenter.software.interfaces.RequestSubmissionI;
import fr.sorbonne_u.datacenter.software.ports.RequestNotificationInboundPort;
import fr.sorbonne_u.datacenter.software.ports.RequestNotificationOutboundPort;
import fr.sorbonne_u.datacenter.software.ports.RequestSubmissionInboundPort;
import fr.sorbonne_u.datacenter.software.ports.RequestSubmissionOutboundPort;
import fr.sorbonne_u.sylalexcenter.requestdispatcher.interfaces.RequestDispatcherManagementI;
import fr.sorbonne_u.sylalexcenter.requestdispatcher.ports.RequestDispatcherManagementInboundPort;

/**
 * The class <code>RequestDispatcher</code> implements a request dispatcher.
 * 
 * <p>
 * <strong>Description</strong>
 * </p>
 * 
 * The request dispatcher component will receive requests from the request
 * generator and forward them to an application's dedicated virtual machines.
 * 
 * When the request dispatcher receives a request, it goes through the list of
 * available application virtual machines (AVMs) and submits the request to the 
 * least recently used AVM.
 *
 *
 * Sorbonne University 2018-2019
 * @author Alexandra Tudor
 * @author Sylia Righi
 *
 */
public class RequestDispatcher extends AbstractComponent implements RequestDispatcherManagementI, RequestSubmissionHandlerI, RequestNotificationHandlerI {

	private static int DEBUG_LEVEL = 2;

	private String rdURI;
	
	// RequestDispatcher Ports
	// -------------------------------------------------------------------------
	private RequestDispatcherManagementInboundPort rdmip;

	private RequestSubmissionInboundPort rsip;
	private ArrayList<RequestSubmissionOutboundPort> rsopList;
	private ArrayList<RequestNotificationInboundPort> rnipList;
	private RequestNotificationOutboundPort rnop;
	
	// List of available avm
	private ArrayList<String> vmURIList;
	private ArrayList<Long> vmPriority;

	
	// Constructor
	// -------------------------------------------------------------------------
	public RequestDispatcher (
			String rdURI,
			ArrayList<String> vmURIList,
			String requestDispatcherManagementInboundPortURI,
			String requestDispatcherSubmissionInboundPortURI,
			ArrayList<String> requestDispatcherSubmissionOutboundPortURIList,
			ArrayList<String> requestDispatcherNotificationInboundPortURIList,
			String requestDispatcherNotificationOutboundPortURI
		) throws Exception {
		
		super(rdURI,1, 1);
		
		// preconditions check
		assert rdURI != null;
		assert requestDispatcherManagementInboundPortURI != null;
		assert requestDispatcherSubmissionInboundPortURI != null;
		assert vmURIList != null && vmURIList.size() > 0;
		assert requestDispatcherSubmissionOutboundPortURIList != null && requestDispatcherSubmissionOutboundPortURIList.size() > 0;
		assert requestDispatcherNotificationInboundPortURIList != null && requestDispatcherNotificationInboundPortURIList.size() > 0;
		assert requestDispatcherNotificationOutboundPortURI != null;

		// initialization
		this.rdURI = rdURI;
		this.vmURIList = new ArrayList<>(vmURIList);

		this.vmPriority = new ArrayList<>();
		for (int i = 0; i < this.vmURIList.size(); i++ ) {
			this.vmPriority.add(System.nanoTime());
		}	
		
		this.rdmip = new RequestDispatcherManagementInboundPort(requestDispatcherManagementInboundPortURI, this);
		this.addPort(rdmip);
		this.rdmip.publishPort();
		
		this.addOfferedInterface(RequestSubmissionI.class);
		this.rsip = new RequestSubmissionInboundPort(requestDispatcherSubmissionInboundPortURI, this);
		this.addPort(this.rsip);
		this.rsip.publishPort();
		
		this.addRequiredInterface(RequestNotificationI.class);
		this.rnop = new RequestNotificationOutboundPort(requestDispatcherNotificationOutboundPortURI, this);
		this.addPort(this.rnop);
		this.rnop.publishPort();
		
		this.rnipList = new ArrayList<>();
		this.rsopList = new ArrayList<>();
		
		for (int i = 0; i < vmURIList.size(); i++ ) {
			this.addOfferedInterface(RequestNotificationI.class);
			RequestNotificationInboundPort rnip = new RequestNotificationInboundPort(requestDispatcherNotificationInboundPortURIList.get(i), this);
			this.rnipList.add(rnip);
			this.addPort(rnip);
			this.rnipList.get(i).publishPort();
			
			this.addRequiredInterface(RequestSubmissionI.class);
			RequestSubmissionOutboundPort rsop = new RequestSubmissionOutboundPort(requestDispatcherSubmissionOutboundPortURIList.get(i), this);
			this.rsopList.add(rsop);
			this.addPort(rsop);
			this.rsopList.get(i).publishPort();
		}
		
		this.tracer.setRelativePosition(1, 0);
		
		// post-conditions check
		assert this.rsopList != null && this.rsopList.size() > 0;
		assert this.rnipList != null && this.rnipList.size() > 0;
	}

	// Component life-cycle
	// -------------------------------------------------------------------------
	@Override
	public void start() throws ComponentStartException {
		this.toggleTracing();
		this.toggleLogging();
		super.start();		
	}

	@Override
	public void finalise() throws Exception {
		for (int i = 0; i < this.vmURIList.size(); i++ ) {
			if (this.rsopList.get(i).connected()) this.doPortDisconnection(this.rsopList.get(i).getPortURI());
		}
		if (this.rnop.connected()) this.doPortDisconnection(this.rnop.getPortURI());

		super.finalise();
	}
	
	@Override
	public void shutdown() throws ComponentShutdownException {

		try {
			if (this.rdmip.isPublished()) this.rdmip.unpublishPort();
			if (this.rsip.isPublished()) this.rsip.unpublishPort();
			for (int i = 0; i < this.vmURIList.size(); i++ ) { 
				if (this.rsopList.get(i).isPublished()) this.rsopList.get(i).unpublishPort();
				if (this.rnipList.get(i).isPublished()) this.rnipList.get(i).unpublishPort();
			}
			if (this.rnop.isPublished()) this.rnop.unpublishPort();
			
		} catch (Exception e) {
			throw new ComponentShutdownException(e);
		}

		super.shutdown();
	}	
	
	private int leastUsedVM() {
		int index = -1;
		long time = System.nanoTime();

		for (int i = 0; i < this.vmURIList.size(); i++ ) { 
			if (this.vmPriority.get(i) < time) {
				index = i;
				time = this.vmPriority.get(i);
			}
		}
		return index;
	}
	
	// Component internal services
	// -------------------------------------------------------------------------	

	/**
	 * accept a request submission from request generator and send it to
	 * least recently used AVM
	 *
	 * @param r request that just terminated.
	 * @throws Exception <i>todo.</i>
	 */
	@Override
	public void acceptRequestSubmission(RequestI r) throws Exception {
		assert r != null;
		
		int vmIndex = leastUsedVM();
		if (vmIndex >= 0) {
			this.vmPriority.set(vmIndex, System.nanoTime());
			
			if (RequestDispatcher.DEBUG_LEVEL == 2) {
				this.logMessage ("Request dispatcher " + this.rdURI + " accepted submission request " + r.getRequestURI());
			}
			this.rsopList.get(vmIndex).submitRequest(r);
		} else {
			if (RequestDispatcher.DEBUG_LEVEL == 2) {
				this.logMessage ("ERROR: Request dispatcher " + this.rdURI + " refused submission request " + r.getRequestURI());
			}			
		}
	}

	/**
	 * accept a request submission from request generator, send it to the
	 * least recently used AVM and and require notifications of request execution progress.
	 *
	 * @param r request that just terminated.
	 * @throws Exception <i>todo.</i>
	 */
	@Override
	public void acceptRequestSubmissionAndNotify(RequestI r) throws Exception {
		assert r != null;
		int vmIndex = leastUsedVM();
		if (vmIndex >= 0) {
			this.vmPriority.set(vmIndex, System.nanoTime());
			
			if (RequestDispatcher.DEBUG_LEVEL == 2) {
				this.logMessage ("Request dispatcher " + this.rdURI + " accepted submission request " + r.getRequestURI() +
						" and required notification of request execution progress");
			}
			this.rsopList.get(vmIndex).submitRequestAndNotify(r);
			
		} else {
			if (RequestDispatcher.DEBUG_LEVEL == 2) {
				this.logMessage ("ERROR: Request dispatcher " + this.rdURI + " refused submission and notify request " + r.getRequestURI());
			}			
		}
	}

	/**
	 * notify request generator that a request was terminated
	 *
	 * @param r request that just terminated.
	 * @throws Exception <i>todo.</i>
	 */
	@Override
	public void acceptRequestTerminationNotification(RequestI r) throws Exception {
		assert r != null;
		this.rnop.notifyRequestTermination(r);
		
		if (RequestDispatcher.DEBUG_LEVEL == 2) {
			this.logMessage ("Request dispatcher " + this.rdURI + " notified request generator that request " + 
					r.getRequestURI() + " has terminated");
		}
	}	

}
