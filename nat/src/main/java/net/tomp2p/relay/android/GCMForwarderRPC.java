package net.tomp2p.relay.android;

import java.io.IOException;

import net.tomp2p.connection.PeerConnection;
import net.tomp2p.connection.Responder;
import net.tomp2p.message.Message;
import net.tomp2p.p2p.Peer;
import net.tomp2p.peers.PeerAddress;
import net.tomp2p.relay.BaseRelayForwarderRPC;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.android.gcm.server.Result;
import com.google.android.gcm.server.Sender;

/**
 * Manages the mapping between a peer address and the registration id. The registration id is sent by the
 * mobile device when the relay is set up.
 * 
 * @author Nico Rutishauser
 *
 */
public class GCMForwarderRPC extends BaseRelayForwarderRPC {

	private static final Logger LOG = LoggerFactory.getLogger(GCMForwarderRPC.class);
	private final int retries = 5; // TODO make configurable if requested
	private final Sender sender;
	private final String registrationId;

	public GCMForwarderRPC(Peer peer, PeerConnection peerConnection, String authToken, String registrationId) {
		super(peer, peerConnection);
		this.registrationId = registrationId;
		this.sender = new Sender(authToken);
		
		// TODO init some listener to detect when the relay is not reachable anymore
	}

	@Override
	public boolean peerFound(PeerAddress remotePeer, PeerAddress referrer, PeerConnection peerConnection) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	protected void handleRelay(Message message, Responder responder, PeerAddress sender) throws Exception {
		// TODO Save the message content in a buffer and notify the mobile device
		sendTickleMessage();
	}

	@Override
	protected void handlePing(Message message, Responder responder, PeerAddress sender) {
		// TODO Check if the mobile device is still alive and answer appropriately
	}

	private Result sendTickleMessage() {
		// Tickle the device with the given registration id.
		com.google.android.gcm.server.Message tickleMessage = new com.google.android.gcm.server.Message.Builder().build();
		try {
			// TODO make asynchronous
			return sender.send(tickleMessage, registrationId, retries);
		} catch (IOException e) {
			LOG.error("Cannot send tickle message to device {}", registrationId, e);
			return null;
		}
	}
}
