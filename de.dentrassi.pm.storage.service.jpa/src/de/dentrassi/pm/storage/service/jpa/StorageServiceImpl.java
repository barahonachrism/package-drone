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
package de.dentrassi.pm.storage.service.jpa;

import static de.dentrassi.pm.storage.service.jpa.StreamServiceHelper.getArtifactFacets;
import static de.dentrassi.pm.storage.service.jpa.StreamServiceHelper.getParentId;
import static de.dentrassi.pm.storage.service.jpa.StreamServiceHelper.testLocked;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.function.Supplier;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.osgi.framework.FrameworkUtil;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Multimap;

import de.dentrassi.osgi.profiler.Profile;
import de.dentrassi.osgi.web.LinkTarget;
import de.dentrassi.pm.common.ArtifactInformation;
import de.dentrassi.pm.common.ChannelAspectInformation;
import de.dentrassi.pm.common.DetailedArtifactInformation;
import de.dentrassi.pm.common.MetaKey;
import de.dentrassi.pm.common.SimpleArtifactInformation;
import de.dentrassi.pm.common.lm.LockContext;
import de.dentrassi.pm.common.lm.LockManager;
import de.dentrassi.pm.common.service.AbstractJpaServiceImpl;
import de.dentrassi.pm.common.utils.ThrowingConsumer;
import de.dentrassi.pm.core.CoreService;
import de.dentrassi.pm.generator.GeneratorProcessor;
import de.dentrassi.pm.storage.Artifact;
import de.dentrassi.pm.storage.ArtifactReceiver;
import de.dentrassi.pm.storage.CacheEntry;
import de.dentrassi.pm.storage.CacheEntryInformation;
import de.dentrassi.pm.storage.Channel;
import de.dentrassi.pm.storage.DeployGroup;
import de.dentrassi.pm.storage.DeployKey;
import de.dentrassi.pm.storage.ValidationMessage;
import de.dentrassi.pm.storage.jpa.ArtifactEntity;
import de.dentrassi.pm.storage.jpa.ArtifactEntity_;
import de.dentrassi.pm.storage.jpa.ChannelEntity;
import de.dentrassi.pm.storage.jpa.DeployGroupEntity;
import de.dentrassi.pm.storage.jpa.DeployKeyEntity;
import de.dentrassi.pm.storage.jpa.ExtractedArtifactPropertyEntity;
import de.dentrassi.pm.storage.jpa.ExtractedChannelPropertyEntity;
import de.dentrassi.pm.storage.jpa.GeneratorArtifactEntity;
import de.dentrassi.pm.storage.jpa.PropertyEntity;
import de.dentrassi.pm.storage.jpa.StoredArtifactEntity;
import de.dentrassi.pm.storage.jpa.ValidationMessageEntity;
import de.dentrassi.pm.storage.jpa.VirtualArtifactEntity;
import de.dentrassi.pm.storage.service.ServiceStatistics;
import de.dentrassi.pm.storage.service.StorageService;
import de.dentrassi.pm.storage.service.StorageServiceAdmin;
import de.dentrassi.pm.storage.service.jpa.blob.BlobStore;
import de.dentrassi.pm.storage.service.jpa.guard.ArtifactGuard;

public class StorageServiceImpl extends AbstractJpaServiceImpl implements StorageService, StreamServiceHelper, StorageServiceAdmin
{
    private final static Logger logger = LoggerFactory.getLogger ( StorageServiceImpl.class );

    private final GeneratorProcessor generatorProcessor = new GeneratorProcessor ( FrameworkUtil.getBundle ( StorageServiceImpl.class ).getBundleContext () );

    private LockManager lockManager;

    private EventAdmin eventAdmin;

    private CoreService coreService;

    private final BlobStore blobStore = new BlobStore ();

    public void setCoreService ( final CoreService coreService )
    {
        this.coreService = coreService;
    }

    public void setEventAdmin ( final EventAdmin eventAdmin )
    {
        this.eventAdmin = eventAdmin;
    }

    public void start ()
    {
        final Map<String, Object> props = this.entityManagerFactory.getProperties ();
        if ( "com.mysql.jdbc.Driver".equals ( props.get ( "driverClassName" ) ) )
        {
            // is mysql
            logger.warn ( "Initializing with MySQL single resource lock manager" );
            this.lockManager = new LockManager ( true );
        }
        else
        {
            this.lockManager = new LockManager ();
        }

        this.generatorProcessor.open ();
        this.blobStore.open ( this.coreService );
    }

