/*******************************************************************************
 * Copyright (c) 2015 IBH SYSTEMS GmbH.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBH SYSTEMS GmbH - initial API and implementation
 *******************************************************************************/
package org.eclipse.packagedrone.testing;

import java.io.File;

import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebDriver;

public interface WebContext extends SearchContext
{
    public WebDriver getDriver ();

    public String resolve ( String url );

    public File getTestFile ( String localFileName );

    public default WebDriver getResolved ( final String url )
    {
        final WebDriver driver = getDriver ();
        driver.get ( resolve ( url ) );
        return driver;
    }
}
