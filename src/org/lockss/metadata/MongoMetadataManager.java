package org.lockss.metadata;

import static org.lockss.db.DbManager.MAX_NAME_COLUMN;
import static org.lockss.db.MongoDbManager.*;
import static org.lockss.db.SqlDbManager.MD_ITEM_TYPE_BOOK;
import static org.lockss.db.SqlDbManager.MD_ITEM_TYPE_BOOK_SERIES;
import static org.lockss.db.SqlDbManager.MD_ITEM_TYPE_JOURNAL;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.types.ObjectId;
import org.lockss.app.LockssDaemon;
import org.lockss.config.Configuration;
import org.lockss.config.Configuration.Differences;
import org.lockss.db.DbManager;
import org.lockss.db.MongoDbManager;
import org.lockss.db.MongoHelper;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.plugin.PluginManager;
import org.lockss.util.StringUtil;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.QueryBuilder;

public class MongoMetadataManager extends MetadataManager {
	
	private PluginManager pluginMgr = null;
	private MongoDbManager mongoDbManager = null;
	private DB mongoDatabase = null;
	
	
	
	@Override
	public void startService() {
	    final String DEBUG_HEADER = "startService(): ";
	    log.debug(DEBUG_HEADER + "Starting mongoMetadataManager");

	    //pluginMgr = getDaemon().getPluginManager();
	    LockssDaemon ld =  getDaemon();
	    pluginMgr = ld.getPluginManager();
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
	    final String DEBUG_HEADER = "findOrCreatePlugin(): ";
	    log.debug3(DEBUG_HEADER + "pluginId = " + pluginId);
	    Long pluginSeq = findPlugin(pluginId);
	    log.debug3(DEBUG_HEADER + "pluginSeq = " + pluginSeq);

	    // Check whether it is a new plugin.
	    if (pluginSeq == null) {
	      // Yes: Add to the database the new plugin.
	      pluginSeq = addPlugin(pluginId, platform);
	      log.debug3(DEBUG_HEADER + "new pluginSeq = " + pluginSeq);
	    }

	    return pluginSeq;
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
	    final String DEBUG_HEADER = "findOrCreatePublication(): ";
	    Long publicationSeq = null;

	    // Get the title name.
	    String title = null;
	    log.debug3(DEBUG_HEADER + "name = " + name);

	    if (!StringUtil.isNullString(name)) {
	      title = name.substring(0, Math.min(name.length(), MAX_NAME_COLUMN));
	    }
	    log.debug3(DEBUG_HEADER + "title = " + title);
	    
	    // Check whether it is a book series.
	    if (isBookSeries(pIssn, eIssn, pIsbn, eIsbn, volume)) {
	      // Yes: Find or create the book series.
	      log.debug3(DEBUG_HEADER + "is book series.");
	      publicationSeq =
		  findOrCreateBookInBookSeries(pIssn, eIssn, pIsbn, eIsbn,
		                                publisherSeq, name, date, proprietaryId,
		                                volume);
	      // No: Check whether it is a book.
	    } else if (isBook(pIsbn, eIsbn)) {
	      // Yes: Find or create the book.
	      log.debug3(DEBUG_HEADER + "is book.");
	      publicationSeq =
		  findOrCreateBook(pIsbn, eIsbn, publisherSeq, title, null, date,
		                   proprietaryId);
	    } else {
	      // No, it is a journal article: Find or create the journal.
	      log.debug3(DEBUG_HEADER + "is journal.");
	      publicationSeq =
		  findOrCreateJournal(pIssn, eIssn, publisherSeq, title, date,
		                      proprietaryId);
	    }

	    
	    publicationSeq =  addPublication(pIssn, eIssn, pIsbn, eIsbn, publisherSeq, name, date, proprietaryId, volume,
	    		null, //TODO:  CMU --- THIS SHOULD BE THE ParentSEQ, which is a bookseries
	    		title, proprietaryId);
	    
	    return publicationSeq;
	}
	
	/**
	 * Creates a publication based on the given parameters
	 * @param pIssn
	 * @param eIssn
	 * @param pIsbn
	 * @param eIsbn
	 * @param publisherSeq
	 * @param name
	 * @param date
	 * @param proprietaryId
	 * @param volume
	 * @return
	 */
	private Long addPublication(String pIssn, String eIssn, String pIsbn,
			String eIsbn, Long publisherSeq, String name, String date,
			String proprietaryId, String volume, 
			Long parentSeq, String mdItemType, String title) {

		DBCollection collection = mongoDatabase
				.getCollection(PUBLICATIONS_COLLECTION);
		BasicDBObject publisherDocument = new BasicDBObject("pIssn", pIssn)
				.append("eIssn", eIssn).append("pIsbn", pIsbn)
				.append("eIsbn", eIsbn).append("publisherSeq", publisherSeq)
				.append("name", name).append("date", date)
				.append("proprietaryId", proprietaryId)
				.append("volume", volume)
				.append("parentSeq", parentSeq)
				.append("mdItemType", mdItemType)
				.append("title", title);
		
		collection.insert(publisherDocument);
		

		return mongoDbManager.createLongId(publisherDocument, collection);
	}
	