    public void stop ()
    {
        this.blobStore.close ();
        this.generatorProcessor.close ();
    }

    @Override
    public Channel createChannel ( final String name, final String description )
    {
        return doWithHandler ( ( handler ) -> convert ( handler.createChannel ( name, description, null ) ) );
    }

    protected ChannelImpl convertImpl ( final ChannelEntity channel )
    {
        return StreamServiceHelper.convert ( channel, this );
    }

    protected Channel convert ( final ChannelEntity channel )
    {
        return Profile.proxy ( StreamServiceHelper.convert ( channel, this ), Channel.class );
    }

    @Override
    public void deleteChannel ( final String channelId )
    {
        doWithHandlerVoid ( ( storage ) -> storage.deleteChannel ( channelId, true ) );
    }

    @Override
    public Channel getChannel ( final String channelId )
    {
        return Profile.call ( this, "getChannel", () -> {
            return doWithTransaction ( em -> {
                final ChannelEntity channel = em.find ( ChannelEntity.class, channelId );
                return convert ( channel );
            } );
        } );
    }

    @Override
    public Channel getChannelWithAlias ( final String channelIdOrName )
    {
        return doWithTransaction ( em -> {
            ChannelEntity channel = em.find ( ChannelEntity.class, channelIdOrName );
            if ( channel == null )
            {
                channel = findByName ( em, channelIdOrName );
            }
            return convert ( channel );
        } );
    }

    @Override
    public Channel getChannelByAlias ( final String channelName )
    {
        return doWithTransaction ( em -> convert ( findByName ( em, channelName ) ) );
    }

    protected ChannelEntity findByName ( final EntityManager em, final String channelName )
    {
        final TypedQuery<ChannelEntity> q = em.createQuery ( String.format ( "SELECT c FROM %s AS c WHERE c.name=:name", ChannelEntity.class.getName () ), ChannelEntity.class );
        q.setParameter ( "name", channelName );

        // we don't use getSingleResult since it throws an exception if the entry is not found

        final List<ChannelEntity> result = q.getResultList ();
        if ( result.isEmpty () )
        {
            return null;
        }

        return result.get ( 0 );
    }

    @Override
    public Artifact createGeneratorArtifact ( final String channelId, final String name, final String generatorId, final InputStream stream, final Map<MetaKey, String> providedMetaData )
    {
        return internalCreateArtifact ( channelId, name, () -> {
            final GeneratorArtifactEntity gae = new GeneratorArtifactEntity ();
            gae.setGeneratorId ( generatorId );
            return gae;
        } , stream, providedMetaData );
    }

    @Override
    public Artifact createArtifact ( final String channelId, final String name, final InputStream stream, final Map<MetaKey, String> providedMetaData )
    {
        return internalCreateArtifact ( channelId, name, StoredArtifactEntity::new, stream, providedMetaData );
    }

    private Artifact internalCreateArtifact ( final String channelId, final String name, final Supplier<ArtifactEntity> entityCreator, final InputStream stream, final Map<MetaKey, String> providedMetaData )
    {
        return doWithHandler ( hi -> {
            final ChannelEntity channel = hi.getCheckedChannel ( channelId );

            testLocked ( channel );

            final ArtifactEntity artifact = hi.internalCreateArtifact ( channelId, name, entityCreator, stream, providedMetaData, true );

            if ( artifact == null )
            {
                return null;
            }

            return convert ( convertImpl ( artifact.getChannel () ), artifact, null );
        } );
    }

    public Artifact createAttachedArtifact ( final String channelId, final String parentArtifactId, final String name, final InputStream stream, final Map<MetaKey, String> providedMetaData )
    {
        return doWithHandler ( ( hi ) -> {
            final ArtifactEntity artifact = hi.createAttachedArtifact ( parentArtifactId, name, stream, providedMetaData );
            if ( artifact == null )
            {
                return null;
            }
            return convert ( convertImpl ( artifact.getChannel () ), artifact, null );
        } );
    }

    protected ChannelEntity getCheckedChannel ( final EntityManager em, final String channelId )
    {
        final ChannelEntity channel = em.find ( ChannelEntity.class, channelId );
        if ( channel == null )
        {
            throw new IllegalArgumentException ( String.format ( "Channel %s unknown", channelId ) );
        }
        return channel;
    }

