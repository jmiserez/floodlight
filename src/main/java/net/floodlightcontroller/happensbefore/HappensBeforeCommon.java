package net.floodlightcontroller.happensbefore;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import net.floodlightcontroller.core.FloodlightContextStore;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.base64.Base64;
import org.jboss.netty.util.CharsetUtil;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFType;

public abstract class HappensBeforeCommon {

	protected static HashMap<Long, String> threadToLatestMsgIn = new HashMap<Long, String>();
	protected static final String HAPPENSBEFORE_MSG_IN = "net.floodlightcontroller.happensbefore.HappensBefore.msgin";
	protected static final List<OFType> IN_TYPES = Arrays.asList(OFType.PACKET_IN, OFType.FLOW_REMOVED);
	protected static final List<OFType> OUT_TYPES = Arrays.asList(OFType.PACKET_OUT, OFType.FLOW_MOD, OFType.BARRIER_REQUEST);
	protected static final FloodlightContextStore<String> hbStore = new FloodlightContextStore<String>();

	protected String formatMsg(OFMessage msg, long swid) {
		ChannelBuffer buf = ChannelBuffers.dynamicBuffer();
		msg.writeTo(buf);
		ChannelBuffer encoded = Base64.encode(buf);
		String b64_msg = encoded.toString(CharsetUtil.UTF_8).replace("\n", "");
		return Long.toString(swid)+":"+b64_msg;
	}

}