	@Override
	Long findPublicationMetadataItem(Long publicationSeq) throws Exception {
	    final String DEBUG_HEADER = "findPublicationMetadataItem(): ";
	    Long mdItemSeq = null;
	    //TODO: CMU --  Replace with Mango start
//	    PreparedStatement findMdItem =
//		sqlDbManager.prepareStatement(FIND_PUBLICATION_METADATA_ITEM_QUERY);
//	    ResultSet resultSet = null;
//
	    try {
//	      findMdItem.setLong(1, publicationSeq);
//
//	      resultSet = sqlDbManager.executeQuery(findMdItem);
//	      if (resultSet.next()) {
//		mdItemSeq = resultSet.getLong(MD_ITEM_SEQ_COLUMN);
		//TODO: CMU --  Replace with Mango end
		log.debug3(DEBUG_HEADER + "mdItemSeq = " + mdItemSeq);
	      
	    } catch (Exception sqle) {
		log.error("Cannot find publication metadata item", sqle);
		log.error("publicationSeq = '" + publicationSeq + "'.");
		throw sqle;
	    }

	    return mdItemSeq;
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
	    if (name == null || type == null) {
	        return;
	      }
	    //TODO: CMU -- Replace with Mango -- Start
//	      PreparedStatement insertMdItemName =
//	  	sqlDbManager.prepareStatement(conn, INSERT_MD_ITEM_NAME_QUERY);

	      try {
//	        insertMdItemName.setLong(1, mdItemSeq);
//	        insertMdItemName.setString(2, name);
//	        insertMdItemName.setString(3, type);
//	        int count = sqlDbManager.executeUpdate(insertMdItemName);

	        if (log.isDebug3()) {
	  	log.debug3("count = " );
	  	log.debug3("Added metadata item name = " + name);
	        }
	      }finally
	      {
	    	  
	      }
	      //TODO: CMU -- Replace with Mango -- End
	      

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

	  /**
	   * Provides the identifier of a plugin.
	   * 
	   * @param conn
	   *          A Connection with the database connection to be used.
	   * @param pluginId
	   *          A String with the plugin identifier.
	   * @return a Long with the identifier of the plugin.
	   * @throws Exception
	   *           if any problem occurred accessing the database.
	   */
	  Long findPlugin(String pluginId) throws Exception {
	    final String DEBUG_HEADER = "findPlugin(): ";
	    log.debug3(DEBUG_HEADER + "pluginId = " + pluginId);
	    Long pluginSeq = null;
	    
		DBCollection collection = mongoDatabase.getCollection(PLUGIN_COLLECTION);
		BasicDBObject query = new BasicDBObject("pluginId", pluginId);
		DBObject result = collection.findOne(query);
		
		if(result != null) {
			return MongoHelper.readLong(result, "longId");
		}
		
		return null;
	  }
	  
	private Long addPlugin(String pluginId, String platform) {
		DBCollection collection = mongoDatabase
				.getCollection(PLUGIN_COLLECTION);
		BasicDBObject pluginDocument = new BasicDBObject("pluginId", pluginId)
				.append("platform", platform);
		collection.insert(pluginDocument);
		ObjectId id = (ObjectId) pluginDocument.get("_id");
		Long longId = MongoHelper.objectIdToLongId(id);

		pluginDocument.append("longId", longId);
		BasicDBObject query = new BasicDBObject("_id", id);
		collection.update(query, pluginDocument);

		return longId;
	}
	
	
	/**
	   * Provides an indication of whether a metadata set corresponds to a book
	   * series.
	   * 
	   * @param pIssn
	   *          A String with the print ISSN in the metadata.
	   * @param eIssn
	   *          A String with the electronic ISSN in the metadata.
	   * @param pIsbn
	   *          A String with the print ISBN in the metadata.
	   * @param eIsbn
	   *          A String with the electronic ISBN in the metadata.
	   * @param volume
	   *          A String with the volume in the metadata.
	   * @return <code>true</code> if the metadata set corresponds to a book series,
	   *         <code>false</code> otherwise.
	   */
	  private boolean isBookSeries(String pIssn, String eIssn, String pIsbn,
	      String eIsbn, String volume) {
	    final String DEBUG_HEADER = "isBook(): ";

	    // If the metadata contains both ISBN and ISSN values, it is a book that is
	    // part of a book series.
	    boolean isBookSeries =
		isBook(pIsbn, eIsbn)
		    && (!StringUtil.isNullString(pIssn) || !StringUtil
			.isNullString(eIssn));
	    log.debug3(DEBUG_HEADER + "isBookSeries = " + isBookSeries);

	    // Handle book series with no ISSNs.
	    if (!isBookSeries && isBook(pIsbn, eIsbn)) {
	      isBookSeries = !StringUtil.isNullString(volume);
	    }

	    return isBookSeries;
	  }

	  
	  /**
	   * Provides the identifier of a book that belongs to a book series if existing
	   * or after creating it otherwise.
	   * 
	   * @param conn
	   *          A Connection with the database connection to be used.
	   * @param pIssn
	   *          A String with the print ISSN of the book series.
	   * @param eIssn
	   *          A String with the electronic ISSN of the book series.
	   * @param pIsbn
	   *          A String with the print ISBN of the book series.
	   * @param eIsbn
	   *          A String with the electronic ISBN of the book series.
	   * @param publisherSeq
	   *          A Long with the publisher identifier.
	   * @param title
	   *          A String with the name of the book series.
	   * @param date
	   *          A String with the publication date of the book series.
	   * @param proprietaryId
	   *          A String with the proprietary identifier of the book series.
	   * @param volume
	   *          A String with the bibliographic volume.
	   * @return a Long with the identifier of the book.
	   * @throws Exception
	   *           if any problem occurred accessing the database.
	   */
	  private Long findOrCreateBookInBookSeries(String pIssn,
	      String eIssn, String pIsbn, String eIsbn, Long publisherSeq, String title,
	      String date, String proprietaryId, String volume) throws Exception {
	    final String DEBUG_HEADER = "findOrCreateBookInBookSeries(): ";
	    Long bookSeq = null;
	    Long bookSeriesSeq = null;
	    Long mdItemSeq = null;

	    // Construct a title for the book in the series.
	    String bookTitle = title + " Volume " + volume;
	    log.debug3(DEBUG_HEADER + "bookTitle = " + bookTitle);

	    // Find the book series.
	    bookSeriesSeq =
		findPublication(title, publisherSeq, pIssn, eIssn, pIsbn, eIsbn,
				MD_ITEM_TYPE_BOOK_SERIES);
	    log.debug3(DEBUG_HEADER + "bookSeriesSeq = " + bookSeriesSeq);

	    // Check whether it is a new book series.
	    if (bookSeriesSeq == null) {
	      // Yes: Add to the database the new book series.
	      bookSeriesSeq =
		  addPublication(null, MD_ITEM_TYPE_BOOK_SERIES, date, title,
		                 proprietaryId, publisherSeq);
	      log.debug3(DEBUG_HEADER + "new bookSeriesSeq = " + bookSeriesSeq);

	      // Skip it if the new book series could not be added.
	      if (bookSeriesSeq == null) {
		log.error("Title for new book series '" + title
		    + "' could not be created.");
		return bookSeq;
	      }

	      // Get the book series metadata item identifier.
	      mdItemSeq = findPublicationMetadataItem(bookSeriesSeq);
	      log.debug3(DEBUG_HEADER + "mdItemSeq = " + mdItemSeq);

	      // Add to the database the new book series ISSN values.
	      addMdItemIssns(mdItemSeq, pIssn, eIssn);
	      log.debug3(DEBUG_HEADER + "added title ISSNs.");

	      // Add to the database the new book.
	      bookSeq =
		  addPublication(bookSeriesSeq, MD_ITEM_TYPE_BOOK, date,
		                 bookTitle, proprietaryId, publisherSeq);
	      log.debug3(DEBUG_HEADER + "new bookSeq = " + bookSeq);

	      // Skip it if the new book could not be added.
	      if (bookSeq == null) {
		log.error("Title for new book '" + bookTitle
		    + "' could not be created.");
		return bookSeq;
	      }

	      // Get the book metadata item identifier.
	      mdItemSeq = findPublicationMetadataItem(bookSeq);
	      log.debug3(DEBUG_HEADER + "mdItemSeq = " + mdItemSeq);

	      // Add to the database the new book ISBN values.
	      addMdItemIsbns(mdItemSeq, pIsbn, eIsbn);
	      log.debug3(DEBUG_HEADER + "added title ISBNs.");
	    } else {
	      // No: Get the book series metadata item identifier.
	      mdItemSeq = findPublicationMetadataItem(bookSeriesSeq);
	      log.debug3(DEBUG_HEADER + "mdItemSeq = " + mdItemSeq);

	      // Add to the database the book series name in the metadata as an
	      // alternate, if new.
	      addNewMdItemName(mdItemSeq, title);
	      log.debug3(DEBUG_HEADER + "added new title name.");

	      // Add to the database the ISSN values in the metadata, if new.
	      addNewMdItemIssns(mdItemSeq, pIssn, eIssn);
	      log.debug3(DEBUG_HEADER + "added new title ISSNs.");

	      // Find or create the book.
	      bookSeq =
		  findOrCreateBook(pIsbn, eIsbn, publisherSeq, bookTitle,
				   bookSeriesSeq, date, proprietaryId);
	    }

	    return bookSeq;
	  }
	  
	  /**
	   * Provides the identifier of a publication by its title, publisher, ISSNs
	   * and/or ISBNs.
	   * 
	   * @param conn
	   *          A Connection with the database connection to be used.
	   * @param title
	   *          A String with the title of the publication.
	   * @param publisherSeq
	   *          A Long with the publisher identifier.
	   * @param pIssn
	   *          A String with the print ISSN of the publication.
	   * @param eIssn
	   *          A String with the electronic ISSN of the publication.
	   * @param pIsbn
	   *          A String with the print ISBN of the publication.
	   * @param eIsbn
	   *          A String with the electronic ISBN of the publication.
	   * @param mdItemType
	   *          A String with the type of publication to be identified.
	   * @return a Long with the identifier of the publication.
	   * @throws Exception
	   *           if any problem occurred accessing the database.
	   *
	   *///
	   
	  private Long findPublication(String title, Long publisherSeq,
	      String pIssn, String eIssn, String pIsbn, String eIsbn, String mdItemType)
		  throws Exception {
		  
	    final String DEBUG_HEADER = "findPublication(): ";
	    Long publicationSeq = null;
	    boolean hasIssns = pIssn != null || eIssn != null;
        log.debug3(DEBUG_HEADER + "hasIssns = " + hasIssns);
	    boolean hasIsbns = pIsbn != null || eIsbn != null;
	    log.debug3(DEBUG_HEADER + "hasIsbns = " + hasIsbns);
	    boolean hasName = !StringUtil.isNullString(title);
	    log.debug3(DEBUG_HEADER + "hasName = " + hasName);

	    if (!hasIssns && !hasIsbns && !hasName) {
	      log.debug3(DEBUG_HEADER + "Cannot find publication with no name, ISSNs"
		  + " or ISBNs.");
	      return null;
	    }

	    if (hasIssns && hasIsbns && hasName) {
	      publicationSeq =
		  findPublicationByIssnsOrIsbnsOrName(title, publisherSeq, pIssn,
						      eIssn, pIsbn, eIsbn, mdItemType); 
	    } else if (hasIssns && hasName) {
	      publicationSeq =
		  findPublicationByIssnsOrName(title, publisherSeq, pIssn, eIssn,
					       mdItemType);
	    } else if (hasIsbns && hasName) {
	      publicationSeq =
		  findPublicationByIsbnsOrName(title, publisherSeq, pIsbn, eIsbn,
					       mdItemType);
	    } else if (hasIssns) {
	      publicationSeq =
		  findPublicationByIssns(publisherSeq, pIssn, eIssn, mdItemType);
	    } else if (hasIsbns) {
	      publicationSeq =
		  findPublicationByIsbns(publisherSeq, pIsbn, eIsbn, mdItemType);
	    } else if (hasName) {
	      publicationSeq =
		  findPublicationByName(title, publisherSeq, mdItemType);
	    }

	    return publicationSeq;
	  }

	  /**
	   * Provides the identifier of a book existing or after creating it otherwise.
	   * 
	   * @param conn
	   *          A Connection with the database connection to be used.
	   * @param pIsbn
	   *          A String with the print ISBN of the book.
	   * @param eIsbn
	   *          A String with the electronic ISBN of the book.
	   * @param publisherSeq
	   *          A Long with the publisher identifier.
	   * @param title
	   *          A String with the name of the book.
	   * @param parentSeq
	   *          A Long with the publication parent publication identifier.
	   * @param date
	   *          A String with the publication date of the book.
	   * @param proprietaryId
	   *          A String with the proprietary identifier of the book.
	   * @param volume
	   *          A String with the bibliographic volume.
	   * @return a Long with the identifier of the book.
	   * @throws Exception
	   *           if any problem occurred accessing the database.
	   */
	  private Long findOrCreateBook(String pIsbn, String eIsbn,
	      Long publisherSeq, String title, Long parentSeq, String date,
	      String proprietaryId) throws Exception {
	    final String DEBUG_HEADER = "findOrCreateBook(): ";
	    Long publicationSeq = null;
	    Long mdItemSeq = null;

	    // Find the book.
	    publicationSeq =
		findPublication(title, publisherSeq, null, null, pIsbn, eIsbn,
				MD_ITEM_TYPE_BOOK);
	    log.debug3(DEBUG_HEADER + "publicationSeq = " + publicationSeq);

	    // Check whether it is a new book.
	    if (publicationSeq == null) {
	      // Yes: Add to the database the new book.
	      publicationSeq =
		  addPublication(parentSeq, MD_ITEM_TYPE_BOOK, date, title,
				 proprietaryId, publisherSeq);
	      log.debug3(DEBUG_HEADER + "new publicationSeq = " + publicationSeq);

	      // Skip it if the new book could not be added.
	      if (publicationSeq == null) {
		log.error("Publication for new book '" + title
		    + "' could not be created.");
		return publicationSeq;
	      }

	      // Get the book metadata item identifier.
	      mdItemSeq = findPublicationMetadataItem(publicationSeq);
	      log.debug3(DEBUG_HEADER + "mdItemSeq = " + mdItemSeq);

	      // Add to the database the new book ISBN values.
	      addMdItemIsbns(mdItemSeq, pIsbn, eIsbn);
	      log.debug3(DEBUG_HEADER + "added title ISBNs.");
	    } else {
	      // No: Get the book metadata item identifier.
	      mdItemSeq = findPublicationMetadataItem(publicationSeq);
	      log.debug3(DEBUG_HEADER + "mdItemSeq = " + mdItemSeq);

	      // Add to the database the book name in the metadata as an alternate,
	      // if new.
	      addNewMdItemName(mdItemSeq, title);
	      log.debug3(DEBUG_HEADER + "added new title name.");

	      // Add to the database the ISBN values in the metadata, if new.
	      addNewMdItemIsbns(mdItemSeq, pIsbn, eIsbn);
	      log.debug3(DEBUG_HEADER + "added new title ISBNs.");
	    }

	    return publicationSeq;
	  }	  

	  /**
	   * Adds a publication to the database.
	   * 
	   * @param conn
	   *          A Connection with the database connection to be used.
	   * @param parentSeq
	   *          A Long with the publication parent publication identifier.
	   * @param mdItemType
	   *          A String with the type of publication.
	   * @param date
	   *          A String with the publication date of the publication.
	   * @param title
	   *          A String with the title of the publication.
	   * @param proprietaryId
	   *          A String with the proprietary identifier of the publication.
	   * @param publisherSeq
	   *          A Long with the publisher identifier.
	   * @return a Long with the identifier of the publication just added.
	   * @throws Exception
	   *           if any problem occurred accessing the database.
	   */
	  private Long addPublication(Long parentSeq,
	      String mdItemType, String date, String title, String proprietaryId,
	      Long publisherSeq) throws Exception {
	    final String DEBUG_HEADER = "addPublication(): ";
	    Long publicationSeq = null;

	    Long mdItemTypeSeq = findMetadataItemType(mdItemType);
	    log.debug2(DEBUG_HEADER + "mdItemTypeSeq = " + mdItemTypeSeq);

	    if (mdItemTypeSeq == null) {
		log.error("Unable to find the metadata item type " + mdItemType);
		return null;
	    }

	    Long mdItemSeq =
		addMdItem(parentSeq, mdItemTypeSeq, null, date, null);
	    log.debug2(DEBUG_HEADER + "mdItemSeq = " + mdItemSeq);

	    if (mdItemSeq == null) {
		log.error("Unable to create metadata item table row.");
		return null;
	    }

	    addMdItemName(mdItemSeq, title, PRIMARY_NAME_TYPE);

	    //TODO: CMU Add the mango stuff --- start
//	    ResultSet resultSet = null;

//	    PreparedStatement insertPublication = sqlDbManager.prepareStatement(conn,
//		INSERT_PUBLICATION_QUERY, Statement.RETURN_GENERATED_KEYS);
//
//	    try {
//	      // skip auto-increment key field #0
//	      insertPublication.setLong(1, mdItemSeq);
//	      insertPublication.setLong(2, publisherSeq);
//	      insertPublication.setString(3, proprietaryId);
//	      sqlDbManager.executeUpdate(insertPublication);
//	      resultSet = insertPublication.getGeneratedKeys();
//
//	      if (!resultSet.next()) {
//		log.error("Unable to create publication table row.");
//		return null;
//	      }
//
//	      publicationSeq = resultSet.getLong(1);
//	      log.debug3(DEBUG_HEADER + "Added publicationSeq = " + publicationSeq);
//	    } catch (Exception sqle) {
//	      log.error("Cannot insert publication", sqle);
//	      log.error("mdItemSeq = '" + mdItemSeq + "'.");
//	      log.error("SQL = '" + INSERT_PUBLICATION_QUERY + "'.");
//	      throw sqle;
//	    } finally {
//
//	    }
	  //TODO: CMU Add the mango stuff --- end
	    return publicationSeq;
	  }
	  
	  /**
	   * Adds to the database the ISSNs of a metadata item.
	   * 
	   * @param conn
	   *          A Connection with the database connection to be used.
	   * @param mdItemSeq
	   *          A Long with the metadata item identifier.
	   * @param pIssn
	   *          A String with the print ISSN of the metadata item.
	   * @param eIssn
	   *          A String with the electronic ISSN of the metadata item.
	   * @throws Exception
	   *           if any problem occurred accessing the database.
	   */
	  private void addMdItemIssns(Long mdItemSeq, String pIssn,
	      String eIssn) throws Exception {
	    final String DEBUG_HEADER = "addMdItemIssns(): ";

	    if (pIssn == null && eIssn == null) {
	      return;
	    }

	    //TODO: CMU mongo replacement --- Start
//	    PreparedStatement insertIssn =
//		sqlDbManager.prepareStatement(INSERT_ISSN_QUERY);
//
//	    try {
//	      if (pIssn != null) {
//		log.debug3(DEBUG_HEADER + "pIssn = " + pIssn);
//		insertIssn.setLong(1, mdItemSeq);
//		insertIssn.setString(2, pIssn);
//		insertIssn.setString(3, P_ISSN_TYPE);
//		int count = sqlDbManager.executeUpdate(insertIssn);
//
//		if (log.isDebug3()) {
//		  log.debug3(DEBUG_HEADER + "count = " + count);
//		  log.debug3(DEBUG_HEADER + "Added PISSN = " + pIssn);
//		}
//	      }
//
//	      if (eIssn != null) {
//		log.debug3(DEBUG_HEADER + "eIssn = " + eIssn);
//		insertIssn.setLong(1, mdItemSeq);
//		insertIssn.setString(2, eIssn);
//		insertIssn.setString(3, E_ISSN_TYPE);
//		int count = sqlDbManager.executeUpdate(insertIssn);
//
//		if (log.isDebug3()) {
//		  log.debug3(DEBUG_HEADER + "count = " + count);
//		  log.debug3(DEBUG_HEADER + "Added EISSN = " + eIssn);
//		}
//	      }
//	    } finally {
//	      SqlDbManager.safeCloseStatement(insertIssn);
//	    }
	  //TODO: CMU mongo replacement --- end
	  }
	  
	  /**
	   * Adds to the database the name of a metadata item, if it does not exist
	   * already.
	   * 
	   * @param conn
	   *          A Connection with the database connection to be used.
	   * @param mdItemSeq
	   *          A Long with the metadata item identifier.
	   * @param mdItemName
	   *          A String with the name to be added, if new.
	   * @throws Exception
	   *           if any problem occurred accessing the database.
	   */
	  private void addNewMdItemName(Long mdItemSeq,
	      String mdItemName) throws Exception {
	    final String DEBUG_HEADER = "addNewMdItemName(): ";

	    if (mdItemName == null) {
	      return;
	    }

	    Map<String, String> titleNames = getMdItemNames(mdItemSeq);

	    for (String name : titleNames.keySet()) {
	      if (name.equals(mdItemName)) {
		log.debug3(DEBUG_HEADER + "Title name = " + mdItemName
		    + " already exists.");
		return;
	      }
	    }

	    addMdItemName(mdItemSeq, mdItemName, NOT_PRIMARY_NAME_TYPE);
	  }
	  
	  /**
	   * Adds to the database the ISBNs of a metadata item.
	   * 
	   * @param conn
	   *          A Connection with the database connection to be used.
	   * @param mdItemSeq
	   *          A Long with the metadata item identifier.
	   * @param pIsbn
	   *          A String with the print ISBN of the metadata item.
	   * @param eIsbn
	   *          A String with the electronic ISBN of the metadata item.
	   * @throws Exception
	   *           if any problem occurred accessing the database.
	   */
	  private void addMdItemIsbns(Long mdItemSeq, String pIsbn,
	      String eIsbn) throws Exception {
	    final String DEBUG_HEADER = "addMdItemIsbns(): ";

	    if (pIsbn == null && eIsbn == null) {
	      return;
	    }

	    //TODO: CMU mango thingy -- Start
//	    PreparedStatement insertIsbn =
//		sqlDbManager.prepareStatement(INSERT_ISBN_QUERY);
//
//	    try {
//	      if (pIsbn != null) {
//		log.debug3(DEBUG_HEADER + "pIsbn = " + pIsbn);
//		insertIsbn.setLong(1, mdItemSeq);
//		insertIsbn.setString(2, pIsbn);
//		insertIsbn.setString(3, P_ISBN_TYPE);
//		int count = sqlDbManager.executeUpdate(insertIsbn);
//
//		if (log.isDebug3()) {
//		  log.debug3(DEBUG_HEADER + "count = " + count);
//		  log.debug3(DEBUG_HEADER + "Added PISBN = " + pIsbn);
//		}
//	      }
//
//	      if (eIsbn != null) {
//		log.debug3(DEBUG_HEADER + "eIsbn = " + eIsbn);
//		insertIsbn.setLong(1, mdItemSeq);
//		insertIsbn.setString(2, eIsbn);
//		insertIsbn.setString(3, E_ISBN_TYPE);
//		int count = sqlDbManager.executeUpdate(insertIsbn);
//
//		if (log.isDebug3()) {
//		  log.debug3(DEBUG_HEADER + "count = " + count);
//		  log.debug3(DEBUG_HEADER + "Added EISBN = " + eIsbn);
//		}
//	      }
//	    } finally {
//	      SqlDbManager.safeCloseStatement(insertIsbn);
//	    }
	  //TODO: CMU mango thingy -- End
	  }
	  
	  /**
	   * Adds to the database the ISSNs of a metadata item, if they do not exist
	   * already.
	   * 
	   * @param conn
	   *          A Connection with the database connection to be used.
	   * @param mdItemSeq
	   *          A Long with the metadata item identifier.
	   * @param pIssn
	   *          A String with the print ISSN of the metadata item.
	   * @param eIssn
	   *          A String with the electronic ISSN of the metadata item.
	   * @throws Exception
	   *           if any problem occurred accessing the database.
	   */
	  private void addNewMdItemIssns(Long mdItemSeq, String pIssn,
	      String eIssn) throws Exception {
	    if (pIssn == null && eIssn == null) {
	      return;
	    }

	  //TODO: CMU mango thingy -- Start
//	    PreparedStatement findIssns =
//		sqlDbManager.prepareStatement(FIND_MD_ITEM_ISSN_QUERY);
//
//	    ResultSet resultSet = null;
//
//	    try {
//	      findIssns.setLong(1, mdItemSeq);
//	      resultSet = sqlDbManager.executeQuery(findIssns);
//
//	      while (resultSet.next()) {
//		if (pIssn != null && pIssn.equals(resultSet.getString(ISSN_COLUMN))
//		    && P_ISSN_TYPE.equals(resultSet.getString(ISSN_TYPE_COLUMN))) {
//		  pIssn = null;
//		}
//
//		if (eIssn != null && eIssn.equals(resultSet.getString(ISSN_COLUMN))
//		    && E_ISSN_TYPE.equals(resultSet.getString(ISSN_TYPE_COLUMN))) {
//		  eIssn = null;
//		}
//	      }
//	    } finally {
//	      SqlDbManager.safeCloseResultSet(resultSet);
//	      SqlDbManager.safeCloseStatement(findIssns);
//	    }
	  //TODO: CMU mango thingy -- end

	    addMdItemIssns(mdItemSeq, pIssn, eIssn);
	  }	  

	  /**
	   * Provides the identifier of a journal if existing or after creating it
	   * otherwise.
	   * 
	   * @param conn
	   *          A Connection with the database connection to be used.
	   * @param pIssn
	   *          A String with the print ISSN of the journal.
	   * @param eIssn
	   *          A String with the electronic ISSN of the journal.
	   * @param publisherSeq
	   *          A Long with the publisher identifier.
	   * @param title
	   *          A String with the name of the journal.
	   * @param date
	   *          A String with the publication date of the journal.
	   * @param proprietaryId
	   *          A String with the proprietary identifier of the journal.
	   * @return a Long with the identifier of the journal.
	   * @throws Exception
	   *           if any problem occurred accessing the database.
	   */
	  private Long findOrCreateJournal(String pIssn,
	      String eIssn, Long publisherSeq, String title, String date,
	      String proprietaryId) throws Exception {
	    final String DEBUG_HEADER = "findOrCreateJournal(): ";
	    Long publicationSeq = null;
	    Long mdItemSeq = null;
	    Long parentSeq = null;

	    // Skip it if it no title name or ISSNs, as it will not be possible to
	    // find the journal to which it belongs in the database.
	    if (StringUtil.isNullString(title) && pIssn == null && eIssn == null) {
	      log.error("Title for article cannot be created as it has no name or ISSN "
		  + "values.");
	      return publicationSeq;
	    }

	    // Find the journal.
	    publicationSeq =
		findPublication(title, publisherSeq, pIssn, eIssn, null, null,
				MD_ITEM_TYPE_JOURNAL);
	    log.debug3(DEBUG_HEADER + "publicationSeq = " + publicationSeq);

	    // Check whether it is a new journal.
	    if (publicationSeq == null) {
	      // Yes: Add to the database the new journal.
	      publicationSeq =
		  addPublication(parentSeq, MD_ITEM_TYPE_JOURNAL, date, title,
				 proprietaryId, publisherSeq);
	      log.debug3(DEBUG_HEADER + "new publicationSeq = " + publicationSeq);

	      // Skip it if the new journal could not be added.
	      if (publicationSeq == null) {
		log.error("Publication for new journal '" + title
		    + "' could not be created.");
		return publicationSeq;
	      }

	      // Get the journal metadata item identifier.
	      mdItemSeq = findPublicationMetadataItem(publicationSeq);
	      log.debug3(DEBUG_HEADER + "mdItemSeq = " + mdItemSeq);

	      // Add to the database the new journal ISSN values.
	      addMdItemIssns(mdItemSeq, pIssn, eIssn);
	      log.debug3(DEBUG_HEADER + "added title ISSNs.");
	    } else {
	      // No: Get the journal metadata item identifier.
	      mdItemSeq = findPublicationMetadataItem(publicationSeq);
	      log.debug3(DEBUG_HEADER + "mdItemSeq = " + mdItemSeq);

	      // No: Add to the database the journal name in the metadata as an
	      // alternate, if new.
	      addNewMdItemName(mdItemSeq, title);
	      log.debug3(DEBUG_HEADER + "added new title name.");

	      // Add to the database the ISSN values in the metadata, if new.
	      addNewMdItemIssns(mdItemSeq, pIssn, eIssn);
	      log.debug3(DEBUG_HEADER + "added new title ISSNs.");
	    }

	    return publicationSeq;
	  }
	  
	  /**
	   * Provides the identifier of a publication by its title, publisher, ISSNs and
	   * ISBNs.
	   * 
	   * @param conn
	   *          A Connection with the database connection to be used.
	   * @param title
	   *          A String with the title of the publication.
	   * @param publisherSeq
	   *          A Long with the publisher identifier.
	   * @param pIssn
	   *          A String with the print ISSN of the publication.
	   * @param eIssn
	   *          A String with the electronic ISSN of the publication.
	   * @param pIsbn
	   *          A String with the print ISBN of the publication.
	   * @param eIsbn
	   *          A String with the electronic ISBN of the publication.
	   * @param mdItemType
	   *          A String with the type of publication to be identified.
	   * @return a Long with the identifier of the publication.
	   * @throws Exception
	   *           if any problem occurred accessing the database.
	   */
	  private Long findPublicationByIssnsOrIsbnsOrName(String title, Long publisherSeq, String pIssn, String eIssn, String pIsbn,
	      String eIsbn, String mdItemType) throws Exception {
	    Long publicationSeq =
		findPublicationByIssns(publisherSeq, pIssn, eIssn, mdItemType);

	    if (publicationSeq == null) {
	      publicationSeq =
		  findPublicationByIsbnsOrName(title, publisherSeq, pIsbn, eIsbn,
					       mdItemType);
	    }

	    return publicationSeq;
	  }	  
	  
	  /**
	   * Provides the identifier of a publication by its publisher and ISSNs.
	   * 
	   * @param conn
	   *          A Connection with the database connection to be used.
	   * @param publisherSeq
	   *          A Long with the publisher identifier.
	   * @param pIssn
	   *          A String with the print ISSN of the publication.
	   * @param eIssn
	   *          A String with the electronic ISSN of the publication.
	   * @param mdItemType
	   *          A String with the type of publication to be identified.
	   * @return a Long with the identifier of the publication.
	   * @throws Exception
	   *           if any problem occurred accessing the database.
	   */
	  private Long findPublicationByIssns(Long publisherSeq,
	      String pIssn, String eIssn, String mdItemType) throws Exception {
	    final String DEBUG_HEADER = "findPublicationByIssns(): ";
	    log.debug3(DEBUG_HEADER + "publisherSeq = " + publisherSeq);
	    log.debug3(DEBUG_HEADER + "pIssn = " + pIssn);
	    log.debug3(DEBUG_HEADER + "eIssn = " + eIssn);
	    log.debug3(DEBUG_HEADER + "mdItemType = " + mdItemType);
	    Long publicationSeq = null;
	    
	    
	    DBCollection collection = mongoDatabase.getCollection(PUBLICATIONS_COLLECTION);	    

	    DBObject finalQuery = QueryBuilder.start().and(
                QueryBuilder.start("publisherSeq").is(publisherSeq).get(),
                QueryBuilder.start("mdItemType").is(mdItemType).get(),
                QueryBuilder.start().or(
                        QueryBuilder.start("pIssn").is("pIssn").get(),
                        QueryBuilder.start("eIssn").is("eIssn").get()
                ).get()
        ).get();

		try {
			DBObject result = collection.findOne(finalQuery);
			publicationSeq = MongoHelper.readLong(result, "longId");
		} catch (Exception e) {

			log.error("Cannot find publication", e);
			log.error("publisherSeq = '" + publisherSeq + "'.");
			log.error("pIssn = " + pIssn);
			log.error("eIssn = " + eIssn);
			throw e;
		}
	    return publicationSeq;
	  }

	  /**
	   * Provides the identifier of a publication by its publisher and ISBNs.
	   * 
	   * @param conn
	   *          A Connection with the database connection to be used.
	   * @param publisherSeq
	   *          A Long with the publisher identifier.
	   * @param pIsbn
	   *          A String with the print ISBN of the publication.
	   * @param eIsbn
	   *          A String with the electronic ISBN of the publication.
	   * @param mdItemType
	   *          A String with the type of publication to be identified.
	   * @return a Long with the identifier of the publication.
	   * @throws Exception
	   *           if any problem occurred accessing the database.
	   */
	  private Long findPublicationByIsbns(Long publisherSeq,
	      String pIsbn, String eIsbn, String mdItemType) throws Exception {
	    final String DEBUG_HEADER = "findPublicationByIsbns(): ";
	    Long publicationSeq = null;
	    
	    
	    DBCollection collection = mongoDatabase.getCollection(PUBLICATIONS_COLLECTION);	    

	    DBObject finalQuery = QueryBuilder.start().and(
                QueryBuilder.start("publisherSeq").is(publisherSeq).get(),
                QueryBuilder.start("mdItemType").is(mdItemType).get(),
                QueryBuilder.start().or(
                        QueryBuilder.start("pIsbn").is(pIsbn).get(),
                        QueryBuilder.start("eIsbn").is(eIsbn).get()
                ).get()
        ).get();

		try {
			DBObject result = collection.findOne(finalQuery);
			publicationSeq = MongoHelper.readLong(result, "longId");
		} catch (Exception e) {

		      log.error("Cannot find publication", e);
		      log.error("publisherSeq = '" + publisherSeq + "'.");
		      log.error("pIsbn = " + pIsbn);
		      log.error("eIsbn = " + eIsbn);
		      
			throw e;
		}
	    return publicationSeq;
	  }	  
	  
	  /**
	   * Provides the identifier of a publication by its publisher and title or
	   * ISSNs.
	   * 
	   * @param conn
	   *          A Connection with the database connection to be used.
	   * @param title
	   *          A String with the title of the publication.
	   * @param publisherSeq
	   *          A Long with the publisher identifier.
	   * @param pIssn
	   *          A String with the print ISSN of the publication.
	   * @param eIssn
	   *          A String with the electronic ISSN of the publication.
	   * @param mdItemType
	   *          A String with the type of publication to be identified.
	   * @return a Long with the identifier of the publication.
	   * @throws Exception
	   *           if any problem occurred accessing the database.
	   */
	  private Long findPublicationByIssnsOrName(String title,
	      Long publisherSeq, String pIssn, String eIssn, String mdItemType)
		  throws Exception {
	    Long publicationSeq =
		findPublicationByIssns(publisherSeq, pIssn, eIssn, mdItemType);

	    if (publicationSeq == null) {
	      publicationSeq =
		  findPublicationByName(title, publisherSeq, mdItemType);
	    }

	    return publicationSeq;
	  }	  
	  
	  /**
	   * Provides the identifier of a publication by its publisher and title or
	   * ISBNs.
	   * 
	   * @param conn
	   *          A Connection with the database connection to be used.
	   * @param title
	   *          A String with the title of the publication.
	   * @param publisherSeq
	   *          A Long with the publisher identifier.
	   * @param pIsbn
	   *          A String with the print ISBN of the publication.
	   * @param eIsbn
	   *          A String with the electronic ISBN of the publication.
	   * @param mdItemType
	   *          A String with the type of publication to be identified.
	   * @return a Long with the identifier of the publication.
	   * @throws Exception
	   *           if any problem occurred accessing the database.
	   */
	  private Long findPublicationByIsbnsOrName(String title,
	      Long publisherSeq, String pIsbn, String eIsbn, String mdItemType)
	      throws Exception {
	    Long publicationSeq =
		findPublicationByIsbns(publisherSeq, pIsbn, eIsbn, mdItemType);

	    if (publicationSeq == null) {
	      publicationSeq =
		  findPublicationByName(title, publisherSeq, mdItemType);
	    }

	    return publicationSeq;
	  }	  
	  
	  /**
	   * Provides the identifier of a publication by its title and publisher.
	   * 
	   * @param conn
	   *          A Connection with the database connection to be used.
	   * @param title
	   *          A String with the title of the publication.
	   * @param publisherSeq
	   *          A Long with the publisher identifier.
	   * @param mdItemType
	   *          A String with the type of publication to be identified.
	   * @return a Long with the identifier of the publication.
	   * @throws Exception
	   *           if any problem occurred accessing the database.
	   */
	  private Long findPublicationByName(String title,
	      Long publisherSeq, String mdItemType) throws Exception {
	    final String DEBUG_HEADER = "findPublicationByName(): ";
	    Long publicationSeq = null;
	    
	    DBCollection collection = mongoDatabase.getCollection(PUBLICATIONS_COLLECTION);	    

	    DBObject finalQuery = QueryBuilder.start().and(
                QueryBuilder.start("publisherSeq").is(publisherSeq).get(),
                QueryBuilder.start("name").is(title).get() //NOTE SQL --> Search Name Colunm with Title.
                ).get();

		try {
			DBObject result = collection.findOne(finalQuery);
			publicationSeq = MongoHelper.readLong(result, "longId");
		} catch (Exception e) {

			log.error("Cannot find publication", e);
			log.error("publisherSeq = '" + publisherSeq + "'.");
			log.error("title = " + title);
			
			throw e;
		}
	    return publicationSeq;
	  }
	  /**
	   * Adds to the database the ISBNs of a metadata item, if they do not exist
	   * already.
	   * 
	   * @param conn
	   *          A Connection with the database connection to be used.
	   * @param mdItemSeq
	   *          A Long with the metadata item identifier.
	   * @param pIsbn
	   *          A String with the print ISBN of the metadata item.
	   * @param eIsbn
	   *          A String with the electronic ISBN of the metadata item.
	   * @throws Exception
	   *           if any problem occurred accessing the database.
	   */
	  private void addNewMdItemIsbns(Long mdItemSeq, String pIsbn,
	      String eIsbn) throws Exception {
	    if (pIsbn == null && eIsbn == null) {
	      return;
	    }

	  //TODO: CMU mango thingy -- Start
//	    PreparedStatement findIsbns =
//		sqlDbManager.prepareStatement(FIND_MD_ITEM_ISBN_QUERY);
//
//	    ResultSet resultSet = null;
//
//	    try {
//	      findIsbns.setLong(1, mdItemSeq);
//	      resultSet = sqlDbManager.executeQuery(findIsbns);
//
//	      while (resultSet.next()) {
//		if (pIsbn != null && pIsbn.equals(resultSet.getString(ISBN_COLUMN))
//		    && P_ISBN_TYPE.equals(resultSet.getString(ISBN_TYPE_COLUMN))) {
//		  pIsbn = null;
//		}
//
//		if (eIsbn != null && eIsbn.equals(resultSet.getString(ISBN_COLUMN))
//		    && E_ISBN_TYPE.equals(resultSet.getString(ISBN_TYPE_COLUMN))) {
//		  eIsbn = null;
//		}
//	      }
//	    } finally {
//	      SqlDbManager.safeCloseResultSet(resultSet);
//	      SqlDbManager.safeCloseStatement(findIsbns);
//	    }
	  //TODO: CMU mango thingy -- End
	    addMdItemIsbns(mdItemSeq, pIsbn, eIsbn);
	  }	  

	  /**
	   * Provides the names of a metadata item.
	   * 
	   * @param conn
	   *          A Connection with the database connection to be used.
	   * @param mdItemSeq
	   *          A Long with the metadata item identifier.
	   * @return a Map<String, String> with the names and name types of the metadata
	   *         item.
	   * @throws Exception
	   *           if any problem occurred accessing the database.
	   */
	  private Map<String, String> getMdItemNames(Long mdItemSeq)
	      throws Exception {
	    final String DEBUG_HEADER = "getMdItemNames(): ";
	    Map<String, String> names = new HashMap<String, String>();
	  //TODO: CMU mango thingy -- Start
//	    PreparedStatement getNames =
//		sqlDbManager.prepareStatement(FIND_MD_ITEM_NAME_QUERY);
//	    ResultSet resultSet = null;
//
//	    try {
//	      getNames.setLong(1, mdItemSeq);
//	      resultSet = sqlDbManager.executeQuery(getNames);
//	      while (resultSet.next()) {
//		names.put(resultSet.getString(NAME_COLUMN),
//			  resultSet.getString(NAME_TYPE_COLUMN));
//		log.debug3(DEBUG_HEADER + "Found metadata item name = '"
//		    + resultSet.getString(NAME_COLUMN) + "' of type '"
//		    + resultSet.getString(NAME_TYPE_COLUMN) + "'.");
//	      }
//	    } finally {
//	      SqlDbManager.safeCloseResultSet(resultSet);
//	      SqlDbManager.safeCloseStatement(getNames);
//	    }
	  //TODO: CMU mango thingy -- End
	    return names;
	  }	  
	  
}
