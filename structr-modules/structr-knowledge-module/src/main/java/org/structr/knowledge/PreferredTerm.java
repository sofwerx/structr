/**
 * Copyright (C) 2010-2017 Structr GmbH
 *
 * This file is part of Structr <http://structr.org>.
 *
 * Structr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Structr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Structr.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.structr.knowledge;

import java.net.URI;
import org.structr.core.entity.Relation.Cardinality;
import org.structr.schema.SchemaService;
import org.structr.schema.json.JsonObjectType;
import org.structr.schema.json.JsonSchema;

/**
 * Base class of a preferred term as defined in ISO 25964
 */

public interface PreferredTerm extends ThesaurusTerm {

	static class Impl { static {

		final JsonSchema schema      = SchemaService.getDynamicSchema();
		final JsonObjectType type    = schema.addType("PreferredTerm");
		final JsonObjectType concept = schema.addType("ThesaurusConcept");

		type.setImplements(URI.create("https://structr.org/v1.1/definitions/PreferredTerm"));
		type.setExtends(URI.create("#/definitions/ThesaurusTerm"));

		type.relate(concept, "HAS_LABEL", Cardinality.ManyToOne, null, "preferredLabels");
	}}

	/*

	private static final Logger logger = LoggerFactory.getLogger(PreferredTerm.class.getName());

	public static final Property<ThesaurusConcept> preferredLabels = new EndNode<>("preferredLabels", TermHasLabel.class);

	static {

		SchemaService.registerBuiltinTypeOverride("PreferredTerm", PreferredTerm.class.getName());
	}
	*/
}
