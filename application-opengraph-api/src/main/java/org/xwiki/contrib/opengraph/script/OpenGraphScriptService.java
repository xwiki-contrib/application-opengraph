/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.contrib.opengraph.script;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.opengraph.OpenGraphException;
import org.xwiki.contrib.opengraph.internal.OpenGraphMetaClassInitializer;
import org.xwiki.script.service.ScriptService;
import org.xwiki.stability.Unstable;
import org.xwiki.wiki.descriptor.WikiDescriptor;
import org.xwiki.wiki.descriptor.WikiDescriptorManager;
import org.xwiki.wiki.manager.WikiManagerException;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.web.DownloadAction;

import static com.xpn.xwiki.web.ViewAction.VIEW_ACTION;
import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.abbreviate;
import static org.apache.commons.lang3.StringUtils.trim;
import static org.xwiki.rendering.syntax.Syntax.PLAIN_1_0;

/**
 * Open Graph Script Service.
 *
 * @version $Id$
 * @since 1.2
 */
@Component
@Singleton
@Named("opengraph")
@Unstable
public class OpenGraphScriptService implements ScriptService
{
    private static final String OG_URL_PROPERTY = "og:url";

    private static final String OG_TYPE_PROPERTY = "og:type";

    private static final String OG_TITLE_PROPERTY = "og:title";

    private static final String OG_DESCRIPTION_PROPERTY = "og:description";

    private static final String OG_IMAGE_PROPERTY = "og:image";

    private static final String OG_METAS_CONTEXT = "og.metas";

    @Inject
    @Named("readonly")
    private Provider<XWikiContext> contextProvider;

    @Inject
    private WikiDescriptorManager wikiDescriptorManager;

    /**
     * Retrieve the properties from the {@link OpenGraphMetaClassInitializer#XCLASS_DOCUMENT_REFERENCE} XObjects of the
     * current document. Some missing properties are automatically completed when missing: {@link #OG_URL_PROPERTY},
     * {@link #OG_TYPE_PROPERTY}, {@link #OG_TITLE_PROPERTY}, {@link #OG_DESCRIPTION_PROPERTY}, and
     * {@link #OG_IMAGE_PROPERTY}.
     *
     * @return a map of open graph properties associated to one or many contents
     * @throws OpenGraphException in case of issues during the retrieval of the properties
     */
    public Map<String, List<String>> metas() throws OpenGraphException
    {
        XWikiContext context = this.contextProvider.get();
        // Early stop when this context is initialized. Otherwise, this method is called again when the rendering of 
        // the current document is called again to generate the description, leading to an infinite loop.
        if (context.get(OG_METAS_CONTEXT) != null) {
            return Collections.emptyMap();
        }

        context.put(OG_METAS_CONTEXT, OG_METAS_CONTEXT);

        try {
            Map<String, List<String>> metasMap = new HashMap<>();
            XWikiDocument doc = context.getDoc();
            XWikiDocument tdoc = loadTDoc(context, doc);
            WikiDescriptor wikiDescriptor = loadWikiDescriptor(context);

            // Load the open graph metadata from the XObjects of the current document.
            doc.getXObjects(OpenGraphMetaClassInitializer.XCLASS_DOCUMENT_REFERENCE).forEach(baseObject -> {
                String property =
                    normalizeProperty(baseObject.getStringValue(OpenGraphMetaClassInitializer.PROPERTY_FIELD));
                String content = baseObject.displayView(OpenGraphMetaClassInitializer.CONTENT_FIELD, context);
                metasMap.putIfAbsent(property, new ArrayList<>());
                metasMap.get(property).add(content);
            });

            // Complete with the missing properties if they are not already defined in the XObjects.
            metasMap.computeIfAbsent(OG_URL_PROPERTY, key -> singletonList(doc.getExternalURL(VIEW_ACTION, context)));
            metasMap.putIfAbsent(OG_TYPE_PROPERTY, singletonList("article"));
            metasMap.computeIfAbsent(OG_TITLE_PROPERTY,
                key -> singletonList(tdoc.getRenderedTitle(PLAIN_1_0, context)));
            if (metasMap.get(OG_DESCRIPTION_PROPERTY) == null) {
                try {
                    metasMap.put(OG_DESCRIPTION_PROPERTY,
                        singletonList(abbreviate(trim(tdoc.getRenderedContent(PLAIN_1_0, context)), 200)));
                } catch (XWikiException e) {
                    throw new OpenGraphException(
                        String.format("Failed to render the content of document [%s] in syntax [%s].", doc, PLAIN_1_0),
                        e);
                }
            }

            // Only try to compute images if the current document is not the main page of the current wiki. 
            if (!Objects.equals(wikiDescriptor.getMainPageReference(), doc.getDocumentReference())
                && !doc.getAttachmentList().isEmpty())
            {
                metasMap.computeIfAbsent(OG_IMAGE_PROPERTY, key -> doc.getAttachmentList()
                    .stream()
                    .filter(attachment -> attachment.isImage(context))
                    .map(image ->
                        doc.getExternalAttachmentURL(image.getFilename(), DownloadAction.ACTION_NAME, context))
                    .collect(Collectors.toList()));
            }

            return metasMap;
        } finally {
            context.put(OG_METAS_CONTEXT, null);
        }
    }

    private WikiDescriptor loadWikiDescriptor(XWikiContext context) throws OpenGraphException
    {
        WikiDescriptor wikiDescriptor;
        try {
            wikiDescriptor = this.wikiDescriptorManager.getById(context.getWikiId());
        } catch (WikiManagerException e) {
            throw new OpenGraphException(
                String.format("Failed to load the wiki descriptor manager for wiki [%s]", context.getWikiId()), e);
        }
        return wikiDescriptor;
    }

    private static XWikiDocument loadTDoc(XWikiContext context, XWikiDocument doc) throws OpenGraphException
    {
        XWikiDocument tdoc;
        try {
            tdoc = doc.getTranslatedDocument(context);
        } catch (XWikiException e) {
            throw new OpenGraphException(String.format("Failed to get the translated document for [%s]", doc), e);
        }
        return tdoc;
    }

    /**
     * Add the {@code og:} prefix to the property if missing.
     *
     * @param property the property before normalization
     * @return a normalized property
     */
    private String normalizeProperty(String property)
    {
        if (property == null) {
            throw new InvalidParameterException("property should not be null.");
        }
        if (!property.startsWith("og:")) {
            return String.format("og:%s", property);
        }
        return property;
    }
}
