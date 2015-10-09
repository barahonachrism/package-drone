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
package org.eclipse.packagedrone.utils.rpm;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tukaani.xz.LZMAInputStream;
import org.tukaani.xz.XZInputStream;

import com.google.common.io.CountingInputStream;

public class RpmInputStream extends InputStream
{
    private final static Logger logger = LoggerFactory.getLogger ( RpmInputStream.class );

    private static final byte[] LEAD_MAGIC = new byte[] { (byte)0xED, (byte)0xAB, (byte)0xEE, (byte)0xDB };

    private static final byte[] HEADER_MAGIC = new byte[] { (byte)0x8E, (byte)0xAD, (byte)0xE8 };

    private static final byte[] DUMMY = new byte[128];

    private final DataInputStream in;

    private boolean closed;

    private RpmLead lead;

    private RpmHeader<RpmSignatureTag> signatureHeader;

    private RpmHeader<RpmTag> payloadHeader;

    private InputStream payloadStream;

    private CpioArchiveInputStream cpioStream;

    private final CountingInputStream count;

    public RpmInputStream ( final InputStream in )
    {
        this.count = new CountingInputStream ( in );
        this.in = new DataInputStream ( this.count );
    }

    @Override
    public void close () throws IOException
    {
        if ( !this.closed )
        {
            this.in.close ();
            this.closed = true;
        }
    }

    protected void ensureInit () throws IOException
    {
        if ( this.lead == null )
        {
            this.lead = readLead ();
        }

        if ( this.signatureHeader == null )
        {
            this.signatureHeader = readHeader ( true );
        }

        if ( this.payloadHeader == null )
        {
            this.payloadHeader = readHeader ( false );
        }

        // set up content stream

        if ( this.payloadStream == null )
        {
            this.payloadStream = setupPayloadStream ();
            this.cpioStream = new CpioArchiveInputStream ( this.payloadStream ); // we did ensure that we only support CPIO before
        }
    }

    private InputStream setupPayloadStream () throws IOException
    {
        final Object payloadFormatValue = this.payloadHeader.getRawTags ().get ( RpmTag.PAYLOAD_FORMAT.getValue () );
        final Object payloadCodingValue = this.payloadHeader.getRawTags ().get ( RpmTag.PAYLOAD_CODING.getValue () );

        if ( payloadFormatValue != null && ! ( payloadFormatValue instanceof String ) )
        {
            throw new IOException ( "Payload format must be a single string" );
        }

        if ( payloadFormatValue != null && ! ( payloadCodingValue instanceof String ) )
        {
            throw new IOException ( "Payload coding must be a single string" );
        }

        String payloadFormat = (String)payloadFormatValue;
        String payloadCoding = (String)payloadCodingValue;

        if ( payloadFormat == null || payloadFormat.isEmpty () )
        {
            payloadFormat = "cpio";
        }

        if ( payloadCoding == null || payloadCoding.isEmpty () )
        {
            payloadCoding = "gzip";
        }

        if ( !"cpio".equals ( payloadFormat ) )
        {
            throw new IOException ( String.format ( "Unknown payload format: %s", payloadFormat ) );
        }

        switch ( payloadCoding )
        {
            case "none":
                return this.in;
            case "gzip":
                return new GzipCompressorInputStream ( this.in );
            case "bzip2":
                return new BZip2CompressorInputStream ( this.in );
            case "lzma":
                return new LZMAInputStream ( this.in );
            case "xz":
                return new XZInputStream ( this.in );
            default:
                throw new IOException ( String.format ( "Unknown coding: %s", payloadCoding ) );
        }
    }

    public CpioArchiveInputStream getCpioStream ()
    {
        return this.cpioStream;
    }

    public RpmLead getLead () throws IOException
    {
        ensureInit ();
        return this.lead;
    }

    public RpmHeader<RpmSignatureTag> getSignatureHeader () throws IOException
    {
        ensureInit ();
        return this.signatureHeader;
    }

