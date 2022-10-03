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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.xwiki.contrib.opengraph.internal.OpenGraphMetaClassInitializer;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.security.authorization.AuthorizationManager;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.wiki.descriptor.WikiDescriptor;
import org.xwiki.wiki.descriptor.WikiDescriptorManager;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiAttachment;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.web.DownloadAction;

import static com.xpn.xwiki.web.ViewAction.VIEW_ACTION;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.xwiki.rendering.syntax.Syntax.PLAIN_1_0;

/**
 * Test of {@link OpenGraphScriptService}.
 *
 * @version $Id$
 * @since 1.2
 */
@ComponentTest
class OpenGraphScriptServiceTest
{
    private static final DocumentReference DOC_DOCUMENT_REFERENCE = new DocumentReference("xwiki", "Space", "Doc");

    private static final DocumentReference MAIN_DOCUMENT_REFERENCE = new DocumentReference("xwiki", "Space", "Main");

    private static final String OG_METAS_CONTEXT = "og.metas";

    @InjectMockComponents
    private OpenGraphScriptService openGraphScriptService;

    @MockComponent
    private Provider<XWikiContext> contextProvider;

    @MockComponent
    private WikiDescriptorManager wikiDescriptorManager;

    @MockComponent
    private AuthorizationManager authorizationManager;

    @Mock
    private XWikiContext context;

    @Mock
    private XWikiDocument doc;

    @Mock
    private XWikiDocument tdoc;

    @Mock
    private WikiDescriptor wikiDescriptor;

    @BeforeEach
    void setUp() throws Exception
    {
        when(this.contextProvider.get()).thenReturn(this.context);
        when(this.context.getDoc()).thenReturn(this.doc);
        when(this.doc.getTranslatedDocument(this.context)).thenReturn(this.tdoc);
        when(this.doc.getExternalURL(VIEW_ACTION, this.context)).thenReturn("http://domain/uri");
        when(this.doc.getDocumentReference()).thenReturn(DOC_DOCUMENT_REFERENCE);
        when(this.tdoc.getRenderedTitle(PLAIN_1_0, this.context)).thenReturn("my title");
        // Wrapped in spaces to test if the final result is trimmed.
        when(this.tdoc.getRenderedContent(PLAIN_1_0, this.context)).thenReturn("  my content  ");
        when(this.context.getWikiId()).thenReturn("xwiki");
        when(this.wikiDescriptorManager.getById("xwiki")).thenReturn(this.wikiDescriptor);
        when(this.wikiDescriptor.getMainPageReference()).thenReturn(MAIN_DOCUMENT_REFERENCE);
        when(this.authorizationManager.hasAccess(any(), any(), any())).thenReturn(true);
    }

    @Test
    void metas()
    {
        Map<String, List<String>> metas = this.openGraphScriptService.metas();
        HashMap<Object, Object> expected = new HashMap<>();
        expected.put("og:type", singletonList("article"));
        expected.put("og:title", singletonList("my title"));
        expected.put("og:url", singletonList("http://domain/uri"));
        expected.put("og:description", singletonList("my content"));

        assertEquals(expected, metas);
        verify(this.context).put(OG_METAS_CONTEXT, OG_METAS_CONTEXT);
        verify(this.context).put(OG_METAS_CONTEXT, null);
    }

    @Test
    void metasWithAttachments()
    {
        XWikiAttachment attachmentA = mock(XWikiAttachment.class);
        XWikiAttachment attachmentB = mock(XWikiAttachment.class);
        XWikiAttachment attachmentC = mock(XWikiAttachment.class);

        when(attachmentA.isImage(this.context)).thenReturn(true);
        when(attachmentB.isImage(this.context)).thenReturn(false);
        when(attachmentC.isImage(this.context)).thenReturn(true);

        when(attachmentA.getFilename()).thenReturn("attachment A");
        when(attachmentB.getFilename()).thenReturn("attachment B");
        when(attachmentC.getFilename()).thenReturn("attachment C");

        when(this.doc.getExternalAttachmentURL("attachment A", DownloadAction.ACTION_NAME, this.context))
            .thenReturn("http://domain/download/A");
        when(this.doc.getExternalAttachmentURL("attachment B", DownloadAction.ACTION_NAME, this.context))
            .thenReturn("http://domain/download/B");
        when(this.doc.getExternalAttachmentURL("attachment C", DownloadAction.ACTION_NAME, this.context))
            .thenReturn("http://domain/download/C");

        when(this.doc.getAttachmentList()).thenReturn(asList(
            attachmentA,
            attachmentB,
            attachmentC
        ));

        Map<String, List<String>> metas = this.openGraphScriptService.metas();
        HashMap<Object, Object> expected = new HashMap<>();
        expected.put("og:type", singletonList("article"));
        expected.put("og:title", singletonList("my title"));
        expected.put("og:url", singletonList("http://domain/uri"));
        expected.put("og:description", singletonList("my content"));
        expected.put("og:image", asList("http://domain/download/A", "http://domain/download/C"));

        assertEquals(expected, metas);
        verify(this.context).put(OG_METAS_CONTEXT, OG_METAS_CONTEXT);
        verify(this.context).put(OG_METAS_CONTEXT, null);
    }

