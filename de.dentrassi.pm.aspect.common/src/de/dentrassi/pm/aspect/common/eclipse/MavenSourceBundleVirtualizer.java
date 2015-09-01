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
package de.dentrassi.pm.aspect.common.eclipse;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.osgi.framework.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.ByteStreams;

import de.dentrassi.pm.aspect.common.osgi.OsgiExtractor;
import de.dentrassi.pm.aspect.virtual.Virtualizer;
import de.dentrassi.pm.common.MetaKey;
import de.dentrassi.pm.osgi.bundle.BundleInformation;
import de.dentrassi.pm.storage.channel.ArtifactInformation;

public class MavenSourceBundleVirtualizer implements Virtualizer
{
    private final static Logger logger = LoggerFactory.getLogger ( MavenSourceBundleVirtualizer.class );

    @Override
    public void virtualize ( final Context context )
    {
        final ArtifactInformation ai = context.getArtifactInformation ();

        if ( ai.getParentId () == null )
        {
            logger.debug ( "don't create - parent id is null" );
            return;
        }

        if ( !ai.getName ().endsWith ( "-sources.jar" ) )
        {
            logger.debug ( "don't create - name does not match" );
            return;
        }

        final BundleInformation bi = findBundleInformation ( context );
        if ( bi == null )
        {
            logger.debug ( "don't create - parent has no bundle information" );
            return;
        }

        try
        {
            createSourceBundle ( context, bi );
        }
        catch ( final Exception e )
        {
            throw new RuntimeException ( "Failed to create virtual source bundle", e );
        }
    }

    protected void createSourceBundle ( final Context context, final BundleInformation bi ) throws Exception
    {
        final Map<MetaKey, String> providedMetaData = new HashMap<> ();

        final Path tmp = Files.createTempFile ( "src-", null );
        try
        {
            final String name = String.format ( "%s.source_%s.jar", bi.getId (), bi.getVersion () );

            createSourceBundle ( tmp, context, bi );

            try ( BufferedInputStream in = new BufferedInputStream ( new FileInputStream ( tmp.toFile () ) ) )
            {
                context.createVirtualArtifact ( name, in, providedMetaData );
            }
        }
        finally
        {
            Files.deleteIfExists ( tmp );
        }
    }

    private void createSourceBundle ( final Path tmp, final Context context, final BundleInformation bi ) throws Exception
    {
        try ( ZipInputStream zis = new ZipInputStream ( new BufferedInputStream ( new FileInputStream ( context.getFile ().toFile () ) ) );
              ZipOutputStream zos = new ZipOutputStream ( new BufferedOutputStream ( new FileOutputStream ( tmp.toFile () ) ) ) )
        {
            ZipEntry entry;
            while ( ( entry = zis.getNextEntry () ) != null )
            {
                if ( entry.getName ().equals ( "META-INF/MANIFEST.MF" ) )
                {
                    continue;
                }

                zos.putNextEntry ( entry );
                ByteStreams.copy ( zis, zos );

            }

            entry = new ZipEntry ( "META-INF/MANIFEST.MF" );
            zos.putNextEntry ( entry );
            final Manifest mf = new Manifest ();
            fillManifest ( mf, bi );
            mf.write ( zos );

            if ( bi.getLocalization () != null && !bi.getLocalization ().isEmpty () )
            {
                for ( final Map.Entry<String, Properties> le : bi.getLocalization ().entrySet () )
                {
                    final String locale = le.getKey ();
                    final String suffix = locale != null && !locale.isEmpty () ? "_" + locale : "";
                    entry = new ZipEntry ( bi.getLocalization () + suffix );
                    zos.putNextEntry ( entry );
                    le.getValue ().store ( zos, null );
                }
            }
        }
    }

    private void fillManifest ( final Manifest mf, final BundleInformation bi )
    {
        final Attributes attr = mf.getMainAttributes ();

        attr.put ( Attributes.Name.MANIFEST_VERSION, "1.0" );

        attr.putValue ( Constants.BUNDLE_SYMBOLICNAME, bi.getId () + ".source" );
        attr.putValue ( Constants.BUNDLE_VERSION, "" + bi.getVersion () );
        attr.putValue ( Constants.BUNDLE_MANIFESTVERSION, "2" );
        attr.putValue ( Constants.BUNDLE_VENDOR, bi.getVendor () );
        attr.putValue ( Constants.BUNDLE_NAME, String.format ( "Source bundle for '%s'", bi.getId () ) );

        attr.putValue ( "Created-By", "Package Drone" );

        attr.putValue ( "Eclipse-SourceBundle", makeSourceString ( bi ) );

        if ( bi.getBundleLocalization () != null )
        {
            attr.putValue ( Constants.BUNDLE_LOCALIZATION, bi.getBundleLocalization () );
        }
    }

    private String makeSourceString ( final BundleInformation bi )
    {
        final StringBuilder sb = new StringBuilder ();

        sb.append ( bi.getId () );
        sb.append ( ';' );
        sb.append ( "version=\"" ).append ( bi.getVersion () ).append ( "\"" );
        sb.append ( ';' );
        sb.append ( "roots:=" );
        sb.append ( "\".\"" );

        return sb.toString ();
    }

    private BundleInformation findBundleInformation ( final Context context )
    {
        final ArtifactInformation parent = context.getOtherArtifactInformation ( context.getArtifactInformation ().getParentId () );
        if ( parent == null )
        {
            return null;
        }

        final String biString = parent.getMetaData ().get ( new MetaKey ( OsgiExtractor.NAMESPACE, OsgiExtractor.KEY_BUNDLE_INFORMATION ) );

        return BundleInformation.fromJson ( biString );
    }
}
