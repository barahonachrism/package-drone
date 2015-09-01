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
package de.dentrassi.pm.unzip;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.activation.FileTypeMap;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.maven.artifact.versioning.ComparableVersion;
import org.eclipse.scada.utils.str.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.ByteStreams;

import de.dentrassi.pm.common.ArtifactInformation;
import de.dentrassi.pm.common.MetaKey;
import de.dentrassi.pm.common.utils.IOConsumer;
import de.dentrassi.pm.storage.Artifact;
import de.dentrassi.pm.storage.Channel;
import de.dentrassi.pm.storage.channel.util.DownloadHelper;
import de.dentrassi.pm.storage.service.servlet.AbstractStorageServiceServlet;

public class UnzipServlet extends AbstractStorageServiceServlet
{

    private static final MetaKey MK_MIME_TYPE = new MetaKey ( "mime", "type" );

    private static final MetaKey MK_MVN_EXTENSION = new MetaKey ( "mvn", "extension" );

    private static final long serialVersionUID = 1L;

    private final static Logger logger = LoggerFactory.getLogger ( UnzipServlet.class );

    private static final MetaKey MK_GROUP_ID = new MetaKey ( "mvn", "groupId" );

    private static final MetaKey MK_ARTIFACT_ID = new MetaKey ( "mvn", "artifactId" );

    private static final MetaKey MK_CLASSIFIER = new MetaKey ( "mvn", "classifier" );

    private static final MetaKey MK_VERSION = new MetaKey ( "mvn", "version" );

    private static final MetaKey MK_SNAPSHOT_VERSION = new MetaKey ( "mvn", "snapshotVersion" );

    private FileTypeMap fileTypeMap;

    @Override
    public void init () throws ServletException
    {
        super.init ();
        this.fileTypeMap = FileTypeMap.getDefaultFileTypeMap ();
    }

    @Override
    protected void doGet ( final HttpServletRequest request, final HttpServletResponse response ) throws ServletException, IOException
    {
        String pathString = request.getPathInfo ();
        if ( pathString == null )
        {
            handleNotFound ( "", response );
            return;
        }

        if ( pathString.startsWith ( "/" ) )
        {
            pathString = pathString.substring ( 1 );
        }

        final String[] toks = pathString.split ( "/" );
        if ( toks.length < 1 )
        {
            handleNotFound ( request.getPathInfo (), response );
            return;
        }

        final LinkedList<String> path = new LinkedList<> ( Arrays.asList ( toks ) );

        try
        {
            final String type = path.pop ();
            switch ( type )
            {
                case "artifact":
                    handleArtifact ( request, response, path );
                    return;
                case "newest":
                    handleNewest ( request, response, path );
                    return;
                case "newestZip":
                    handleNewestZip ( request, response, path );
                    return;
                case "newestByName":
                    handleNewestByName ( request, response, path );
                    return;
                case "maven":
                    handleMaven ( request, response, path );
                    return;
                default:
                    handleNotFoundError ( response, String.format ( "Unzip target type '%s' unknown.", type ) );
                    return;
            }
        }
        catch ( final IllegalStateException e )
        {
            response.setStatus ( HttpServletResponse.SC_NOT_FOUND );
            response.setContentType ( "text/plain" );
            response.getWriter ().write ( e.getMessage () );
            return;
        }
        catch ( final IllegalArgumentException e )
        {
            response.setStatus ( HttpServletResponse.SC_BAD_GATEWAY );
            response.setContentType ( "text/plain" );
            response.getWriter ().write ( e.getMessage () );
            return;
        }
    }

