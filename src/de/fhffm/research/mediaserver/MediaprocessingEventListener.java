package de.fhffm.research.mediaserver;

import java.util.EventListener;

public interface MediaprocessingEventListener extends EventListener {
	public void myEventOccurred(MediaprocessingReadyEvent evt);
}
