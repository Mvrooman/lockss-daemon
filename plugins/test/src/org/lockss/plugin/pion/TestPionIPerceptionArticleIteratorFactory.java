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

package org.lockss.plugin.pion;

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

public class TestPionIPerceptionArticleIteratorFactory extends ArticleIteratorTestCase {
	
	private SimulatedArchivalUnit sau;	// Simulated AU to generate content
	
	private final String PLUGIN_NAME = "org.lockss.plugin.pion.ClockssPionIPerceptionPlugin";
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
      PluginTestUtil.createAndStartAu(PLUGIN_NAME,
			      ConfigurationUtil.fromArgs("base_url",
							 "http://www.example.com/",
							 "base_url2", "http://www.example2.com/",
							 "volume_name", "123",
							 "journal_code", "j"));
  }
  
  Configuration simAuConfig(String rootPath) {
	    Configuration conf = ConfigManager.newConfiguration();
	    conf.put("root", rootPath);
	    conf.put("base_url", "http://www.example.com/");
	    conf.put("depth", "1");
	    conf.put("branch", "4");
	    conf.put("numFiles", "7");
	    conf.put("fileTypes",
	             "" + (  SimulatedContentGenerator.FILE_TYPE_HTML
	                   | SimulatedContentGenerator.FILE_TYPE_PDF));
	    conf.put("binFileSize", ""+DEFAULT_FILESIZE);
	    return conf;
	  }
  
  Configuration pionAuConfig() {
	    return ConfigurationUtil.fromArgs("base_url",
				 "http://www.example.com/",
				 "base_url2", "http://www.example2.com/",
				 "volume_name", "123",
				 "journal_code", "j");
	  }

  public void testRoots() throws Exception {
    SubTreeArticleIterator artIter = createSubTreeIter();
    assertEquals(ListUtil.list("http://www.example.com/journal/j/volume/123"),
		 getRootUrls(artIter));
  }

  public void testUrlsWithPrefixes() throws Exception {
    SubTreeArticleIterator artIter = createSubTreeIter();
    Pattern pat = getPattern(artIter);
    //"\"%sjournal/%s/volume/%s/article/[^/]+\", base_url, journal_code, volume_name, journal_code";

    assertNotMatchesRE(pat, "http://www.wrong.com/fulltext/j0123/j0143.pdf");
    assertNotMatchesRE(pat, "http://www.wrong.com/fulltext/J0123/J0143.pdf");
    assertNotMatchesRE(pat, "http://www.example.com/fulltextt/j0123/j0143.pdf");
    assertNotMatchesRE(pat, "http://www.example.com/fulltextt/j123/j0143.pdf");
    assertNotMatchesRE(pat, "http://www.example.com/fulltext/j123/j0143.pdfwrong");
    assertNotMatchesRE(pat, "http://www.example.com/fulltext/j0123/j0143.pdfwrong");
    assertNotMatchesRE(pat, "http://www.example.com/fulltext/k0123/j0143.pdf");
    assertNotMatchesRE(pat, "http://www.example.com/fulltext/j0123/k0143.pdf");
    assertNotMatchesRE(pat, "http://www.example.com/fulltext/k123/j0142.pdf");
    assertNotMatchesRE(pat, "http://www.example.com/fulltext/j123/k01323.pdf");
    assertNotMatchesRE(pat, "http://www.example.com/fulltext/j137/j2934.pdf");
    assertNotMatchesRE(pat, "http://www.example.com/fulltext/J384/J392.pdf");
    assertMatchesRE(pat, "http://www.example.com/journal/j/volume/123/article/j382");
    assertMatchesRE(pat, "http://www.example.com/journal/j/volume/123/article/J392");
    assertMatchesRE(pat, "http://www.example.com/journal/j/volume/123/article/j032");
    assertMatchesRE(pat, "http://www.example.com/journal/j/volume/123/article/j2938");
    assertNotMatchesRE(pat, "http://www.example.com/fulltext/");
    assertNotMatchesRE(pat, "http://www.example.com/fulltext/j");
    assertNotMatchesRE(pat, "http://www.example.com/fulltext/j0");
    assertNotMatchesRE(pat, "http://www.example.com/fulltext/j0123");
    assertNotMatchesRE(pat, "http://www.example.com/fulltext/j123");
    assertNotMatchesRE(pat, "http://www.example.com/fulltext/j0123/j");
    assertNotMatchesRE(pat, "http://www.example.com/fulltext/j0123/j383");
    assertNotMatchesRE(pat, "http://www.example.com/fulltext/j0123/j383.pdfwrong");
  }

  public void testCreateArticleFiles() throws Exception {
    PluginTestUtil.crawlSimAu(sau);
    String pat1 = "branch(\\d+)/(\\d+file\\.html)";
    String rep1 = "journal/j/volume/123/article/j3948";
    PluginTestUtil.copyAu(sau, au, ".*[^.][^p][^d][^f]$", pat1, rep1);
    String pat2 = "branch(\\d+)/(\\d+file\\.pdf)";
    String rep2 = "fulltext/j123/j3948.pdf";
    PluginTestUtil.copyAu(sau, au, ".*\\.pdf$", pat2, rep2);
  
    String pdfUrl = "http://www.example.com/fulltext/j0123/j3948.pdf";
    String citationUrl = "http://www.example2.com/ris.cgi?id=j3948";
    String url = "http://www.example.com/journal/j/volume/123/article/j3948";
    CachedUrl cu = au.makeCachedUrl(url);
    assertNotNull(cu);
    SubTreeArticleIterator artIter = createSubTreeIter();
    assertNotNull(artIter);
    ArticleFiles af = createArticleFiles(artIter, cu);
    assertNotNull(af);
    assertEquals(cu, af.getRoleCu(ArticleFiles.ROLE_ABSTRACT));
  }			

}