    public Set<Artifact> listArtifacts ( final String channelId )
    {
        return doWithHandler ( hi -> {
            final ChannelEntity ce = hi.getCheckedChannel ( channelId );
            final ChannelImpl channel = convertImpl ( ce );
            final Multimap<String, MetaDataEntry> properties = hi.getChannelArtifactProperties ( ce );
            return hi.<Artifact> listArtifacts ( ce, ( ae ) -> convert ( channel, ae, properties ) );
        } );
    }

    public Set<ArtifactInformation> listArtifactInformations ( final String channelId )
    {
        return doWithHandler ( ( hi ) -> hi.getArtifacts ( channelId ) );
    }

    public Set<SimpleArtifactInformation> listSimpleArtifacts ( final String channelId )
    {
        return doWithHandler ( ( hi ) -> hi.<SimpleArtifactInformation> listArtifacts ( hi.getCheckedChannel ( channelId ), ( ae ) -> convertSimple ( ae ) ) );
    }

    /**
     * List all artifacts with DetailedArtifactInformation level information
     * <p>
     * This method mainly fetches all artifacts and their meta data. It does not
     * provide the child relations.
     * </p>
     *
     * @param channelId
     *            the channel to query
     * @return the result set of artifact information
     */
    public Set<DetailedArtifactInformation> listDetailedArtifacts ( final String channelId )
    {
        return doWithHandler ( ( hi ) -> hi.getDetailedArtifacts ( hi.getCheckedChannel ( channelId ) ) );
    }

    private SimpleArtifactInformation convertSimple ( final ArtifactEntity ae )
    {
        if ( ae == null )
        {
            return null;
        }

        logger.trace ( "Convert to simple: {}", ae.getId () );

        return new SimpleArtifactInformation ( ae.getId (), getParentId ( ae ), ae.getSize (), ae.getName (), ae.getChannel ().getId (), ae.getCreationTimestamp (), ae.getAggregatedNumberOfWarnings (), ae.getAggregatedNumberOfErrors (), getArtifactFacets ( ae ) );
    }

    private Artifact convert ( final ChannelImpl channel, final ArtifactEntity ae, final Multimap<String, MetaDataEntry> properties )
    {
        if ( ae == null )
        {
            return null;
        }

        logger.debug ( "Converting entity: {} / {}", ae.getId (), ae.getClass () );

        if ( logger.isTraceEnabled () )
        {
            final Class<?>[] clsArray = ae.getClass ().getInterfaces ();
            for ( final Class<?> cls : clsArray )
            {
                logger.trace ( "interface: {}", cls );
            }
        }

        if ( ae instanceof GeneratorArtifactEntity )
        {
            final LinkTarget[] targets = new LinkTarget[1];
            this.generatorProcessor.process ( ( (GeneratorArtifactEntity)ae ).getGeneratorId (), ( gen ) -> targets[0] = gen.getEditTarget ( ae.getId () ) );
            return new GeneratorArtifactImpl ( channel, ae.getId (), StreamServiceHelper.convert ( ae, properties ), targets[0] );
        }
        else
        {
            return new ArtifactImpl ( channel, ae.getId (), StreamServiceHelper.convert ( ae, properties ) );
        }
    }

    public boolean streamArtifact ( final String artifactId, final ThrowingConsumer<InputStream> consumer )
    {
        return doWithTransaction ( em -> {
            final ArtifactEntity ae = em.find ( ArtifactEntity.class, artifactId );
            if ( ae == null )
            {
                return false;
            }

            this.blobStore.streamArtifact ( em, ae, consumer );

            return true;
        } );
    }

    @Override
    public boolean streamArtifact ( final String artifactId, final ArtifactReceiver receiver )
    {
        return doWithTransaction ( em -> {

            final ArtifactEntity ae = em.find ( ArtifactEntity.class, artifactId );

            if ( ae == null )
            {
                return false;
            }

            this.blobStore.streamArtifact ( em, ae, receiver );

            return true;
        } );
    }

