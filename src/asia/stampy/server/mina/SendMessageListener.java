package asia.stampy.server.mina;

import org.apache.mina.core.session.IoSession;

import asia.stampy.common.HostPort;
import asia.stampy.common.message.StampyMessage;
import asia.stampy.common.message.StampyMessageType;
import asia.stampy.common.mina.StampyMinaMessageListener;

public class SendMessageListener implements StampyMinaMessageListener {

	@Override
	public StampyMessageType[] getMessageTypes() {
		return new StampyMessageType[] { StampyMessageType.SEND };
	}

	@Override
	public boolean isForMessage(StampyMessage<?> message) {
		return true;
	}

	@Override
	public void messageReceived(StampyMessage<?> message, IoSession session, HostPort hostPort) throws Exception {
		// Override and implement
	}

}
