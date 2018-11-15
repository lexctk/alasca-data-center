package fr.sorbonne_u.sylalexcenter.admissioncontroller;

import java.util.ArrayList;

import fr.sorbonne_u.components.AbstractComponent;
import fr.sorbonne_u.components.connectors.DataConnector;
import fr.sorbonne_u.components.cvm.AbstractCVM;
import fr.sorbonne_u.components.exceptions.ComponentShutdownException;
import fr.sorbonne_u.components.exceptions.ComponentStartException;
import fr.sorbonne_u.datacenter.connectors.ControlledDataConnector;
import fr.sorbonne_u.datacenter.hardware.computers.connectors.ComputerServicesConnector;
import fr.sorbonne_u.datacenter.hardware.computers.interfaces.ComputerDynamicStateI;
import fr.sorbonne_u.datacenter.hardware.computers.interfaces.ComputerServicesI;
import fr.sorbonne_u.datacenter.hardware.computers.interfaces.ComputerStateDataConsumerI;
import fr.sorbonne_u.datacenter.hardware.computers.interfaces.ComputerStaticStateDataI;
import fr.sorbonne_u.datacenter.hardware.computers.interfaces.ComputerStaticStateI;
import fr.sorbonne_u.datacenter.hardware.computers.ports.ComputerDynamicStateDataOutboundPort;
import fr.sorbonne_u.datacenter.hardware.computers.ports.ComputerServicesOutboundPort;
import fr.sorbonne_u.datacenter.hardware.computers.ports.ComputerStaticStateDataOutboundPort;
import fr.sorbonne_u.datacenter.interfaces.ControlledDataOfferedI;
import fr.sorbonne_u.datacenter.software.applicationvm.ApplicationVM;
import fr.sorbonne_u.datacenter.software.interfaces.RequestI;
import fr.sorbonne_u.datacenter.software.interfaces.RequestNotificationHandlerI;
import fr.sorbonne_u.datacenter.software.interfaces.RequestSubmissionHandlerI;
import fr.sorbonne_u.sylalexcenter.application.interfaces.ApplicationNotificationI;
import fr.sorbonne_u.sylalexcenter.application.interfaces.ApplicationSubmissionI;
import fr.sorbonne_u.sylalexcenter.application.ports.ApplicationManagementInboundPort;
import fr.sorbonne_u.sylalexcenter.application.ports.ApplicationNotificationInboundPort;
import fr.sorbonne_u.sylalexcenter.application.ports.ApplicationSubmissionInboundPort;
import fr.sorbonne_u.sylalexcenter.requestdispatcher.RequestDispatcher;

/**
 * The class <code>AdmissionController</code> implements an admission controller.
 * 
 * <p>
 * <strong>Description</strong>
 * </p>
 * 
 * The admission controller component will receive requests from an application, check
 * available resources, and if possible, deploy application vm and request dispatcher.
 *
 */

public class AdmissionController extends AbstractComponent implements ComputerStateDataConsumerI, RequestSubmissionHandlerI, RequestNotificationHandlerI {
	
	public static int DEBUG_LEVEL = 2;
	
	protected String acURI;
	
	protected ComputerServicesOutboundPort csop;
	protected ComputerStaticStateDataOutboundPort cssdop;
	protected ComputerDynamicStateDataOutboundPort cdsdop;
	
	protected ApplicationManagementInboundPort amip;
	protected ApplicationSubmissionInboundPort asip;
	protected ApplicationNotificationInboundPort anip;
	
	protected String computerServicesInboundPortURI;
	protected String computerStaticStateDataInboundPortURI;
	protected String computerDynamicStateDataInboundPortURI;

	public AdmissionController (
			String acURI, 
			String computersURI, //single computer for now
			String computerServicesInboundPortURI,
			String computerStaticStateDataInboundPortURI,
			String computerDynamicStateDataInboundPortURI,
			String applicationManagementInboundPortURI,
			String applicationSubmissionInboundPortURI, // request generator 
			String applicationNotificationInboundPortURI // request generator 
		) throws Exception {
		
		super(1, 1);
		
		assert acURI != null;
		assert computerServicesInboundPortURI != null;
		assert computerStaticStateDataInboundPortURI != null;
		assert computerDynamicStateDataInboundPortURI != null;
		assert applicationSubmissionInboundPortURI != null;
		assert applicationNotificationInboundPortURI != null;
		
		this.acURI = acURI;
		this.computerServicesInboundPortURI = computerServicesInboundPortURI;
		this.computerStaticStateDataInboundPortURI = computerStaticStateDataInboundPortURI;
		this.computerDynamicStateDataInboundPortURI = computerDynamicStateDataInboundPortURI;
		
		this.addRequiredInterface(ComputerServicesI.class);
		this.csop = new ComputerServicesOutboundPort (this);
		this.addPort(this.csop);
		this.csop.publishPort();			

		this.addRequiredInterface(ComputerStaticStateDataI.class);
		this.cssdop = new ComputerStaticStateDataOutboundPort (this, computersURI);
		this.addPort(this.cssdop);
		this.cssdop.publishPort();

		this.addRequiredInterface(ControlledDataOfferedI.ControlledPullI.class);
		this.cdsdop = new ComputerDynamicStateDataOutboundPort (this, computersURI);
		this.addPort(cdsdop);
		this.cdsdop.publishPort();
		
		this.addOfferedInterface(ApplicationSubmissionI.class);
		this.asip = new ApplicationSubmissionInboundPort(applicationSubmissionInboundPortURI, this);
		this.addPort(this.asip);
		this.asip.publishPort();

		this.addOfferedInterface(ApplicationNotificationI.class);
		this.anip = new ApplicationNotificationInboundPort(applicationNotificationInboundPortURI, this);
		this.addPort(this.anip);
		this.anip.publishPort();
	}