    @Override
    public Collection<Channel> listChannels ()
    {
        return Profile.call ( "listChannels", () -> {
            return doWithTransaction ( em -> {
                final CriteriaQuery<ChannelEntity> cq = em.getCriteriaBuilder ().createQuery ( ChannelEntity.class );

                final TypedQuery<ChannelEntity> q = em.createQuery ( cq );
                final List<ChannelEntity> rl = q.getResultList ();

                final List<Channel> result = new ArrayList<> ( rl.size () );
                for ( final ChannelEntity ce : rl )
                {
                    result.add ( convert ( ce ) );
                }

                return result;
            } );
        } );
    }

    @Override
    public SimpleArtifactInformation deleteArtifact ( final String artifactId )
    {
        return doWithHandler ( hi -> hi.deleteArtifact ( artifactId ) );
    }

    public List<ChannelAspectInformation> getChannelAspectInformations ( final String channelId )
    {
        return doWithTransaction ( em -> {
            final ChannelEntity channel = getCheckedChannel ( em, channelId );
            return Activator.getChannelAspects ().resolve ( channel.getAspects ().keySet () );
        } );
    }

    @Override
    public void addChannelAspect ( final String channelId, final String aspectFactoryId, final boolean withDependencies )
    {
        doWithHandlerVoid ( ( handler ) -> handler.addChannelAspects ( channelId, Collections.singleton ( aspectFactoryId ), withDependencies ) );
    }

    public void addChannelAspects ( final String channelId, final Set<String> aspectFactoryIds, final boolean withDependencies )
    {
        doWithHandlerVoid ( ( handler ) -> handler.addChannelAspects ( channelId, aspectFactoryIds, withDependencies ) );
    }

    @Override
    public void refreshChannelAspect ( final String channelId, final String aspectFactoryId )
    {
        doWithHandlerVoid ( hi -> {

            LockContext.modify ( channelId );

            final ChannelEntity channel = hi.getCheckedChannel ( channelId );

            testLocked ( channel );

            if ( channel.getAspects ().containsKey ( aspectFactoryId ) )
            {
                hi.reprocessAspects ( channel, Collections.singleton ( aspectFactoryId ) );
            }
        } );
        postAspectEvent ( channelId, aspectFactoryId, "refresh" );
    }

    @Override
    public void refreshAllChannelAspects ( final String channelId )
    {
        doWithHandlerVoid ( hi -> {

            LockContext.modify ( channelId );

            final ChannelEntity channel = hi.getCheckedChannel ( channelId );

            testLocked ( channel );

            hi.reprocessAspects ( channel, channel.getAspects ().keySet () );
        } );
        postAspectEvent ( channelId, "all", "refresh" );
    }

    protected void postAspectEvent ( final String channelId, final String aspectId, final String operation )
    {
        final Map<String, Object> data = new HashMap<> ( 2 );
        data.put ( "operation", operation );
        data.put ( "aspectFactoryId", aspectId );
        this.eventAdmin.postEvent ( new Event ( String.format ( "drone/channel/%s/aspect", makeSafeTopic ( channelId ) ), data ) );
    }

    private static String makeSafeTopic ( final String aspectId )
    {
        return aspectId.replaceAll ( "[^a-zA-Z0-9_\\-]", "_" );
    }

    @Override
    public void removeChannelAspect ( final String channelId, final String aspectFactoryId )
    {
        removeChannelAspects ( channelId, Collections.singleton ( aspectFactoryId ) );
    }