    protected void handleMaven ( final HttpServletRequest request, final HttpServletResponse response, final LinkedList<String> path ) throws IOException
    {
        final IOConsumer<MavenVersionedArtifact> consumer = ( artifact ) -> {
            streamArtifactEntry ( request, response, artifact.getArtifact ().getId (), path );
        };

        if ( path.isEmpty () )
        {
            throw new IllegalArgumentException ( String.format ( "The 'maven' type needs an addition type (latest, prefixed, ...)" ) );
        }

        final String mavenType = path.pop ();

        if ( path.isEmpty () )
        {
            throw new IllegalArgumentException ( String.format ( "The 'maven' type needs the channel id or name after the sub-type" ) );
        }

        final String channelIdOrName = path.pop ();

        final Supplier<Collection<Artifact>> artifactSupplier = () -> {
            final Channel channel = getService ( request ).getChannelWithAlias ( channelIdOrName );
            if ( channel == null )
            {
                throw new IllegalStateException ( String.format ( "Channel with ID or name '%s' not found", channelIdOrName ) );
            }
            return channel.getArtifacts ();
        };

        switch ( mavenType )
        {
            case "latest":
                handleMavenLatest ( artifactSupplier, channelIdOrName, path, false, consumer );
                break;
            case "latest-SNAPSHOT":
                handleMavenLatest ( artifactSupplier, channelIdOrName, path, true, consumer );
                break;
            case "prefixed":
                handleMavenPrefixed ( artifactSupplier, channelIdOrName, path, consumer );
                break;
            case "perfect":
                handleMavenPerfect ( artifactSupplier, channelIdOrName, path, consumer );
                break;
            default:
                handleNotFoundError ( response, String.format ( "Unknown maven sub-type: %s", mavenType ) );
                break;
        }
    }

    protected void handleNewest ( final HttpServletRequest request, final HttpServletResponse response, final LinkedList<String> path ) throws IOException
    {
        handleWithFilter ( "newest", request, response, path, null );
    }

    protected void handleNewestZip ( final HttpServletRequest request, final HttpServletResponse response, final LinkedList<String> path ) throws IOException
    {
        handleWithFilter ( "newestZip", request, response, path, UnzipServlet::isZip );
    }

    protected void handleWithFilter ( final String type, final HttpServletRequest request, final HttpServletResponse response, final LinkedList<String> path, final Predicate<Artifact> filter ) throws IOException
    {
        requirePathPrefix ( path, 1, String.format ( "The '%1$s' method requires at least one parameter: channel. e.g. /unzip/%1$s/<channelIdOrName>/path/to/file", type ) );

        final String channelIdOrName = path.pop ();

        final Channel channel = getService ( request ).getChannelWithAlias ( channelIdOrName );
        if ( channel == null )
        {
            throw new IllegalStateException ( String.format ( "Channel with ID or name '%s' not found", channelIdOrName ) );
        }

        List<Artifact> arts = new ArrayList<> ( channel.getArtifacts () );

        if ( filter != null )
        {
            arts = arts.stream ().filter ( filter ).collect ( Collectors.toList () );
        }

        if ( arts.isEmpty () )
        {
            throw new IllegalStateException ( String.format ( "Unable to find artifacts in channel '%s' (%s)", channelIdOrName, channel.getId () ) );
        }

        Collections.sort ( arts, Artifact.CREATION_TIMESTAMP_COMPARATOR );

        final Artifact artifact = arts.get ( 0 );

        logger.debug ( "Streaming artifact {} for channel {}", artifact.getId (), channelIdOrName );

        streamArtifactEntry ( request, response, artifact.getId (), path );
    }

    protected static void processArtifacts ( final String sourceName, final List<MavenVersionedArtifact> arts, final IOConsumer<MavenVersionedArtifact> consumer ) throws IOException
    {
        if ( arts.isEmpty () )
        {
            throw new IllegalStateException ( String.format ( "Unable to find artifacts in %s", sourceName ) );
        }

        Collections.sort ( arts ); // by version

        final MavenVersionedArtifact artifact = arts.get ( arts.size () - 1 ); // get last

        logger.debug ( "Streaming artifact {} for {}", artifact.getArtifact ().getId (), sourceName );

        consumer.accept ( artifact );
    }

