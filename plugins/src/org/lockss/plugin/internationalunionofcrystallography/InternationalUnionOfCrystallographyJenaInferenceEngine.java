package org.lockss.plugin.internationalunionofcrystallography;

import static org.lockss.db.MongoDbManager.AUS_COLLECTION;
import static org.lockss.db.MongoDbManager.PLUGIN_COLLECTION;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

import org.apache.jena.atlas.logging.Log;
import org.lockss.daemon.PluginException;
import org.lockss.db.DbManager;
import org.lockss.db.MongoDbManager;
import org.lockss.db.MongoHelper;
import org.lockss.extractor.JenaInferenceEngine;
import org.lockss.metadata.ArticleMetadataBuffer.ArticleMetadataInfo;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.plugin.ArticleFiles;
import org.lockss.util.Logger;

import com.google.gson.Gson;
import com.hp.hpl.jena.query.*;
import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.SimpleSelector;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.tdb.TDBFactory;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.QueryBuilder;

/**
 * @author TofuPrd
 * 
 */
/**
 * @author TofuPrd
 *
 */
/**
 * @author TofuPrd
 *
 */
public class InternationalUnionOfCrystallographyJenaInferenceEngine implements
		JenaInferenceEngine {

	static Logger log = Logger
			.getLogger(InternationalUnionOfCrystallographyJenaInferenceEngine.class
					.getName());

	private final String jenaDirectory = "db/jena/DB1";

	private Dataset dataset = null;
	private Model model = null;
	private Resource article = null;

	/**
	 * 
	 */
	public InternationalUnionOfCrystallographyJenaInferenceEngine() {
		new File(jenaDirectory).mkdirs();
		dataset = TDBFactory.createDataset(jenaDirectory);
		model = dataset.getDefaultModel();
	}

	/**
	 * The extract method.
	 */
	@Override
	public void extract(ArchivalUnit au, DbManager dbManager)
			throws IOException, PluginException {
		log.info("--- INFERENCE STARTED ---");

		String auId = au.getAuId();
		String pluginId = au.getPluginId();

		DB mongoDatabase = ((MongoDbManager) dbManager).getDb();

		// find plugin sequence number
		DBCollection collection = mongoDatabase
				.getCollection(PLUGIN_COLLECTION);
		BasicDBObject query = new BasicDBObject("pluginId", pluginId);
		DBObject result = collection.findOne(query);
		Long pluginSeq = MongoHelper.readLong(result, "longId");

		// find raw au
		DBCollection auCollection = mongoDatabase.getCollection(AUS_COLLECTION);
		DBObject finalQuery = QueryBuilder
				.start()
				.and(QueryBuilder.start("pluginSeq").is(pluginSeq).get(),
						QueryBuilder.start("auKey").is(auId).get()).get();
		DBObject auTarget = auCollection.findOne(finalQuery);

		// get the list of metadata
		BasicDBList articleMetadata = (BasicDBList) auTarget
				.get("articleMetadata");
		Iterator<Object> it = articleMetadata.iterator();

		while (it.hasNext()) {
			DBObject obj = (DBObject) it.next();

			Gson gson = new Gson();
			ArticleMetadataInfo metadataJson = gson.fromJson(obj.toString(),
					ArticleMetadataInfo.class);

			// Property propertyForQuery = model.getProperty("");
			article = model.getResource(metadataJson.accessUrl);
			StmtIterator iter = model.listStatements(new SimpleSelector(
					article, null, (RDFNode) null) {
				public boolean selects(Statement s) {
					return true;
				}
			});

			log.info("Found Results!! - " + iter.toList().size());
			getMachineByAuthor(metadataJson);

		}

		getMachineCount();

		// Query Author that uses a machine.
		Property propertyForQuery = model.getProperty("usesMachine");
		StmtIterator iter = model.listStatements(new SimpleSelector(null,
				propertyForQuery, (RDFNode) null) {
			public boolean selects(Statement s) {
				return s.getString().equalsIgnoreCase("'SMART (Bruker, 2007)'");
			}
		});

		while (iter.hasNext()) {
			log.info(iter.next().getSubject().toString());
		}

		// Query the machines used by an author
		Property propertyForQuery2 = model.getProperty("usesMachine");
		StmtIterator iter2 = model.listStatements(new SimpleSelector(null,
				propertyForQuery2, (RDFNode) null) {
			public boolean selects(Statement s) {
				return s.getSubject().toString().equalsIgnoreCase("Fan, Y.-Q.");
			}
		});

		while (iter2.hasNext()) {
			log.info(iter2.next().getObject().toString());
		}

		// Query for the machine count
		Property propertyForQuery3 = model.getProperty("hasMachineUsageCount");
		StmtIterator iter3 = model.listStatements(new SimpleSelector(null,
				propertyForQuery3, (RDFNode) null) {
			public boolean selects(Statement s) {
				return true;
			}
		});

		while (iter3.hasNext()) {
			Statement stmt = iter3.next();
			log.info(stmt.getSubject().toString() + " -- "
					+ stmt.getObject().toString());

		}

	}

	/**
	 * Save the metadata to jena
	 * @param metadataJson
	 */
	private void getMachineByAuthor(ArticleMetadataInfo metadataJson) {
		Statement authorSet = getStatementFromProperty("authorSet");

		Statement machineSet = getStatementFromProperty("_computing_data_collection");
		// log.info("Objecg Found - " + machineSet.getObject().toString());

		if (machineSet == null)
			return;

		String[] authors = authorSet.getObject().toString().split(";");
		Property property = model.getProperty("usesMachine");
		for (int i = 0; i < authors.length; i++) {
			Resource authorResource = model.createResource(authors[i]);
			authorResource.addProperty(property, (String) machineSet
					.getObject().toString());
		}

	}

	/**
	 *Used to get the machine Count and save it to jena 
	 */
	private void getMachineCount() {
		Property property = model.getProperty("usesMachine");
		Hashtable<String, Integer> map = new Hashtable<String, Integer>();
		StmtIterator iter2 = model.listStatements(new SimpleSelector(null,
				property, (RDFNode) null) {
			public boolean selects(Statement s) {
				return true;
			}
		});

		while (iter2.hasNext()) {
			String machineName = iter2.next().getObject().toString();
			Integer currentCount = 0;
			if (map.containsKey(machineName)) {
				currentCount = map.get(machineName);
			}
			currentCount++;
			map.put(machineName, currentCount);
		}

		Property property2 = model.createProperty("hasMachineUsageCount");
		Iterator<String> mapIterator = map.keySet().iterator();

		while (mapIterator.hasNext()) {
			String machine = mapIterator.next();
			int machineTotal = map.get(machine);
			Resource machineResource = model.createResource(machine);
			machineResource.removeAll(property2);
			machineResource.addProperty(property2, "" + machineTotal);
		}
	}

	/**
	 * Get the statement based on the predicate
	 * @param predicate
	 * @return
	 */
	private Statement getStatementFromProperty(String predicate) {

		Property property = model.createProperty(predicate);
		StmtIterator iter = model.listStatements(new SimpleSelector(article,
				property, (RDFNode) null) {

			public boolean selects(Statement s) {
				return true;
			}
		});

		if (iter.hasNext()) {
			return iter.next();
		} else
			return null;
	}

}
