package org.lockss.db;

import java.net.UnknownHostException;

import com.mongodb.DB;
import com.mongodb.MongoClient;
import org.lockss.config.Configuration;
import org.lockss.config.Configuration.Differences;
import org.lockss.util.Logger;



public class MongoDbManager extends DbManager {

	private static final Logger log = Logger.getLogger(MongoDbManager.class);
	private boolean ready = false;
	//The mongo database
	private DB mongoDatabase;

	/**
	 * Starts the DbManager service.
	 */
	@Override
	public void startService() {
		log.info("Starting mongoDB manager");
		ready = ready && mongoDatabase != null;
		if (ready) {
			return;
		}
		MongoClient mongoClient = null;
		try {
			mongoClient = new MongoClient("ec2-54-241-200-25.us-west-1.compute.amazonaws.com", 27017);
			mongoDatabase = mongoClient.getDB("lockss");

		} catch (UnknownHostException e) {
			log.error(e.getMessage());
			//TODO: Add logging/handling here
		}
		ready = true;
	}


	@Override
	public OpenUrlResolverDbManager getOpenUrlResolverDbManager() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isConnectionReady() throws Exception {
		return ready;
	}


	@Override
	public void setConfig(Configuration newConfig, Configuration prevConfig,
			Differences changedKeys) {
		// TODO Auto-generated method stub

	}

}
