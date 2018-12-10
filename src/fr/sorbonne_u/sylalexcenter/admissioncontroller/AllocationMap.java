package fr.sorbonne_u.sylalexcenter.admissioncontroller;

import fr.sorbonne_u.datacenter.hardware.computers.Computer.AllocatedCore;
import fr.sorbonne_u.datacenter.hardware.computers.ports.ComputerServicesOutboundPort;

public class AllocationMap {

	private String computerURI;
	private ComputerServicesOutboundPort csop;
	private Integer numberOfCoresPerAVM;
	private AllocatedCore[] allocatedCores;

	AllocationMap(String computerURI,
	              ComputerServicesOutboundPort csop,
	              Integer numberOfCoresPerAVM,
	              AllocatedCore[] allocatedCores) {
		this.computerURI = computerURI;
		this.csop = csop;
		this.numberOfCoresPerAVM = numberOfCoresPerAVM;
		this.allocatedCores = allocatedCores;
	}

	public ComputerServicesOutboundPort getCsop() {
		return csop;
	}

	public Integer getNumberOfCoresPerAVM() {
		return numberOfCoresPerAVM;
	}

	public AllocatedCore[] getAllocatedCores() {
		return allocatedCores;
	}

	public String getComputerURI() {
		return computerURI;
	}
}
