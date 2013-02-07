/*

Copyright (c) 2000-2012 Board of Trustees of Leland Stanford Jr. University,
all rights reserved.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL
STANFORD UNIVERSITY BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

Except as contained in this notice, the name of Stanford University shall not
be used in advertising or otherwise to promote the sale, use or other dealings
in this Software without prior written authorization from Stanford University.
*/

package org.lockss.plugin.casaeditriceclueb;

import java.io.File;
import java.util.regex.Pattern;

import org.lockss.config.ConfigManager;
import org.lockss.config.Configuration;
import org.lockss.plugin.*;
import org.lockss.plugin.simulated.SimulatedArchivalUnit;
import org.lockss.plugin.simulated.SimulatedContentGenerator;
import org.lockss.repository.LockssRepositoryImpl;
import org.lockss.test.*;
import org.lockss.util.*;

public class TestCasaEditriceCluebSourceArticleIteratorFactory extends ArticleIteratorTestCase {
	
	private SimulatedArchivalUnit sau;	// Simulated AU to generate content
	
	private final String PLUGIN_NAME = "org.lockss.plugin.casaeditriceclueb.ClockssCasaEditriceCluebSourcePlugin";
	private static final int DEFAULT_FILESIZE = 3000;

  public void setUp() throws Exception {
    super.setUp();
    String tempDirPath = setUpDiskSpace();

    au = createAu();
    sau = PluginTestUtil.createAndStartSimAu(simAuConfig(tempDirPath));
  }
  
  public void tearDown() throws Exception {
	    sau.deleteContentTree();
	    super.tearDown();
	  }

  protected ArchivalUnit createAu() throws ArchivalUnit.ConfigurationException {
    return
      PluginTestUtil.createAndStartAu(PLUGIN_NAME, aipAuConfig());
  }
  
  Configuration simAuConfig(String rootPath) {
	    Configuration conf = ConfigManager.newConfiguration();
	    conf.put("root", rootPath);
	    conf.put("base_url", "http://www.example.com/");
	    conf.put("year", "2012");
	    conf.put("depth", "1");
	    conf.put("branch", "4");
	    conf.put("numFiles", "7");
	    conf.put("fileTypes",
	             "" + (  SimulatedContentGenerator.FILE_TYPE_XML
	                   | SimulatedContentGenerator.FILE_TYPE_PDF));
	    conf.put("binFileSize", ""+DEFAULT_FILESIZE);
	    return conf;
	  }
  
  Configuration aipAuConfig() {
	    return ConfigurationUtil.fromArgs("base_url",
				 "http://www.example.com/",
				 "year", "2012");
	  }

  public void testRoots() throws Exception {
    SubTreeArticleIterator artIter = createSubTreeIter();
    assertEquals(ListUtil.list("http://www.example.com/2012"),
		 getRootUrls(artIter));
  }

  public void testUrlsWithPrefixes() throws Exception {
    SubTreeArticleIterator artIter = createSubTreeIter();
    Pattern pat = getPattern(artIter);
    
    assertNotMatchesRE(pat, "http://www.wrong.com/2012/CLUEB_chapters.zip!/0000000X/00.0000_0000.xml");
    assertNotMatchesRE(pat, "http://www.example.com/0000/CLUEB_chapters.zip!/0000000X/00.0000_0000.xml");
    assertNotMatchesRE(pat, "http://www.example.com/2012/wrong_chapters.zip!/0000000X/00.0000_0000.xml");
    assertNotMatchesRE(pat, "http://www.example.com/2012/CLUEB_wrong.zip!/0000000X/00.0000_0000.xml");
    assertNotMatchesRE(pat, "http://www.example.com/2012/CLUEB_chapters.tar!/0000000X/00.0000_0000.xml");
    assertNotMatchesRE(pat, "http://www.example.com/2012/CLUEB_chapters.zip!//00.0000_0000.xml");
    assertNotMatchesRE(pat, "http://www.example.com/2012/CLUEB_chapters.zip!/0000000X/.0000_0000.xml");
    assertNotMatchesRE(pat, "http://www.example.com/2012/CLUEB_chapters.zip!/0000000X/000000_0000.xml");
    assertNotMatchesRE(pat, "http://www.example.com/2012/CLUEB_chapters.zip!/0000000X/00.00000000.xml");
    assertNotMatchesRE(pat, "http://www.example.com/2012/CLUEB_chapters.zip!/0000000X/00.00_00_0000.xml");
    assertNotMatchesRE(pat, "http://www.example.com/2012/CLUEB_chapters.zip!/0000000X/00.0000_0000xml");
    assertNotMatchesRE(pat, "http://www.example.com/2012/CLUEB_chapters.zip!/0000000X/00.0000_0000.html");
    assertNotMatchesRE(pat, "http://www.example.com/2012/CLUEB_chapters.zip!/0000000X/00.0000_0000.pdf");
    assertNotMatchesRE(pat, "http://www.example.com/2012/CLUEB_chapters.zip!/0000000X/00.0000_0000.xmll");
    assertMatchesRE(pat, "http://www.example.com/2012/CLUEB_chapters.zip!/0000000X/00.0000_0000.xml");
    assertMatchesRE(pat, "http://www.example.com/2012/CLUEB_chapters.zip!/X0000000/00.0000_0000.xml");
    assertMatchesRE(pat, "http://www.example.com/2012/CLUEB_chapters.zip!/0000X000/00.0000_0000.xml");
    assertMatchesRE(pat, "http://www.example.com/2012/CLUEB_chapters.zip!/0000000/00.0000_0000.xml");
   }

  public void testCreateArticleFiles() throws Exception {
    PluginTestUtil.crawlSimAu(sau);
    String pat1 = "branch(\\d+)/(\\d+file\\.xml)";
    String rep1 = "CLUEB_chapters.zip!/0000000/00.0000_0000.xml";
    PluginTestUtil.copyAu(sau, au, ".*[^.][^p][^d][^f]$", pat1, rep1);
  
    String url = "http://www.example.com/2012/CLUEB_chapters.zip!/0000000/00.0000_0000.xml";
    CachedUrl cu = au.makeCachedUrl(url);
    assertNotNull(cu);
    SubTreeArticleIterator artIter = createSubTreeIter();
    assertNotNull(artIter);
    ArticleFiles af = createArticleFiles(artIter, cu);
    assertNotNull(af);
    assertEquals(cu, af.getFullTextCu());
  }			

}