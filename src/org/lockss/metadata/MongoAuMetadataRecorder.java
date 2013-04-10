package org.lockss.metadata;

import static org.lockss.metadata.MetadataManager.NEVER_EXTRACTED_EXTRACTION_TIME;
import java.util.Iterator;
import org.lockss.db.MongoDbManager;
import org.lockss.metadata.ArticleMetadataBuffer.ArticleMetadataInfo;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.plugin.PluginManager;

public class MongoAuMetadataRecorder extends AuMetadataRecorder {

	// The metadata manager.
	private final MongoMetadataManager mdManager; //TODO: Change to a Mongo DB Manager

	// The database manager.
	private final MongoDbManager mongoDbManager;

	// Database identifiers related to the AU. 
	private Long publisherSeq = null;
	private Long pluginSeq = null;
	private Long auSeq = null;
	private Long auMdSeq = null;

	public MongoAuMetadataRecorder(ReindexingTask task,
			MetadataManager mdManager, ArchivalUnit au) {
		super(task, mdManager, au);
		this.mdManager = (MongoMetadataManager) mdManager;
		mongoDbManager = (MongoDbManager) mdManager.getDbManager();
		// TODO Auto-generated constructor stub
	}

	@Override
	void recordMetadata(Iterator<ArticleMetadataInfo> mditr) throws Exception {
		final String DEBUG_HEADER = "recordMetadata(): ";
		// Loop through the metadata for each article.
		while (mditr.hasNext()) {
			// Normalize all the metadata fields.
			ArticleMetadataInfo normalizedMdInfo = normalizeMetadata(mditr.next());

			// Store the metadata fields in the database.
			storeMetadata(normalizedMdInfo);

			// Count the processed article.
			task.incrementUpdatedArticleCount();
			log.debug3(DEBUG_HEADER + "updatedArticleCount = "
					+ task.getUpdatedArticleCount());
		}

		if (auSeq != null) {
			// Update the AU last extraction timestamp.
			mdManager.updateAuLastExtractionTime(auSeq);
		} else {
			log.warning("auSeq is null for auid = '" + au.getAuId() + "'.");
		}
	}

