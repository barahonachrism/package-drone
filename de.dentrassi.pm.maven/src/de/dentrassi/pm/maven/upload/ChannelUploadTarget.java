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
package de.dentrassi.pm.maven.upload;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import de.dentrassi.pm.common.MetaKey;
import de.dentrassi.pm.storage.channel.ArtifactInformation;
import de.dentrassi.pm.storage.channel.ModifiableChannel;

/**
 * A channel based implementation for the {@link UploadTarget}
 */
public class ChannelUploadTarget implements UploadTarget
{
    private static final MetaKey KEY_EXTENSION = new MetaKey ( "mvn", "extension" );

    private static final MetaKey KEY_CLASSIFIER = new MetaKey ( "mvn", "classifier" );

    private static final MetaKey KEY_QUALIFIED_VERSION = new MetaKey ( "mvn", "qualifiedVersion" );

    private static final MetaKey KEY_VERSION = new MetaKey ( "mvn", "version" );

    private static final MetaKey KEY_ARTIFACT_ID = new MetaKey ( "mvn", "artifactId" );

    private static final MetaKey KEY_GROUP_ID = new MetaKey ( "mvn", "groupId" );

    private final ModifiableChannel channel;

    public ChannelUploadTarget ( final ModifiableChannel channel )
    {
        this.channel = channel;
    }

    @Override
    public String createArtifact ( final String parentId, final Coordinates coordinates, final InputStream stream, final Map<MetaKey, String> metaData ) throws IOException
    {
        final Map<MetaKey, String> providedMetaData = new HashMap<> ();

        // copy over provided meta data

        if ( metaData != null )
        {
            providedMetaData.putAll ( metaData );
        }

        // insert coordinates

        fillMetaData ( coordinates, providedMetaData );

        // create artifact

        final ArtifactInformation result = this.channel.getContext ().createArtifact ( parentId, stream, coordinates.toFileName (), providedMetaData );

        if ( result != null )
        {
            // accepted
            return result.getId ();
        }

        // rejected
        return null;
    }

    @Override
    public Set<String> findArtifacts ( final Coordinates coordinates )
    {
        return internalFindArtifacts ( coordinates ).stream ().map ( ArtifactInformation::getId ).collect ( Collectors.toSet () );
    }

    @Override
    public void validateChecksum ( final Coordinates coordinates, final String algorithm, final String value ) throws ChecksumValidationException
    {
        final Collection<ArtifactInformation> arts = internalFindArtifacts ( coordinates );

        if ( arts.isEmpty () )
        {
            throw new ChecksumValidationException ( String.format ( "Unable to find artifact: %s", coordinates ) );
        }

        if ( arts.size () > 1 )
        {
            throw new ChecksumValidationException ( String.format ( "Multiple artifacts found for: %s -> %s", coordinates, gatherIds ( arts ) ) );
        }

        final ArtifactInformation art = arts.iterator ().next ();

        final String actualValue = art.getMetaData ().get ( new MetaKey ( "hasher", algorithm ) );
        if ( actualValue == null )
        {
            return;
        }

        if ( !actualValue.equalsIgnoreCase ( value ) )
        {
            throw new ChecksumValidationException ( String.format ( "Invalid checksum: {} - expected: {}, actual: {}", coordinates, value, actualValue ) );
        }
    }

    private String gatherIds ( final Collection<ArtifactInformation> arts )
    {
        final StringBuilder sb = new StringBuilder ( '{' );

        int i = 0;
        for ( final ArtifactInformation art : arts )
        {
            if ( i > 0 )
            {
                sb.append ( ", " );
            }
            sb.append ( art.getId () );
            i++;
        }

        sb.append ( '}' );

        return sb.toString ();
    }

    protected Collection<ArtifactInformation> internalFindArtifacts ( final Coordinates coordinates )
    {
        final String name = coordinates.toFileName ();

        final Map<MetaKey, String> search = new HashMap<> ();
        fillMetaData ( coordinates, search );

        final List<ArtifactInformation> candidates = this.channel.findByName ( name );

        return candidates.stream ().filter ( a -> matches ( a.getMetaData (), search ) ).collect ( Collectors.toList () );
    }

    private static boolean matches ( final Map<MetaKey, String> metaData, final Map<MetaKey, String> search )
    {
        if ( metaData.size () < search.size () )
        {
            // the map we search in does not contain all requires keys ... this will never be a match
            return false;
        }

        for ( final Map.Entry<MetaKey, String> entry : search.entrySet () )
        {
            final String otherEntry = metaData.get ( entry.getKey () );

            if ( otherEntry == entry.getValue () )
            {
                continue; // a match .. possibly "null"
            }

            if ( otherEntry == null )
            {
                // if other is null, but not equal to entry.getValue() .. then this is not a match

                return false;
            }

            // at this point neither otherEntry nor entry.getValue() can be null

            return otherEntry.equals ( entry.getValue () );
        }

        return true; // everything matches
    }

    private static void fillMetaData ( final Coordinates c, final Map<MetaKey, String> metaData )
    {
        // we can insert null here, which will mark the absence of an attribute

        metaData.put ( KEY_GROUP_ID, c.getGroupId () );
        metaData.put ( KEY_ARTIFACT_ID, c.getArtifactId () );
        metaData.put ( KEY_VERSION, c.getVersion () );
        metaData.put ( KEY_QUALIFIED_VERSION, c.getQualifiedVersion () );

        metaData.put ( KEY_CLASSIFIER, c.getClassifier () );

        metaData.put ( KEY_EXTENSION, c.getExtension () );
    }

}
