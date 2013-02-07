/*
 * $Id: OJS2HtmlFilterFactory.java,v 1.7 2012/12/24 00:33:35 ldoan Exp $
 */

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

package org.lockss.plugin.ojs2;

import java.io.InputStream;

import org.htmlparser.NodeFilter;
import org.htmlparser.filters.*;
import org.lockss.filter.html.*;
import org.lockss.plugin.*;

public class OJS2HtmlFilterFactory implements FilterFactory {

    public InputStream createFilteredInputStream(ArchivalUnit au,
                                                 InputStream in,
                                                 String encoding) {
        NodeFilter[] filters = new NodeFilter[] {
            // Some OJS sites have a tag cloud
            HtmlNodeFilters.tagWithAttribute("div", "id", "sidebarKeywordCloud"),
            // Some OJS sites have a subscription status area
            HtmlNodeFilters.tagWithAttribute("div", "id", "sidebarSubscription"),
            // Some OJS sites have a language switcher, which can change over time
            HtmlNodeFilters.tagWithAttribute("div", "id", "sidebarLanguageToggle"),
            // Top-level menu items sometimes change over time
            HtmlNodeFilters.tagWithAttribute("div", "id", "navbar"),
            // Popular location for sidebar customizations
            HtmlNodeFilters.tagWithAttribute("div", "id", "custom"),
            // Site customizations often involve Javascript (e.g. Google Analytics), which can change over time
            new TagNameFilter("script"),
            // Date accessed is a variable
            HtmlNodeFilters.tagWithTextRegex("div", "Date accessed: "),
            // The version of the OJS software, which can change over time, appears in a tag
            HtmlNodeFilters.tagWithAttribute("meta", "name", "generator"),
            // Header image with variable dimensions
            HtmlNodeFilters.tagWithAttribute("div", "id", "headerTitle"),
	    // For Ubiquity Press
	    HtmlNodeFilters.tagWithAttribute("div", "id", "rightSidebar")
        };
        return new HtmlFilterInputStream(in,
                                         encoding,
                                         HtmlNodeFilterTransform.exclude(new OrFilter(filters)));
    }
    
}