	/**
	 * Stores in the database metadata for the Archival Unit.
	 * 
	 * @param conn
	 *          A Connection with the connection to the database
	 * @param mdinfo
	 *          An ArticleMetadataInfo providing the metadata.
	 * @throws Exception 
	 */
	private void storeMetadata(ArticleMetadataInfo mdinfo) throws Exception {
		final String DEBUG_HEADER = "storeMetadata(): ";
		if (log.isDebug3()) {
			log.debug3(DEBUG_HEADER + "Starting: auId = " + auId);
			log.debug3(DEBUG_HEADER + "auKey = " + auKey);
			log.debug3(DEBUG_HEADER + "auMdSeq = " + auMdSeq);
			log.debug3(DEBUG_HEADER + "mdinfo.articleTitle = " + mdinfo.articleTitle);
		}

		// Check whether the publisher has not been located in the database.
		if (publisherSeq == null) {
			// Yes: Get the publisher received in the metadata.
			String publisher = mdinfo.publisher;
			log.debug3(DEBUG_HEADER + "publisher = " + publisher);

			// Find the publisher or create it.
			publisherSeq = mdManager.findOrCreatePublisher(publisher);
			log.debug3(DEBUG_HEADER + "publisherSeq = " + publisherSeq);
		}

		// Get any ISBN values received in the metadata.
		String pIsbn = mdinfo.isbn;
		log.debug3(DEBUG_HEADER + "pIsbn = " + pIsbn);

		String eIsbn = mdinfo.eisbn;
		log.debug3(DEBUG_HEADER + "eIsbn = " + eIsbn);

		// Get any ISSN values received in the metadata.
		String pIssn = mdinfo.issn;
		log.debug3(DEBUG_HEADER + "pIssn = " + pIssn);

		String eIssn = mdinfo.eissn;
		log.debug3(DEBUG_HEADER + "eIssn = " + eIssn);

		// Get the proprietary identifier received in the metadata.
		String proprietaryId = mdinfo.proprietaryIdentifier;
		log.debug3(DEBUG_HEADER + "proprietaryId = " + proprietaryId);

		// Get the publication date received in the metadata.
		String date = mdinfo.pubDate;
		log.debug3(DEBUG_HEADER + "date = " + date);

		// Get the volume received in the metadata.
		String volume = mdinfo.volume;
		log.debug3(DEBUG_HEADER + "volume = " + volume);

		// Get the publication to which this metadata belongs.
		Long publicationSeq =
				mdManager.findOrCreatePublication(pIssn, eIssn, pIsbn, eIsbn,
						publisherSeq, mdinfo.journalTitle,
						date, proprietaryId, volume);
		log.debug3(DEBUG_HEADER + "publicationSeq = " + publicationSeq);

		// Skip it if the publication could not be found or created.
		if (publicationSeq == null) {
			log.debug3(DEBUG_HEADER + "Done: publicationSeq is null.");
			return;
		}

		// Check whether the plugin has not been located in the database.
		if (pluginSeq == null) {
			// Yes: Get its identifier.
			String pluginId = PluginManager.pluginIdFromAuId(auId);
			log.debug3(DEBUG_HEADER + "pluginId = " + pluginId);

			// Find the plugin or create it.
			pluginSeq = mdManager.findOrCreatePlugin(pluginId, platform);
			log.debug3(DEBUG_HEADER + "pluginSeq = " + pluginSeq);

			// Skip it if the plugin could not be found or created.
			if (pluginSeq == null) {
				log.debug3(DEBUG_HEADER + "Done: pluginSeq is null.");
				return;
			}
		}

		// Check whether the Archival Unit has not been located in the database.
		if (auSeq == null) {
			// Yes: Find it or create it.
			auSeq = mdManager.findOrCreateAu(pluginSeq, auKey);
			log.debug3(DEBUG_HEADER + "auSeq = " + auSeq);

			// Skip it if the Archival Unit could not be found or created.
			if (auSeq == null) {
				log.debug3(DEBUG_HEADER + "Done: auSeq is null.");
				return;
			}
		}

		// Yes: Add to the database the new Archival Unit metadata.
		auMdSeq = mdManager.addAuMd(auSeq, pluginVersion,
				NEVER_EXTRACTED_EXTRACTION_TIME, publicationSeq, mdinfo);
		log.debug3(DEBUG_HEADER + "new auSeq = " + auMdSeq);

		
		// Update or create the metadata item.
		//updateOrCreateMdItem(newAu, publicationSeq, mdinfo);

		log.debug3(DEBUG_HEADER + "Done.");
	}

	/**
	 * Updates the metadata version an Archival Unit in the database.
	 * 
	 * @param auMdSeq
	 *          A Long with the identifier of the archival unit metadata.
	 * @param version
	 *          A String with the archival unit metadata version.
	 * @throws Exception
	 *           if any problem occurred accessing the database.
	 */
	private void updateAuMd(Long auMdSeq, int version)
			throws Exception {
		// TODO: Implement
	}

	/**
	 * Updates a metadata item if it exists in the database, otherwise it creates
	 * it.
	 * 
	 * @param newAu
	 *          A boolean with the indication of whether this is a new AU.
	 * @param publicationSeq
	 *          A Long with the identifier of the publication.
	 * @param mdinfo
	 *          An ArticleMetadataInfo providing the metadata.
	 * @throws Exception
	 *           if any problem occurred accessing the database.
	 */
	private void updateOrCreateMdItem(boolean newAu,
			Long publicationSeq, ArticleMetadataInfo mdinfo) throws Exception {
		// TODO: Implement
	}

}