    protected static void handleMavenPrefixed ( final Supplier<Collection<Artifact>> artifactsSupplier, final String channelIdOrName, final LinkedList<String> path, final IOConsumer<MavenVersionedArtifact> consumer ) throws IOException
    {
        requirePathPrefix ( path, 3, "The 'maven' method requires at least one parameter: channel. e.g. /unzip/maven/prefixed/<channelIdOrName>/<group.id>/<artifact.id>/<version>/path/to/file" );

        final String groupId = path.pop ();
        final String artifactId = path.pop ();
        final String versionString = path.pop ();

        final String versionPrefix;
        final int idx = versionString.toLowerCase ().indexOf ( 'x' );
        if ( idx > 0 )
        {
            // the x marks the spot
            versionPrefix = versionString.substring ( 0, idx - 1 );
        }
        else
        {
            versionPrefix = versionString;
        }

        final boolean snapshot = versionString.endsWith ( "-SNAPSHOT" );

        final List<MavenVersionedArtifact> arts = getMavenArtifacts ( artifactsSupplier, groupId, artifactId, snapshot, ( a ) -> a.toString ().startsWith ( versionPrefix ) );

        if ( arts.isEmpty () )
        {
            // no result,
            throw new IllegalStateException ( String.format ( "No artifacts found for - groupId: %s, artifactId: %s, version: %s, snapshots: %s", groupId, artifactId, versionPrefix, snapshot ) );
        }

        processArtifacts ( String.format ( "maven artifact %s/%s/%s in channel %s", groupId, artifactId, versionString, channelIdOrName ), arts, consumer );
    }

    protected static void handleMavenPerfect ( final Supplier<Collection<Artifact>> artifactsSupplier, final String channelIdOrName, final LinkedList<String> path, final IOConsumer<MavenVersionedArtifact> consumer ) throws IOException
    {
        requirePathPrefix ( path, 3, "The 'maven' method requires at least one parameter: channel. e.g. /unzip/maven/perfect/<channelIdOrName>/<group.id>/<artifact.id>/<version>/path/to/file" );

        final String groupId = path.pop ();
        final String artifactId = path.pop ();
        final String versionString = path.pop ();

        final ComparableVersion v = new ComparableVersion ( versionString );

        final List<MavenVersionedArtifact> arts = getMavenArtifacts ( artifactsSupplier, groupId, artifactId, true, ( a ) -> a.compareTo ( v ) == 0 );

        if ( arts.isEmpty () )
        {
            // no result,
            throw new IllegalStateException ( String.format ( "No artifacts found for - groupId: %s, artifactId: %s, version: %s", groupId, artifactId, v ) );
        }

        processArtifacts ( String.format ( "maven artifact %s/%s/%s in channel %s", groupId, artifactId, versionString, channelIdOrName ), arts, consumer );
    }

    protected static void handleMavenLatest ( final Supplier<Collection<Artifact>> artifactsSupplier, final String channelIdOrName, final LinkedList<String> path, final boolean snapshot, final IOConsumer<MavenVersionedArtifact> consumer ) throws IOException
    {
        requirePathPrefix ( path, 2, "The 'maven' method requires at least two parameters: groupId, artifactId. e.g. /unzip/maven/latest(-SNAPSHOT)/<channelIdOrName>/<group.id>/<artifact.id>/path/to/file" );

        final String groupId = path.pop ();
        final String artifactId = path.pop ();

        final List<MavenVersionedArtifact> arts = getMavenArtifacts ( artifactsSupplier, groupId, artifactId, snapshot, null );

        if ( arts.isEmpty () )
        {
            // no result,
            throw new IllegalStateException ( String.format ( "No artifacts found for - groupId: %s, artifactId: %s", groupId, artifactId ) );
        }

        processArtifacts ( String.format ( "latest maven artifact %s/%s in channel %s", groupId, artifactId, channelIdOrName ), arts, consumer );
    }