    public void removeChannelAspects ( final String channelId, final Set<String> aspectFactoryIds )
    {
        this.lockManager.run ( () -> {

            LockContext.modify ( channelId );

            doWithTransactionVoid ( em -> {
                final ChannelEntity channel = getCheckedChannel ( em, channelId );

                testLocked ( channel );

                channel.getAspects ().keySet ().removeAll ( aspectFactoryIds );
                em.persist ( channel );
                em.flush ();

                {
                    final Query q = em.createQuery ( String.format ( "DELETE from %s ap where ap.namespace in :factoryId and ap.artifact.channel.id=:channelId", ExtractedArtifactPropertyEntity.class.getSimpleName () ) );
                    q.setParameter ( "factoryId", aspectFactoryIds );
                    q.setParameter ( "channelId", channelId );
                    q.executeUpdate ();
                }

                {
                    final Query q = em.createQuery ( String.format ( "DELETE from %s cp where cp.namespace in :factoryId and cp.channel.id=:channelId", ExtractedChannelPropertyEntity.class.getSimpleName () ) );
                    q.setParameter ( "factoryId", aspectFactoryIds );
                    q.setParameter ( "channelId", channelId );
                    q.executeUpdate ();
                }

                {
                    final Query q = em.createQuery ( String.format ( "DELETE from %s va where va.namespace in :factoryId and va.channel.id=:channelId", VirtualArtifactEntity.class.getSimpleName () ) );
                    q.setParameter ( "factoryId", aspectFactoryIds );
                    q.setParameter ( "channelId", channelId );
                    q.executeUpdate ();
                }

                {
                    final Query q = em.createQuery ( String.format ( "DELETE from %s vme where vme.namespace in :factoryId and vme.channel.id=:channelId", ValidationMessageEntity.class.getSimpleName () ) );
                    q.setParameter ( "factoryId", aspectFactoryIds );
                    q.setParameter ( "channelId", channelId );
                    q.executeUpdate ();
                }

                final StorageHandlerImpl hi = new StorageHandlerImpl ( em, this.generatorProcessor, this.blobStore );

                hi.recreateAllVirtualArtifacts ( channel );
            } , createArtifactGuard () );

        } );

        for ( final String aspectFactoryId : aspectFactoryIds )
        {
            postAspectEvent ( channelId, aspectFactoryId, "remove" );
        }
    }

    private ArtifactEntity getArtifact ( final EntityManager em, final String artifactId )
    {
        final CriteriaBuilder cb = em.getCriteriaBuilder ();
        final CriteriaQuery<ArtifactEntity> cq = cb.createQuery ( ArtifactEntity.class );

        // query

        final Root<ArtifactEntity> root = cq.from ( ArtifactEntity.class );
        final Predicate where = cb.equal ( root.get ( ArtifactEntity_.id ), artifactId );

        // fetch
        root.fetch ( ArtifactEntity_.channel );
        root.fetch ( ArtifactEntity_.providedProperties, JoinType.LEFT );
        root.fetch ( ArtifactEntity_.extractedProperties, JoinType.LEFT );

        // select

        cq.select ( root ).where ( where );

        // convert

        final TypedQuery<ArtifactEntity> q = em.createQuery ( cq );

        // q.setMaxResults ( 1 );
        final List<ArtifactEntity> rl = q.getResultList ();
        if ( rl.isEmpty () )
        {
            return null;
        }
        else
        {
            return rl.get ( 0 );
            // return em.find ( ArtifactEntity.class, artifactId );
        }
    }

    @Override
    public ArtifactInformation getArtifactInformation ( final String artifactId )
    {
        return doWithTransaction ( em -> StreamServiceHelper.convert ( getArtifact ( em, artifactId ), null ) );
    }

    @Override
    public Artifact getArtifact ( final String artifactId )
    {
        return doWithTransaction ( em -> {
            final ArtifactEntity artifact = getArtifact ( em, artifactId );
            if ( artifact == null )
            {
                return null;
            }
            return convert ( convertImpl ( artifact.getChannel () ), artifact, null );
        } );
    }

    public Map<MetaKey, String> applyMetaData ( final String artifactId, final Map<MetaKey, String> metadata )
    {
        try
        {
            return this.lockManager.call ( () -> doWithTransaction ( em -> {
                final StorageHandlerImpl hi = new StorageHandlerImpl ( em, this.generatorProcessor, this.blobStore );

                final ArtifactEntity artifact = hi.getCheckedArtifact ( artifactId );

                if ( artifact == null )
                {
                    return null;
                }

                LockContext.modify ( artifact.getChannel ().getId () );

                testLocked ( artifact.getChannel () );

                final Map<MetaKey, String> result = convert ( artifact.getProvidedProperties () );

                // merge

                mergeMetaData ( metadata, result );

                // first clear all

                artifact.getProvidedProperties ().clear ();

                em.persist ( artifact );
                em.flush ();

                // now add the new set

                Helper.convertProvidedProperties ( result, artifact, artifact.getProvidedProperties () );

                // store

                em.persist ( artifact );
                em.flush ();

                // recreate virtual artifacts

                hi.recreateVirtualArtifacts ( artifact );

                // recreate generated artifacts

                if ( artifact instanceof GeneratorArtifactEntity )
                {
                    hi.regenerateArtifact ( (GeneratorArtifactEntity)artifact, true );
                }

                return result;
            } ) );
        }
        catch ( final Exception e )
        {
            throw new RuntimeException ( e );
        }
    }

