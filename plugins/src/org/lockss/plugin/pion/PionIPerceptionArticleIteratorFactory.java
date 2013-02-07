/*
 * $Id: PionIPerceptionArticleIteratorFactory.java,v 1.1 2011/12/13 01:17:54 dylanrhodes Exp $
 */

/*

Copyright (c) 2000-2010 Board of Trustees of Leland Stanford Jr. University,
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

import java.io.*;
import java.util.*;
import java.util.regex.*;

import org.lockss.util.*;
import org.lockss.daemon.*;
import org.lockss.plugin.*;
import org.lockss.extractor.*;
import org.lockss.daemon.PluginException;

public class PionIPerceptionArticleIteratorFactory
  implements ArticleIteratorFactory,
	     ArticleMetadataExtractorFactory {
  
  protected static Logger log = Logger.getLogger("PionIPerceptionArticleIteratorFactory");
	  
  protected static final String ROOT_TEMPLATE = "\"%sjournal/%s/volume/%s\", base_url, journal_code, volume_name";
  	
  protected static final String PATTERN_TEMPLATE = "\"%sjournal/%s/volume/%s/article/[^/]+\", base_url, journal_code, volume_name, journal_code";

  public Iterator<ArticleFiles> createArticleIterator(ArchivalUnit au,
						      MetadataTarget target)
      throws PluginException {
	  log.debug("PionIPerceptionArticleIteratorFactory running");
    return new PionIPerceptionArticleIterator(au, new SubTreeArticleIterator.Spec()
				          .setTarget(target)
				          .setRootTemplate(ROOT_TEMPLATE)
				          .setPatternTemplate(PATTERN_TEMPLATE));
  }
  
  protected static class PionIPerceptionArticleIterator extends SubTreeArticleIterator {
    
    protected Pattern pattern;
    
    protected String journalCode, volumeName, base_url, base_url2, journalCodeLower;
    
    public PionIPerceptionArticleIterator(ArchivalUnit au,
                                  SubTreeArticleIterator.Spec spec) {
      super(au, spec);
  	log.debug("PionIPerceptionArticleIterator constructor invoked");
      journalCode = au.getConfiguration().get("journal_code");
      journalCodeLower = journalCode.toLowerCase();
      volumeName = au.getConfiguration().get(ConfigParamDescr.VOLUME_NAME.getKey());
      base_url2 = au.getConfiguration().get(ConfigParamDescr.BASE_URL2.getKey());
      base_url = au.getConfiguration().get(ConfigParamDescr.BASE_URL.getKey());
  	log.debug("PionIPerceptionArticleIterator created with volumeName: "+volumeName+base_url+base_url2+journalCode);
      this.pattern = Pattern.compile(String.format("%sjournal/%s/volume/%s/article/(%s[^/]+)", base_url, journalCode, volumeName, journalCode), Pattern.CASE_INSENSITIVE);
    }
    
    @Override
    protected ArticleFiles createArticleFiles(CachedUrl cu) {
    	log.debug("Attempting to pattern match cu: "+cu);
      String url = cu.getUrl();
      Matcher mat = pattern.matcher(url);
      if (mat.find()) {
    	  log.debug("Pattern matched for previous cu, attempting to store as pdf");
    	  return processAbstract(cu, mat);
      }
      log.warning("Mismatch between article iterator factory and article iterator: " + url);
      return null;
    }
    
    protected ArticleFiles processAbstract(CachedUrl abstractCu, Matcher absMat) {
        ArticleFiles af = new ArticleFiles();
        af.setRoleCu(ArticleFiles.ROLE_ABSTRACT,abstractCu);
        af.setRoleCu(ArticleFiles.ROLE_ARTICLE_METADATA, abstractCu);
        af.setFullTextCu(abstractCu);
        
        if(true)//spec.getTarget() != MetadataTarget.Article)
        {
        	guessPdf(af, absMat);
			guessRisCitation(af, absMat);
        }
        
        return af;
      }
    
    protected void guessPdf(ArticleFiles af, Matcher mat) {
      CachedUrl pdfCu = au.makeCachedUrl(mat.replaceFirst(base_url+"fulltext/"+journalCodeLower+"0"+volumeName+"/$1.pdf"));
      CachedUrl pdfCu2 = au.makeCachedUrl(mat.replaceFirst(base_url+"fulltext/"+journalCodeLower+volumeName+"/$1.pdf"));
      log.debug("pdfCu generated is: "+pdfCu.getUrl());
      log.debug("pdfCu2 generated is: "+pdfCu2.getUrl());
      if (pdfCu != null && pdfCu.hasContent()) {
    	  log.debug("pdf Cu stored");
			//af.setFullTextCu(pdfCu);
			af.setRoleCu(ArticleFiles.ROLE_FULL_TEXT_PDF, pdfCu);
		}
      else if(pdfCu2 != null && pdfCu2.hasContent()) {
    	  log.debug("pdf Cu2 stored");
    	  af.setFullTextCu(pdfCu);
    	  af.setRoleCu(ArticleFiles.ROLE_FULL_TEXT_PDF, pdfCu);
      }
    }
    
    protected void guessRisCitation(ArticleFiles af, Matcher mat) {
      CachedUrl risCu = au.makeCachedUrl(mat.replaceFirst(base_url2+"ris.cgi?id=$1"));
      log.debug("risCu generated is: "+risCu.getUrl());
      if (risCu != null && risCu.hasContent()) {
    	  log.debug("risCu stored");
        af.setRoleCu(ArticleFiles.ROLE_CITATION + "_" + "application/x-research-info-systems", risCu);
      }
    }
  }
  
  public ArticleMetadataExtractor createArticleMetadataExtractor(MetadataTarget target)
      throws PluginException {
    return new BaseArticleMetadataExtractor(null);
  }
}
