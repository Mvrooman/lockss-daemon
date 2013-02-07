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

package org.lockss.plugin.igiglobal;

import java.io.File;
import java.util.Iterator;
import java.util.Stack;
import java.util.regex.Pattern;

import org.lockss.config.ConfigManager;
import org.lockss.config.Configuration;
import org.lockss.daemon.ConfigParamDescr;
import org.lockss.plugin.*;
import org.lockss.plugin.simulated.SimulatedArchivalUnit;
import org.lockss.plugin.simulated.SimulatedContentGenerator;
import org.lockss.repository.LockssRepositoryImpl;
import org.lockss.test.*;
import org.lockss.util.*;

/*
 * PDF Full Text: http://www.igi-global.com/viewtitle.aspx?titleid=55656
 * HTML Abstract: http://www.igi-global.com/gateway/contentowned/article.aspx?titleid=55656
 */
public class TestIgiGlobalArticleIteratorFactory extends ArticleIteratorTestCase {
	
	private SimulatedArchivalUnit sau;	// Simulated AU to generate content
	private final String ARTICLE_FAIL_MSG = "Article files not created properly";
	private final String PATTERN_FAIL_MSG = "Article file URL pattern changed or incorrect";
	private final String PLUGIN_NAME = "org.lockss.plugin.igiglobal.IgiGlobalPlugin";
	static final String BASE_URL_KEY = ConfigParamDescr.BASE_URL.getKey();
	static final String JOURNAL_ISSN_KEY = ConfigParamDescr.JOURNAL_ISSN.getKey();
	static final String VOLUME_NUMBER_KEY = ConfigParamDescr.VOLUME_NUMBER.getKey();
	private final String BASE_URL = "http://www.example.com/";
	private final String VOLUME_NUMBER = "352";
	private final String JOURNAL_ISSN = "nejm";
	private final Configuration AU_CONFIG = ConfigurationUtil.fromArgs(
												BASE_URL_KEY, BASE_URL,
												VOLUME_NUMBER_KEY, VOLUME_NUMBER,
												JOURNAL_ISSN_KEY, JOURNAL_ISSN);
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
      PluginTestUtil.createAndStartAu(PLUGIN_NAME, AU_CONFIG);
  }
  
  Configuration simAuConfig(String rootPath) {
	    Configuration conf = ConfigManager.newConfiguration();
	    conf.put("root", rootPath);
	    conf.put(BASE_URL_KEY, BASE_URL);
	    conf.put("depth", "1");
	    conf.put("branch", "2");
	    conf.put("numFiles", "6");
	    conf.put("fileTypes",
	             "" + (  SimulatedContentGenerator.FILE_TYPE_HTML
	                   | SimulatedContentGenerator.FILE_TYPE_PDF));
	    conf.put("binFileSize", ""+DEFAULT_FILESIZE);
	    return conf;
  }

  public void testRoots() throws Exception {
    SubTreeArticleIterator artIter = createSubTreeIter();
    assertEquals("Article file root URL pattern changed or incorrect" ,ListUtil.list(BASE_URL + "gateway/article/"),
		 getRootUrls(artIter));
  }

  public void testUrlsWithPrefixes() throws Exception {
    SubTreeArticleIterator artIter = createSubTreeIter();
    Pattern pat = getPattern(artIter);
    
    assertNotMatchesRE(PATTERN_FAIL_MSG, pat, "http://www.wrong.com/article/55656");
    assertNotMatchesRE(PATTERN_FAIL_MSG, pat, BASE_URL + "gateway/articles/55656");
    assertNotMatchesRE(PATTERN_FAIL_MSG, pat, BASE_URL + "gateway/article/full");
    assertNotMatchesRE(PATTERN_FAIL_MSG, pat, BASE_URL + "/gateway/article/55656");
    assertMatchesRE(PATTERN_FAIL_MSG, pat, BASE_URL + "gateway/article/55656");
    assertMatchesRE(PATTERN_FAIL_MSG, pat, BASE_URL + "gateway/article/1");
   }

  /*
   * PDF Full Text: http://www.igi-global.com/gateway/contentowned/article.aspx?titleid=55656
   * HTML Abstract: http://www.igi-global.com/viewtitle.aspx?titleid=55656
   */
  public void testCreateArticleFiles() throws Exception {
    PluginTestUtil.crawlSimAu(sau);
    String[] urls = {
					    BASE_URL + "gateway/article/full-text-html/11111",
					    BASE_URL + "gateway/article/full-text-pdf/2222",
					    BASE_URL + "gateway/article/full-text-html/55656",
					    BASE_URL + "gateway/article/full-text-pdf/55656",
					    BASE_URL + "gateway/article/full-text-pdf/12345",
					    BASE_URL + "gateway/article/11111",
					    BASE_URL + "gateway/article/55656",
					    BASE_URL + "gateway/article/54321",
					    BASE_URL + "gateway/articles/full-text-pdf/12345",
					    BASE_URL + "gateway/issue/54321",
					    BASE_URL + "gateway/issue/12345",
					    BASE_URL,
					    BASE_URL + "gateway"
    				};
    Iterator<CachedUrlSetNode> cuIter = sau.getAuCachedUrlSet().contentHashIterator();
    
    if(cuIter.hasNext()){
	    CachedUrlSetNode cusn = cuIter.next();
	    CachedUrl cuPdf = null;
	    CachedUrl cuHtml = null;
	    UrlCacher uc;
	    while(cuIter.hasNext() && (cuPdf == null || cuHtml == null))
		{
	    	if(cusn.getType() == CachedUrlSetNode.TYPE_CACHED_URL && cusn.hasContent())
	    	{
	    		CachedUrl cu = (CachedUrl)cusn;
	    		if(cuPdf == null && cu.getContentType().toLowerCase().startsWith(Constants.MIME_TYPE_PDF))
	    		{
	    			cuPdf = cu;
	    		}
	    		else if (cuHtml == null && cu.getContentType().toLowerCase().startsWith(Constants.MIME_TYPE_HTML))
	    		{
	    			cuHtml = cu;
	    		}
	    	}
	    	cusn = cuIter.next();
		}
	    for (String url : urls) {
	      uc = au.makeUrlCacher(url);
              uc.storeContent(cuHtml.getUnfilteredInputStream(), cuHtml.getProperties());
	    }
    }
    
    Stack<String[]> expStack = new Stack<String[]>();
    String [] af1 = {BASE_URL + "gateway/article/full-text-html/11111",
    		     null,
    		     BASE_URL + "gateway/article/11111"};
    
    String [] af2 = {null,
		     null,
		     BASE_URL + "gateway/article/54321"};
    
    String [] af3 = {BASE_URL + "gateway/article/full-text-html/55656",
		     BASE_URL + "gateway/article/full-text-pdf/55656",
		     BASE_URL + "gateway/article/55656"};
    
    expStack.push(af3);
    expStack.push(af2);
    expStack.push(af1);
    
    for ( SubTreeArticleIterator artIter = createSubTreeIter(); artIter.hasNext(); ) 
    {
    		ArticleFiles af = artIter.next();
    		String[] act = {
					af.getFullTextUrl(),
					af.getRoleUrl(ArticleFiles.ROLE_FULL_TEXT_PDF),
					af.getRoleUrl(ArticleFiles.ROLE_ABSTRACT)
					};
    		String[] exp = expStack.pop();
    		if(act.length == exp.length){
    			for(int i = 0;i< act.length; i++){
	    			assertEquals(ARTICLE_FAIL_MSG + " Expected: " + exp[i] + " Actual: " + act[i], exp[i],act[i]);
	    		}
    		}
    		else fail(ARTICLE_FAIL_MSG + " length of expected and actual ArticleFiles content not the same:" + exp.length + "!=" + act.length);
    }
    
  }			
}
