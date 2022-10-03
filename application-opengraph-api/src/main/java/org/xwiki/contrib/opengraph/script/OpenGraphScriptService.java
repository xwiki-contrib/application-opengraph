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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.opengraph.OpenGraphException;
import org.xwiki.contrib.opengraph.internal.OpenGraphMetaClassInitializer;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.script.service.ScriptService;
import org.xwiki.security.authorization.AuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.stability.Unstable;
import org.xwiki.wiki.descriptor.WikiDescriptor;
import org.xwiki.wiki.descriptor.WikiDescriptorManager;
import org.xwiki.wiki.manager.WikiManagerException;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.web.DownloadAction;

import static com.xpn.xwiki.web.ViewAction.VIEW_ACTION;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.abbreviate;
import static org.apache.commons.lang3.StringUtils.trim;
import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCauseMessage;
import static org.xwiki.contrib.opengraph.internal.OpenGraphMetaClassInitializer.CONTENT_FIELD;
import static org.xwiki.contrib.opengraph.internal.OpenGraphMetaClassInitializer.PROPERTY_FIELD;
import static org.xwiki.contrib.opengraph.internal.OpenGraphMetaClassInitializer.XCLASS_DOCUMENT_REFERENCE;
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
    private Provider<XWikiContext> contextProvider;

    @Inject
    private WikiDescriptorManager wikiDescriptorManager;

    @Inject
    private AuthorizationManager authorizationManager;

    @Inject
    private Logger logger;

    /**
     * Retrieve the properties from the {@link OpenGraphMetaClassInitializer#XCLASS_DOCUMENT_REFERENCE} XObjects of the
     * current document. Some missing properties are automatically completed when missing: {@link #OG_URL_PROPERTY},
     * {@link #OG_TYPE_PROPERTY}, {@link #OG_TITLE_PROPERTY}, {@link #OG_DESCRIPTION_PROPERTY}, and
     * {@link #OG_IMAGE_PROPERTY}.
     *
     * @return a map of open graph properties associated to one or many contents
     */
    public Map<String, List<String>> metas()
    {
        XWikiContext context = this.contextProvider.get();
        // Early stop when this context is initialized. Otherwise, this method is called again when the rendering of 
        // the current document is called again to generate the description, leading to an infinite loop.
        if (context.get(OG_METAS_CONTEXT) != null) {
            return emptyMap();
        }

        // No metadata when the user is not supposed to view the current document.
        DocumentReference currentDocumentReference = context.getDoc().getDocumentReference();
        if (!this.authorizationManager.hasAccess(Right.VIEW, context.getUserReference(), currentDocumentReference)) {
            return emptyMap();
        }

        context.put(OG_METAS_CONTEXT, OG_METAS_CONTEXT);

        try {
            return metas(context);
        } catch (OpenGraphException e) {
            this.logger.warn("Failed to load open graph medatata on page [{}]. Cause: [{}]", currentDocumentReference,
                getRootCauseMessage(e));
            return emptyMap();
        } finally {
            context.put(OG_METAS_CONTEXT, null);
        }
    }

    private Map<String, List<String>> metas(XWikiContext context) throws OpenGraphException
    {
        Map<String, List<String>> metasMap = new HashMap<>();
        XWikiDocument doc = context.getDoc();
        XWikiDocument tdoc = loadTDoc(context, doc);
        WikiDescriptor wikiDescriptor = loadWikiDescriptor(context);

        // Load the open graph metadata from the XObjects of the current document.
        doc.getXObjects(XCLASS_DOCUMENT_REFERENCE)
            .forEach(baseObject -> normalizeProperty(baseObject.getStringValue(PROPERTY_FIELD))
                .ifPresent(property -> {
                    metasMap.putIfAbsent(property, new ArrayList<>());
                    metasMap.get(property).add(baseObject.displayView(CONTENT_FIELD, context));
                }));

        // Complete with the missing properties if they are not already defined in the XObjects.
        metasMap.computeIfAbsent(OG_URL_PROPERTY, key -> singletonList(doc.getExternalURL(VIEW_ACTION, context)));
        metasMap.putIfAbsent(OG_TYPE_PROPERTY, singletonList("article"));
        metasMap.computeIfAbsent(OG_TITLE_PROPERTY, key -> singletonList(tdoc.getRenderedTitle(PLAIN_1_0, context)));
        if (metasMap.get(OG_DESCRIPTION_PROPERTY) == null) {
            try {
                String renderedContent = tdoc.getRenderedContent(PLAIN_1_0, context);
                metasMap.put(OG_DESCRIPTION_PROPERTY, singletonList(abbreviate(trim(renderedContent), 200)));
            } catch (XWikiException e) {
                throw new OpenGraphException(
                    String.format("Failed to render the content of document [%s] in syntax [%s].", doc, PLAIN_1_0), e);
            }
        }

        // Only try to compute images if the current document is not the main page of the current wiki and a least 
        // one attachment is an image.
        if (!Objects.equals(wikiDescriptor.getMainPageReference(), doc.getDocumentReference())
            && doc.getAttachmentList().stream().anyMatch(attachment -> attachment.isImage(context)))
        {
            metasMap.computeIfAbsent(OG_IMAGE_PROPERTY, key -> doc.getAttachmentList()
                .stream()
                .filter(attachment -> attachment.isImage(context))
                .map(image -> doc.getExternalAttachmentURL(image.getFilename(), DownloadAction.ACTION_NAME, context))
                .collect(Collectors.toList()));
        }

        return metasMap;
    }

    private WikiDescriptor loadWikiDescriptor(XWikiContext context) throws OpenGraphException
    {
        try {
            return this.wikiDescriptorManager.getById(context.getWikiId());
        } catch (WikiManagerException e) {
            throw new OpenGraphException(
                String.format("Failed to load the wiki descriptor manager for wiki [%s]", context.getWikiId()), e);
        }
    }

    private static XWikiDocument loadTDoc(XWikiContext context, XWikiDocument doc) throws OpenGraphException
    {
        try {
            return doc.getTranslatedDocument(context);
        } catch (XWikiException e) {
            throw new OpenGraphException(String.format("Failed to get the translated document for [%s]", doc), e);
        }
    }

    /**
     * Add the {@code og:} prefix to the property if missing.
     *
     * @param property the property before normalization
     * @return a normalized property
     */
    private Optional<String> normalizeProperty(String property)
    {
        Optional<String> result;
        if (StringUtils.isEmpty(property)) {
            // Skipping silently invalid properties.
            result = Optional.empty();
        } else if (!property.startsWith("og:")) {
            result = Optional.of(String.format("og:%s", property));
        } else {
            result = Optional.of(property);
        }
        return result;
    }
}
