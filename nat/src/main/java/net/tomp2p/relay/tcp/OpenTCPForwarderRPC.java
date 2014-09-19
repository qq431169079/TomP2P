package net.tomp2p.relay.tcp;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.SignatureException;

import net.tomp2p.connection.ConnectionConfiguration;
import net.tomp2p.connection.PeerConnection;
import net.tomp2p.connection.PeerException;
import net.tomp2p.connection.Responder;
import net.tomp2p.futures.BaseFutureAdapter;
import net.tomp2p.futures.FutureDone;
import net.tomp2p.futures.FutureResponse;
import net.tomp2p.message.Buffer;
import net.tomp2p.message.Message;
import net.tomp2p.message.Message.Type;
import net.tomp2p.p2p.Peer;
import net.tomp2p.peers.PeerAddress;
import net.tomp2p.relay.BaseRelayForwarderRPC;
import net.tomp2p.relay.RelayUtils;
import net.tomp2p.rpc.RPC;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The RelayForwarder is responsible for forwarding all messages that are
 * received on a relay peer, but are intended for an unreachable peer that is
 * connected to the relay peer. Every unreachable node has an own instance of
 * this class at the relay server.
 * 
 * @author Raphael Voellmy
 * @author Nico Rutishauser
 * 
 */
public class OpenTCPForwarderRPC extends BaseRelayForwarderRPC {

	private final static Logger LOG = LoggerFactory.getLogger(OpenTCPForwarderRPC.class);

	// connection to unreachable peer
	private final PeerConnection peerConnection;
	private final ConnectionConfiguration config;

	/**
	 * 
	 * @param peerConnection
	 *            A peer connection to an unreachable peer that is permanently
	 *            open
	 * @param peer
	 *            The relay peer
	 */
	public OpenTCPForwarderRPC(final PeerConnection peerConnection, final Peer peer, ConnectionConfiguration config) {
		super(peer, peerConnection);
		this.config = config;
		this.peerConnection = peerConnection.changeRemotePeer(unreachablePeerAddress());
		
		// add a listener when the connection is closed
		peerConnection.closeFuture().addListener(new BaseFutureAdapter<FutureDone<Void>>() {
			@Override
            public void operationComplete(FutureDone<Void> future) throws Exception {
				peer.peerBean().removePeerStatusListeners(OpenTCPForwarderRPC.this);
				peer.connectionBean().dispatcher().removeIoHandler(unreachablePeerId());
            }
		});
		
		LOG.debug("Created TCP forwarder from peer {} to peer {}", peer.peerAddress(), unreachablePeerAddress());
	}
	
	@Override
    public boolean peerFailed(PeerAddress remotePeer, PeerException exception) {
	    //not handled here
	    return false;
    }

	@Override
    public boolean peerFound(PeerAddress remotePeer, PeerAddress referrer, PeerConnection peerConnection2) {
		boolean firstHand = referrer == null;
		boolean secondHand = remotePeer.equals(referrer);
		boolean samePeerConnection = peerConnection.equals(peerConnection2);
		//if firsthand, then full trust, if second hand and a stable peerconnection, we can trust as well
		if((firstHand || (secondHand && samePeerConnection))  && remotePeer.peerId().equals(unreachablePeerId()) && remotePeer.isRelayed()) {
			//we got new information about this peer, e.g. its active relays
			LOG.debug("Update the unreachable peer to {} based on {}, ref {}", unreachablePeerAddress(), remotePeer, referrer);
			unreachablePeerAddress(remotePeer);
		}
	    return false;
    }
	
	@Override
	protected void handleRelay(final Message message, final Responder responder, final PeerAddress sender) throws InvalidKeyException, SignatureException, IOException {
		// Send message via direct message through the open connection to the unreachable peer
		message.restoreContentReferences();
		
		final Message envelope = createMessage(peerConnection.remotePeer(), RPC.Commands.RELAY.getNr(), Type.REQUEST_2);
		envelope.buffer(RelayUtils.encodeMessage(message));
		// always keep the connection open
		envelope.keepAlive(true);
		// Forward a message through the open peer connection to the unreachable  peer.
		FutureResponse fr = RelayUtils.send(peerConnection, peerBean(), connectionBean(), config, message);

		fr.addListener(new BaseFutureAdapter<FutureResponse>() {
			public void operationComplete(FutureResponse future) throws Exception {
				if (future.isSuccess()) {
					Buffer buffer = future.responseMessage().buffer(0);
					Message responseFromUnreachablePeer = RelayUtils.decodeMessage(buffer, message.recipientSocket(),
					        message.senderSocket());
					responseFromUnreachablePeer.restoreContentReferences();
					responseFromUnreachablePeer.sender(sender);
					responseFromUnreachablePeer.recipient(message.sender());
					LOG.debug("Response from unreachable peer: {}", responseFromUnreachablePeer);
					responder.response(responseFromUnreachablePeer);
				} else {
					responder.failed(Type.USER1, "Relaying message failed: " + future.failedReason());
				}
			}
		});
	}
	
	@Override
	protected void handlePing(Message message, Responder responder, PeerAddress sender) {
		LOG.debug("peerconnection open? {}", peerConnection.isOpen());
		Message response = createResponseMessage(message, peerConnection.isOpen() ? Type.OK : Type.EXCEPTION, sender);
		responder.response(response);
	}
}
