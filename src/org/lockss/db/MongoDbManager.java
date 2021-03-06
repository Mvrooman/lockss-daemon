package org.lockss.db;

import java.net.UnknownHostException;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.MongoClient;

import org.bson.types.ObjectId;
import org.lockss.config.Configuration;
import org.lockss.config.Configuration.Differences;
import org.lockss.util.Logger;

public class MongoDbManager extends DbManager {

	private static final Logger log = Logger.getLogger(MongoDbManager.class);
	
	public static final String PUBLISHERS_COLLECTION = "publishers";
	
	public static final String PLUGIN_COLLECTION = "plugins";
	
	public static final String PUBLICATIONS_COLLECTION = "publications";
	
	public static final String AUS_COLLECTION = "aus";

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
			//mongoClient = new MongoClient("ec2-54-241-200-25.us-west-1.compute.amazonaws.com", 27017);
			mongoClient = new MongoClient("24.10.146.230", 27017);
			mongoDatabase = mongoClient.getDB("lockss");  
			initializeCollections();
		} catch (UnknownHostException e) {
			log.error(e.getMessage());
			//TODO: Add logging/handling here
		}
		
		ready = true;
	}

	/**
	 * 
	 */
	private void initializeCollections() {
		if (!mongoDatabase.collectionExists(PUBLISHERS_COLLECTION))
			mongoDatabase.createCollection(PUBLISHERS_COLLECTION, null);
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
	
	/**
	 * 
	 * @return The current Mongo database
	 */
	public DB getDb(){
		return mongoDatabase;
	}
	
	/**
	 * Creates the Mongo long ID based on the _id
	 * @param dbObject
	 * @param collection
	 * @return
	 */
	public Long createLongId(BasicDBObject dbObject, DBCollection collection)
	{
		ObjectId id = (ObjectId) dbObject.get("_id");
		Long longId = MongoHelper.objectIdToLongId(id);

		dbObject.append("longId", longId);
		BasicDBObject query = new BasicDBObject("_id", id);
		collection.update(query, dbObject);

		
		return longId;
	}

}
