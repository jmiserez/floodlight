package net.floodlightcontroller.happensbefore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;

import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Based on tutorial from here: http://docs.projectfloodlight.org/display/floodlightcontroller/How+to+Write+a+Module
 */
public class HappensBeforePre extends HappensBeforeCommon implements IFloodlightModule, IOFMessageListener {
	
	protected IFloodlightProviderService floodlightProvider;
	protected static Logger log;
	
	@Override
	public String getName() {
		return HappensBeforePre.class.getSimpleName();
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		// "name" should be called BEFORE this
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		// "name" should be called AFTER this
		return !name.equals(getName()) && (IN_TYPES.contains(type) ||name.contains("HappensBeforePost"));
	}
	
	@Override
	public net.floodlightcontroller.core.IListener.Command receive(
			IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		
		String currentMsgString = this.formatMsg(msg, sw.getId());
		
		if (IN_TYPES.contains(msg.getType())){
			hbStore.put(cntx, HAPPENSBEFORE_MSG_IN, currentMsgString);
			System.out.format("net.floodlightcontroller.happensbefore.HappensBefore-MessageIn-[%s]%n", currentMsgString);
			System.out.flush();
			
			Thread t = Thread.currentThread();
			long workerId = t.getId();
			threadToLatestMsgIn.put(workerId, currentMsgString);
			
		}
        return Command.CONTINUE;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
	    log = LoggerFactory.getLogger(HappensBeforePre.class);
	    log.debug("HappensBeforePre logger loading.");
	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		for (OFType type : IN_TYPES) {
			floodlightProvider.addOFMessageListener(type, this);
		}
	}

}