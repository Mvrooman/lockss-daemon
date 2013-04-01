package org.lockss.metadata;

import static org.lockss.db.MongoDbManager.*;

import java.util.List;

import org.bson.types.ObjectId;
import org.lockss.config.Configuration;
import org.lockss.config.Configuration.Differences;
import org.lockss.db.DbManager;
import org.lockss.db.MongoDbManager;
import org.lockss.db.MongoHelper;
import org.lockss.db.SqlDbManager;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.plugin.PluginManager;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;

public class MongoMetadataManager extends MetadataManager {
	
	private PluginManager pluginMgr = null;
	private MongoDbManager mongoDbManager = null;
	private DB mongoDatabase = null;
	
	@Override
	public void startService() {
	    final String DEBUG_HEADER = "startService(): ";
	    log.debug(DEBUG_HEADER + "Starting mongoMetadataManager");

	    pluginMgr = getDaemon().getPluginManager();
	    mongoDbManager = (MongoDbManager) getDaemon().getDbManager();
	    mongoDatabase = mongoDbManager.getDb();
	}

	@Override
	public void setConfig(Configuration newConfig, Configuration prevConfig,
			Differences changedKeys) {
		// TODO Auto-generated method stub

	}

	@Override
	List<String> getPrioritizedAuIdsToReindex(int maxAuIds) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	Long findOrCreateAu(Long pluginSeq, String auKey) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	void restartService() {
		// TODO Auto-generated method stub

	}

	@Override
	long getActiveReindexingCount() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	long getSuccessfulReindexingCount() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	long getFailedReindexingCount() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	List<SqlReindexingTask> getReindexingTasks() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	long getArticleCount() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	long getPendingAusCount() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	boolean isIndexingEnabled() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	Long findOrCreatePlugin(String pluginId, String platform) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	Long addAuMd(Long auSeq, int version, long extractTime) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Long findOrCreatePublisher(String publisher) throws Exception {
	    final String DEBUG_HEADER = "findOrCreatePublisher(): ";
	    Long publisherSeq = findPublisher(publisher);
	    log.debug3(DEBUG_HEADER + "publisherSeq = " + publisherSeq);

	    // Check whether it is a new publisher.
	    if (publisherSeq == null) {
	      // Yes: Add to the database the new publisher.
	      publisherSeq = addPublisher(publisher);
	      log.debug3(DEBUG_HEADER + "new publisherSeq = " + publisherSeq);
	    }

	    return publisherSeq;
	}
	
	private Long findPublisher(String publisher) {
		DBCollection collection = mongoDatabase.getCollection(PUBLISHERS_COLLECTION);
		BasicDBObject query = new BasicDBObject("name", publisher);
		DBObject result = collection.findOne(query);
		
		if(result != null) {
			return MongoHelper.readLong(result, "longId");
		}
		
		return null;
	}
	
	private Long addPublisher(String publisher) {
		DBCollection collection = mongoDatabase.getCollection(PUBLISHERS_COLLECTION);
		BasicDBObject publisherDocument = new BasicDBObject("name", publisher);
		collection.insert(publisherDocument);
		ObjectId id = (ObjectId)publisherDocument.get( "_id" );
		Long longId = MongoHelper.objectIdToLongId(id);
		
		publisherDocument.append("longId", longId);
		BasicDBObject query = new BasicDBObject("_id", id);
		collection.update(query, publisherDocument);
		
		return longId;
	}

	@Override
	public Long findOrCreatePublication(String pIssn, String eIssn,
			String pIsbn, String eIsbn, Long publisherSeq, String name,
			String date, String proprietaryId, String volume) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	Long findPublicationMetadataItem(Long publicationSeq) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	Long findMetadataItemType(String typeName) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	Long addMdItem(Long parentSeq, Long mdItemTypeSeq, Long auMdSeq,
			String date, String coverage) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	void addMdItemName(Long mdItemSeq, String name, String type)
			throws Exception {
		// TODO Auto-generated method stub

	}

	@Override
	void addMdItemUrl(Long mdItemSeq, String feature, String url)
			throws Exception {
		// TODO Auto-generated method stub

	}

	@Override
	void addMdItemDoi(Long mdItemSeq, String doi) throws Exception {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean addAuToReindex(ArchivalUnit au) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean addAuToReindex(ArchivalUnit au, boolean inBatch) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean addAuToReindex(ArchivalUnit au, boolean inBatch,
			boolean fullReindex) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean disableAuIndexing(ArchivalUnit au) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public DbManager getDbManager() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	Long findAuMd(Long auSeq) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void updateAuLastExtractionTime(Long auMdSeq) throws Exception {
		// TODO Auto-generated method stub

	}

}
