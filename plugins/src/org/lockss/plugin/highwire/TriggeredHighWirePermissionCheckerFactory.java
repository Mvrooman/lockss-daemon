/*
 * $Id: TriggeredHighWirePermissionCheckerFactory.java,v 1.3 2012/12/18 16:15:08 pgust Exp $
 */

/*

Copyright (c) 2000-2006 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin.highwire;

import java.io.Reader;
import java.util.*;
import org.lockss.daemon.*;
import org.lockss.plugin.*;

/**
 * This class implements a permission checker that is called only after 
 * the permission statement is verified to exist. This permssion checker 
 * always returns <code>true</code>. 
 * <p>
 * It replaces the standard {@link HighwirePermissionChecker} in the parent
 * HighWirePlugin that requires a probe to appear on the combined HighWire 
 * permission/manifest page and verifies that the URL it indicates can be 
 * accessed. The permission statement for the triggered content is not on the
 * manifest page with the probe link, so the probe link check must be disabled.
 *  
 * @author phil
 *
 */
public class TriggeredHighWirePermissionCheckerFactory implements
    PermissionCheckerFactory {

  public List<PermissionChecker> createPermissionCheckers(ArchivalUnit au) {
    List<PermissionChecker> list = 
      Collections.<PermissionChecker>singletonList(
        new PermissionChecker() {
          @Override public boolean checkPermission (
            Crawler.PermissionHelper pHelper, Reader rdr, String url) {
            return true;
          }
        }
      );
    return list;
  }
}