    public RpmHeader<RpmTag> getPayloadHeader () throws IOException
    {
        ensureInit ();
        return this.payloadHeader;
    }

    protected RpmLead readLead () throws IOException
    {
        final byte[] magic = readComplete ( 4 );

        if ( !Arrays.equals ( magic, LEAD_MAGIC ) )
        {
            throw new IOException ( String.format ( "File corrupt: Expected magic %s, read: %s", Arrays.toString ( LEAD_MAGIC ), Arrays.toString ( magic ) ) );
        }

        final byte[] version = readComplete ( 2 );

        skipFully ( 4 ); // TYPE + ARCH

        final byte[] nameData = readComplete ( 66 ); // NAME

        final String name = StandardCharsets.UTF_8.decode ( ByteBuffer.wrap ( nameData ) ).toString ();

        skipFully ( 2 ); // OS

        final int sigType = this.in.readUnsignedShort ();

        skipFully ( 16 ); // RESERVED

        return new RpmLead ( version[0], version[1], name, sigType );
    }

    protected <T extends RpmBaseTag> RpmHeader<T> readHeader ( final boolean withPadding ) throws IOException
    {
        final long start = this.count.getCount ();

        final byte[] magic = readComplete ( 3 );

        if ( !Arrays.equals ( magic, HEADER_MAGIC ) )
        {
            throw new IOException ( String.format ( "File corrupt: Expected entry magic %s, read: %s", Arrays.toString ( HEADER_MAGIC ), Arrays.toString ( magic ) ) );
        }

        final byte version = this.in.readByte ();
        if ( version != 1 )
        {
            throw new IOException ( String.format ( "File corrupt: Invalid header entry version: %s (valid: 1)", version ) );
        }

        skipFully ( 4 ); // RESERVED

        final int indexCount = this.in.readInt ();
        final long storeSize = this.in.readInt () & 0xFFFFFFFF;

        final RpmEntry[] entries = new RpmEntry[indexCount];

        for ( int i = 0; i < indexCount; i++ )
        {
            entries[i] = readEntry ();
        }

        final ByteBuffer store = ByteBuffer.wrap ( readComplete ( (int)storeSize ) ); // FIXME: bad casting ...

        for ( int i = 0; i < indexCount; i++ )
        {
            entries[i].fillFromStore ( store );
        }

        if ( withPadding )
        {
            // pad remaining bytes - to 8

            final long rem = storeSize % 8;
            if ( rem > 0 )
            {
                final int skip = (int) ( 8 - rem );
                logger.debug ( "Skipping {} pad bytes", skip );
                skipFully ( skip );
            }
        }

        final long end = this.count.getCount ();

        return new RpmHeader<T> ( entries, start, end - start );
    }

    private RpmEntry readEntry () throws IOException
    {
        final int tag = this.in.readInt ();
        final int type = this.in.readInt ();
        final int offset = this.in.readInt ();
        final int count = this.in.readInt ();

        return new RpmEntry ( tag, type, offset, count );
    }

    private byte[] readComplete ( final int size ) throws IOException
    {
        final byte[] result = new byte[size];
        this.in.readFully ( result );
        return result;
    }

    private void skipFully ( final int count ) throws IOException
    {
        this.in.readFully ( DUMMY, 0, count );
    }

    // forward methods

    @Override
    public void reset () throws IOException
    {
        ensureInit ();
        this.payloadStream.reset ();
    }

    @Override
    public int read () throws IOException
    {
        ensureInit ();
        return this.payloadStream.read ();
    }

    @Override
    public long skip ( final long n ) throws IOException
    {
        ensureInit ();
        return this.payloadStream.skip ( n );
    }

    @Override
    public int available () throws IOException
    {
        ensureInit ();
        return this.payloadStream.available ();
    }

    @Override
    public int read ( final byte[] b ) throws IOException
    {
        ensureInit ();
        return this.payloadStream.read ( b );
    }

    @Override
    public int read ( final byte[] b, final int off, final int len ) throws IOException
    {
        return this.payloadStream.read ( b, off, len );
    }

}
