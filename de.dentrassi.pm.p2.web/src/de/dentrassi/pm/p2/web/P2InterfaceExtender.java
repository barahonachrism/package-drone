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
package de.dentrassi.pm.p2.web;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import de.dentrassi.osgi.web.LinkTarget;
import de.dentrassi.pm.common.web.Modifier;
import de.dentrassi.pm.common.web.menu.MenuEntry;
import de.dentrassi.pm.p2.aspect.P2RepositoryAspect;
import de.dentrassi.pm.storage.AbstractChannelInterfaceExtender;
import de.dentrassi.pm.storage.channel.ChannelInformation;

public class P2InterfaceExtender extends AbstractChannelInterfaceExtender
{
    public static final String P2_METADATA_ASPECT_ID = "p2.metadata";

    @Override
    protected List<MenuEntry> getChannelActions ( final HttpServletRequest request, final ChannelInformation channel )
    {
        final Map<String, Object> model = new HashMap<> ( 1 );
        model.put ( "channelId", channel.getId () );
        if ( channel.getName () != null && !channel.getName ().isEmpty () )
        {
            model.put ( "channelAlias", channel.getName () );
        }

        final List<MenuEntry> result = new LinkedList<> ();

        repoActions ( request, channel, model, result );
        metaDataActions ( request, channel, model, result );

        return result;
    }

    private void metaDataActions ( final HttpServletRequest request, final ChannelInformation channel, final Map<String, Object> model, final List<MenuEntry> result )
    {
        if ( !channel.hasAspect ( P2_METADATA_ASPECT_ID ) )
        {
            return;
        }

        if ( request.isUserInRole ( "MANAGER" ) )
        {
            result.add ( new MenuEntry ( "Edit", Integer.MAX_VALUE, "P2 Meta Data Generator", 10_000, new LinkTarget ( "/p2.metadata/{channelId}/edit" ).expand ( model ), Modifier.DEFAULT, null, false, 0 ) );
        }
    }

    private void repoActions ( final HttpServletRequest request, final ChannelInformation channel, final Map<String, Object> model, final List<MenuEntry> result )
    {
        if ( !channel.hasAspect ( P2RepositoryAspect.ID ) )
        {
            return;
        }

        result.add ( new MenuEntry ( null, 0, "P2 (by ID)", 10_000, new LinkTarget ( "/p2/{channelId}" ).expand ( model ), Modifier.LINK, null, false, 0 ) );

        if ( model.containsKey ( "channelAlias" ) )
        {
            result.add ( new MenuEntry ( null, 0, "P2 (by name)", 10_000, new LinkTarget ( "/p2/{channelAlias}" ).expand ( model ), Modifier.LINK, null, false, 0 ) );
        }

        if ( request.isUserInRole ( "MANAGER" ) )
        {
            result.add ( new MenuEntry ( "Edit", Integer.MAX_VALUE, "P2 Repository Information", 10_000, new LinkTarget ( "/p2.repo/{channelId}/edit" ).expand ( model ), Modifier.DEFAULT, null, false, 0 ) );
        }
    }

    @Override
    protected List<MenuEntry> getChannelViews ( final HttpServletRequest request, final ChannelInformation channel )
    {
        final Map<String, Object> model = new HashMap<> ( 1 );
        model.put ( "channelId", channel.getId () );

        final List<MenuEntry> result = new LinkedList<> ();

        if ( channel.hasAspect ( P2RepositoryAspect.ID ) )
        {
            result.add ( new MenuEntry ( "P2", 5_000, "Repository", 1000, new LinkTarget ( "/p2.repo/{channelId}/info" ).expand ( model ), Modifier.INFO, null, false, 0 ) );
        }

        if ( channel.hasAspect ( P2_METADATA_ASPECT_ID ) )
        {
            result.add ( new MenuEntry ( "P2", 5_000, "Meta Data Generation", 500, new LinkTarget ( "/p2.metadata/{channelId}/info" ).expand ( model ), Modifier.INFO, null, false, 0 ) );
        }

        return result;
    }
}
