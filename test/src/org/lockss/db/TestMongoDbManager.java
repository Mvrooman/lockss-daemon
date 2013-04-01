package org.lockss.db;

import org.lockss.test.LockssTestCase;
import org.lockss.test.MockLockssDaemon;

public class TestMongoDbManager extends LockssTestCase{

	  private MockLockssDaemon theDaemon;
	  private MongoDbManager mongoDbManager;
	
	  @Override
	  public void setUp() throws Exception {
	    super.setUp();
	    
	    mongoDbManager = new MongoDbManager();
	    mongoDbManager.startService();
	  }
	  
	  //test the connection
	  public void testConnection()
	  {
		  try {
			assertTrue(mongoDbManager.isConnectionReady());
		} catch (Exception e) {
			fail(e.toString());
			
		}
	  }

}
