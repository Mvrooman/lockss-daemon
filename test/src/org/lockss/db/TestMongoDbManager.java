package org.lockss.db;

import static org.lockss.db.MongoDbManager.*;

import org.lockss.test.LockssTestCase;
import org.lockss.test.MockLockssDaemon;

import com.mongodb.DB;
import com.mongodb.DBCollection;

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
	  public void testConnection() {
		  try {
			assertTrue(mongoDbManager.isConnectionReady());
		} catch (Exception e) {
			fail(e.toString());
			
		}
	  }
	  
	  public void testCollectionInitialization() {
		  DB db = mongoDbManager.getDb();
		  assertTrue(db.collectionExists(PUBLISHERS_COLLECTION));
	  }

}