    /**
     * Get a list of all relevant maven artifacts
     *
     * @param artifactsSupplier
     *            the supplier of artifacts
     * @param groupId
     *            the group id to filter for, must not be <code>null
     * @param artifactId
     *            the artifact id to filter for, must not be <code>null
     * @param snapshot
     *            whether to consider snapshot versions of not
     * @param versionFilter
     *            an optional version filter
     * @return a list of all matching artifacts wrapped in
     *         {@link MavenVersionedArtifact}, if there is a snapshot version
     *         present, then the snapshot version of used as version
     */
    protected static List<MavenVersionedArtifact> getMavenArtifacts ( final Supplier<Collection<Artifact>> artifactsSupplier, final String groupId, final String artifactId, final boolean snapshot, final Predicate<ComparableVersion> versionFilter )
    {
        final List<MavenVersionedArtifact> arts = new ArrayList<> ();

        for ( final Artifact art : artifactsSupplier.get () )
        {
            if ( !isZip ( art ) )
            {
                // if is is anot a zip, then this is not for the unzip plugin
                continue;
            }

            // fetch meta data

            final ArtifactInformation ai = art.getInformation ();
            final String mvnGroupId = ai.getMetaData ().get ( MK_GROUP_ID );
            final String mvnArtifactId = ai.getMetaData ().get ( MK_ARTIFACT_ID );

            final String classifier = ai.getMetaData ().get ( MK_CLASSIFIER );

            final String mvnVersion = ai.getMetaData ().get ( MK_VERSION );
            final String mvnSnapshotVersion = ai.getMetaData ().get ( MK_SNAPSHOT_VERSION );

            if ( mvnGroupId == null || mvnArtifactId == null || mvnVersion == null )
            {
                // no GAV information
                continue;
            }

            if ( classifier != null && !classifier.isEmpty () )
            {
                // no classifiers right now
                continue;
            }

            if ( !mvnGroupId.equals ( groupId ) || !mvnArtifactId.equals ( artifactId ) )
            {
                // wrong group or artifact id
                continue;
            }

            if ( !snapshot && ( mvnSnapshotVersion != null || mvnVersion.endsWith ( "-SNAPSHOT" ) ) )
            {
                // we are not looking for snapshots
                continue;
            }

            final ComparableVersion v = parseVersion ( mvnVersion );
            final ComparableVersion sv = parseVersion ( mvnSnapshotVersion );

            if ( v == null )
            {
                // unable to parse v
                continue;
            }

            if ( versionFilter == null )
            {
                // no filter, add it
                arts.add ( new MavenVersionedArtifact ( sv != null ? sv : v, art ) );
            }
            else if ( versionFilter.test ( v ) )
            {
                // filter matched, add it
                arts.add ( new MavenVersionedArtifact ( sv != null ? sv : v, art ) );
            }
            else if ( sv != null && versionFilter.test ( sv ) )
            {
                // we have a snapshot version and it matched, add it
                arts.add ( new MavenVersionedArtifact ( sv, art ) );
            }
        }

        return arts;
    }

    private static ComparableVersion parseVersion ( final String version )
    {
        if ( version == null )
        {
            return null;
        }

        try
        {
            return new ComparableVersion ( version );
        }
        catch ( final Exception e )
        {
            logger.debug ( "Version not parsable: " + version, e );
            return null;
        }
    }

    /**
     * Check if an artifact is a ZIP file
     * <p>
     * An artifact is a ZIP file if at least one of th following tests is true:
     * <ul>
     * <li>Its lower case name ends with <code>.zip</code></li>
     * <li>The meta data field <code>mvn:extension</code> is set to
     * <code>zip</code>
     * <li>The meta data field <code>mime:type</code> is set to
     * <code>application/zip</code>
     * </ul>
     * </p>
     *
     * @param artifact
     *            the artifact to check
     * @return <code>true</code> if the artifact is a ZIP file,
     *         <code>false</code> otherwise
     */
    protected static boolean isZip ( final Artifact artifact )
    {
        if ( artifact.getInformation ().getName ().toLowerCase ().endsWith ( ".zip" ) )
        {
            return true;
        }

        final String mdExtension = artifact.getInformation ().getMetaData ().get ( MK_MVN_EXTENSION );
        if ( mdExtension != null && mdExtension.equalsIgnoreCase ( "zip" ) )
        {
            return true;
        }

        final String mdMime = artifact.getInformation ().getMetaData ().get ( MK_MIME_TYPE );
        if ( mdMime != null && mdMime.equalsIgnoreCase ( "application/zip" ) )
        {
            return true;
        }

        return false;
    }