    public Map<MetaKey, String> applyChannelMetaData ( final String channelId, final Map<MetaKey, String> metadata )
    {
        try
        {
            return this.lockManager.call ( () -> doWithTransaction ( em -> {

                final StorageHandlerImpl hi = new StorageHandlerImpl ( em, this.generatorProcessor, this.blobStore );

                final ChannelEntity channel = getCheckedChannel ( em, channelId );

                LockContext.modify ( channel.getId () );

                testLocked ( channel );

                final Map<MetaKey, String> result = convert ( channel.getProvidedProperties () );

                // merge

                mergeMetaData ( metadata, result );

                // first clear all

                channel.getProvidedProperties ().clear ();

                em.persist ( channel );
                em.flush ();

                // now add the new set

                Helper.convertProvidedProperties ( result, channel, channel.getProvidedProperties () );

                // store

                em.persist ( channel );
                em.flush ();

                // reprocess all aspects, the virtualizers might generate different artifacts now

                hi.reprocessAspects ( channel, channel.getAspects ().keySet () );

                return result;

            } , createArtifactGuard () ) );
        }
        catch ( final Exception e )
        {
            throw new RuntimeException ( e );
        }
    }

    protected static void mergeMetaData ( final Map<MetaKey, String> metadata, final Map<MetaKey, String> result )
    {
        for ( final Map.Entry<MetaKey, String> entry : metadata.entrySet () )
        {
            if ( entry.getValue () == null )
            {
                result.remove ( entry.getKey () );
            }
            else
            {
                result.put ( entry.getKey (), entry.getValue () );
            }
        }
    }

    public SortedMap<MetaKey, String> getChannelMetaData ( final String id )
    {
        return doWithHandler ( hi -> hi.getChannelMetaData ( id ) );
    }

    public SortedMap<MetaKey, String> getChannelProvidedMetaData ( final String id )
    {
        return doWithHandler ( hi -> hi.getChannelProvidedMetaData ( id ) );
    }

    private Map<MetaKey, String> convert ( final Collection<? extends PropertyEntity> properties )
    {
        final Map<MetaKey, String> result = new HashMap<MetaKey, String> ( properties.size () );

        for ( final PropertyEntity ape : properties )
        {
            result.put ( new MetaKey ( ape.getNamespace (), ape.getKey () ), ape.getValue () );
        }

        return result;
    }

    public List<Artifact> findByName ( final String channelId, final String artifactName )
    {
        return doWithTransaction ( em -> {

            final ChannelEntity channel = getCheckedChannel ( em, channelId );

            final TypedQuery<ArtifactEntity> q = em.createQuery ( String.format ( "SELECT a FROM %s AS a WHERE a.name=:artifactName and a.channel.id=:channelId", ArtifactEntity.class.getName () ), ArtifactEntity.class );
            q.setParameter ( "artifactName", artifactName );
            q.setParameter ( "channelId", channelId );

            final ChannelImpl ci = convertImpl ( channel );

            final List<Artifact> result = new LinkedList<> ();
            for ( final ArtifactEntity ae : q.getResultList () )
            {
                result.add ( convert ( ci, ae, null /*TODO: use properties*/ ) );
            }

            return result;
        } );
    }

    @Override
    public void clearChannel ( final String channelId )
    {
        doWithHandlerVoid ( hi -> hi.clearChannel ( channelId ) );
    }

    @Override
    public void updateChannel ( final String channelId, final String name, final String description )
    {
        doWithHandlerVoid ( hi -> hi.updateChannel ( channelId, name, description ) );
    }

    public void generateArtifact ( final String id )
    {
        doWithHandlerVoid ( hi -> hi.generateArtifact ( id ) );
    }

    public Collection<DeployKey> getAllDeployKeys ( final String channelId )
    {
        return doWithTransaction ( ( em ) -> {
            final Set<DeployKey> result = new HashSet<> ();

            final ChannelEntity channel = getCheckedChannel ( em, channelId );

            for ( final DeployGroupEntity dg : channel.getDeployGroups () )
            {
                for ( final DeployKeyEntity dk : dg.getKeys () )
                {
                    result.add ( DeployAuthServiceImpl.convert ( dk ) );
                }
            }

            return result;
        } );
    }

