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

package org.lockss.plugin.elsevier;

import java.util.Iterator;
import java.util.regex.*;

import org.lockss.daemon.PluginException;
import org.lockss.extractor.ArticleMetadataExtractor;
import org.lockss.extractor.ArticleMetadataExtractorFactory;
import org.lockss.extractor.MetadataTarget;
import org.lockss.plugin.*;
import org.lockss.util.Logger;

public class ElsevierFTPArticleIteratorFactory implements ArticleIteratorFactory, ArticleMetadataExtractorFactory {

  protected static Logger log = Logger.getLogger("ElsevierFTPArticleIteratorFactory");
  
  protected static final String ROOT_TEMPLATE = "\"%s%d\",base_url,year";
  
  protected static final String PATTERN_TEMPLATE = "\"%s%d/[^/]+/[\\d]+[^/]+/[\\d]+/[\\d]+/main.pdf$\",base_url,year";

  protected static final String INCLUDE_SUBTREE_TEMPLATE = "\"%s%d/[^/]+/[\\d]+[^/]+/[\\d]+/[\\d]+/main.pdf$\",base_url,year";

  @Override
  public Iterator<ArticleFiles> createArticleIterator(ArchivalUnit au,
                                                      MetadataTarget target)
      throws PluginException {
    return new ElsevierFTPArticleIterator(au, new SubTreeArticleIterator.Spec()
                                       .setTarget(target)
                                       .setRootTemplate(ROOT_TEMPLATE)
                                       .setPatternTemplate(PATTERN_TEMPLATE, Pattern.CASE_INSENSITIVE));
//                                       .setIncludeSubTreePatternTemplate(INCLUDE_SUBTREE_TEMPLATE, Pattern.CASE_INSENSITIVE));
  }
  
  protected static class ElsevierFTPArticleIterator extends SubTreeArticleIterator {
	 
    protected static Pattern PATTERN = Pattern.compile("([^/]+[\\d]+/[\\d]+[^/]+/[\\d]+/[\\d]+/main.)pdf$", Pattern.CASE_INSENSITIVE);
    
    protected ElsevierFTPArticleIterator(ArchivalUnit au,
                                  SubTreeArticleIterator.Spec spec) {
      super(au, spec);
      spec.setVisitArchiveMembers(true);
    }
    
    // Duplicate of definition in ArticleFiles for use here until 1.59 is ready (PJG).
    private static final String ROLE_FULL_TEXT_XML = "FullTextXML";

    @Override
    protected ArticleFiles createArticleFiles(CachedUrl cu) {
      String url = cu.getUrl();
      
      Matcher mat = PATTERN.matcher(url);
      if (mat.find()) {
        return processFullText(cu, mat);
      }
      log.warning("Mismatch between article iterator factory and article iterator: " + url);
      return null;
    }

    protected ArticleFiles processFullText(CachedUrl cu, Matcher mat) {
      ArticleFiles af = new ArticleFiles();
      af.setFullTextCu(cu);
      
      if(spec.getTarget() != MetadataTarget.Article())
      {
		af.setRoleCu(ArticleFiles.ROLE_FULL_TEXT_PDF, cu);
		guessXml(af,mat);
      }
      
      return af;
    }
    
    protected void guessXml(ArticleFiles af, Matcher mat) {
        CachedUrl xmlCu = au.makeCachedUrl(mat.replaceFirst("$1xml"));
        
        if (xmlCu != null && xmlCu.hasContent()) {
          af.setRoleCu(ROLE_FULL_TEXT_XML, xmlCu);
        }
      } 
  }
  
  public ArticleMetadataExtractor createArticleMetadataExtractor(MetadataTarget target)
	      throws PluginException {
	    return new ElsevierSourceArticleMetadataExtractor();
  }
}