    @Test
    void metasWithAttachmentsOnMainPage()
    {
        when(this.doc.getDocumentReference()).thenReturn(MAIN_DOCUMENT_REFERENCE);

        XWikiAttachment attachmentA = mock(XWikiAttachment.class);
        XWikiAttachment attachmentB = mock(XWikiAttachment.class);
        XWikiAttachment attachmentC = mock(XWikiAttachment.class);

        when(attachmentA.isImage(this.context)).thenReturn(true);
        when(attachmentB.isImage(this.context)).thenReturn(false);
        when(attachmentC.isImage(this.context)).thenReturn(true);

        when(attachmentA.getFilename()).thenReturn("attachment A");
        when(attachmentB.getFilename()).thenReturn("attachment B");
        when(attachmentC.getFilename()).thenReturn("attachment C");

        when(this.doc.getExternalAttachmentURL("attachment A", DownloadAction.ACTION_NAME, this.context))
            .thenReturn("http://domain/download/A");
        when(this.doc.getExternalAttachmentURL("attachment B", DownloadAction.ACTION_NAME, this.context))
            .thenReturn("http://domain/download/B");
        when(this.doc.getExternalAttachmentURL("attachment C", DownloadAction.ACTION_NAME, this.context))
            .thenReturn("http://domain/download/C");

        when(this.doc.getAttachmentList()).thenReturn(asList(
            attachmentA,
            attachmentB,
            attachmentC
        ));

        Map<String, List<String>> metas = this.openGraphScriptService.metas();
        HashMap<Object, Object> expected = new HashMap<>();
        expected.put("og:type", singletonList("article"));
        expected.put("og:title", singletonList("my title"));
        expected.put("og:url", singletonList("http://domain/uri"));
        expected.put("og:description", singletonList("my content"));

        assertEquals(expected, metas);
        verify(this.context).put(OG_METAS_CONTEXT, OG_METAS_CONTEXT);
        verify(this.context).put(OG_METAS_CONTEXT, null);
    }

    @Test
    void metasSkipOnContext()
    {
        when(this.context.get(OG_METAS_CONTEXT)).thenReturn(OG_METAS_CONTEXT);
        assertEquals(Collections.emptyMap(), this.openGraphScriptService.metas());
        verify(this.context, never()).put(any(), any());
        verifyZeroInteractions(this.doc);
    }

    @Test
    void metasWithXObject()
    {
        BaseObject baseObjectTitle = mock(BaseObject.class);
        BaseObject baseObjectOther = mock(BaseObject.class);
        BaseObject baseObjectX1 = mock(BaseObject.class);
        BaseObject baseObjectX2 = mock(BaseObject.class);
        when(this.doc.getXObjects(OpenGraphMetaClassInitializer.XCLASS_DOCUMENT_REFERENCE)).thenReturn(Arrays.asList(
            baseObjectTitle,
            baseObjectOther,
            baseObjectX1,
            baseObjectX2
        ));

        when(baseObjectTitle.getStringValue(OpenGraphMetaClassInitializer.PROPERTY_FIELD)).thenReturn("og:title");
        when(baseObjectOther.getStringValue(OpenGraphMetaClassInitializer.PROPERTY_FIELD)).thenReturn("og:other");
        when(baseObjectX1.getStringValue(OpenGraphMetaClassInitializer.PROPERTY_FIELD)).thenReturn("x");
        when(baseObjectX2.getStringValue(OpenGraphMetaClassInitializer.PROPERTY_FIELD)).thenReturn("og:x");

        when(baseObjectTitle.displayView(OpenGraphMetaClassInitializer.CONTENT_FIELD, this.context))
            .thenReturn("new title");
        when(baseObjectOther.displayView(OpenGraphMetaClassInitializer.CONTENT_FIELD, this.context)).thenReturn(
            "other");
        when(baseObjectX1.displayView(OpenGraphMetaClassInitializer.CONTENT_FIELD, this.context)).thenReturn("x1");
        when(baseObjectX2.displayView(OpenGraphMetaClassInitializer.CONTENT_FIELD, this.context)).thenReturn("x2");

        Map<String, List<String>> metas = this.openGraphScriptService.metas();
        HashMap<Object, Object> expected = new HashMap<>();
        expected.put("og:type", singletonList("article"));
        expected.put("og:title", singletonList("new title"));
        expected.put("og:url", singletonList("http://domain/uri"));
        expected.put("og:description", singletonList("my content"));
        expected.put("og:other", singletonList("other"));
        expected.put("og:x", asList("x1", "x2"));

        assertEquals(expected, metas);
        verify(this.context).put(OG_METAS_CONTEXT, OG_METAS_CONTEXT);
        verify(this.context).put(OG_METAS_CONTEXT, null);
    }
}
