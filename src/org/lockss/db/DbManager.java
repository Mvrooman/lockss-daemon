package org.lockss.db;

public interface DbManager {
	
	OpenUrlResolverDbManager getOpenUrlResolverDbManager();

	boolean isConnectionReady() throws Exception;
}
