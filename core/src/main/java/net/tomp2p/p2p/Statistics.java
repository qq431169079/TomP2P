/*
 * Copyright 2012 Thomas Bocek
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package net.tomp2p.p2p;

import java.util.List;
import java.util.Map;

import net.tomp2p.peers.Number256;
import net.tomp2p.peers.PeerMap;
import net.tomp2p.peers.PeerStatistic;

public class Statistics {

	private final static double MAX = Math.pow(2, Number256.BITS);

	private double estimatedNumberOfPeers = 1;

	private double avgGap = MAX / 2;
	
	final private PeerMap peerMap;

	public Statistics(final PeerMap peerMap) {
		this.peerMap = peerMap;
	}
	
	public double estimatedNumberOfNodes() {
		final List<Map<Number256, PeerStatistic>> map = peerMap.peerMapVerified();
		// assume we are full
		double gap = 0D;
		int gapCount = 0;
		for (int i = 0; i < Number256.BITS; i++) {
			Map<Number256, PeerStatistic> peers = map.get(i);
			final int numPeers = peers.size();

			if (numPeers > 0 && numPeers < peerMap.bagSizeVerified(i)) {
				double currentGap = Math.pow(2, i) / numPeers;
				gap += currentGap * numPeers;
				gapCount += numPeers;
			} else if (numPeers == 0) {
				// we are empty
			} else if (numPeers == peerMap.bagSizeVerified(i)) {
				// we are full
			}
		}
		avgGap = gap / gapCount;
		estimatedNumberOfPeers = (MAX / avgGap);
		return estimatedNumberOfPeers;
	}

	public double avgGap() {
		return avgGap;
	}
}
