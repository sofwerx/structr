/**
 * Copyright (C) 2010-2016 Structr GmbH
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
package org.structr.core.parser.function;

import java.util.logging.Level;
import org.structr.common.Permission;
import org.structr.common.Permissions;
import org.structr.common.error.FrameworkException;
import org.structr.core.GraphObject;
import org.structr.core.entity.AbstractNode;
import org.structr.core.entity.Principal;
import org.structr.schema.action.ActionContext;
import org.structr.schema.action.Function;

/**
 *
 */
public class RevokeFunction extends Function<Object, Object> {

	public static final String ERROR_MESSAGE_REVOKE    = "Usage: ${revoke(principal, node, permissions)}. Example: ${revoke(me, this, 'write, delete'))}";
	public static final String ERROR_MESSAGE_REVOKE_JS = "Usage: ${{Structr.revoke(principal, node, permissions)}}. Example: ${{Structr.revoke(Structr.('me'), Structr.this, 'write, delete'))}}";

	@Override
	public String getName() {
		return "revoke()";
	}

	@Override
	public Object apply(final ActionContext ctx, final GraphObject entity, final Object[] sources) throws FrameworkException {

		if (arrayHasLengthAndAllElementsNotNull(sources, 3)) {

			if (sources[0] instanceof Principal) {

				final Principal principal = (Principal)sources[0];

				if (sources[1] instanceof AbstractNode) {

					final AbstractNode node = (AbstractNode)sources[1];

					if (sources[2] instanceof String) {

						final String[] parts = ((String)sources[2]).split("[,]+");
						for (final String part : parts) {

							final String trimmedPart = part.trim();
							if (trimmedPart.length() > 0) {

								final Permission permission = Permissions.valueOf(trimmedPart);
								if (permission != null) {

									node.revoke(permission, principal);

								} else {

									logger.log(Level.WARNING, "Error: unknown permission \"{0}\". Parameters: {1}", new Object[] { trimmedPart, getParametersAsString(sources) });
									return "Error: unknown permission " + trimmedPart;
								}
							}
						}

						return "";

					} else {

						logger.log(Level.WARNING, "Error: third argument is not a string. Parameters: {0}", getParametersAsString(sources));
						return "Error: third argument is not a string.";
					}

				} else {

					logger.log(Level.WARNING, "Error: second argument is not a node. Parameters: {0}", getParametersAsString(sources));
					return "Error: second argument is not a node.";
				}

			} else {

				logger.log(Level.WARNING, "Error: first argument is not of type Principal. Parameters: {0}", getParametersAsString(sources));
				return "Error: first argument is not of type Principal.";
			}

		} else {

			logParameterError(entity, sources, ctx.isJavaScriptContext());

			return usage(ctx.isJavaScriptContext());

		}
	}

	@Override
	public String usage(boolean inJavaScriptContext) {
		return (inJavaScriptContext ? ERROR_MESSAGE_REVOKE_JS : ERROR_MESSAGE_REVOKE);
	}

	@Override
	public String shortDescription() {
		return "Revokes the given permissions on the given entity from a user";
	}

}
