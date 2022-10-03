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
package org.xwiki.contrib.opengraph.internal;

import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.LocalDocumentReference;

import com.xpn.xwiki.doc.AbstractMandatoryClassInitializer;
import com.xpn.xwiki.objects.classes.BaseClass;

import static com.xpn.xwiki.objects.classes.TextAreaClass.ContentType.VELOCITY_CODE;
import static com.xpn.xwiki.objects.classes.TextAreaClass.EditorType.WYSIWYG;
import static java.util.Arrays.asList;

/**
 * Initializes the {@code XWiki.OpenGraph.Code.OpenGraphMetaClass} XClass for Opengraph metadata definition.
 *
 * @version $Id$
 * @since 1.2
 */
@Component
@Named("XWiki.OpenGraph.Code.OpenGraphMetaClass")
@Singleton
public class OpenGraphMetaClassInitializer extends AbstractMandatoryClassInitializer
{
    /**
     * {@code OpenGraphMetaClass} document reference.
     */
    public static final LocalDocumentReference XCLASS_DOCUMENT_REFERENCE =
        new LocalDocumentReference(asList("XWiki", "OpenGraph", "Code"), "OpenGraphMetaClass");

    /**
     * {@code property} field of the {@link #XCLASS_DOCUMENT_REFERENCE} XClass.
     */
    public static final String PROPERTY_FIELD = "property";

    /**
     * {@code content} field of the {@link #XCLASS_DOCUMENT_REFERENCE} XClass.
     */
    public static final String CONTENT_FIELD = "content";

    /**
     * Default constructor.
     */
    public OpenGraphMetaClassInitializer()
    {
        super(XCLASS_DOCUMENT_REFERENCE);
    }

    @Override
    protected void createClass(BaseClass xclass)
    {
        super.createClass(xclass);

        xclass.addTextField(PROPERTY_FIELD, "Property", 30);
        xclass.addTextAreaField(CONTENT_FIELD, "Content", 300, 30, WYSIWYG, VELOCITY_CODE);
    }
}
