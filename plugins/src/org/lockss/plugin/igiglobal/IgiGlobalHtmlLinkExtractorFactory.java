/*
 * $Id: IgiGlobalHtmlLinkExtractorFactory.java,v 1.1 2012/06/24 21:02:20 pgust Exp $
 */

/*

Copyright (c) 2000-2008 Board of Trustees of Leland Stanford Jr. University,
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

import java.io.IOException;

import org.lockss.daemon.PluginException;
import org.lockss.extractor.GoslingHtmlLinkExtractor;
import org.lockss.extractor.LinkExtractor;
import org.lockss.extractor.LinkExtractorFactory;
import org.lockss.plugin.ArchivalUnit;

/**
 * This is a temporary class until GoslingHtmlLinkExtractor
 * is able to handle the 'iframe' tag in 1.56.
 * @author phil
 *
 */
public class IgiGlobalHtmlLinkExtractorFactory implements LinkExtractorFactory {

  public LinkExtractor createLinkExtractor(String mimeType) throws PluginException {
    return new IgiGlobalHtmlLinkExtractor();
  }

  static private class IgiGlobalHtmlLinkExtractor 
    extends GoslingHtmlLinkExtractor {

    protected static final String IFRAMETAG = "iframe";

    public IgiGlobalHtmlLinkExtractor() {
      super();
    }
    
    @Override
    protected String extractLinkFromTag(StringBuffer link,
                                        ArchivalUnit au,
                                        Callback cb)
        throws IOException {
      // give GoslingHtmlLinkExtractor a try first
      String url = super.extractLinkFromTag(link, au, cb);
      if (url == null) {
        switch (link.charAt(0)) {
          case 'i':
          case 'I':
            // GoslingHtmlLinkExtractor doesn't handle the tag
            if (beginsWithTag(link, IFRAMETAG)) {
              url = getAttributeValue(SRC, link);
            }
            break;
        }
      }
      return url;
    }
  }    
}
