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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.Base64;

public final class Tokens
{
    private static final SecureRandom random = new SecureRandom ();

    private Tokens ()
    {
    }

    public static String hex ( final byte[] digest )
    {
        final StringBuilder sb = new StringBuilder ( digest.length * 2 );

        for ( int i = 0; i < digest.length; i++ )
        {
            sb.append ( String.format ( "%02x", digest[i] & 0xFF ) );
        }

        return sb.toString ();
    }

    public static String createToken ( final int length )
    {
        final byte[] data = new byte[length];

        random.nextBytes ( data );

        return hex ( data );
    }

    public static String hashIt ( final String salt, String data )
    {
        data = Normalizer.normalize ( data, Form.NFC );

        final byte[] strData = data.getBytes ( StandardCharsets.UTF_8 );
        final byte[] saltData = salt.getBytes ( StandardCharsets.UTF_8 );

        final byte[] first = new byte[saltData.length + strData.length];
        System.arraycopy ( saltData, 0, first, 0, saltData.length );
        System.arraycopy ( strData, 0, first, saltData.length, strData.length );

        MessageDigest md;
        try
        {
            md = MessageDigest.getInstance ( "SHA-256" );
        }
        catch ( final NoSuchAlgorithmException e )
        {
            throw new IllegalStateException ( e );
        }

        byte[] digest = md.digest ( first );
        final byte[] current = new byte[saltData.length + digest.length];

        for ( int i = 0; i < 1000; i++ )
        {
            System.arraycopy ( saltData, 0, current, 0, saltData.length );
            System.arraycopy ( digest, 0, current, saltData.length, digest.length );

            digest = md.digest ( current );
        }

        return Base64.getEncoder ().encodeToString ( digest );
    }

}
