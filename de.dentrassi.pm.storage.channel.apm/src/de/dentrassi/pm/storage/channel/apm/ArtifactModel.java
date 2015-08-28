package de.dentrassi.pm.storage.channel.apm;

import static java.util.stream.Collectors.toList;

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

import de.dentrassi.pm.common.MetaKey;
import de.dentrassi.pm.storage.channel.ArtifactInformation;
import de.dentrassi.pm.storage.channel.ValidationMessage;

public class ArtifactModel
{
    private String parentId;

    private Set<String> childIds;

    private String name;

    private long size;

    private Date date;

    private List<ValidationMessageModel> validationMessages;

    private Map<MetaKey, String> providedMetaData;

    private Map<MetaKey, String> extractedMetaData;

    private Set<String> facets;

    public ArtifactModel ()
    {
        this.validationMessages = new LinkedList<> ();
        this.providedMetaData = new HashMap<> ();
        this.extractedMetaData = new HashMap<> ();
        this.facets = new CopyOnWriteArraySet<> ();
    }

    public ArtifactModel ( final ArtifactModel other )
    {
        this.parentId = other.parentId;
        this.childIds = other.childIds != null ? new CopyOnWriteArraySet<> ( other.childIds ) : new CopyOnWriteArraySet<> ();
        this.facets = other.facets != null ? new CopyOnWriteArraySet<> ( other.facets ) : new CopyOnWriteArraySet<> ();

        this.name = other.name;
        this.size = other.size;
        this.date = other.date;

        this.validationMessages = new CopyOnWriteArrayList<> ( other.validationMessages );
        this.providedMetaData = new HashMap<> ( other.providedMetaData );
        this.extractedMetaData = new HashMap<> ( other.extractedMetaData );
    }

    public void setParentId ( final String parentId )
    {
        this.parentId = parentId;
    }

    public String getParentId ()
    {
        return this.parentId;
    }

    public void setChildIds ( final Set<String> childIds )
    {
        this.childIds = childIds;
    }

    public Set<String> getChildIds ()
    {
        return this.childIds;
    }

    public void setName ( final String name )
    {
        this.name = name;
    }

    public String getName ()
    {
        return this.name;
    }

    public void setSize ( final long size )
    {
        this.size = size;
    }

    public long getSize ()
    {
        return this.size;
    }

    public void setDate ( final Date date )
    {
        this.date = date;
    }

    public Date getDate ()
    {
        return this.date;
    }

    public void setValidationMessages ( final List<ValidationMessageModel> validationMessages )
    {
        this.validationMessages = validationMessages;
    }

    public List<ValidationMessageModel> getValidationMessages ()
    {
        return this.validationMessages;
    }

    public void setProvidedMetaData ( final Map<MetaKey, String> providedMetaData )
    {
        this.providedMetaData = providedMetaData;
    }

    public Map<MetaKey, String> getProvidedMetaData ()
    {
        return this.providedMetaData;
    }

    public void setExtractedMetaData ( final Map<MetaKey, String> extractedMetaData )
    {
        this.extractedMetaData = extractedMetaData;
    }

    public Map<MetaKey, String> getExtractedMetaData ()
    {
        return this.extractedMetaData;
    }

    public void setFacets ( final Set<String> facets )
    {
        this.facets = facets;
    }

    public Set<String> getFacets ()
    {
        return this.facets;
    }

    public static ArtifactInformation toInformation ( final String id, final ArtifactModel model )
    {
        final List<ValidationMessage> messages = model.getValidationMessages ().stream ().map ( ValidationMessageModel::toMessage ).collect ( Collectors.toList () );
        return new ArtifactInformation ( id, model.getParentId (), model.getChildIds (), model.getName (), model.getSize (), model.getDate ().toInstant (), model.getFacets (), messages, model.getProvidedMetaData (), model.getExtractedMetaData () );
    }

    public static ArtifactInformation toInformation ( final Map.Entry<String, ArtifactModel> entry )
    {
        return toInformation ( entry.getKey (), entry.getValue () );
    }

    public static ArtifactModel fromInformation ( final ArtifactInformation ai )
    {
        final ArtifactModel result = new ArtifactModel ();

        result.setFacets ( ai.getFacets () );

        result.setParentId ( ai.getParentId () );
        result.setChildIds ( ai.getChildIds () );

        result.setName ( ai.getName () );
        result.setSize ( ai.getSize () );
        result.setDate ( new Date ( ai.getCreationInstant ().toEpochMilli () ) );

        result.setExtractedMetaData ( ai.getExtractedMetaData () );
        result.setProvidedMetaData ( ai.getProvidedMetaData () );

        result.setValidationMessages ( ai.getValidationMessages ().stream ().map ( ValidationMessageModel::fromMessage ).collect ( toList () ) );

        return result;
    }
}
