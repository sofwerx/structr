/**
 * Copyright (C) 2010-2018 Structr GmbH
 *
 * This file is part of Structr <http://structr.org>.
 *
 * Structr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Structr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.schema.parser;

import java.lang.reflect.Constructor;
import org.structr.common.error.ErrorBuffer;
import org.structr.common.error.FrameworkException;
import org.structr.core.property.Property;
import org.structr.schema.Schema;
import org.structr.schema.SchemaHelper.Type;

/**
 *
 */
public class CustomPropertyParser extends PropertySourceGenerator {

	private String propertyType         = null;
	private String valueType            = null;
	private String unqualifiedValueType = null;
	private String propertyParameters   = null;

	public CustomPropertyParser(final ErrorBuffer errorBuffer, final String className, final PropertyDefinition params) {
		super(errorBuffer, className, params);

		final String fqcn = params.getFqcn();
		if (fqcn != null) {

			// instantiate class and initialize values
			try {

				final Class<Property> type = (Class<Property>)Class.forName(fqcn);
				if (type != null) {

					final Constructor<Property> constr = type.getConstructor(String.class);
					if (constr != null) {

						final Property property = constr.newInstance(params.getPropertyName());
						if (property != null) {

							this.propertyType         = type.getSimpleName();
							this.valueType            = property.valueType().getName();
							this.unqualifiedValueType = type.getSimpleName();
						}
					}
				}

			} catch (Throwable t) {
				t.printStackTrace();
			}
		}
	}

	@Override
	public String getPropertyType() {
		return propertyType;
	}

	@Override
	public String getValueType() {
		return valueType;
	}

	@Override
	public String getUnqualifiedValueType() {
		return unqualifiedValueType;
	}

	@Override
	public String getPropertyParameters() {
		return propertyParameters;
	}

	@Override
	public Type getKey() {
		return Type.Custom;
	}

	@Override
	public void parseFormatString(final Schema entity, final String expression) throws FrameworkException {
	}
}