    protected void handleNewestByName ( final HttpServletRequest request, final HttpServletResponse response, final LinkedList<String> path ) throws IOException
    {
        requirePathPrefix ( path, 2, "The 'newestByName' method requires at least two parameters: channel and name. e.g. /unzip/newestByName/<channelIdOrName>/<artifactName>/path/to/file" );

        final String channelIdOrName = path.pop ();
        final String name = path.pop ();

        final Channel channel = getService ( request ).getChannelWithAlias ( channelIdOrName );
        if ( channel == null )
        {
            throw new IllegalStateException ( String.format ( "Channel with ID or name '%s' not found", channelIdOrName ) );
        }

        final List<Artifact> arts = channel.findByName ( name );

        if ( arts.isEmpty () )
        {
            throw new IllegalStateException ( String.format ( "Unable to find artifact with name '%s' in channel '%s' (%s)", name, channelIdOrName, channel.getId () ) );
        }

        Collections.sort ( arts, Artifact.CREATION_TIMESTAMP_COMPARATOR );

        final Artifact artifact = arts.get ( 0 );

        logger.debug ( "Streaming artifact {} for name {} in channel {}", artifact.getId (), name, channelIdOrName );

        streamArtifactEntry ( request, response, artifact.getId (), path );
    }

    private static void requirePathPrefix ( final LinkedList<String> path, final int pathPrefixCount, final String message )
    {
        if ( path.size () < pathPrefixCount )
        {
            throw new IllegalArgumentException ( message );
        }
    }

    protected void handleArtifact ( final HttpServletRequest request, final HttpServletResponse response, final LinkedList<String> path ) throws IOException
    {
        requirePathPrefix ( path, 1, "The 'artifact' method requires at least one parameter: artifactId. e.g. /unzip/artifact/<artifactId>/path/to/file" );

        final String artifactId = path.pop ();
        try
        {
            streamArtifactEntry ( request, response, artifactId, path );
        }
        catch ( final FileNotFoundException e )
        {
            handleNotFoundError ( response, String.format ( "Artifact '%s' could not be found", artifactId ) );
            return;
        }
    }

    protected void streamArtifactEntry ( final HttpServletRequest request, final HttpServletResponse response, final String artifactId, final List<String> path ) throws IOException
    {
        final String localPath = StringHelper.join ( path, "/" );

        if ( localPath.isEmpty () )
        {
            DownloadHelper.streamArtifact ( response, getService ( request ), artifactId, null, true );
            return;
        }

        // TODO: implement cache

        if ( !getService ( request ).streamArtifact ( artifactId, ( ai, stream ) -> {
            try ( final ZipInputStream zis = new ZipInputStream ( stream ) )
            {
                ZipEntry entry;
                while ( ( entry = zis.getNextEntry () ) != null )
                {
                    if ( entry.getName ().equals ( localPath ) )
                    {
                        final String type = this.fileTypeMap.getContentType ( entry.getName () );
                        response.setContentType ( type );
                        response.setContentLengthLong ( entry.getSize () );
                        response.setDateHeader ( "Last-Modified", ai.getCreationTimestamp ().getTime () );
                        ByteStreams.copy ( zis, response.getOutputStream () );
                        break;
                    }
                }
                if ( entry == null || !entry.getName ().equals ( localPath ) )
                {
                    handleNotFoundError ( response, String.format ( "File entry '%s' could not be found in artifact '%s'", localPath, artifactId ) );
                }
            }
        } ) )
        {
            handleNotFoundError ( response, String.format ( "Artifact %s could not be found", artifactId ) );
        }
    }

    protected void handleNotFound ( final String path, final HttpServletResponse response ) throws IOException
    {
        handleNotFoundError ( response, String.format ( "Resource '%s' cound not be found", path ) );
    }

    protected void handleNotFoundError ( final HttpServletResponse response, final String message ) throws IOException
    {
        response.setStatus ( HttpServletResponse.SC_NOT_FOUND );
        response.setContentType ( "text/plain" );
        response.getWriter ().write ( message );
    }
}
