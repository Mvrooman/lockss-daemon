package org.lockss.metadata;

import org.lockss.db.MongoDbManager;
import org.lockss.db.SqlDbManager;
import org.lockss.plugin.PluginManager;
import org.lockss.plugin.simulated.SimulatedArchivalUnit;
import org.lockss.test.LockssTestCase;
import org.lockss.test.MockLockssDaemon;

public class TestMongoMetadataManager extends LockssTestCase {
	
	  private MockLockssDaemon theDaemon;
	  private MongoMetadataManager mongoMetadataManager;
	  private MongoDbManager mongoDbManager;
	   
	  @Override
	  public void setUp() throws Exception {
	    super.setUp();
	    
	    theDaemon = getMockLockssDaemon();
	    
	    mongoDbManager = new MongoDbManager();
	    theDaemon.setDbManager(mongoDbManager);
	    mongoDbManager.initService(theDaemon);
	    mongoDbManager.startService();
	    
	    mongoMetadataManager = new MongoMetadataManager();
	    mongoMetadataManager.startService();
	  }
	  

}