    public Collection<DeployGroup> getDeployGroups ( final String channelId )
    {
        return doWithTransaction ( ( em ) -> {
            final Set<DeployGroup> result = new HashSet<> ();

            final ChannelEntity channel = getCheckedChannel ( em, channelId );
            for ( final DeployGroupEntity dg : channel.getDeployGroups () )
            {
                result.add ( DeployAuthServiceImpl.convert ( dg ) );
            }

            return result;
        } );
    }

    public void addDeployGroup ( final String channelId, final String groupId )
    {
        this.lockManager.run ( () -> doWithTransactionVoid ( ( em ) -> {

            LockContext.modify ( channelId );

            final ChannelEntity channel = getCheckedChannel ( em, channelId );
            final DeployGroupEntity group = DeployAuthServiceImpl.getGroupChecked ( em, groupId );
            channel.getDeployGroups ().add ( group );
            em.persist ( channel );
        } ) );
    }

    public void removeDeployGroup ( final String channelId, final String groupId )
    {
        this.lockManager.run ( () -> doWithTransactionVoid ( ( em ) -> {

            LockContext.modify ( channelId );

            final ChannelEntity channel = getCheckedChannel ( em, channelId );

            final Iterator<DeployGroupEntity> i = channel.getDeployGroups ().iterator ();
            while ( i.hasNext () )
            {
                final DeployGroupEntity dg = i.next ();
                if ( dg.getId ().equals ( groupId ) )
                {
                    i.remove ();
                }
            }

            em.persist ( channel );
        } ) );
    }

    public Map<String, String> getChannelAspects ( final String channelId )
    {
        return doWithTransaction ( em -> {
            final ChannelEntity channel = getCheckedChannel ( em, channelId );
            return new HashMap<> ( channel.getAspects () );
        } );
    }

    public void lockChannel ( final String channelId )
    {
        setChannelLock ( channelId, true );
    }

    public void unlockChannel ( final String channelId )
    {
        setChannelLock ( channelId, false );
    }

    private void setChannelLock ( final String channelId, final boolean state )
    {
        this.lockManager.run ( () -> doWithTransactionVoid ( ( em ) -> {
            LockContext.modify ( channelId );

            final ChannelEntity channel = getCheckedChannel ( em, channelId );
            channel.setLocked ( state );
            em.persist ( channel );
        } ) );
    }

    public boolean streamCacheEntry ( final String channelId, final String namespace, final String key, final ThrowingConsumer<CacheEntry> consumer )
    {
        return doWithHandler ( ( handler ) -> handler.streamCacheEntry ( channelId, namespace, key, consumer ) );
    }

    public ArtifactGuard createArtifactGuard ()
    {
        return new ArtifactGuard ( this.entityManagerFactory::createEntityManager, this.blobStore );
    }

    protected void doWithHandlerVoid ( final ThrowingConsumer<StorageHandler> consumer )
    {
        this.lockManager.run ( () -> {
            doWithTransactionVoid ( ( em ) -> {
                consumer.accept ( createStorageHandler ( em ) );
            } , createArtifactGuard () );
        } );
    }

    protected <R> R doWithHandler ( final ManagerFunction<R, StorageHandler> consumer )
    {
        try
        {
            return this.lockManager.call ( () -> {
                return doWithTransaction ( ( em ) -> {
                    return consumer.process ( createStorageHandler ( em ) );
                } , createArtifactGuard () );
            } );
        }
        catch ( final Exception e )
        {
            throw new RuntimeException ( e );
        }
    }

    private StorageHandler createStorageHandler ( final EntityManager em )
    {
        return new StorageHandlerImpl ( em, this.generatorProcessor, this.blobStore );
    }

    public List<CacheEntryInformation> getAllCacheEntries ( final String channelId )
    {
        return doWithHandler ( ( handler ) -> handler.getAllCacheEntries ( channelId ) );
    }

    protected TransferHandler createTransferHandler ( final EntityManager em )
    {
        return new TransferHandler ( em, this.blobStore );
    }

    protected <R> R doWithTransferHandler ( final ManagerFunction<R, TransferHandler> consumer )
    {
        return doWithTransaction ( em -> consumer.process ( createTransferHandler ( em ) ), createArtifactGuard () );
    }

