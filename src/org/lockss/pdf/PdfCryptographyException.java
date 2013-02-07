/*
 * $Id: PdfCryptographyException.java,v 1.1 2012/07/10 23:59:49 thib_gc Exp $
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

package org.lockss.pdf;

/**
 * <p>
 * A PDF exception having to do with failure to encrypt or decrypt
 * data or otherwise process PDF data at the cryptographic level.
 * </p>
 * @author Thib Guicherd-Callin
 * @since 1.56
 */
public class PdfCryptographyException extends PdfException {

  /**
   * <p>
   * No-arg constructor.
   * </p>
   * @since 1.56
   */
  public PdfCryptographyException() {
    super();
  }

  /**
   * <p>
   * Message constructor.
   * </p>
   * @param message A message.
   * @since 1.56
   */
  public PdfCryptographyException(String message) {
    super(message);
  }

  /**
   * <p>
   * Message and cause constructor.
   * </p>
   * @param message A message.
   * @param cause The cause.
   * @since 1.56
   */
  public PdfCryptographyException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * <p>
   * Cause constructor.
   * </p>
   * @param cause The cause.
   * @since 1.56
   */
  public PdfCryptographyException(Throwable cause) {
    super(cause);
  }

}
