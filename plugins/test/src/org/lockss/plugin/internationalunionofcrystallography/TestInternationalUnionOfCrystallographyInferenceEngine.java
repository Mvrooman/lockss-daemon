package org.lockss.plugin.internationalunionofcrystallography;

import java.io.IOException;

import org.lockss.daemon.PluginException;
import org.lockss.db.MongoDbManager;
import org.lockss.test.LockssTestCase;
import org.lockss.test.MockArchivalUnit;

public class TestInternationalUnionOfCrystallographyInferenceEngine extends LockssTestCase {

	public void testInferenceEngine() {
		MongoDbManager dbManager = new MongoDbManager();
		dbManager.startService();
		MockArchivalUnit au = new MockArchivalUnit();
		
		au.setAuId("base_url~http%3A%2F%2Fjournals%2Eiucr%2Eorg%2F&issue~01&journal_id~e&scripts_url~http%3A%2F%2Fscripts%2Eiucr%2Eorg%2F&year~2011");
		au.setPluginId("org|lockss|plugin|internationalunionofcrystallography|InternationalUnionOfCrystallographyPlugin");
		au.setPlugin(null);
		
		try {
			new InternationalUnionOfCrystallographyJenaInferenceEngine().extract(au, dbManager);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (PluginException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
}
