package org.lockss.plugin.internationalunionofcrystallography;

import static org.lockss.db.MongoDbManager.*;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import org.lockss.daemon.PluginException;
import org.lockss.db.DbManager;
import org.lockss.db.MongoDbManager;
import org.lockss.db.MongoHelper;
import org.lockss.extractor.JenaMetadataExtractor;
import org.lockss.metadata.ArticleMetadataBuffer.ArticleMetadataInfo;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.util.Logger;

import com.google.gson.Gson;
import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.query.*;
import com.hp.hpl.jena.rdf.model.*;
import com.hp.hpl.jena.tdb.TDBFactory;
import com.hp.hpl.jena.vocabulary.VCARD;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.QueryBuilder;

public class InternationalUnionOfCrystallographyJenaMetadataExtractor implements
		JenaMetadataExtractor {
	static Logger log = Logger
			.getLogger(InternationalUnionOfCrystallographyJenaMetadataExtractor.class
					.getName());

	@Override
	public void extract(ArchivalUnit au, DbManager dbManager)
			throws IOException, PluginException {
		log.info("EXTRACTION STARTED!");
		
		String auId = au.getAuId();
		String pluginId = au.getPluginId();
		
		// TODO: CHEATING
		DB mongoDatabase = ((MongoDbManager)dbManager).getDb();
		
		// find plugin sequence number
		DBCollection collection = mongoDatabase.getCollection(PLUGIN_COLLECTION);
		BasicDBObject query = new BasicDBObject("pluginId", pluginId);
		DBObject result = collection.findOne(query);
		Long pluginSeq = MongoHelper.readLong(result, "longId");
		
		// find raw au
		DBCollection auCollection = mongoDatabase.getCollection(AUS_COLLECTION);
		DBObject finalQuery = QueryBuilder.start().and(
                QueryBuilder.start("pluginSeq").is(pluginSeq).get(),
                QueryBuilder.start("auKey").is(auId).get()).get();
		DBObject auTarget = auCollection.findOne(finalQuery);
		
		// get the list of metadata
		BasicDBList articleMetadata = (BasicDBList) auTarget.get("articleMetadata");
		Iterator<Object> it = articleMetadata.iterator();
		
		// get the Jena store
		String directory = "db/jena/DB1";
		new File(directory).mkdirs();
		Dataset dataset = TDBFactory.createDataset(directory);
		Model m = dataset.getDefaultModel();
		
		// establish some properties
		String baseUri = "http://www.iucr.org/__data/iucr/cif/standard#";
		Property PDRM = m.createProperty(baseUri, "_diffrn_radiation_monochromator");
		
		while(it.hasNext()) {
			DBObject obj = (DBObject) it.next();
			Gson gson = new Gson();
			ArticleMetadataInfo metadataJson = gson.fromJson(obj.toString(), ArticleMetadataInfo.class);
			
			String drm = metadataJson.additionalMetadata.get("_diffrn_radiation_monochromator");
			
			Resource article = m.createResource(metadataJson.accessUrl);
			
			if(drm != null) {
				article.addProperty(PDRM, drm);
			}
		}
		
		m.write(System.out, "N-TRIPLE");
		
		StmtIterator iter = m.listStatements(
			    new SimpleSelector(null, PDRM, (RDFNode) null) {
			        public boolean selects(Statement s)
			            {return s.getString().endsWith("ite");}
			    });
			
		log.info(""+iter.toList().size());
		
		String queryString = "SELECT * WHERE { ?o <http://www.iucr.org/__data/iucr/cif/standard#_diffrn_radiation_monochromator> \"'silicon 111'\" }";
		Query qery = QueryFactory.create(queryString);
		
		QueryExecution qexec = QueryExecutionFactory.create(qery, m);

		try {
			ResultSet results = qexec.execSelect();
			for( ; results.hasNext();){
				QuerySolution soln = results.nextSolution();
				RDFNode n = soln.get("o");
				if(n.isLiteral()){
				log.info(""+((Literal)n).getLexicalForm() );
				}else{
					Resource r = (Resource)n;
					log.info(""+r.getURI());
				}
			}
		
		}finally {qexec.close();}
		dataset.close();
	}
}
