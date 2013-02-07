/*
 * $Id: MetaPressArticleIteratorFactory.java,v 1.2 2012/03/26 19:41:19 akanshab01 Exp $
 */

/*

Copyright (c) 2000-2011 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin.metapress;

import java.util.Iterator;
import java.util.regex.*;

import org.lockss.daemon.PluginException;
import org.lockss.extractor.*;
import org.lockss.plugin.*;
import org.lockss.util.Constants;
import org.lockss.util.Logger;


public class MetaPressArticleIteratorFactory
    implements ArticleIteratorFactory,
               ArticleMetadataExtractorFactory {

  protected static Logger log = Logger.getLogger("MetaPressArticleIteratorFactory");
  
  protected static final String ROOT_TEMPLATE = "\"%scontent\", base_url";
  
  protected static final String PATTERN_TEMPLATE = "\"^%scontent/[A-Za-z0-9]{16}/fulltext\\.pdf$\", base_url";

  @Override
  public Iterator<ArticleFiles> createArticleIterator(ArchivalUnit au,
                                                      MetadataTarget target)
      throws PluginException {
    return new MetaPressArticleIterator(au,
                                        new SubTreeArticleIterator.Spec()
                                            .setTarget(target)
                                            .setRootTemplate(ROOT_TEMPLATE)
                                            .setPatternTemplate(PATTERN_TEMPLATE),
                                        target);
  }

  protected static class MetaPressArticleIterator extends SubTreeArticleIterator {

    protected Pattern PATTERN = Pattern.compile("/content/([a-z0-9]{16})/fulltext\\.pdf$", Pattern.CASE_INSENSITIVE);
    
    protected MetadataTarget target;
    
    public MetaPressArticleIterator(ArchivalUnit au,
                                    SubTreeArticleIterator.Spec spec,
                                    MetadataTarget target) {
      super(au, spec);
      this.target = target;
    }
    
    @Override
    protected ArticleFiles createArticleFiles(CachedUrl cu) {
      String url = cu.getUrl();
      Matcher mat;

      mat = PATTERN.matcher(url);
      if (mat.find()) {
        return processFullTextPdf(cu, mat);
      }

  log.warning("Mismatch between article iterator factory and article iterator: "+ url);
      return null;
    }
    //http://inderscience.metapress.com/content/kv824m8x38336011/fulltext.pdfmap={FullTextPdfLanding=[BCU: http://inderscience.metapress.com/content/p20687286306321u], FullTextPdfFile=[BCU: http://inderscience.metapress.com/content/p20687286306321u/fulltext.pdf], IssueMetadata=[BCU: http://inderscience.metapress.com/content/p20687286306321u], Citation=[BCU: http://inderscience.metapress.com/export.mpx?code=P20687286306321U&mode=ris], Abstract=[BCU: http://inderscience.metapress.com/content/p20687286306321u]}])

    //http://inderscience.metapress.com/export.mpx?code=KV824M8X38336011&mode=ris
    protected ArticleFiles processFullTextPdf(CachedUrl pdfCu, Matcher pdfMat) {
      ArticleFiles af = new ArticleFiles();
      af.setFullTextCu(pdfCu);
      af.setRoleCu(ArticleFiles.ROLE_FULL_TEXT_PDF, pdfCu);
      if (target != MetadataTarget.Article) {
        guessAbstract(af, pdfMat);
        guessReferences(af, pdfMat);
        guessCitations(af,pdfMat);
       }
      return af;
    }
          
    protected void guessAbstract(ArticleFiles af, Matcher mat) {
      CachedUrl absCu = au.makeCachedUrl(mat.replaceFirst("/content/$1"));
      if (absCu != null && absCu.hasContent()) {
        af.setRoleCu(ArticleFiles.ROLE_ABSTRACT, absCu);
        af.setRoleCu(ArticleFiles.ROLE_FULL_TEXT_PDF_LANDING_PAGE, absCu);
        af.setRoleCu(ArticleFiles.ROLE_ISSUE_METADATA, absCu);
      }
    }
    
    
    protected void guessCitations(ArticleFiles af, Matcher mat) {
      String uppercaseStr = mat.replaceFirst("/export.mpx?code=$1&mode=ris").toUpperCase();
      String endStrUrl = uppercaseStr.substring(uppercaseStr.length()-9,uppercaseStr.length()).toLowerCase();
      String startStrUrl = uppercaseStr.substring(0, 50).toLowerCase();
      String midStrUrl = uppercaseStr.substring(50,uppercaseStr.length()-9);
      String wantedUrl = startStrUrl+midStrUrl+endStrUrl;
      CachedUrl citCu = au.makeCachedUrl(wantedUrl);
      if (citCu != null && citCu.hasContent()) {
        af.setRoleCu(ArticleFiles.ROLE_CITATION, citCu);
        log.debug3("citcu :" + citCu);
      }
     }
        
    protected void guessReferences(ArticleFiles af, Matcher mat) {
      CachedUrl refCu = au.makeCachedUrl(mat.replaceFirst("/content/$1/?referencesMode=Show"));
      if (refCu != null && refCu.hasContent()) {
        af.setRoleCu(ArticleFiles.ROLE_REFERENCES, refCu);
      }
    }

  }
  
  
  @Override
  public ArticleMetadataExtractor createArticleMetadataExtractor(MetadataTarget target)
      throws PluginException {
   // return new MetaPressArticleMetadataExtractor();
    return new BaseArticleMetadataExtractor(ArticleFiles.ROLE_CITATION);
  }

}
