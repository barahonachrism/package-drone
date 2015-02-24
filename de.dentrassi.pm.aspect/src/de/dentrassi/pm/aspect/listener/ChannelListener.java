/*******************************************************************************
 * Copyright (c) 2014, 2015 IBH SYSTEMS GmbH.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBH SYSTEMS GmbH - initial API and implementation
 *******************************************************************************/
package de.dentrassi.pm.aspect.listener;

public interface ChannelListener
{
    /**
     * Process a request to add an artifact <br/>
     * In general it is possible to check an incoming (not stored yet) artifact
     * and veto its creation.
     *
     * @param context
     *            the context information
     */
    public default void artifactPreAdd ( final PreAddContext context ) throws Exception
    {
    }

    public default void artifactAdded ( final PostAddContext context ) throws Exception
    {
    }
}
