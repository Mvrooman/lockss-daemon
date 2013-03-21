package org.lockss.db;

import org.lockss.daemon.OpenUrlResolver.OpenUrlInfo;

public interface OpenUrlResolverDbManager {
	
	OpenUrlInfo resolveFromDoi(String doi);

}
