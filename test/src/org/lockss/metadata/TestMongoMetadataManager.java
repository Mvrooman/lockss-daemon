package org.lockss.metadata;

import java.io.File;
import java.util.Properties;

import org.lockss.config.ConfigManager;
import org.lockss.db.MongoDbManager;
import org.lockss.db.SqlDbManager;
import org.lockss.plugin.PluginManager;
import org.lockss.plugin.simulated.SimulatedArchivalUnit;
import org.lockss.test.ConfigurationUtil;
import org.lockss.test.LockssTestCase;
import org.lockss.test.MockLockssDaemon;

public class TestMongoMetadataManager extends LockssTestCase {
	
	  private MockLockssDaemon theDaemon;
	  private MongoMetadataManager mongoMetadataManager;
	  private MongoDbManager mongoDbManager;
	  private PluginManager pluginManager;
	  private String tempDirPath;
	   
	  @Override
	  public void setUp() throws Exception {
	    super.setUp();
	    
	    tempDirPath = getTempDir().getAbsolutePath();

	    // set derby database log 
	    System.setProperty("derby.stream.error.file",
	                       new File(tempDirPath,"derby.log").getAbsolutePath());

	    Properties props = new Properties();
	    props.setProperty(SqlMetadataManager.PARAM_INDEXING_ENABLED, "true");
	    props.setProperty(ConfigManager.PARAM_PLATFORM_DISK_SPACE_LIST,
			      tempDirPath);
	    ConfigurationUtil.setCurrentConfigFromProps(props);
	    
	    theDaemon = getMockLockssDaemon();
	    theDaemon.getAlertManager();
	    pluginManager = theDaemon.getPluginManager();
	    pluginManager.setLoadablePluginsReady(true);
	    theDaemon.setDaemonInited(true);
	    pluginManager.startService();
	    
	    mongoDbManager = new MongoDbManager();
	    theDaemon.setDbManager(mongoDbManager);
	    mongoDbManager.initService(theDaemon);
	    mongoDbManager.startService();
	    
	    mongoMetadataManager = new MongoMetadataManager();
	    theDaemon.setMetadataManager(mongoMetadataManager);
	    mongoMetadataManager = new MongoMetadataManager();
	    mongoMetadataManager.initService(theDaemon);
	    mongoMetadataManager.startService();
	  }
	  
	  
	  public void testCreateAndFindPublisher() throws Exception{
		  
		  long createID =  mongoMetadataManager.findOrCreatePublisher("Create And Find");
		  long findID = mongoMetadataManager.findOrCreatePublisher("Create And Find");
		  assertEquals(createID, findID);
		  
	  }
	  
	  
	  public void testCreateAndFindPlugin() throws Exception{
		  
		  long createID =  mongoMetadataManager.findOrCreatePlugin("test plugin", "windows");
		  long findID = mongoMetadataManager.findOrCreatePlugin("test plugin", "windows");
		  assertEquals(createID, findID);
		  
	  }
	  
	  public void testCreateAndFindPublication()throws Exception
	  {
		  long createID =  mongoMetadataManager.findOrCreatePublication("pIssn", "eIssn", "pIsbn", "eIsbn", 1111111L, "name", "date", "proprietaryId", "volume");
		  assertNotNull(createID);
		  
	  }
	  

}