	// Component life-cycle
	// -------------------------------------------------------------------------
	@Override
	public void start() throws ComponentStartException {
		super.start();
		
		try {
			this.doPortConnection(this.csop.getPortURI(), computerServicesInboundPortURI,
					ComputerServicesConnector.class.getCanonicalName());
			this.doPortConnection(this.cssdop.getPortURI(), computerStaticStateDataInboundPortURI,
					DataConnector.class.getCanonicalName());
			this.doPortConnection(this.cdsdop.getPortURI(), computerDynamicStateDataInboundPortURI,
					ControlledDataConnector.class.getCanonicalName());
		} catch (Exception e) {
			throw new ComponentStartException(e);
		}
		
	}
	
	@Override
	public void execute() throws Exception {
		super.execute();
	}
	
	@Override
	public void finalise() throws Exception {

		if (this.csop.connected()) this.doPortDisconnection(this.csop.getPortURI());
		if (this.cssdop.connected()) this.doPortDisconnection(this.cssdop.getPortURI());
		if (this.cdsdop.connected()) this.doPortDisconnection(this.cdsdop.getPortURI());

		super.finalise();
	}
	
	@Override
	public void shutdown() throws ComponentShutdownException {

		try {
			if (this.csop.isPublished()) this.csop.unpublishPort();
			if (this.cssdop.isPublished()) this.cssdop.unpublishPort();
			if (this.cdsdop.isPublished()) this.cdsdop.unpublishPort();
			if (this.asip.isPublished()) this.asip.unpublishPort();
			if (this.anip.isPublished()) this.anip.unpublishPort();
			
		} catch (Exception e) {
			throw new ComponentShutdownException(e);
		}

		super.shutdown();
	}
	
	public void deploy() throws Exception {
		
		this.logMessage("AdmissionController starting deployment");
		
		// Deploy numAvm AVM
		// --------------------------------------------------------------------
		
		ArrayList<String> applicationVMManagementInboundPortURIList = new ArrayList<String>();
		ArrayList<String> applicationVMRequestSubmissionInboundPortURIList = new ArrayList<String>();
		ArrayList<String> applicationVMRequestNotificationInboundPortURIList = new ArrayList<String>();
		
		ApplicationVM applicationVM = null;
		
		ArrayList<String> vmURIList = new ArrayList<String>();
		int numAvm = 4;
		
		for (int i = 0; i < numAvm; i++) {
			vmURIList.add("avm" + i);
			applicationVMManagementInboundPortURIList.add("avmip" + i);
			applicationVMRequestSubmissionInboundPortURIList.add("avmrsip" + i);
			applicationVMRequestNotificationInboundPortURIList.add("avmrnip" + i);
		}

		for (int i = 0; i < numAvm; i++) {
			try {
				applicationVM = new ApplicationVM (
						vmURIList.get(i), 
						applicationVMManagementInboundPortURIList.get(i), 
						applicationVMRequestSubmissionInboundPortURIList.get(i), 
						applicationVMRequestNotificationInboundPortURIList.get(i)
				);
			} catch (Exception e) {
				e.printStackTrace();
			}
			applicationVM.toggleTracing();
			applicationVM.toggleLogging();
			
			AbstractCVM.getCVM().addDeployedComponent(applicationVM);
		}

		
		// Deploy the request dispatcher
		// --------------------------------------------------------------------
		
		RequestDispatcher requestDispatcher;
		
		String requestDispatcherManagementInboundPortURI = "rdmip";
		
		String rdURI = "rd0";
		
		requestDispatcher = new RequestDispatcher (
				rdURI, 
				requestDispatcherManagementInboundPortURI,
				this.asip.getPortURI(),
				this.anip.getPortURI(),
				vmURIList,
				applicationVMRequestSubmissionInboundPortURIList,
				applicationVMRequestNotificationInboundPortURIList
		);
		
		requestDispatcher.toggleTracing();
		requestDispatcher.toggleLogging();
		
		AbstractCVM.getCVM().addDeployedComponent(requestDispatcher);
		
		this.logMessage("AdmissionController deployment done");
	}

	@Override
	public void acceptComputerStaticData(String computerURI, ComputerStaticStateI staticState) throws Exception {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void acceptComputerDynamicData(String computerURI, ComputerDynamicStateI currentDynamicState)
			throws Exception {
		// TODO Auto-generated method stub
		
	}
	

	private boolean resourcesAvailable() {
		// TODO Auto-generated method stub
		return true;
	}
	
	
	@Override
	public void acceptRequestSubmission(RequestI r) throws Exception {
		acceptRequestSubmissionAndNotify(r);
	}


	@Override
	public void acceptRequestSubmissionAndNotify(RequestI r) throws Exception {
		assert r != null;
		
		if (resourcesAvailable ()) {
			if (AdmissionController.DEBUG_LEVEL == 2) {
				this.logMessage ("Admission controller " + this.acURI + " accepted application " + r.getRequestURI() +
						"and required notification of application execution progress");
			}
			deploy();
			
		} else {
			if (AdmissionController.DEBUG_LEVEL == 2) {
				this.logMessage ("Admission controller " + this.acURI + " rejected application " + r.getRequestURI() +
						"because there aren't enough resources");
			}
		}
	}


	@Override
	public void acceptRequestTerminationNotification(RequestI r) throws Exception {
		// TODO Auto-generated method stub
		
	}

}
