package de.fhffm.research.mediaserver;

import java.net.InetAddress;

public class SIPAuthorizedClient {

	private long sSRCAudio = -1;
	private long sSRCVideo = -1;

	private InetAddress clientAddress = null;

	public InetAddress getClientAddress(){
		return clientAddress;
	}
	
	public void setClientAddress(InetAddress clientAddress){
		this.clientAddress = clientAddress;
	}
	
	/**
	 * @return the sSRCAudio
	 */
	public long getsSRCAudio() {
		return sSRCAudio;
	}

	/**
	 * @param sSRCAudio the sSRCAudio to set
	 */
	public void setsSRCAudio(long sSRCAudio) {
		this.sSRCAudio = sSRCAudio;
	}

	/**
	 * @return the sSRCVideo
	 */
	public long getsSRCVideo() {
		return sSRCVideo;
	}

	/**
	 * @param sSRCVideo the sSRCVideo to set
	 */
	public void setsSRCVideo(long sSRCVideo) {
		this.sSRCVideo = sSRCVideo;
	}
	
	
}
