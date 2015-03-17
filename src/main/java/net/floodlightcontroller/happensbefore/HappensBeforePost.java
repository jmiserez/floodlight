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
public class HappensBeforePost extends HappensBeforeCommon implements IFloodlightModule, IOFMessageListener {
	
	protected IFloodlightProviderService floodlightProvider;
	protected static Logger log;
	
	@Override
	public String getName() {
		return HappensBeforePost.class.getSimpleName();
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		// "name" should be called BEFORE this
		return !name.equals(getName()) && (OUT_TYPES.contains(type) || name.contains("HappensBeforePre"));
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		// "name" should be called AFTER this
		return false;
	}
	
	@Override
	public net.floodlightcontroller.core.IListener.Command receive(
			IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		
		String currentMsgString = this.formatMsg(msg, sw.getId());
		
		if (OUT_TYPES.contains(msg.getType())) {
			String previousMsgString = hbStore.get(cntx, HAPPENSBEFORE_MSG_IN);
			if(previousMsgString == null){
				log.error("Floodlight context not passed by previous module. Will analyze call stack instead: "+msg.getType().toString());
				
				// Get information by other means. We know that Floodlight uses only 1 single thread per switch connection.
				// So in case a module does not pass a context, we will just use the latest message in, if there is a Controller.handleMessage in the stack trace
				
				Thread t = Thread.currentThread();
				long workerId = t.getId();
				boolean found = false;
				StackTraceElement[] stack = Thread.currentThread().getStackTrace();
				for( StackTraceElement e : stack){
					if (e.getClassName().equals("net.floodlightcontroller.core.internal.Controller") &&
							e.getMethodName().equals("handleMessage")){
						// This is a reactive message out.
						previousMsgString = threadToLatestMsgIn.get(workerId); //TODO JM: hack, refactor
						found = true;
						break;
					}
				}
				if (!found){
					// This must be a proactive message out. Clear the latest message in and proceed.
					threadToLatestMsgIn.remove(workerId);
				}
			}
			if(previousMsgString != null){
				System.out.format("net.floodlightcontroller.happensbefore.HappensBefore-MessageOut-[%s:%s]%n", previousMsgString, currentMsgString);
				System.out.flush();
			}
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
	    log = LoggerFactory.getLogger(HappensBeforePost.class);
	    log.debug("HappensBeforePost logger loading.");
	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		for (OFType type : OUT_TYPES) {
			floodlightProvider.addOFMessageListener(type, this);
		}
	}

}