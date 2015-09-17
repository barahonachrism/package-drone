package de.dentrassi.pm.storage.channel.apm;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicReference;

import org.osgi.service.event.EventAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.LongSerializationPolicy;

import de.dentrassi.pm.apm.AbstractSimpleStorageModelProvider;
import de.dentrassi.pm.apm.StorageContext;
import de.dentrassi.pm.common.MetaKey;
import de.dentrassi.pm.storage.channel.apm.internal.Finally;
import de.dentrassi.pm.storage.channel.apm.store.BlobStore;
import de.dentrassi.pm.storage.channel.apm.store.BlobStore.Transaction;
import de.dentrassi.pm.storage.channel.apm.store.CacheStore;
import de.dentrassi.pm.storage.channel.provider.AccessContext;

public class ChannelModelProvider extends AbstractSimpleStorageModelProvider<AccessContext, ModifyContextImpl>
{
    private final static Logger logger = LoggerFactory.getLogger ( ChannelModelProvider.class );

    private final String channelId;

    private BlobStore store;

    private CacheStore cacheStore;

    private final EventAdmin eventAdmin;

    public ChannelModelProvider ( final EventAdmin eventAdmin, final String channelId )
    {
        super ( AccessContext.class, ModifyContextImpl.class );

        this.eventAdmin = eventAdmin;

        this.channelId = channelId;
    }

    @Override
    public void start ( final StorageContext context ) throws Exception
    {
        this.store = new BlobStore ( makeBasePath ( context, this.channelId ).resolve ( "blobs" ) );
        this.cacheStore = new CacheStore ( makeBasePath ( context, this.channelId ).resolve ( "cache" ) );
        super.start ( context );
    }

    @Override
    public void stop ()
    {
        super.stop ();
        this.store.close ();
        this.cacheStore.close ();
    }

    @Override
    protected AccessContext makeViewModelTyped ( final ModifyContextImpl writeModel )
    {
        return writeModel;
    }

    @Override
    protected ModifyContextImpl cloneWriteModel ( final ModifyContextImpl writeModel )
    {
        return new ModifyContextImpl ( writeModel );
    }

    public static Path makeBasePath ( final StorageContext context, final String channelId )
    {
        return context.getBasePath ().resolve ( Paths.get ( "channels", channelId ) );
    }

    public static Path makeStatePath ( final StorageContext context, final String channelId )
    {
        return makeBasePath ( context, channelId ).resolve ( "state.json" );
    }

    private Gson createGson ()
    {
        final GsonBuilder builder = new GsonBuilder ();

        builder.setPrettyPrinting ();
        builder.serializeNulls ();
        builder.setLongSerializationPolicy ( LongSerializationPolicy.STRING );
        builder.setDateFormat ( "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'" );
        builder.registerTypeAdapter ( MetaKey.class, new JsonDeserializer<MetaKey> () {

            @Override
            public MetaKey deserialize ( final JsonElement json, final Type type, final JsonDeserializationContext ctx ) throws JsonParseException
            {
                return MetaKey.fromString ( json.getAsString () );
            }
        } );

        return builder.create ();
    }

    @Override
    protected void persistWriteModel ( final StorageContext context, final ModifyContextImpl writeModel ) throws Exception
    {
        final AtomicReference<Transaction> t = new AtomicReference<> ( writeModel.claimTransaction () );
        final AtomicReference<CacheStore.Transaction> ct = new AtomicReference<> ( writeModel.claimCacheTransaction () );

        final Finally f = new Finally ();

        f.add ( () -> {
            final Transaction v = t.get ();
            if ( v != null )
            {
                v.rollback ();
            }
        } );

        f.add ( () -> {
            final CacheStore.Transaction v = ct.get ();
            if ( v != null )
            {
                v.rollback ();
            }
        } );

        try
        {
            final Path path = makeStatePath ( context, this.channelId );
            Files.createDirectories ( path.getParent () );

            // commit blob store

            if ( t.get () != null )
            {
                t.get ().commit ();
                t.set ( null );
            }

            // write model

            try ( Writer writer = Files.newBufferedWriter ( path, StandardCharsets.UTF_8 ) )
            {
                final Gson gson = createGson ();
                gson.toJson ( writeModel.getModel (), writer );
            }

            // commit cache store

            if ( ct.get () != null )
            {
                ct.get ().commit ();
                ct.set ( null );
            }
        }
        catch ( final Exception e )
        {
            logger.warn ( "Failed to persist model", e );
            throw e;
        }
        finally
        {
            f.runAll ();
        }
    }

    @Override
    protected ModifyContextImpl loadWriteModel ( final StorageContext context ) throws Exception
    {
        final Path path = makeStatePath ( context, this.channelId );

        try ( Reader reader = Files.newBufferedReader ( path, StandardCharsets.UTF_8 ) )
        {
            final Gson gson = createGson ();
            final ChannelModel model = gson.fromJson ( reader, ChannelModel.class );
            if ( model == null )
            {
                // FIXME: handle broken channel state
                throw new IllegalStateException ( "Unable to load channel model" );
            }
            return new ModifyContextImpl ( this.channelId, this.eventAdmin, this.store, this.cacheStore, model );
        }
        catch ( final NoSuchFileException e )
        {
            // create a new model
            return new ModifyContextImpl ( this.channelId, this.eventAdmin, this.store, this.cacheStore, new ChannelModel () );
        }
    }

}