    protected void doWithTransferHandlerVoid ( final ThrowingConsumer<TransferHandler> consumer )
    {
        doWithTransactionVoid ( em -> consumer.accept ( createTransferHandler ( em ) ) );
    }

    protected ValidationHandler createValidationHandler ( final EntityManager em )
    {
        return new ValidationHandler ( em );
    }

    protected <R> R doWithValidationHandler ( final ManagerFunction<R, ValidationHandler> consumer )
    {
        return doWithTransaction ( em -> consumer.process ( createValidationHandler ( em ) ), createArtifactGuard () );
    }

    protected void doWithValidationHandlerVoid ( final ThrowingConsumer<ValidationHandler> consumer )
    {
        doWithTransactionVoid ( em -> consumer.accept ( createValidationHandler ( em ) ) );
    }

    @Override
    public void exportChannel ( final String channelId, final OutputStream stream ) throws IOException
    {
        this.lockManager.run ( () -> {
            doWithTransferHandlerVoid ( ( handler ) -> handler.exportChannel ( channelId, stream ) );
        } );
    }

    @Override
    public Channel importChannel ( final InputStream inputStream, final boolean useChannelName )
    {
        try
        {
            return this.lockManager.call ( () -> doWithTransaction ( ( em ) -> {
                final StorageHandlerImpl storage = new StorageHandlerImpl ( em, this.generatorProcessor, this.blobStore );
                final TransferHandler transfer = new TransferHandler ( em, this.blobStore );
                return convert ( transfer.importChannel ( storage, inputStream, useChannelName ) );
            } , createArtifactGuard () ) );
        }
        catch ( final Exception e )
        {
            throw new RuntimeException ( e );
        }
    }

    @Override
    public void exportAll ( final OutputStream stream ) throws IOException
    {
        this.lockManager.run ( () -> {
            doWithTransferHandlerVoid ( ( handler ) -> handler.exportAll ( stream ) );
        } );
    }

    @Override
    public void importAll ( final InputStream inputStream, final boolean useChannelNames, final boolean wipe )
    {
        this.lockManager.run ( () -> doWithTransactionVoid ( ( em ) -> {
            final StorageHandlerImpl storage = new StorageHandlerImpl ( em, this.generatorProcessor, this.blobStore );
            final TransferHandler transfer = new TransferHandler ( em, this.blobStore );
            transfer.importAll ( storage, inputStream, useChannelNames, wipe );
        } ) );
    }

    @Override
    public void wipeClean ()
    {
        doWithHandlerVoid ( ( storage ) -> storage.wipeAllChannels () );
    }

    @Override
    public void setBlobStoreLocation ( final File location ) throws IOException
    {
        logger.info ( "Setting blob store location: {}", location );
        this.blobStore.setLocation ( location );
    }

    public List<ValidationMessage> getValidationMessages ( final String channelId )
    {
        return doWithValidationHandler ( handler -> handler.getValidationMessages ( channelId ) );
    }

    public List<ValidationMessage> getValidationMessagesForArtifact ( final String artifactId )
    {
        return doWithValidationHandler ( handler -> handler.getValidationMessagesForArtifact ( artifactId ) );
    }

    public long getNumberOfArtifacts ( final String channelId )
    {
        return doWithTransaction ( ( em ) -> {
            final TypedQuery<Number> q = em.createQuery ( String.format ( "SELECT COUNT(c.artifacts) FROM %s c WHERE c.id=:ID", ChannelEntity.class.getName () ), Number.class );

            q.setParameter ( "ID", channelId );

            final List<Number> result = q.getResultList ();
            if ( result == null || result.isEmpty () )
            {
                return -1L;
            }

            final Number count = result.get ( 0 );
            if ( count == null )
            {
                return -1L;
            }
            return count.longValue ();
        } );
    }

    @Override
    public ServiceStatistics getStatistics ()
    {
        return doWithTransaction ( ( em ) -> {
            final ServiceStatistics result = new ServiceStatistics ();

            final Query q = em.createQuery ( String.format ( "SELECT COUNT(a), SUM(a.size) FROM %s a", ArtifactEntity.class.getName () ) );

            final Object[] row = (Object[])q.getSingleResult ();

            result.setTotalNumberOfArtifacts ( ( (Number)row[0] ).longValue () );
            result.setTotalNumberOfBytes ( ( (Number)row[1] ).longValue () );

            return result;
        } );
    }
}
