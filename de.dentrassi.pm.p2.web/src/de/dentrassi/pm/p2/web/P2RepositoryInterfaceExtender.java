/*******************************************************************************
 * Copyright (c) 2015 Jens Reimann.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Jens Reimann - initial API and implementation
 *******************************************************************************/
package de.dentrassi.pm.p2.web;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import de.dentrassi.osgi.web.LinkTarget;
import de.dentrassi.pm.common.web.Modifier;
import de.dentrassi.pm.common.web.menu.MenuEntry;
import de.dentrassi.pm.storage.Channel;

public class P2RepositoryInterfaceExtender extends AbstractChannelnterfaceExtender
{

    @Override
    protected List<MenuEntry> getChannelAction ( final HttpServletRequest request, final Channel channel )
    {
        final Map<String, Object> model = new HashMap<> ( 1 );
        model.put ( "channelId", channel.getId () );

        final List<MenuEntry> result = new LinkedList<> ();

        result.add ( new MenuEntry ( null, 0, "P2 Repository", 10_000, new LinkTarget ( "/p2/{channelId}" ).expand ( model ), Modifier.LINK, null, false ) );

        if ( request.getUserPrincipal () != null )
        {
            result.add ( new MenuEntry ( "Edit", Integer.MAX_VALUE, "P2 Repository Information", 10_000, new LinkTarget ( "/p2.repo/{channelId}/edit" ).expand ( model ), Modifier.DEFAULT, null, false ) );
        }

        return result;
    }

    @Override
    protected List<MenuEntry> getChannelViews ( final HttpServletRequest request, final Channel channel )
    {
        final Map<String, Object> model = new HashMap<> ( 1 );
        model.put ( "channelId", channel.getId () );

        final List<MenuEntry> result = new LinkedList<> ();
        result.add ( new MenuEntry ( null, 0, "P2", 1000, new LinkTarget ( "/p2.repo/{channelId}/info" ).expand ( model ), Modifier.INFO, null, false ) );
        // result.add ( new MenuEntry ( "P2", 10_000, "Details", 200, new LinkTarget ( "/p2.repo/{channelId}/details" ).expand ( model ), Modifier.DEFAULT, null, false ) );
        return result;
    }
}
